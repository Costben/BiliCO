package erl.bilico.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.LivePlayback
import erl.bilico.data.model.LiveRoomDetail
import erl.bilico.data.repository.LiveRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveRoomUiState(
    val detail: LiveRoomDetail? = null,
    val playback: LivePlayback? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPlaybackLoading: Boolean = false,
    val playbackError: String? = null,
    val requestedQuality: Int = 10000,
    val activeQuality: Int = 0,
)

class LiveRoomViewModel(
    private val repository: LiveRepository,
    private val roomId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveRoomUiState())
    val state: StateFlow<LiveRoomUiState> = _state.asStateFlow()

    private var playbackJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.detail(roomId)
                .onSuccess { detail ->
                    _state.update { it.copy(detail = detail, isLoading = false) }
                    if (detail.isLive) loadPlayback(_state.value.requestedQuality)
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
}
