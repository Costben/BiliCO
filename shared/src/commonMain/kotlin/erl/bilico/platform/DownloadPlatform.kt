package erl.bilico.platform

/**
 * 跨平台可重入互斥锁，供离线缓存索引与队列状态并发保护使用。
 */
expect class DownloadLock() {
    fun <T> withLock(block: () -> T): T
}

/** 返回并确保离线缓存根目录存在（iOS 下位于 Application Support/downloads 且排除 iCloud 备份）。 */
expect fun downloadsRootPath(context: PlatformContext): String

expect fun dlEnsureDir(path: String)

expect fun dlFileExists(path: String): Boolean

expect fun dlFileLength(path: String): Long

expect fun dlReadText(path: String): String?

expect fun dlReadBytes(path: String): ByteArray?

expect fun dlWriteTextAtomic(path: String, content: String): Boolean

expect fun dlWriteBytes(path: String, bytes: ByteArray): Boolean

expect fun dlMoveOrReplace(srcPath: String, dstPath: String): Boolean

expect fun dlDeleteFile(path: String)

expect fun dlDeleteDirectory(path: String)

expect fun dlDirectoryDiskUsage(path: String, excludeNames: Set<String> = emptySet()): Long

expect fun dlPathToFileUri(path: String): String

expect fun dlFileUriToPath(uri: String): String

/** 探测远程媒体流总字节数（`Range: bytes=0-0`），失败返回 -1。 */
expect suspend fun dlProbeContentLength(
    url: String,
    headers: Map<String, String>,
): Long

/**
 * 流式断点续传下载单个文件。支持 `Range: bytes=<existing>-`、`206`/`416` 处理、
 * 实时写入磁盘（零整文件内存缓冲）与协程取消立即中断底层传输。
 */
expect suspend fun dlDownloadFileRange(
    url: String,
    headers: Map<String, String>,
    partPath: String,
    finalPath: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit,
): Long
