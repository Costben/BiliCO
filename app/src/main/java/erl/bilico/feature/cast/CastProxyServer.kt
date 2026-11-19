package erl.bilico.feature.cast

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 本地轻量 HTTP 中转代理：帮电视加上 B 站需要的 `User-Agent` / `Referer` 并透传 `Range` 请求。
 */
internal class CastProxyServer(
    private val http: OkHttpClient,
) {
    @Volatile
    private var upstreamUrl: String = ""

    @Volatile
    private var upstreamHeaders: Map<String, String> = emptyMap()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    private var pool: ExecutorService? = null

    val port: Int get() = serverSocket?.localPort ?: -1

    @Synchronized
    fun startIfNeeded(): Int {
        serverSocket?.takeIf { !it.isClosed }?.let { return it.localPort }
        val socket = ServerSocket(0)
        serverSocket = socket
        val executor = Executors.newCachedThreadPool()
        pool = executor
        acceptThread = Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                executor.execute { handleClient(client) }
            }
        }.apply {
            name = "CastProxyAccept"
            isDaemon = true
            start()
        }
        return socket.localPort
    }

    fun updateTarget(url: String, headers: Map<String, String>): String {
        upstreamUrl = url
        upstreamHeaders = headers
        return "/stream_${System.currentTimeMillis()}.mp4"
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
        pool?.shutdownNow()
        pool = null
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0)?.uppercase() ?: return
            val reqHeaders = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    reqHeaders[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
            }

            val out = socket.getOutputStream()
            val target = upstreamUrl
            if (target.isBlank()) {
                writeSimpleStatus(out, "404 Not Found")
                return
            }

            val builder = Request.Builder().url(target)
            upstreamHeaders.forEach { (k, v) -> builder.header(k, v) }
            reqHeaders["range"]?.let { builder.header("Range", it) }
            if (method == "HEAD") builder.head() else builder.get()

            http.newCall(builder.build()).execute().use { resp ->
                val statusText = "${resp.code} ${resp.message.ifBlank { "OK" }}"
                val headerBlock = buildString {
                    append("HTTP/1.1 $statusText\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Connection: close\r\n")
                    resp.header("Content-Type")?.let { append("Content-Type: $it\r\n") }
                        ?: append("Content-Type: video/mp4\r\n")
                    resp.header("Content-Range")?.let { append("Content-Range: $it\r\n") }
                    resp.header("Content-Length")?.let { append("Content-Length: $it\r\n") }
                    append("\r\n")
                }
                out.write(headerBlock.toByteArray(Charsets.ISO_8859_1))
                if (method != "HEAD") {
                    val bodyStream = resp.body.byteStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = bodyStream.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                    }
                }
                out.flush()
            }
        }
    }

    private fun writeSimpleStatus(out: OutputStream, status: String) {
        val text = "HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        runCatching {
            out.write(text.toByteArray(Charsets.ISO_8859_1))
            out.flush()
        }
    }
}
