package erl.bilico.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.repository.HeartbeatReporter
import erl.bilico.data.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoUiState(
    val detail: VideoDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val source: PlaybackSource? = null,
    val isPlaybackLoading: Boolean = false,
    val playbackError: String? = null,
    val activeQuality: Int = 0,
    val requestedQuality: Int = 80,
    val selectedCid: Long = 0,
    val selectedPartIndex: Int = 0,
    val resumePositionMs: Long = 0,
    val message: String? = null,
)

class VideoViewModel(
    private val repository: VideoRepository,
    private val heartbeat: HeartbeatReporter,
    private val bvid: String,
    startPositionMs: Long = 0L,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoUiState(resumePositionMs = startPositionMs.coerceAtLeast(0L)))
    val state: StateFlow<VideoUiState> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var startTs = 0L
    private var lastHeartbeatAt = 0L

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.detail(bvid)
                .onSuccess { detail ->
                    val index = detail.pages.indexOfFirst { it.cid == detail.cid }
                        .coerceAtLeast(0)
                    _state.update {
                        it.copy(
                            detail = detail,
                            isLoading = false,
                            selectedCid = detail.cid,
                            selectedPartIndex = index,
                        )
                    }
                    loadPlayback(qn = DEFAULT_QUALITY, cid = detail.cid, notifyDowngrade = false)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "视频加载失败")
                    }
                }
        }
    }

    fun selectPart(index: Int, positionMs: Long) {
        val detail = _state.value.detail ?: return
        val part = detail.pages.getOrNull(index) ?: return
        if (part.cid == _state.value.selectedCid) return
        _state.update { it.copy(selectedPartIndex = index, selectedCid = part.cid) }
        loadPlayback(
            qn = _state.value.requestedQuality,
            cid = part.cid,
            notifyDowngrade = false,
            positionMs = 0L,
        )
    }

    fun selectQuality(qn: Int, positionMs: Long) {
        val current = _state.value
        if (qn == current.activeQuality || current.selectedCid <= 0) return
        loadPlayback(
            qn = qn,
            cid = current.selectedCid,
            notifyDowngrade = true,
            positionMs = positionMs,
        )
    }

    fun markPlaybackStarted() {
        startTs = System.currentTimeMillis() / 1000
        lastHeartbeatAt = 0L
    }

    /** 界面每 500ms 调一次，内部按 15 秒节流。 */
    fun reportHeartbeat(playedSec: Long, isPlaying: Boolean) {
        val detail = _state.value.detail ?: return
        if (!isPlaying || startTs <= 0L) return
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatAt = now
        viewModelScope.launch {
            heartbeat.report(
                bvid = bvid,
                aid = detail.aid,
                cid = _state.value.selectedCid,
                playedTimeSec = playedSec,
                realPlayedTimeSec = playedSec,
                startTsSec = startTs,
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun loadPlayback(
        qn: Int,
        cid: Long,
        notifyDowngrade: Boolean,
        positionMs: Long = _state.value.resumePositionMs,
    ) {
        if (cid <= 0) return
        playbackJob?.cancel()
        _state.update {
            it.copy(
                isPlaybackLoading = true,
                playbackError = null,
                requestedQuality = qn,
                resumePositionMs = positionMs,
            )
        }
        playbackJob = viewModelScope.launch {
            repository.playback(bvid, cid, qn)
                .onSuccess { source ->
                    val downgraded = qn > source.quality && qn >= 80 && notifyDowngrade
                    _state.update {
                        it.copy(
                            source = source,
                            isPlaybackLoading = false,
                            activeQuality = source.quality,
                            message = if (downgraded) {
                                "该清晰度不可用，已使用 ${qualityLabel(source, source.quality)}"
                            } else {
                                it.message
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isPlaybackLoading = false,
                            playbackError = error.message ?: "播放地址获取失败",
                        )
                    }
                }
        }
    }

    private fun qualityLabel(source: PlaybackSource, qn: Int): String =
        source.qualities.firstOrNull { it.quality == qn }?.label?.takeIf { it.isNotBlank() }
            ?: "${qn}P"

    private companion object {
        const val DEFAULT_QUALITY = 80
        const val HEARTBEAT_INTERVAL_MS = 15_000L
    }
}
