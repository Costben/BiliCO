package erl.bilico.platform

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

actual class DownloadLock actual constructor() {
    private val lock = ReentrantLock()
    actual fun <T> withLock(block: () -> T): T = lock.withLock(block)
}

private val downloadHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

actual fun downloadsRootPath(context: PlatformContext): String {
    val androidCtx = context.androidContext
    val base = androidCtx.getExternalFilesDir(null) ?: androidCtx.filesDir
    val dir = File(base, "downloads")
    if (!dir.exists()) dir.mkdirs()
    val nomedia = File(dir, ".nomedia")
    if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
    return dir.absolutePath
}

actual fun dlEnsureDir(path: String) {
    val dir = File(path)
    if (!dir.exists()) dir.mkdirs()
}

actual fun dlFileExists(path: String): Boolean = File(path).exists()

actual fun dlFileLength(path: String): Long = File(path).takeIf { it.exists() }?.length() ?: 0L

actual fun dlReadText(path: String): String? {
    val file = File(path)
    if (!file.exists()) return null
    return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
}

actual fun dlReadBytes(path: String): ByteArray? {
    val file = File(path)
    if (!file.exists()) return null
    return runCatching { file.readBytes() }.getOrNull()
}

actual fun dlWriteTextAtomic(path: String, content: String): Boolean {
    val target = File(path)
    target.parentFile?.mkdirs()
    val tmp = File(target.parentFile, "${target.name}.tmp")
    return runCatching {
        tmp.writeText(content, Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            target.writeText(content, Charsets.UTF_8)
            tmp.delete()
        } else if (!tmp.renameTo(target)) {
            target.writeText(content, Charsets.UTF_8)
            tmp.delete()
        }
        true
    }.getOrDefault(false)
}

actual fun dlWriteBytes(path: String, bytes: ByteArray): Boolean {
    val target = File(path)
    target.parentFile?.mkdirs()
    return runCatching {
        target.writeBytes(bytes)
        true
    }.getOrDefault(false)
}

actual fun dlMoveOrReplace(srcPath: String, dstPath: String): Boolean {
    val src = File(srcPath)
    val dst = File(dstPath)
    if (!src.exists()) return false
    dst.parentFile?.mkdirs()
    if (dst.exists()) dst.delete()
    if (src.renameTo(dst)) return true
    return runCatching {
        src.copyTo(dst, overwrite = true)
        src.delete()
        true
    }.getOrDefault(false)
}

actual fun dlDeleteFile(path: String) {
    runCatching { File(path).delete() }
}

actual fun dlDeleteDirectory(path: String) {
    runCatching { File(path).deleteRecursively() }
}

actual fun dlDirectoryDiskUsage(path: String, excludeNames: Set<String>): Long {
    val root = File(path)
    if (!root.exists()) return 0L
    var total = 0L
    fun walk(file: File) {
        if (file.name in excludeNames) return
        if (file.isDirectory) {
            file.listFiles()?.forEach(::walk)
        } else {
            total += file.length()
        }
    }
    walk(root)
    return total
}

actual fun dlPathToFileUri(path: String): String = Uri.fromFile(File(path)).toString()

actual fun dlFileUriToPath(uri: String): String {
    if (!uri.startsWith("file://")) return uri
    return runCatching { Uri.parse(uri).path ?: uri.removePrefix("file://") }.getOrElse { uri.removePrefix("file://") }
}

actual suspend fun dlProbeContentLength(
    url: String,
    headers: Map<String, String>,
): Long = withContext(ioDispatcher) {
    val req = Request.Builder()
        .url(url)
        .apply {
            headers.forEach { (k, v) -> header(k, v) }
            header("Range", "bytes=0-0")
        }
        .build()
    runCatching {
        downloadHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) return@use -1L
            val contentRange = resp.header("Content-Range")
            if (contentRange != null) {
                val total = contentRange.substringAfterLast('/', "").trim().toLongOrNull()
                if (total != null && total > 0L) return@use total
            }
            resp.header("Content-Length")?.trim()?.toLongOrNull()?.takeIf { it > 0L } ?: -1L
        }
    }.getOrDefault(-1L)
}

actual suspend fun dlDownloadFileRange(
    url: String,
    headers: Map<String, String>,
    partPath: String,
    finalPath: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit,
): Long = withContext(ioDispatcher) {
    val partFile = File(partPath)
    val finalFile = File(finalPath)
    partFile.parentFile?.mkdirs()
    finalFile.parentFile?.mkdirs()

    if (finalFile.exists() && finalFile.length() > 0L) {
        val len = finalFile.length()
        onProgress(len, len, 0L)
        return@withContext len
    }

    var existing = partFile.takeIf { it.exists() }?.length() ?: 0L
    val req = Request.Builder()
        .url(url)
        .apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (existing > 0L) header("Range", "bytes=$existing-")
        }
        .build()

    val call = downloadHttpClient.newCall(req)
    val ctx = currentCoroutineContext()
    try {
        call.execute().use { resp ->
            ctx.ensureActive()
            if (resp.code == 416 && existing > 0L) {
                if (dlMoveOrReplace(partPath, finalPath)) {
                    onProgress(existing, existing, 0L)
                    return@withContext existing
                }
            }
            if (!resp.isSuccessful && resp.code != 206) {
                error("HTTP ${resp.code}")
            }
            val append = resp.code == 206 && existing > 0L
            if (!append) existing = 0L

            val contentRangeTotal = resp.header("Content-Range")
                ?.substringAfterLast('/', "")
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            val bodyLen = resp.body.contentLength().takeIf { it > 0L } ?: -1L
            val totalBytes = contentRangeTotal ?: if (bodyLen > 0L) existing + bodyLen else -1L

            var downloaded = existing
            var windowBytes = 0L
            var windowStartNs = System.nanoTime()
            var speed = 0L
            val buffer = ByteArray(64 * 1024)

            resp.body.byteStream().use { input ->
                FileOutputStream(partFile, append).use { output ->
                    while (true) {
                        ctx.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        windowBytes += read
                        val nowNs = System.nanoTime()
                        val elapsedNs = nowNs - windowStartNs
                        if (elapsedNs >= 400_000_000L) {
                            speed = (windowBytes * 1_000_000_000L) / elapsedNs
                            windowBytes = 0L
                            windowStartNs = nowNs
                            onProgress(downloaded, totalBytes, speed)
                        }
                    }
                    output.flush()
                }
            }

            if (totalBytes > 0L && downloaded < totalBytes) {
                error("下载不完整 ($downloaded/$totalBytes)")
            }
            check(dlMoveOrReplace(partPath, finalPath)) { "无法保存缓存文件" }
            onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded, speed)
            downloaded
        }
    } catch (e: CancellationException) {
        call.cancel()
        throw e
    }
}
