package erl.bilico.data.repository

import erl.bilico.platform.dlDownloadFileRange
import erl.bilico.platform.dlFileExists
import erl.bilico.platform.dlFileLength
import erl.bilico.platform.dlProbeContentLength
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class DownloadHttpException(val code: Int, message: String) : Exception(message)

/**
 * 负责单条媒体流（视频轨 / 音频轨）的断点续传下载与候选 CDN 回退。
 */
internal object DownloadTransfer {

    suspend fun probeContentLength(
        candidates: List<String>,
        headers: Map<String, String>,
    ): Long {
        for (url in candidates) {
            if (url.isBlank()) continue
            currentCoroutineContext().ensureActive()
            val length = runCatching { dlProbeContentLength(url, headers) }.getOrDefault(-1L)
            if (length > 0L) return length
        }
        return -1L
    }

    suspend fun downloadWithFallback(
        candidates: List<String>,
        headers: Map<String, String>,
        partPath: String,
        finalPath: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit,
    ): Long {
        if (dlFileExists(finalPath) && dlFileLength(finalPath) > 0L) {
            val size = dlFileLength(finalPath)
            onProgress(size, size, 0L)
            return size
        }
        val urls = candidates.filter { it.isNotBlank() }
        if (urls.isEmpty()) throw IllegalStateException("没有可用的下载地址")

        var lastError: Throwable? = null
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            try {
                return dlDownloadFileRange(
                    url = url,
                    headers = headers,
                    partPath = partPath,
                    finalPath = finalPath,
                    onProgress = onProgress,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }
}
