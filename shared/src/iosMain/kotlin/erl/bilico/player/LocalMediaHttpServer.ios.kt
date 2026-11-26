package erl.bilico.player

import erl.bilico.platform.dlFileLength
import erl.bilico.platform.logWarn
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.darwin.DISPATCH_QUEUE_CONCURRENT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.posix.AF_INET
import platform.posix.INADDR_LOOPBACK
import platform.posix.SEEK_SET
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseeko
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import kotlin.concurrent.Volatile

/**
 * 极简 127.0.0.1 回环 HTTP/1.1 Range 服务，专供 iOS AVPlayer 播放本地离线缓存的 fMP4 (.m4s) / MP4。
 *
 * iOS AVFoundation 的 HLS v7 fMP4 BYTERANGE 不支持自定义 scheme（如 bilicohls://），
 * 经本地 loopback 提供标准 HTTP 206 Partial Content 后，可完整复用苹果原生 HLS/MP4 播放、拖拽、PiP 与后台播控管线。
 */
@OptIn(ExperimentalForeignApi::class)
internal class LocalMediaHttpServer private constructor(
    private val listenFd: Int,
    val port: Int,
    private val memoryFiles: Map<String, Pair<String, ByteArray>>,
    private val diskFiles: Map<String, Pair<String, String>>,
) {
    @Volatile
    private var stopped = false

    private val acceptQueue = dispatch_queue_create("erl.bilico.localhttp.accept", null)
    private val workerQueue = dispatch_queue_create("erl.bilico.localhttp.worker", DISPATCH_QUEUE_CONCURRENT)

    init {
        dispatch_async(acceptQueue) {
            acceptLoop()
        }
    }

    fun urlFor(path: String): String = "http://127.0.0.1:$port$path"

    fun stop() {
        if (stopped) return
        stopped = true
        close(listenFd)
    }

    private fun acceptLoop() {
        while (!stopped) {
            val clientFd = accept(listenFd, null, null)
            if (clientFd < 0) {
                if (stopped) break
                continue
            }
            dispatch_async(workerQueue) {
                handleClient(clientFd)
            }
        }
    }

    private fun handleClient(clientFd: Int) {
        try {
            memScoped {
                val one = alloc<kotlinx.cinterop.IntVar>()
                one.value = 1
                setsockopt(clientFd, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<kotlinx.cinterop.IntVar>().convert())
            }
            val rawReq = readHttpHeader(clientFd) ?: return
            val lines = rawReq.split("\r\n")
            val requestLine = lines.firstOrNull()?.split(' ') ?: return
            if (requestLine.size < 2) return
            val method = requestLine[0].uppercase()
            if (method != "GET" && method != "HEAD") {
                sendAll(clientFd, "HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n".encodeToByteArray())
                return
            }
            val rawTarget = requestLine[1].substringBefore('?').let { t ->
                if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) {
                    "/" + t.substringAfter("://").substringAfter("/", "")
                } else {
                    t
                }
            }
            var rangeHeader: String? = null
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substringAfter(':').trim()
                    break
                }
            }

            memoryFiles[rawTarget]?.let { (mime, bytes) ->
                serveBytes(clientFd, method == "HEAD", mime, bytes, rangeHeader)
                return
            }
            diskFiles[rawTarget]?.let { (mime, filePath) ->
                serveDiskFile(clientFd, method == "HEAD", mime, filePath, rangeHeader)
                return
            }
            sendAll(clientFd, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encodeToByteArray())
        } catch (t: Throwable) {
            if (!stopped) logWarn("LocalMediaHttpServer", "handleClient err=${t.message}")
        } finally {
            close(clientFd)
        }
    }

    private fun readHttpHeader(fd: Int): String? {
        val buf = ByteArray(4096)
        val out = StringBuilder()
        while (out.length < 16_384) {
            val n = buf.usePinned { pinned ->
                recv(fd, pinned.addressOf(0), buf.size.convert(), 0).toInt()
            }
            if (n <= 0) break
            out.append(buf.decodeToString(0, n))
            if (out.contains("\r\n\r\n")) break
        }
        return out.toString().takeIf { it.isNotEmpty() }
    }

    private fun parseRange(rangeHeader: String?, total: Long): Pair<Long, Long>? {
        if (rangeHeader.isNullOrBlank() || total <= 0L) return null
        val spec = rangeHeader.removePrefix("bytes=").substringBefore(',').trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startStr = spec.substring(0, dash).trim()
        val endStr = spec.substring(dash + 1).trim()
        return if (startStr.isEmpty()) {
            val suffix = endStr.toLongOrNull() ?: return null
            if (suffix <= 0L) return null
            val start = (total - suffix).coerceAtLeast(0L)
            start to (total - 1L)
        } else {
            val start = startStr.toLongOrNull() ?: return null
            val end = if (endStr.isEmpty()) total - 1L else (endStr.toLongOrNull() ?: return null)
            if (start < 0L || start >= total || end < start) return null
            start to minOf(end, total - 1L)
        }
    }

    private fun serveBytes(
        fd: Int,
        headOnly: Boolean,
        mime: String,
        bytes: ByteArray,
        rangeHeader: String?,
    ) {
        val total = bytes.size.toLong()
        val range = parseRange(rangeHeader, total)
        val start = range?.first ?: 0L
        val end = range?.second ?: (total - 1L).coerceAtLeast(0L)
        val contentLen = if (total == 0L) 0L else (end - start + 1L)
        val header = buildResponseHeader(range != null, mime, start, end, total, contentLen)
        if (!sendAll(fd, header.encodeToByteArray()) || headOnly || contentLen <= 0L) return
        sendSlice(fd, bytes, start.toInt(), contentLen.toInt())
    }

    private fun serveDiskFile(
        fd: Int,
        headOnly: Boolean,
        mime: String,
        filePath: String,
        rangeHeader: String?,
    ) {
        val total = dlFileLength(filePath)
        if (total <= 0L) {
            sendAll(fd, "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encodeToByteArray())
            return
        }
        val range = parseRange(rangeHeader, total)
        if (rangeHeader != null && range == null) {
            sendAll(
                fd,
                "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .encodeToByteArray(),
            )
            return
        }
        val start = range?.first ?: 0L
        val end = range?.second ?: (total - 1L)
        val contentLen = end - start + 1L
        val header = buildResponseHeader(range != null, mime, start, end, total, contentLen)
        if (!sendAll(fd, header.encodeToByteArray()) || headOnly || contentLen <= 0L) return

        val fp = fopen(filePath, "rb") ?: return
        try {
            if (fseeko(fp, start, SEEK_SET) != 0) return
            val chunk = ByteArray(128 * 1024)
            var remaining = contentLen
            while (remaining > 0L && !stopped) {
                val want = minOf(remaining, chunk.size.toLong()).toInt()
                val read = chunk.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1uL, want.toULong(), fp).toInt()
                }
                if (read <= 0) break
                if (!sendSlice(fd, chunk, 0, read)) break
                remaining -= read.toLong()
            }
        } finally {
            fclose(fp)
        }
    }

    private fun buildResponseHeader(
        isPartial: Boolean,
        mime: String,
        start: Long,
        end: Long,
        total: Long,
        contentLen: Long,
    ): String = buildString {
        append(if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        append("Content-Type: ").append(mime).append("\r\n")
        append("Accept-Ranges: bytes\r\n")
        if (isPartial) {
            append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(total).append("\r\n")
        }
        append("Content-Length: ").append(contentLen).append("\r\n")
        append("Connection: close\r\n\r\n")
    }

    private fun sendAll(fd: Int, data: ByteArray): Boolean = sendSlice(fd, data, 0, data.size)

    private fun sendSlice(fd: Int, data: ByteArray, offset: Int, length: Int): Boolean {
        if (length <= 0) return true
        var sentTotal = 0
        data.usePinned { pinned ->
            while (sentTotal < length && !stopped) {
                val n = send(fd, pinned.addressOf(offset + sentTotal), (length - sentTotal).convert(), 0).toInt()
                if (n <= 0) return false
                sentTotal += n
            }
        }
        return sentTotal == length
    }

    companion object {
        fun start(
            memoryFiles: Map<String, Pair<String, ByteArray>> = emptyMap(),
            diskFiles: Map<String, Pair<String, String>> = emptyMap(),
        ): LocalMediaHttpServer? = memScoped {
            val fd = socket(AF_INET, SOCK_STREAM, 0)
            if (fd < 0) return null
            val one = alloc<kotlinx.cinterop.IntVar>()
            one.value = 1
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<kotlinx.cinterop.IntVar>().convert())
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<kotlinx.cinterop.IntVar>().convert())

            val addr = alloc<sockaddr_in>()
            addr.sin_len = sizeOf<sockaddr_in>().convert()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = 0u
            addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK)

            if (bind(fd, addr.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert()) != 0) {
                close(fd)
                return null
            }
            if (listen(fd, 16) != 0) {
                close(fd)
                return null
            }
            val bound = alloc<sockaddr_in>()
            val boundLen = alloc<socklen_tVar>()
            boundLen.value = sizeOf<sockaddr_in>().convert()
            if (getsockname(fd, bound.ptr.reinterpret<sockaddr>(), boundLen.ptr) != 0) {
                close(fd)
                return null
            }
            val port = ntohs(bound.sin_port)
            if (port <= 0) {
                close(fd)
                return null
            }
            LocalMediaHttpServer(fd, port, memoryFiles, diskFiles)
        }

        private fun htonl(v: UInt): UInt =
            ((v and 0x000000FFu) shl 24) or
                ((v and 0x0000FF00u) shl 8) or
                ((v and 0x00FF0000u) shr 8) or
                ((v and 0xFF000000u) shr 24)

        private fun ntohs(v: UShort): Int {
            val x = v.toInt()
            return ((x and 0xFF) shl 8) or ((x shr 8) and 0xFF)
        }
    }
}
