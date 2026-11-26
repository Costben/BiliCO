package erl.bilico.data.live

import erl.bilico.data.model.LiveDanmakuMessage
import erl.bilico.data.model.LiveSuperChat
import erl.bilico.platform.logDebug
import erl.bilico.platform.logWarn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class LiveDanmakuState { Idle, Connecting, Connected, Failed }

/**
 * 直播弹幕长连接：基于 Ktor WebSocket，认证、心跳、断线重连，只把弹幕正文往上抛。
 */
class LiveDanmakuClient(
    private val scope: CoroutineScope,
    private val http: HttpClient,
) {
    private val _messages = MutableSharedFlow<LiveDanmakuMessage>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<LiveDanmakuMessage> = _messages.asSharedFlow()

    private val _state = MutableStateFlow(LiveDanmakuState.Idle)
    val state: StateFlow<LiveDanmakuState> = _state.asStateFlow()

    private val _onlineCount = MutableSharedFlow<Long>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val onlineCount: SharedFlow<Long> = _onlineCount.asSharedFlow()

    private val _superChats = MutableSharedFlow<LiveSuperChat>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val superChats: SharedFlow<LiveSuperChat> = _superChats.asSharedFlow()

    private val _deletedSuperChatIds = MutableSharedFlow<List<Long>>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val deletedSuperChatIds: SharedFlow<List<Long>> = _deletedSuperChatIds.asSharedFlow()

    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null
    private var session: WebSocketSession? = null
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
        heartbeatJob?.cancel()
        heartbeatJob = null
        connectionJob?.cancel()
        connectionJob = null
        val active = session
        session = null
        if (active != null) {
            scope.launch { runCatching { active.close() } }
        }
        _state.value = LiveDanmakuState.Idle
    }

    private fun open() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            while (isActive && !stopped) {
                _state.value = LiveDanmakuState.Connecting
                logDebug(TAG, "connect $url room=$roomId uid=$uid attempt=$attempt")
                try {
                    val ws = http.webSocketSession {
                        url(this@LiveDanmakuClient.url)
                        header(HttpHeaders.Origin, "https://live.bilibili.com")
                        header(
                            HttpHeaders.UserAgent,
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                        )
                    }
                    session = ws
                    ws.send(
                        Frame.Binary(
                            true,
                            LiveDanmakuProtocol.encode(LiveDanmakuProtocol.OP_AUTH, authBody()),
                        ),
                    )
                    for (frame in ws.incoming) {
                        if (frame is Frame.Binary) {
                            handleFrame(frame.readBytes(), ws)
                        }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    logWarn(TAG, "failure ${t.message}", t)
                } finally {
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    session = null
                }
                if (stopped) break
                if (attempt >= MAX_RETRY) {
                    _state.value = LiveDanmakuState.Failed
                    break
                }
                attempt += 1
                delay(RETRY_MILLIS * attempt)
            }
        }
    }

    private fun authBody(): ByteArray = buildString {
        append("{\"uid\":").append(uid)
        append(",\"roomid\":").append(roomId)
        append(",\"protover\":").append(LiveDanmakuProtocol.PROTO_ZLIB)
        append(",\"platform\":\"web\",\"type\":2")
        append(",\"key\":\"").append(token).append("\"}")
    }.encodeToByteArray()

    private fun handleFrame(data: ByteArray, ws: WebSocketSession) {
        val packets = LiveDanmakuProtocol.decode(data)
        packets.forEach { packet ->
            when (packet.operation) {
                LiveDanmakuProtocol.OP_AUTH_REPLY -> {
                    logDebug(TAG, "auth reply: ${packet.body.decodeToString().take(200)}")
                    attempt = 0
                    _state.value = LiveDanmakuState.Connected
                    startHeartbeat(ws)
                }

                LiveDanmakuProtocol.OP_HEARTBEAT_REPLY -> Unit
                LiveDanmakuProtocol.OP_MESSAGE -> emitMessages(packet.body)
            }
        }
    }

    private fun startHeartbeat(ws: WebSocketSession) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && !stopped) {
                delay(HEARTBEAT_MILLIS)
                runCatching {
                    ws.send(
                        Frame.Binary(
                            true,
                            LiveDanmakuProtocol.encode(
                                LiveDanmakuProtocol.OP_HEARTBEAT,
                                ByteArray(0),
                            ),
                        ),
                    )
                }.onFailure { return@launch }
            }
        }
    }

    private fun emitMessages(body: ByteArray) {
        val text = runCatching { body.decodeToString() }.getOrNull().orEmpty()
        if (text.isBlank()) return
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return
        val items = when (root) {
            is JsonArray -> root.toList()
            is JsonObject -> listOf(root)
            else -> emptyList()
        }
        items.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            parseOnlineRankCount(obj)?.let { _onlineCount.tryEmit(it) }
            parseLiveSuperChat(obj)?.let { _superChats.tryEmit(it) }
            parseDeletedSuperChatIds(obj)?.let { ids -> if (ids.isNotEmpty()) _deletedSuperChatIds.tryEmit(ids) }
            val message = parseDanmaku(obj) ?: return@forEach
            _messages.tryEmit(message)
        }
    }

    private fun parseOnlineRankCount(obj: JsonObject): Long? {
        val cmd = obj["cmd"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (!cmd.startsWith("ONLINE_RANK_COUNT")) return null
        val data = obj["data"]?.jsonObject ?: return null
        return data["count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: data["online_count"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    }

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

    private fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

    private companion object {
        const val TAG = "BiliLiveDanmaku"
        const val HEARTBEAT_MILLIS = 30_000L
        const val RETRY_MILLIS = 1_500L
        const val MAX_RETRY = 3
        val json = Json { ignoreUnknownKeys = true }
    }
}
