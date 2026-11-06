package erl.bilico.feature.pgc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.BangumiDetail
import erl.bilico.data.model.BangumiEpisode
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.repository.PgcRepository
import erl.bilico.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PgcUiState(
    val detail: BangumiDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedEpIndex: Int = 0,
    val source: PlaybackSource? = null,
    val isPlaybackLoading: Boolean = false,
    val playbackError: String? = null,
    val activeQuality: Int = 0,
    val requestedQuality: Int = 80,
    val message: String? = null,
)

class PgcViewModel(
    private val repository: PgcRepository,
    private val settings: SettingsRepository,
    private val seasonId: Long,
    private val epId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(PgcUiState())
    val state: StateFlow<PgcUiState> = _state.asStateFlow()

    private var playbackJob: Job? = null

    val currentEpisode: BangumiEpisode?
        get() = _state.value.detail?.episodes?.getOrNull(_state.value.selectedEpIndex)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.detail(seasonId, epId)
                .onSuccess { detail ->
                    if (detail.episodes.isEmpty()) {
                        _state.update {
                            it.copy(isLoading = false, error = "没有可播放的剧集")
                        }
                        return@onSuccess
                    }
                    val index = if (epId > 0L) {
                        detail.episodes.indexOfFirst { it.epId == epId }.coerceAtLeast(0)
                    } else {
                        0
                    }
                    _state.update {
                        it.copy(detail = detail, isLoading = false, selectedEpIndex = index)
                    }
                    loadPlayback(
                        episode = detail.episodes[index],
                        qn = settings.defaultQuality.value,
                        notifyDowngrade = false,
                        positionMs = 0L,
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "番剧加载失败")
                    }
                }
        }
    }

    fun selectEpisode(index: Int, positionMs: Long = 0L) {
        val detail = _state.value.detail ?: return
        val episode = detail.episodes.getOrNull(index) ?: return
        if (index == _state.value.selectedEpIndex) return
        _state.update { it.copy(selectedEpIndex = index) }
        loadPlayback(episode, _state.value.requestedQuality, notifyDowngrade = false, positionMs)
    }

    fun selectQuality(qn: Int, positionMs: Long) {
        val current = _state.value
        if (qn == current.activeQuality) return
        val episode = currentEpisode ?: return
        loadPlayback(episode, qn, notifyDowngrade = true, positionMs)
    }

    fun retryPlayback() {
        val episode = currentEpisode ?: return
        loadPlayback(episode, _state.value.requestedQuality, notifyDowngrade = false, 0L)
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun loadPlayback(
        episode: BangumiEpisode,
        qn: Int,
        notifyDowngrade: Boolean,
        positionMs: Long,
    ) {
        val detail = _state.value.detail ?: return
        if (episode.cid <= 0L) {
            _state.update { it.copy(playbackError = "这一集没有可用的视频流") }
            return
        }
        playbackJob?.cancel()
        _state.update {
            it.copy(
                isPlaybackLoading = true,
                playbackError = null,
                requestedQuality = qn,
            )
        }
        playbackJob = viewModelScope.launch {
            repository.playback(
                epId = episode.epId,
                cid = episode.cid,
                qn = qn,
                aid = episode.aid,
                seasonId = detail.seasonId,
                bvid = episode.bvid,
            )
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
}
