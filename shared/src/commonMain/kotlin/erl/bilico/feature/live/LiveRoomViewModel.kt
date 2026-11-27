package erl.bilico.feature.live

import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.KeyValueStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.live.LiveDanmakuClient
import erl.bilico.data.live.LiveDanmakuState
import erl.bilico.data.live.mergeSuperChatHistory
import erl.bilico.data.model.LiveStreamSelection
import erl.bilico.data.model.LiveSuperChat
import erl.bilico.data.repository.BlockSnapshot
import erl.bilico.data.repository.LiveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.ktor.client.HttpClient

class LiveRoomViewModel(
    private val repository: LiveRepository,
    http: HttpClient,
    private val roomId: Long,
    private val uid: Long,
    private val streamPrefs: KeyValueStore? = null,
    private val blockSnapshotProvider: () -> BlockSnapshot = { BlockSnapshot() },
) : ViewModel() {

    private val _state = MutableStateFlow(LiveRoomUiState())
    val state: StateFlow<LiveRoomUiState> = _state.asStateFlow()

    private val danmakuClient = LiveDanmakuClient(viewModelScope, http)
    private var playbackJob: Job? = null
    private var danmakuJob: Job? = null
    private val blockedMids = HashSet<Long>()
    private val blockedTexts = HashSet<String>()
    private var nextChatSeq = 1L

    init {
        load()
        viewModelScope.launch {
            danmakuClient.messages.collect { message ->
                if (isBlocked(message.mid, message.text)) return@collect
                val seq = nextChatSeq++
                _state.update {
                    it.copy(
                        chatEntries = (it.chatEntries + LiveChatEntry.Danmaku(seq, message)).takeLast(MAX_MESSAGES),
                        danmaku = (it.danmaku + message).takeLast(MAX_MESSAGES),
                        danmakuSeq = it.danmakuSeq + 1,
                    )
                }
            }
        }
        viewModelScope.launch {
            danmakuClient.superChats.collect { sc ->
                if (isBlocked(sc.uid, sc.message)) return@collect
                val seq = nextChatSeq++
                val now = currentTimeMillis() / 1000L
                val fsEnd = minOf(sc.endTimeSec, now + FULLSCREEN_SC_MAX_SEC)
                _state.update { cur ->
                    val nextScs = (listOf(sc) + cur.activeSuperChats.filterNot { it.id == sc.id })
                        .filter { it.endTimeSec > now }
                    cur.copy(
                        chatEntries = (cur.chatEntries + LiveChatEntry.SuperChat(seq, sc)).takeLast(MAX_MESSAGES),
                        activeSuperChats = nextScs,
                        superChatHistory = mergeSuperChatHistory(cur.superChatHistory, listOf(sc)),
                        fullscreenSuperChat = sc,
                        fullscreenSuperChatEndSec = fsEnd,
                        fullscreenSuperChatIsReplay = false,
                        nowSec = now,
                    )
                }
            }
        }
        viewModelScope.launch {
            danmakuClient.deletedSuperChatIds.collect { ids ->
                val idSet = ids.toSet()
                _state.update { cur ->
                    cur.copy(
                        activeSuperChats = cur.activeSuperChats.filterNot { it.id in idSet },
                        fullscreenSuperChat = cur.fullscreenSuperChat?.takeUnless { it.id in idSet },
                    )
                }
            }
        }
        viewModelScope.launch {
            danmakuClient.state.collect { state -> _state.update { it.copy(danmakuState = state) } }
        }
        viewModelScope.launch {
            danmakuClient.onlineCount.collect { count ->
                _state.update { state ->
                    val detail = state.detail ?: return@update state
                    val realId = detail.roomId.takeIf { it > 0L } ?: roomId
                    repository.cacheOnlineRankCount(realId, count)
                    state.copy(detail = detail.copy(onlineCount = count))
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val cur = _state.value
                if (cur.activeSuperChats.isEmpty() && cur.fullscreenSuperChat == null) continue
                val now = currentTimeMillis() / 1000L
                _state.update { state ->
                    state.copy(
                        nowSec = now,
                        activeSuperChats = state.activeSuperChats.filter { it.endTimeSec > now },
                        fullscreenSuperChat = state.fullscreenSuperChat?.takeIf { state.fullscreenSuperChatEndSec > now },
                    )
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.detail(roomId)
                .onSuccess { detail ->
                    val realRoomId = detail.roomId.takeIf { it > 0L } ?: roomId
                    _state.update { it.copy(detail = detail, isLoading = false) }
                    if (detail.isLive) {
                        loadOnlineRankCount(realRoomId, detail.uid)
                        loadSuperChats(realRoomId)
                        loadPlayback(_state.value.requestedQuality, _state.value.onlyAudio)
                        connectDanmaku(realRoomId)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "直播间加载失败")
                    }
                }
        }
    }

    private fun loadSuperChats(realRoomId: Long) {
        viewModelScope.launch {
            repository.superChats(realRoomId)
                .onSuccess { list ->
                    if (list.isEmpty()) return@onSuccess
                    val now = currentTimeMillis() / 1000L
                    _state.update { cur ->
                        val existingIds = cur.chatEntries
                            .filterIsInstance<LiveChatEntry.SuperChat>()
                            .map { it.sc.id }
                            .toSet()
                        val newEntries = list.filterNot { it.id in existingIds }
                            .map { LiveChatEntry.SuperChat(nextChatSeq++, it) }
                        val mergedActive = (cur.activeSuperChats + list)
                            .distinctBy { it.id }
                            .filter { it.endTimeSec > now }
                            .sortedByDescending { it.startTimeSec }
                        cur.copy(
                            chatEntries = (cur.chatEntries + newEntries).takeLast(MAX_MESSAGES),
                            activeSuperChats = mergedActive,
                            superChatHistory = mergeSuperChatHistory(cur.superChatHistory, list),
                            nowSec = now,
                        )
                    }
                }
        }
    }

    private fun loadOnlineRankCount(realRoomId: Long, ruid: Long) {
        if (ruid <= 0L) return
        viewModelScope.launch {
            repository.onlineRankCount(realRoomId, ruid, forceRefresh = true)
                .onSuccess { count ->
                    _state.update { state ->
                        val detail = state.detail ?: return@update state
                        state.copy(detail = detail.copy(onlineCount = detail.onlineCount ?: count))
                    }
                }
        }
    }

    fun selectQuality(qn: Int) {
        if (qn == _state.value.activeQuality) return
        loadPlayback(qn, _state.value.onlyAudio)
    }

    fun toggleOnlyAudio() {
        if (_state.value.detail?.isLive != true) return
        val next = !_state.value.onlyAudio
        _state.update { it.copy(onlyAudio = next) }
        loadPlayback(_state.value.requestedQuality, next)
    }

    /** 照 PiliPlus `initLiveUrl` + 保存 `[protocol, format, codec]` 偏好。 */
    fun selectLine(selection: LiveStreamSelection) {
        val playback = _state.value.playback ?: return
        val chosen = playback.resolveCandidate(selection) ?: return
        val s = playback.streams.getOrNull(selection.streamIndex)
        val f = s?.formats?.getOrNull(selection.formatIndex)
        val c = f?.codecs?.getOrNull(selection.codecIndex)
        if (s != null && f != null && c != null) {
            savePreferredLine(s.protocolName, f.formatName, c.codecName)
        }
        val reordered = listOf(chosen) + playback.candidates.filterNot { it.url == chosen.url }
        _state.update {
            it.copy(
                playback = playback.copy(candidates = reordered, initialSelection = selection),
                lineSelection = selection,
                activeQuality = chosen.currentQn.takeIf { qn -> qn > 0 } ?: it.activeQuality,
                playbackError = null,
                playbackGeneration = it.playbackGeneration + 1,
            )
        }
    }

    fun updateActiveSelection(selection: LiveStreamSelection) {
        _state.update { it.copy(lineSelection = selection) }
    }

    fun retryPlayback() {
        if (_state.value.detail?.isLive == true) {
            loadPlayback(_state.value.requestedQuality, _state.value.onlyAudio)
        }
    }

    /** 手动刷新直播间：重新拉取直播流并重连弹幕，同时静默更新直播间信息。 */
    fun reconnect() {
        val current = _state.value
        val detail = current.detail
        if (detail == null) {
            load()
            return
        }
        val realRoomId = detail.roomId.takeIf { it > 0L } ?: roomId
        if (detail.isLive) {
            loadPlayback(current.requestedQuality, current.onlyAudio)
            connectDanmaku(realRoomId)
        }
        viewModelScope.launch {
            repository.detail(roomId).onSuccess { fresh ->
                val freshRealId = fresh.roomId.takeIf { it > 0L } ?: roomId
                _state.update { it.copy(detail = fresh) }
                if (fresh.isLive) {
                    loadOnlineRankCount(freshRealId, fresh.uid)
                    loadSuperChats(freshRealId)
                    if (!detail.isLive) {
                        loadPlayback(_state.value.requestedQuality, _state.value.onlyAudio)
                        connectDanmaku(freshRealId)
                    }
                }
            }
        }
    }

    fun replaySuperChat(sc: LiveSuperChat) {
        val now = currentTimeMillis() / 1000L
        _state.update {
            it.copy(
                fullscreenSuperChat = sc,
                fullscreenSuperChatEndSec = now + FULLSCREEN_SC_MAX_SEC,
                fullscreenSuperChatIsReplay = true,
                nowSec = now,
            )
        }
    }

    fun dismissFullscreenSuperChat() {
        _state.update {
            it.copy(fullscreenSuperChat = null, fullscreenSuperChatEndSec = 0L, fullscreenSuperChatIsReplay = false)
        }
    }
    fun blockUserTemporary(mid: Long) { if (mid > 0L) blockedMids.add(mid); pruneBlocked() }
    fun blockContentTemporary(text: String) { text.trim().takeIf { it.isNotEmpty() }?.let(blockedTexts::add); pruneBlocked() }
    /** 仅在当前直播间临时屏蔽该发送者与该条弹幕全文。 */
    fun blockTemporary(mid: Long, text: String) {
        if (mid > 0L) blockedMids.add(mid)
        text.trim().takeIf { it.isNotEmpty() }?.let(blockedTexts::add)
        pruneBlocked()
    }
    fun pruneBlocked() {
        _state.update { state ->
            state.copy(
                chatEntries = state.chatEntries.filterNot { entry ->
                    when (entry) {
                        is LiveChatEntry.Danmaku -> isBlocked(entry.message.mid, entry.message.text)
                        is LiveChatEntry.SuperChat -> isBlocked(entry.sc.uid, entry.sc.message)
                    }
                },
                danmaku = state.danmaku.filterNot { isBlocked(it.mid, it.text) },
                activeSuperChats = state.activeSuperChats.filterNot { isBlocked(it.uid, it.message) },
            )
        }
    }
    fun isBlocked(mid: Long, text: String): Boolean =
        (mid > 0L && mid in blockedMids) || text.trim() in blockedTexts || blockSnapshotProvider().matchesDanmaku(text)

    private fun connectDanmaku(realRoomId: Long) {
        danmakuJob?.cancel()
        danmakuClient.disconnect()
        _state.update { it.copy(danmakuState = LiveDanmakuState.Connecting, danmakuError = null) }
        danmakuJob = viewModelScope.launch {
            repository.danmakuServer(realRoomId)
                .onSuccess { server ->
                    _state.update { it.copy(danmakuError = null) }
                    danmakuClient.connect(realRoomId, server.token, server.urls.first(), uid)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            danmakuState = LiveDanmakuState.Failed,
                            danmakuError = error.message ?: "弹幕连接失败",
                        )
                    }
                }
        }
    }

    private fun loadPlayback(qn: Int, onlyAudio: Boolean) {
        playbackJob?.cancel()
        _state.update {
            it.copy(
                isPlaybackLoading = true,
                playbackError = null,
                requestedQuality = qn,
                onlyAudio = onlyAudio,
            )
        }
        playbackJob = viewModelScope.launch {
            repository.playback(
                roomId = roomId,
                qn = qn,
                onlyAudio = onlyAudio,
                preferredLine = readPreferredLine(),
            )
                .onSuccess { playback ->
                    _state.update {
                        it.copy(
                            playback = playback,
                            isPlaybackLoading = false,
                            activeQuality = playback.activeQuality,
                            lineSelection = playback.initialSelection,
                            playbackGeneration = it.playbackGeneration + 1,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isPlaybackLoading = false,
                            playbackError = error.message ?: "直播流获取失败",
                        )
                    }
                }
        }
    }

    private fun readPreferredLine(): List<String>? {
        val raw = streamPrefs?.getString(KEY_LIVE_STREAM) ?: return null
        return raw.split('|').takeIf { it.size >= 3 }
    }

    private fun savePreferredLine(protocol: String, format: String, codec: String) {
        streamPrefs?.putString(KEY_LIVE_STREAM, "$protocol|$format|$codec")
    }

    override fun onCleared() {
        danmakuClient.disconnect()
    }

    private companion object {
        const val MAX_MESSAGES = 200
        const val FULLSCREEN_SC_MAX_SEC = 10L
        const val KEY_LIVE_STREAM = "preferred_line"
    }
}
