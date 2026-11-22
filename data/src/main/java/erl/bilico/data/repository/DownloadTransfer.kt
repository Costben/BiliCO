package erl.bilico.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal class DownloadHttpException(val code: Int, message: String) : IOException(message)

/**
 * 负责单条媒体流（视频轨 / 音频轨）的断点续传下载与候选 CDN 回退。
 */
internal object DownloadTransfer {

    private const val BUFFER_SIZE = 32 * 1024
    private const val PROGRESS_INTERVAL_MS = 250L

    fun probeContentLength(
        client: OkHttpClient,
        candidates: List<String>,
        headers: Map<String, String>,
    ): Long {
        for (url in candidates) {
            if (url.isBlank()) continue
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("Range", "bytes=0-0")
            }.build()
            val length = runCatching {
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 206) {
                        parseContentRangeTotal(resp.header("Content-Range")) ?: -1L
                    } else if (resp.isSuccessful) {
                        resp.body.contentLength()
                    } else {
                        -1L
                    }
                }
            }.getOrDefault(-1L)
            if (length > 0L) return length
        }
        return -1L
    }

    suspend fun downloadWithFallback(
        client: OkHttpClient,
        candidates: List<String>,
        headers: Map<String, String>,
        partFile: File,
        finalFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit,
    ): Long {
        if (finalFile.exists() && finalFile.length() > 0L) {
            val size = finalFile.length()
            onProgress(size, size, 0L)
            return size
        }
        val urls = candidates.filter { it.isNotBlank() }
        if (urls.isEmpty()) throw IOException("没有可用的下载地址")

        var lastError: Throwable? = null
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            try {
                return downloadSingle(
                    client = client,
                    url = url,
                    headers = headers,
                    partFile = partFile,
                    finalFile = finalFile,
                    onProgress = onProgress,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IOException("下载失败")
    }

    private suspend fun downloadSingle(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        partFile: File,
        finalFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit,
    ): Long {
        var existing = if (partFile.exists()) partFile.length() else 0L
        val requestBuilder = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
            if (existing > 0L) header("Range", "bytes=$existing-")
        }
        val call = client.newCall(requestBuilder.build())
        val job = currentCoroutineContext()[Job]
        val cancelHandle = job?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (response.code == 416) {
                    val total = parseContentRangeTotal(response.header("Content-Range"))
                    if (total != null && total > 0L && existing >= total) {
                        if (!partFile.renameTo(finalFile)) {
                            partFile.copyTo(finalFile, overwrite = true)
                            partFile.delete()
                        }
                        onProgress(total, total, 0L)
                        return total
                    }
                    partFile.delete()
                    existing = 0L
                    throw IOException("断点范围失效，重试")
                }
                if (!response.isSuccessful) {
                    throw DownloadHttpException(response.code, "HTTP ${response.code}")
                }
                val append = response.code == 206 && existing > 0L
                val startBytes = if (append) existing else 0L
                val contentLength = response.body.contentLength()
                val totalBytes = when {
                    response.code == 206 -> {
                        parseContentRangeTotal(response.header("Content-Range"))
                            ?: if (contentLength > 0L) startBytes + contentLength else -1L
                    }
                    contentLength > 0L -> contentLength
                    else -> -1L
                }
                val bodyStream = response.body.byteStream()
                var currentBytes = startBytes
                var windowBytes = 0L
                var lastReportAt = System.currentTimeMillis()
                onProgress(currentBytes, totalBytes, 0L)

                FileOutputStream(partFile, append).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = bodyStream.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        currentBytes += read
                        windowBytes += read
                        val now = System.currentTimeMillis()
                        val elapsed = now - lastReportAt
                        if (elapsed >= PROGRESS_INTERVAL_MS) {
                            val speed = (windowBytes * 1000L / elapsed).coerceAtLeast(0L)
                            onProgress(currentBytes, totalBytes, speed)
                            windowBytes = 0L
                            lastReportAt = now
                        }
                    }
                    out.flush()
                }
                if (totalBytes > 0L && currentBytes < totalBytes) {
                    throw IOException("传输中断 ($currentBytes/$totalBytes)")
                }
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                val finalLength = finalFile.length().coerceAtLeast(currentBytes)
                onProgress(finalLength, finalLength, 0L)
                return finalLength
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val slash = header.lastIndexOf('/')
        if (slash < 0 || slash == header.lastIndex) return null
        val tail = header.substring(slash + 1).trim()
        if (tail == "*") return null
        return tail.toLongOrNull()
    }
}
