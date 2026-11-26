package erl.bilico.platform

import erl.bilico.data.repository.DownloadHttpException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSLock
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.setValue
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.darwin.NSObject
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val DL_PROGRESS_INTERVAL_MS = 250L

actual class DownloadLock actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun downloadsRootPath(context: PlatformContext): String {
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: "/tmp"
    val dir = "$base/downloads"
    dlEnsureDir(dir)
    runCatching {
        NSURL.fileURLWithPath(dir).setResourceValue(
            NSNumber(bool = true),
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        )
    }
    return dir
}

@OptIn(ExperimentalForeignApi::class)
actual fun dlEnsureDir(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
}

actual fun dlFileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

@OptIn(ExperimentalForeignApi::class)
actual fun dlFileLength(path: String): Long {
    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return 0L
    val num = attrs[NSFileSize] as? NSNumber ?: return 0L
    return num.longLongValue.coerceAtLeast(0L)
}

@OptIn(ExperimentalForeignApi::class)
actual fun dlReadText(path: String): String? =
    NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)

@OptIn(ExperimentalForeignApi::class)
actual fun dlReadBytes(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val len = data.length.toInt()
    if (len <= 0) return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun dlWriteTextAtomic(path: String, content: String): Boolean {
    val parent = path.substringBeforeLast('/', "")
    if (parent.isNotEmpty()) dlEnsureDir(parent)
    return NSString.create(string = content).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun dlWriteBytes(path: String, bytes: ByteArray): Boolean {
    val parent = path.substringBeforeLast('/', "")
    if (parent.isNotEmpty()) dlEnsureDir(parent)
    val data = if (bytes.isEmpty()) {
        NSData()
    } else {
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }
    return data.writeToFile(path, atomically = true)
}

@OptIn(ExperimentalForeignApi::class)
actual fun dlMoveOrReplace(srcPath: String, dstPath: String): Boolean {
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(srcPath)) return false
    val parent = dstPath.substringBeforeLast('/', "")
    if (parent.isNotEmpty()) dlEnsureDir(parent)
    if (fm.fileExistsAtPath(dstPath)) {
        fm.removeItemAtPath(dstPath, error = null)
    }
    if (fm.moveItemAtPath(srcPath, toPath = dstPath, error = null)) return true
    if (fm.copyItemAtPath(srcPath, toPath = dstPath, error = null)) {
        fm.removeItemAtPath(srcPath, error = null)
        return true
    }
    return false
}

@OptIn(ExperimentalForeignApi::class)
actual fun dlDeleteFile(path: String) {
    if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun dlDeleteDirectory(path: String) {
    if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun dlDirectoryDiskUsage(path: String, excludeNames: Set<String>): Long {
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(path)) return 0L
    val enumerator = fm.enumeratorAtPath(path) ?: return 0L
    var total = 0L
    while (true) {
        val rel = enumerator.nextObject() as? String ?: break
        val name = rel.substringAfterLast('/')
        if (name in excludeNames) continue
        val fullPath = "$path/$rel"
        val attrs = fm.attributesOfItemAtPath(fullPath, error = null) ?: continue
        val type = attrs[platform.Foundation.NSFileType] as? String
        if (type == platform.Foundation.NSFileTypeRegular) {
            val size = (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
            if (size > 0L) total += size
        }
    }
    return total
}

actual fun dlPathToFileUri(path: String): String =
    NSURL.fileURLWithPath(path).absoluteString ?: "file://$path"

actual fun dlFileUriToPath(uri: String): String {
    if (!uri.startsWith("file://", ignoreCase = true)) return uri
    return NSURL.URLWithString(uri)?.path ?: uri.removePrefix("file://")
}

private fun parseContentRangeTotalIos(header: String?): Long? {
    if (header.isNullOrBlank()) return null
    val slash = header.lastIndexOf('/')
    if (slash < 0 || slash == header.lastIndex) return null
    val tail = header.substring(slash + 1).trim()
    if (tail == "*") return null
    return tail.toLongOrNull()
}

private fun NSHTTPURLResponse.headerIgnoreCase(name: String): String? {
    for ((k, v) in allHeaderFields) {
        val key = k as? String ?: continue
        if (key.equals(name, ignoreCase = true)) return v?.toString()
    }
    return null
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun dlProbeContentLength(
    url: String,
    headers: Map<String, String>,
): Long = suspendCancellableCoroutine { cont ->
    val nsUrl = NSURL.URLWithString(url) ?: run {
        cont.resume(-1L)
        return@suspendCancellableCoroutine
    }
    val req = NSMutableURLRequest(
        uRL = nsUrl,
        cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
        timeoutInterval = 10.0,
    )
    headers.forEach { (k, v) -> req.setValue(v, forHTTPHeaderField = k) }
    req.setValue("bytes=0-0", forHTTPHeaderField = "Range")

    val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { _, response, error ->
        if (!cont.isActive) return@dataTaskWithRequest
        if (error != null) {
            cont.resume(-1L)
            return@dataTaskWithRequest
        }
        val http = response as? NSHTTPURLResponse
        val status = http?.statusCode?.toInt() ?: -1
        val length = when {
            status == 206 -> parseContentRangeTotalIos(http?.headerIgnoreCase("Content-Range")) ?: -1L
            status in 200..299 -> http?.expectedContentLength?.takeIf { it > 0L } ?: -1L
            else -> -1L
        }
        cont.resume(length)
    }
    cont.invokeOnCancellation { task.cancel() }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun dlDownloadFileRange(
    url: String,
    headers: Map<String, String>,
    partPath: String,
    finalPath: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit,
): Long = suspendCancellableCoroutine { cont ->
    val nsUrl = NSURL.URLWithString(url) ?: run {
        cont.resumeWithException(IllegalArgumentException("无效的下载地址"))
        return@suspendCancellableCoroutine
    }
    val parent = partPath.substringBeforeLast('/', "")
    if (parent.isNotEmpty()) dlEnsureDir(parent)

    val existing = if (dlFileExists(partPath)) dlFileLength(partPath) else 0L
    val req = NSMutableURLRequest(
        uRL = nsUrl,
        cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
        timeoutInterval = 30.0,
    )
    headers.forEach { (k, v) -> req.setValue(v, forHTTPHeaderField = k) }
    if (existing > 0L) {
        req.setValue("bytes=$existing-", forHTTPHeaderField = "Range")
    }

    val ioLock = NSLock()
    var filePtr: CPointer<FILE>? = null
    var finished = false
    var currentBytes = 0L
    var totalBytes = -1L
    var windowBytes = 0L
    var lastReportAt = currentTimeMillis()
    var earlyError: Throwable? = null
    var completedVia416Length: Long? = null

    fun closeFileLocked() {
        val fp = filePtr ?: return
        filePtr = null
        fflush(fp)
        fclose(fp)
    }

    fun finishOnce(result: Result<Long>, session: NSURLSession) {
        val shouldResume: Boolean
        ioLock.lock()
        try {
            closeFileLocked()
            shouldResume = !finished
            finished = true
        } finally {
            ioLock.unlock()
        }
        session.finishTasksAndInvalidate()
        if (shouldResume && cont.isActive) {
            result.fold(
                onSuccess = { cont.resume(it) },
                onFailure = { cont.resumeWithException(it) },
            )
        }
    }

    val delegate = object : NSObject(), NSURLSessionDataDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveResponse: NSURLResponse,
            completionHandler: (NSURLSessionResponseDisposition) -> Unit,
        ) {
            val http = didReceiveResponse as? NSHTTPURLResponse
            val status = http?.statusCode?.toInt() ?: 0
            if (status == 416) {
                val total = parseContentRangeTotalIos(http?.headerIgnoreCase("Content-Range"))
                if (total != null && total > 0L && existing >= total) {
                    dlMoveOrReplace(partPath, finalPath)
                    onProgress(total, total, 0L)
                    ioLock.lock()
                    completedVia416Length = total
                    ioLock.unlock()
                } else {
                    dlDeleteFile(partPath)
                    ioLock.lock()
                    earlyError = IllegalStateException("断点范围失效，重试")
                    ioLock.unlock()
                }
                completionHandler(NSURLSessionResponseCancel)
                return
            }
            if (status !in 200..299) {
                ioLock.lock()
                earlyError = DownloadHttpException(status, "HTTP $status")
                ioLock.unlock()
                completionHandler(NSURLSessionResponseCancel)
                return
            }

            val append = status == 206 && existing > 0L
            val startBytes = if (append) existing else 0L
            val contentLen = http?.expectedContentLength ?: -1L
            val resolvedTotal = when {
                status == 206 -> {
                    parseContentRangeTotalIos(http?.headerIgnoreCase("Content-Range"))
                        ?: if (contentLen > 0L) startBytes + contentLen else -1L
                }
                contentLen > 0L -> contentLen
                else -> -1L
            }

            val opened = fopen(partPath, if (append) "ab" else "wb")
            if (opened == null) {
                ioLock.lock()
                earlyError = IllegalStateException("无法创建缓存文件")
                ioLock.unlock()
                completionHandler(NSURLSessionResponseCancel)
                return
            }

            ioLock.lock()
            filePtr = opened
            currentBytes = startBytes
            totalBytes = resolvedTotal
            windowBytes = 0L
            lastReportAt = currentTimeMillis()
            ioLock.unlock()

            onProgress(startBytes, resolvedTotal, 0L)
            completionHandler(NSURLSessionResponseAllow)
        }

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveData: NSData,
        ) {
            val chunkLen = didReceiveData.length
            if (chunkLen == 0uL) return
            val bytesPtr = didReceiveData.bytes ?: return

            var reportDownloaded = -1L
            var reportTotal = -1L
            var reportSpeed = 0L
            var writeFailed = false

            ioLock.lock()
            try {
                val fp = filePtr ?: return
                val written = fwrite(bytesPtr, 1uL, chunkLen, fp)
                if (written != chunkLen) {
                    writeFailed = true
                    earlyError = IllegalStateException("写入离线缓存失败，磁盘空间可能不足")
                    closeFileLocked()
                } else {
                    val delta = chunkLen.toLong()
                    currentBytes += delta
                    windowBytes += delta
                    val now = currentTimeMillis()
                    val elapsed = now - lastReportAt
                    if (elapsed >= DL_PROGRESS_INTERVAL_MS) {
                        reportDownloaded = currentBytes
                        reportTotal = totalBytes
                        reportSpeed = (windowBytes * 1000L / elapsed).coerceAtLeast(0L)
                        windowBytes = 0L
                        lastReportAt = now
                    }
                }
            } finally {
                ioLock.unlock()
            }

            if (writeFailed) {
                dataTask.cancel()
                return
            }
            if (reportDownloaded >= 0L) {
                onProgress(reportDownloaded, reportTotal, reportSpeed)
            }
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val early: Throwable?
            val done416: Long?
            val finalCur: Long
            val finalTotal: Long
            ioLock.lock()
            try {
                closeFileLocked()
                early = earlyError
                done416 = completedVia416Length
                finalCur = currentBytes
                finalTotal = totalBytes
            } finally {
                ioLock.unlock()
            }

            if (done416 != null) {
                finishOnce(Result.success(done416), session)
                return
            }
            if (early != null) {
                finishOnce(Result.failure(early), session)
                return
            }
            if (didCompleteWithError != null) {
                finishOnce(
                    Result.failure(IllegalStateException(didCompleteWithError.localizedDescription)),
                    session,
                )
                return
            }
            if (finalTotal > 0L && finalCur < finalTotal) {
                finishOnce(
                    Result.failure(IllegalStateException("传输中断 ($finalCur/$finalTotal)")),
                    session,
                )
                return
            }
            if (!dlMoveOrReplace(partPath, finalPath)) {
                finishOnce(
                    Result.failure(IllegalStateException("保存离线缓存文件失败")),
                    session,
                )
                return
            }
            val finalLength = dlFileLength(finalPath).coerceAtLeast(finalCur)
            onProgress(finalLength, finalLength, 0L)
            finishOnce(Result.success(finalLength), session)
        }
    }

    val config = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
        timeoutIntervalForRequest = 30.0
        timeoutIntervalForResource = 3600.0
    }
    val session = NSURLSession.sessionWithConfiguration(config, delegate = delegate, delegateQueue = null)
    val task = session.dataTaskWithRequest(req)
    cont.invokeOnCancellation {
        ioLock.lock()
        try {
            finished = true
            closeFileLocked()
        } finally {
            ioLock.unlock()
        }
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}
