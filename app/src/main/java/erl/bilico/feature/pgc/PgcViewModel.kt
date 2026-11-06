package erl.bilico.feature.pgc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.BangumiDetail
import erl.bilico.data.model.BangumiEpisode
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.repository.HeartbeatReporter
import erl.bilico.data.repository.PgcRepository
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
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
    val resumePositionMs: Long = 0,
    val followed: Boolean = false,
    val followBusy: Boolean = false,
    val message: String? = null,
)

class PgcViewModel(
    private val repository: PgcRepository,
    private val heartbeat: HeartbeatReporter,
    private val session: SessionRepository,
    private val settings: SettingsRepository,
    private val seasonId: Long,
    private val epId: Long,
    private val startPositionMs: Long = 0L,
) : ViewModel() {

    private val _state = MutableStateFlow(PgcUiState(resumePositionMs = startPositionMs.coerceAtLeast(0L)))
    val state: StateFlow<PgcUiState> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var startTs = 0L
    private var lastHeartbeatAt = 0L

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
                    val targetEp = if (epId > 0L) epId else detail.lastEpId
                    val index = if (targetEp > 0L) {
                        detail.episodes.indexOfFirst { it.epId == targetEp }.coerceAtLeast(0)
                    } else {
                        0
                    }
                    val chosenEp = detail.episodes[index]
                    val serverResumeMs = if (startPositionMs <= 0L &&
                        chosenEp.epId == detail.lastEpId &&
                        detail.lastTimeSec >= 3L &&
                        (chosenEp.durationSec <= 0L || detail.lastTimeSec < chosenEp.durationSec - 5L)
                    ) {
                        detail.lastTimeSec * 1000L
                    } else {
                        0L
                    }
                    val resumeMs = if (startPositionMs > 0L) startPositionMs else serverResumeMs
                    _state.update {
                        it.copy(
                            detail = detail,
                            isLoading = false,
                            selectedEpIndex = index,
                            followed = detail.followed,
                            message = if (serverResumeMs > 0L) {
                                "已从上次进度 ${erl.bilico.data.util.Formatters.formatDuration(serverResumeMs / 1000)} 继续播放"
                            } else {
                                it.message
                            },
                        )
                    }
                    loadPlayback(
                        episode = chosenEp,
                        qn = settings.defaultQuality.value,
                        notifyDowngrade = false,
                        positionMs = resumeMs,
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "番剧加载失败")
                    }
                }
        }
    }

    fun selectEpisode(index: Int) {
        val detail = _state.value.detail ?: return
        val episode = detail.episodes.getOrNull(index) ?: return
        if (index == _state.value.selectedEpIndex) return
        _state.update { it.copy(selectedEpIndex = index) }
        loadPlayback(episode, _state.value.requestedQuality, notifyDowngrade = false, positionMs = 0L)
    }

    fun playNextEpisode(): Boolean {
        val detail = _state.value.detail ?: return false
        val nextIndex = _state.value.selectedEpIndex + 1
        val nextEp = detail.episodes.getOrNull(nextIndex) ?: return false
        _state.update {
            it.copy(
                selectedEpIndex = nextIndex,
                message = "即将播放 ${nextEp.title.ifBlank { "下一话" }}",
            )
        }
        loadPlayback(nextEp, _state.value.requestedQuality, notifyDowngrade = false, positionMs = 0L)
        return true
    }

    fun consumeResumePosition() {
        if (_state.value.resumePositionMs != 0L) {
            _state.update { it.copy(resumePositionMs = 0L) }
        }
    }

    fun toggleFollow() {
        val detail = _state.value.detail ?: return
        if (!session.isLoggedIn()) {
            _state.update { it.copy(message = "登录后可用") }
            return
        }
        if (_state.value.followBusy) return
        val target = !_state.value.followed
        _state.update { it.copy(followBusy = true) }
        viewModelScope.launch {
            repository.setFollow(detail.seasonId, target)
                .onSuccess {
                    _state.update {
                        it.copy(
                            followed = target,
                            message = if (target) "已追番" else "已取消追番",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "操作失败") }
                }
            _state.update { it.copy(followBusy = false) }
        }
    }

    fun selectQuality(qn: Int, positionMs: Long) {
        val current = _state.value
        if (qn == current.activeQuality) return
        val episode = currentEpisode ?: return
        loadPlayback(episode, qn, notifyDowngrade = true, positionMs.coerceAtLeast(0L))
    }

    fun retryPlayback() {
        val episode = currentEpisode ?: return
        loadPlayback(episode, _state.value.requestedQuality, notifyDowngrade = false, 0L)
    }

    fun markPlaybackStarted() {
        startTs = System.currentTimeMillis() / 1000
        lastHeartbeatAt = 0L
    }

    fun reportHeartbeat(playedSec: Long, isPlaying: Boolean) {
        val detail = _state.value.detail ?: return
        val episode = currentEpisode ?: return
        if (!isPlaying || startTs <= 0L || episode.cid <= 0L) return
        val now = System.currentTimeMillis()
        if (now - lastHeartbeatAt < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatAt = now
        viewModelScope.launch {
            heartbeat.report(
                bvid = episode.bvid,
                aid = episode.aid,
                cid = episode.cid,
                playedTimeSec = playedSec,
                realPlayedTimeSec = playedSec,
                startTsSec = startTs,
                epid = episode.epId,
                sid = detail.seasonId,
                videoType = 4,
                subType = 1,
            )
        }
    }

    fun flushHeartbeat(playedSec: Long, externalScope: CoroutineScope) {
        val detail = _state.value.detail ?: return
        val episode = currentEpisode ?: return
        val ts = startTs
        if (episode.cid <= 0L || ts <= 0L || playedSec <= 0L) return
        externalScope.launch {
            heartbeat.report(
                bvid = episode.bvid,
                aid = episode.aid,
                cid = episode.cid,
                playedTimeSec = playedSec,
                realPlayedTimeSec = playedSec,
                startTsSec = ts,
                epid = episode.epId,
                sid = detail.seasonId,
                videoType = 4,
                subType = 1,
            )
        }
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
                resumePositionMs = positionMs,
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

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 15_000L
    }
}
