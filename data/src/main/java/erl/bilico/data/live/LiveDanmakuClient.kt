package erl.bilico.data.live

import android.util.Log
import erl.bilico.data.model.LiveDanmakuMessage
import erl.bilico.data.model.LiveWatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

enum class LiveDanmakuState { Idle, Connecting, Connected, Failed }

/**
 * 直播弹幕长连接：认证、心跳、断线重连，只把弹幕正文往上抛。
 *
 * 一条 WebSocket 连一个房间，[connect] 会先断开旧的连接；[disconnect] 之后不再自动重连。
 */
class LiveDanmakuClient(
    private val scope: CoroutineScope,
    private val http: OkHttpClient,
) {
    private val _messages = MutableSharedFlow<LiveDanmakuMessage>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<LiveDanmakuMessage> = _messages.asSharedFlow()

    private val _state = MutableStateFlow(LiveDanmakuState.Idle)
    val state: StateFlow<LiveDanmakuState> = _state.asStateFlow()

    private val _watchers = MutableSharedFlow<LiveWatchers>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 服务端隔一会儿就推一次「本场看过人数」，房间页拿它刷新观众数。 */
    val watchers: SharedFlow<LiveWatchers> = _watchers.asSharedFlow()

    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var retryJob: Job? = null
    private var attempt = 0
    private var stopped = false

    private var roomId = 0L
    private var token = ""
    private var url = ""
    private var uid = 0L

    fun connect(roomId: Long, token: String, url: String, uid: Long) {
        disconnect()
        if (roomId <= 0L || token.isBlank() || url.isBlank()) {
            _state.value = LiveDanmakuState.Failed
            return
        }
        this.roomId = roomId
        this.token = token
        this.url = url
        this.uid = uid
        stopped = false
        attempt = 0
        open()
    }

    fun disconnect() {
        stopped = true
        retryJob?.cancel()
        retryJob = null
        heartbeat?.cancel()
        heartbeat = null
        socket?.close(1000, null)
        socket = null
        _state.value = LiveDanmakuState.Idle
    }

    private fun open() {
        _state.value = LiveDanmakuState.Connecting
        val request = Request.Builder()
            .url(url)
            .header("Origin", "https://live.bilibili.com")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            )
            .build()
        Log.d(TAG, "connect $url room=$roomId uid=$uid attempt=$attempt")
        socket = http.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "open, send auth")
            if (stopped) return
            webSocket.send(
                LiveDanmakuProtocol.encode(LiveDanmakuProtocol.OP_AUTH, authBody()).toByteString(),
            )
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleFrame(bytes.toByteArray())
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "failure http=${response?.code} ${t.message}", t)
            scheduleRetry()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "closed code=$code reason=$reason")
            scheduleRetry()
        }
    }

    private fun authBody(): ByteArray = buildString {
        append("{\"uid\":").append(uid)
        append(",\"roomid\":").append(roomId)
        append(",\"protover\":").append(LiveDanmakuProtocol.PROTO_BROTLI)
        append(",\"platform\":\"web\",\"type\":2")
        append(",\"key\":\"").append(token).append("\"}")
    }.toByteArray()

    private fun handleFrame(data: ByteArray) {
        val packets = LiveDanmakuProtocol.decode(data)
        packets.forEach { packet ->
            when (packet.operation) {
                LiveDanmakuProtocol.OP_AUTH_REPLY -> {
                    Log.d(TAG, "auth reply: ${packet.body.toString(Charsets.UTF_8).take(200)}")
                    attempt = 0
                    _state.value = LiveDanmakuState.Connected
                    startHeartbeat()
                }

                LiveDanmakuProtocol.OP_HEARTBEAT_REPLY -> Unit
                LiveDanmakuProtocol.OP_MESSAGE -> emitMessages(packet.body)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MILLIS)
                socket?.send(
                    LiveDanmakuProtocol.encode(
                        LiveDanmakuProtocol.OP_HEARTBEAT,
                        ByteArray(0),
                    ).toByteString(),
                )
            }
        }
    }

    private fun scheduleRetry() {
        if (stopped) return
        heartbeat?.cancel()
        heartbeat = null
        socket = null
        if (attempt >= MAX_RETRY) {
            _state.value = LiveDanmakuState.Failed
            return
        }
        attempt += 1
        _state.value = LiveDanmakuState.Connecting
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(RETRY_MILLIS * attempt)
            if (!stopped) open()
        }
    }

    private fun emitMessages(body: ByteArray) {
        val text = runCatching { body.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
        if (text.isBlank()) return
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return
        val items = when (root) {
            is JsonArray -> root.toList()
            is JsonObject -> listOf(root)
            else -> emptyList()
        }
        items.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            parseWatched(obj)?.let { _watchers.tryEmit(it) }
            val message = parseDanmaku(obj) ?: return@forEach
            _messages.tryEmit(message)
        }
    }

    /** `{"cmd":"WATCHED_CHANGE","data":{"num":19342,"text_small":"1.9万","text_large":"1.9万人看过"}}` */
    private fun parseWatched(obj: JsonObject): LiveWatchers? {
        val cmd = obj["cmd"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (!cmd.startsWith("WATCHED_CHANGE")) return null
        val data = obj["data"]?.jsonObject ?: return null
        val count = data["num"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return null
        val text = data["text_small"]?.jsonPrimitive?.contentOrNull.orEmpty()
            .ifBlank { data["text_large"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        return LiveWatchers(count = count, text = text)
    }

    /** `info` 是个位置敏感数组：1 是正文，2 是发送者，3 是粉丝牌，0 是原始元信息。 */
    private fun parseDanmaku(obj: JsonObject): LiveDanmakuMessage? {
        val cmd = obj["cmd"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (!cmd.startsWith("DANMU_MSG")) return null
        val info = obj["info"]?.arrayOrNull() ?: return null
        val text = info.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty()
        if (text.isBlank()) return null
        val sender = info.getOrNull(2)?.arrayOrNull()
        val name = sender?.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty()
        val mid = sender?.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        val medal = info.getOrNull(3)?.arrayOrNull()
        val medalName = medal?.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty()
        val medalLevel = medal?.getOrNull(0)?.jsonPrimitive?.intOrNull ?: 0
        return LiveDanmakuMessage(
            name = name.ifBlank { "匿名" },
            text = text.trim(),
            mid = mid,
            medalName = medalName,
            medalLevel = medalLevel,
        )
    }

    private fun JsonElement?.arrayOrNull(): JsonArray? = (this as? JsonArray) ?: null

    private companion object {
        const val TAG = "BiliLiveDanmaku"
        const val HEARTBEAT_MILLIS = 30_000L
        const val RETRY_MILLIS = 1_500L
        const val MAX_RETRY = 3
        val json = Json { ignoreUnknownKeys = true }
    }
}
