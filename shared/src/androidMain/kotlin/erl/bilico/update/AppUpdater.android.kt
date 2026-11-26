package erl.bilico.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import erl.bilico.platform.AndroidPlatformBridge
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.openExternalUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

private const val ANDROID_UPDATE_UA = "BiliCO-Android"

actual fun updatePlatformUserAgent(): String = ANDROID_UPDATE_UA

actual fun preferredReleaseAssetExtension(): String = ".apk"

actual fun supportsInAppUpdate(): Boolean = true

actual fun openPlatformUpdateDownload(context: PlatformContext, info: UpdateInfo) {
    openExternalUrl(context, info.apkUrl ?: info.pageUrl)
}

actual suspend fun downloadPlatformUpdatePackage(
    context: PlatformContext,
    url: String,
    version: String,
    force: Boolean,
    onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
): DownloadedUpdatePackage {
    val file = AndroidApkDownloader.downloadApk(
        context = context.androidContext,
        url = url,
        version = version,
        force = force,
        onProgress = onProgress,
    )
    return DownloadedUpdatePackage(
        path = file.absolutePath,
        sizeBytes = file.length(),
    )
}

actual fun installPlatformUpdatePackage(
    context: PlatformContext,
    pkg: DownloadedUpdatePackage,
): Result<Unit> = runCatching {
    val androidContext = context.androidContext
    val file = File(pkg.path)
    if (!file.exists() || file.length() <= 0L) {
        throw IOException("安装包文件不存在")
    }
    val uri = FileProvider.getUriForFile(
        androidContext,
        "${androidContext.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val host = AndroidPlatformBridge.currentActivity() ?: androidContext
    host.startActivity(intent)
}

/**
 * Android 应用内 APK 多镜像测速与分片断点下载器：
 * - 多镜像（gh-proxy / ghfast / 直连）并发测速择优
 * - HTTP Range 4 路分片并发下载与单流断点续传回落
 * - 下载完成后校验 APK 归档包名，避免安装损坏包或错误包名
 */
private object AndroidApkDownloader {
    private val MIRRORS = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "",
    )
    private const val SEGMENTS = 4
    private const val PROBE_BYTES = 64 * 1024L
    private const val PROBE_TIMEOUT_MS = 1500L

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 16
            },
        )
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadApk(
        context: Context,
        url: String,
        version: String,
        force: Boolean = false,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val safeVersion = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, "BiliCO-$safeVersion.apk")

        dir.listFiles()?.forEach { file ->
            if (!file.name.startsWith("BiliCO-$safeVersion.apk")) {
                file.delete()
            } else if (force) {
                file.delete()
            }
        }
        if (!force && target.exists() && target.length() > 1_000_000L && isValidApk(context, target)) {
            val len = target.length()
            onProgress(1f, len, len)
            return@withContext target
        }

        val candidates = raceMirrors(url)
        var lastError: Throwable? = null
        for (candidate in candidates) {
            coroutineContext.ensureActive()
            try {
                downloadFromCandidate(candidate, target, onProgress)
                verifyApkOrThrow(context, target)
                return@withContext target
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IOException("下载更新包失败")
    }

    private fun readPackageArchiveInfo(context: Context, file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }

    private fun isValidApk(context: Context, file: File): Boolean = runCatching {
        val info = readPackageArchiveInfo(context, file)
        info != null && info.packageName == context.packageName
    }.getOrDefault(false)

    private fun verifyApkOrThrow(context: Context, file: File) {
        val archive = readPackageArchiveInfo(context, file)
        if (archive == null) {
            file.delete()
            throw IOException("安装包已损坏或下载不完整")
        }
        if (archive.packageName != context.packageName) {
            file.delete()
            throw IOException("安装包签名或包名不匹配 (${archive.packageName})")
        }
    }

    private suspend inline fun <T> executeCancellable(
        call: Call,
        block: (Response) -> T,
    ): T {
        val handle = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        return try {
            call.execute().use(block)
        } finally {
            handle.dispose()
        }
    }

    private suspend fun raceMirrors(url: String): List<String> = coroutineScope {
        val all = MIRRORS.map { prefix -> if (prefix.isEmpty()) url else prefix + url }
        val jobs = all.map { candidate ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    runCatching {
                        val req = Request.Builder()
                            .url(candidate)
                            .header("User-Agent", ANDROID_UPDATE_UA)
                            .header("Range", "bytes=0-${PROBE_BYTES - 1}")
                            .build()
                        executeCancellable(client.newCall(req)) { resp ->
                            if (!resp.isSuccessful) return@executeCancellable null
                            val stream = resp.body.byteStream()
                            val buf = ByteArray(16 * 1024)
                            var got = 0L
                            while (got < PROBE_BYTES) {
                                coroutineContext.ensureActive()
                                val n = stream.read(buf)
                                if (n < 0) break
                                got += n
                            }
                            if (got >= PROBE_BYTES / 2) candidate else null
                        }
                    }.getOrNull()
                }
            }
        }
        val pending = jobs.toMutableList()
        var winner: String? = null
        while (pending.isNotEmpty() && winner == null) {
            val finished = select {
                pending.forEach { job -> job.onAwait { job to it } }
            }
            pending.remove(finished.first)
            if (finished.second != null) winner = finished.second
        }
        pending.forEach { it.cancel() }
        if (winner != null) listOf(winner) + all.filter { it != winner } else all
    }

    private data class ProbeResult(val total: Long, val ranged: Boolean)

    private suspend fun probe(url: String): ProbeResult {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", ANDROID_UPDATE_UA)
            .header("Range", "bytes=0-0")
            .build()
        return executeCancellable(client.newCall(req)) { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            if (resp.code == 206) {
                val cr = resp.header("Content-Range").orEmpty()
                val slash = cr.lastIndexOf('/')
                val total = if (slash >= 0) cr.substring(slash + 1).trim().toLongOrNull() ?: -1L else -1L
                if (total > 0L) return@executeCancellable ProbeResult(total, true)
            }
            val len = resp.body.contentLength()
            ProbeResult(len, false)
        }
    }

    private suspend fun downloadFromCandidate(
        url: String,
        target: File,
        onProgress: (Float, Long, Long) -> Unit,
    ) {
        val info = probe(url)
        if (info.ranged && info.total > SEGMENTS * 256 * 1024L) {
            downloadSegmented(url, target, info.total, onProgress)
        } else {
            val part = File(target.parentFile, "${target.name}.part")
            downloadSingle(url, part, target, onProgress)
        }
    }

    private suspend fun downloadSegmented(
        url: String,
        target: File,
        total: Long,
        onProgress: (Float, Long, Long) -> Unit,
    ) = coroutineScope {
        val parts = (0 until SEGMENTS).map { i -> File(target.parentFile, "${target.name}.part$i") }
        val chunk = total / SEGMENTS
        val ranges = (0 until SEGMENTS).map { i ->
            val start = i * chunk
            val end = if (i == SEGMENTS - 1) total - 1 else (i + 1) * chunk - 1
            start to end
        }

        val counter = AtomicLong(0L)
        for (i in 0 until SEGMENTS) {
            val expected = ranges[i].second - ranges[i].first + 1
            val len = if (parts[i].exists()) parts[i].length() else 0L
            if (len > expected) {
                parts[i].delete()
            } else {
                counter.addAndGet(len)
            }
        }
        onProgress((counter.get().toFloat() / total).coerceIn(0f, 1f), counter.get(), total)

        val lastTick = AtomicLong(0L)
        fun report(force: Boolean = false) {
            val now = System.currentTimeMillis()
            val prev = lastTick.get()
            if (!force && now - prev < 80L) return
            if (!force && !lastTick.compareAndSet(prev, now)) return
            if (force) lastTick.set(now)
            val done = counter.get()
            onProgress((done.toFloat() / total).coerceIn(0f, 1f), done, total)
        }

        (0 until SEGMENTS).map { i ->
            async(Dispatchers.IO) {
                val (start, end) = ranges[i]
                val expected = end - start + 1
                val part = parts[i]
                val existing = if (part.exists()) part.length() else 0L
                if (existing >= expected) return@async
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", ANDROID_UPDATE_UA)
                    .header("Range", "bytes=${start + existing}-$end")
                    .build()
                executeCancellable(client.newCall(req)) { resp ->
                    if (resp.code != 206) throw IOException("分段下载被拒绝 (HTTP ${resp.code})")
                    val body = resp.body
                    FileOutputStream(part, existing > 0L).use { out ->
                        val buf = ByteArray(64 * 1024)
                        val stream = body.byteStream()
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = stream.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            counter.addAndGet(read.toLong())
                            report()
                        }
                    }
                }
                if (part.length() != expected) {
                    throw IOException("分段 $i 长度不符：${part.length()} / $expected")
                }
            }
        }.awaitAll()

        report(force = true)

        val merged = File(target.parentFile, "${target.name}.merged")
        FileOutputStream(merged, false).use { out ->
            val buf = ByteArray(128 * 1024)
            for (part in parts) {
                FileInputStream(part).use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val r = input.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                    }
                }
            }
        }
        parts.forEach { it.delete() }
        if (target.exists()) target.delete()
        if (!merged.renameTo(target)) {
            merged.copyTo(target, overwrite = true)
            merged.delete()
        }
        onProgress(1f, total, total)
    }

    private suspend fun downloadSingle(
        url: String,
        part: File,
        target: File,
        onProgress: (Float, Long, Long) -> Unit,
        retried416: Boolean = false,
    ) {
        val existing = if (part.exists()) part.length() else 0L
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", ANDROID_UPDATE_UA)
        if (existing > 0L) {
            reqBuilder.header("Range", "bytes=$existing-")
        }
        val shouldRetry416 = executeCancellable(client.newCall(reqBuilder.build())) { resp ->
            if (resp.code == 416 && existing > 0L && !retried416) {
                part.delete()
                return@executeCancellable true
            }
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body
            val append = resp.code == 206 && existing > 0L
            val contentLen = body.contentLength()
            val total = if (contentLen > 0) contentLen + (if (append) existing else 0L) else -1L
            var downloaded = if (append) existing else 0L
            onProgress(if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f, downloaded, total)

            var lastEmit = 0L
            FileOutputStream(part, append).use { out ->
                val buffer = ByteArray(64 * 1024)
                val stream = body.byteStream()
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= 80L) {
                        lastEmit = now
                        val p = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                        onProgress(p, downloaded, total)
                    }
                }
            }
            if (total > 0 && downloaded < total) {
                throw IOException("下载未完成：$downloaded / $total")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            onProgress(1f, downloaded, total)
            false
        }
        if (shouldRetry416) {
            downloadSingle(url, part, target, onProgress, retried416 = true)
        }
    }
}
