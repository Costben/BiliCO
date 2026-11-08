package erl.bilico.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.live.LiveDanmakuClient
import erl.bilico.data.live.LiveDanmakuState
import erl.bilico.data.model.LiveDanmakuMessage
import erl.bilico.data.model.LivePlayback
import erl.bilico.data.model.LiveRoomDetail
import erl.bilico.data.repository.LiveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class LiveRoomUiState(
    val detail: LiveRoomDetail? = null,
    val playback: LivePlayback? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPlaybackLoading: Boolean = false,
    val playbackError: String? = null,
    val requestedQuality: Int = 10000,
    val activeQuality: Int = 0,
    /** 每次成功取流加一：地址可能一模一样，靠它让播放器重新 prepare。 */
    val playbackGeneration: Int = 0,
    val danmaku: List<LiveDanmakuMessage> = emptyList(),
    /** 累计收到的弹幕条数：列表只留最近若干条，靠它定位新消息。 */
    val danmakuSeq: Long = 0L,
    val danmakuState: LiveDanmakuState = LiveDanmakuState.Idle,
    val danmakuError: String? = null,
)

class LiveRoomViewModel(
    private val repository: LiveRepository,
    okHttp: OkHttpClient,
    private val roomId: Long,
    private val uid: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveRoomUiState())
    val state: StateFlow<LiveRoomUiState> = _state.asStateFlow()

    private val danmakuClient = LiveDanmakuClient(viewModelScope, okHttp)
    private var playbackJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            danmakuClient.messages.collect { message ->
                _state.update {
                    it.copy(
                        danmaku = (it.danmaku + message).takeLast(MAX_MESSAGES),
                        danmakuSeq = it.danmakuSeq + 1,
                    )
                }
            }
        }
        viewModelScope.launch {
            danmakuClient.state.collect { state -> _state.update { it.copy(danmakuState = state) } }
        }
        viewModelScope.launch {
            danmakuClient.watchers.collect { watchers ->
                _state.update { state ->
                    val detail = state.detail ?: return@update state
                    if (watchers.count <= 0L) return@update state
                    state.copy(detail = detail.copy(watched = watchers.count))
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.detail(roomId)
                .onSuccess { detail ->
                    _state.update { it.copy(detail = detail, isLoading = false) }
                    if (detail.isLive) {
                        loadPlayback(_state.value.requestedQuality)
                        connectDanmaku(detail.roomId.takeIf { it > 0L } ?: roomId)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "直播间加载失败")
                    }
                }
        }
    }

    fun selectQuality(qn: Int) {
        if (qn == _state.value.activeQuality) return
        loadPlayback(qn)
    }

    fun retryPlayback() {
        if (_state.value.detail?.isLive == true) loadPlayback(_state.value.requestedQuality)
    }

    /** 弹幕 token 与真实房号绑定，短号进房要先换成详情里的 roomId。 */
    private fun connectDanmaku(realRoomId: Long) {
        // 直播弹幕对未登录用户同样开放：uid 传 0，服务端照常下发弹幕和「看过」人数。
        viewModelScope.launch {
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

    private fun loadPlayback(qn: Int) {
        playbackJob?.cancel()
        _state.update {
            it.copy(
                isPlaybackLoading = true,
                playbackError = null,
                requestedQuality = qn,
            )
        }
        playbackJob = viewModelScope.launch {
            repository.playback(roomId, qn)
                .onSuccess { playback ->
                    _state.update {
                        it.copy(
                            playback = playback,
                            isPlaybackLoading = false,
                            activeQuality = playback.activeQuality,
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

    override fun onCleared() {
        danmakuClient.disconnect()
    }

    private companion object {
        const val MAX_MESSAGES = 200
    }
}
