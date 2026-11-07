package erl.bilico.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.repository.HeartbeatReporter
import erl.bilico.data.repository.InteractionRepository
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoUiState(
    val detail: VideoDetail? = null,
    val related: List<FeedVideo> = emptyList(),
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
    val liked: Boolean = false,
    val coined: Int = 0,
    val favorited: Boolean = false,
    val following: Boolean = false,
    val interactionBusy: Boolean = false,
)

class VideoViewModel(
    private val repository: VideoRepository,
    private val heartbeat: HeartbeatReporter,
    private val interactions: InteractionRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository,
    private val bvid: String,
    startPositionMs: Long = 0L,
    private val initialCid: Long = 0L,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoUiState(resumePositionMs = startPositionMs.coerceAtLeast(0L)))
    val state: StateFlow<VideoUiState> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var startTs = 0L
    private var lastHeartbeatAt = 0L
    private var initialResumeSettled = startPositionMs > 0L

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repository.detail(bvid)
                .onSuccess { detail ->
                    val targetCid = initialCid.takeIf { cid -> cid > 0L && detail.pages.any { it.cid == cid } }
                        ?: detail.cid
                    val index = detail.pages.indexOfFirst { it.cid == targetCid }.coerceAtLeast(0)
                    _state.update {
                        it.copy(
                            detail = detail,
                            isLoading = false,
                            selectedCid = targetCid,
                            selectedPartIndex = index,
                        )
                    }
                    loadRelation(detail.aid, detail.upMid)
                    loadRelated()
                    loadPlayback(
                        qn = settings.defaultQuality.value,
                        cid = targetCid,
                        notifyDowngrade = false,
                    )
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
        initialResumeSettled = true
        _state.update { it.copy(selectedPartIndex = index, selectedCid = part.cid) }
        loadPlayback(
            qn = _state.value.requestedQuality,
            cid = part.cid,
            notifyDowngrade = false,
            positionMs = 0L,
        )
    }

    fun playNextPart(): Boolean {
        val detail = _state.value.detail ?: return false
        val nextIndex = _state.value.selectedPartIndex + 1
        val nextPart = detail.pages.getOrNull(nextIndex) ?: return false
        initialResumeSettled = true
        _state.update {
            it.copy(
                selectedPartIndex = nextIndex,
                selectedCid = nextPart.cid,
                message = "即将播放 P${nextPart.index} ${nextPart.title}".trim(),
            )
        }
        loadPlayback(qn = _state.value.requestedQuality, cid = nextPart.cid, notifyDowngrade = false, positionMs = 0L)
        return true
    }

    fun applyServerResumeHint(lastPlayCid: Long, lastPlayTimeMs: Long): Long {
        if (initialResumeSettled || lastPlayTimeMs < 3_000L) return 0L
        val detail = _state.value.detail ?: return 0L
        val targetPartIndex = if (lastPlayCid > 0L) detail.pages.indexOfFirst { it.cid == lastPlayCid } else -1
        val currentCid = _state.value.selectedCid
        if (targetPartIndex >= 0 && detail.pages[targetPartIndex].cid != currentCid) {
            val part = detail.pages[targetPartIndex]
            val maxMs = part.durationSec * 1000L
            if (maxMs > 0L && lastPlayTimeMs >= maxMs - 5_000L) return 0L
            initialResumeSettled = true
            _state.update {
                it.copy(
                    selectedPartIndex = targetPartIndex,
                    selectedCid = part.cid,
                    message = "已从上次进度 ${erl.bilico.data.util.Formatters.formatDuration(lastPlayTimeMs / 1000)} 继续播放",
                )
            }
            loadPlayback(_state.value.requestedQuality, part.cid, notifyDowngrade = false, positionMs = lastPlayTimeMs)
            return 0L
        }
        if (lastPlayCid > 0L && lastPlayCid != currentCid) return 0L
        val curPartDurMs = (detail.pages.getOrNull(_state.value.selectedPartIndex)?.durationSec ?: detail.durationSec.toLong()) * 1000L
        if (curPartDurMs > 0L && lastPlayTimeMs >= curPartDurMs - 5_000L) return 0L
        initialResumeSettled = true
        _state.update {
            it.copy(
                resumePositionMs = lastPlayTimeMs,
                message = "已从上次进度 ${erl.bilico.data.util.Formatters.formatDuration(lastPlayTimeMs / 1000)} 继续播放",
            )
        }
        return lastPlayTimeMs
    }

    fun consumeResumePosition() {
        if (_state.value.resumePositionMs != 0L) {
            _state.update { it.copy(resumePositionMs = 0L) }
        }
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

    /** 页面退出或切 P 时用外部长生命周期协程补发一次末次进度。 */
    fun flushHeartbeat(playedSec: Long, externalScope: kotlinx.coroutines.CoroutineScope) {
        val detail = _state.value.detail ?: return
        val cid = _state.value.selectedCid
        val ts = startTs
        if (cid <= 0L || ts <= 0L || playedSec <= 0L) return
        externalScope.launch {
            heartbeat.report(
                bvid = bvid,
                aid = detail.aid,
                cid = cid,
                playedTimeSec = playedSec,
                realPlayedTimeSec = playedSec,
                startTsSec = ts,
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showLoginHint() {
        _state.update { it.copy(message = "登录后可用") }
    }

    fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    // ---------- 互动 ----------

    fun toggleLike() {
        val detail = _state.value.detail ?: return
        if (!requireLogin()) return
        val target = !_state.value.liked
        interact {
            interactions.like(detail.aid, target).onSuccess {
                _state.update { state ->
                    val current = state.detail ?: return@update state
                    val delta = if (target) 1L else -1L
                    state.copy(
                        liked = target,
                        detail = current.copy(like = (current.like + delta).coerceAtLeast(0L)),
                    )
                }
            }
        }
    }

    fun coin(count: Int) {
        val detail = _state.value.detail ?: return
        if (!requireLogin()) return
        val awarded = _state.value.coined
        if (awarded >= 2) {
            _state.update { it.copy(message = "已经投过币了") }
            return
        }
        val add = (count.coerceIn(1, 2) - awarded).coerceIn(1, 2)
        interact {
            interactions.coin(detail.aid, add).onSuccess {
                _state.update { state ->
                    val current = state.detail ?: return@update state
                    state.copy(
                        coined = (state.coined + add).coerceAtMost(2),
                        detail = current.copy(coin = current.coin + add),
                        message = "投币成功",
                    )
                }
            }
        }
    }

    fun favorite(mediaId: Long) {
        val detail = _state.value.detail ?: return
        if (!requireLogin()) return
        interact {
            interactions.favorite(detail.aid, mediaId).onSuccess {
                _state.update { state ->
                    val current = state.detail ?: return@update state
                    state.copy(
                        favorited = true,
                        detail = current.copy(favorite = current.favorite + 1),
                        message = "已收藏",
                    )
                }
            }
        }
    }

    fun addToWatchLater() {
        val detail = _state.value.detail ?: return
        if (!requireLogin()) return
        interact {
            interactions.addToWatchLater(detail.aid).onSuccess {
                _state.update { it.copy(message = "已加入稍后再看") }
            }
        }
    }

    fun toggleFollow() {
        val detail = _state.value.detail ?: return
        if (!requireLogin()) return
        if (detail.upMid <= 0L) return
        val target = !_state.value.following
        interact {
            interactions.follow(detail.upMid, target).onSuccess {
                _state.update {
                    it.copy(following = target, message = if (target) "已关注" else "已取消关注")
                }
            }
        }
    }

    private fun loadRelation(aid: Long, upMid: Long) {
        if (!session.isLoggedIn()) return
        viewModelScope.launch {
            interactions.relation(aid).onSuccess { relation ->
                _state.update {
                    it.copy(
                        liked = relation.like,
                        coined = relation.coin,
                        favorited = relation.favorite,
                    )
                }
            }
            if (upMid > 0L) {
                val following = interactions.isFollowing(upMid)
                _state.update { it.copy(following = following) }
            }
        }
    }

    private fun requireLogin(): Boolean {
        if (session.isLoggedIn()) return true
        _state.update { it.copy(message = "登录后可用") }
        return false
    }

    private fun interact(block: suspend () -> Result<Unit>) {
        if (_state.value.interactionBusy) return
        _state.update { it.copy(interactionBusy = true) }
        viewModelScope.launch {
            block().onFailure { error ->
                _state.update { it.copy(message = error.message ?: "操作失败") }
            }
            _state.update { it.copy(interactionBusy = false) }
        }
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
            repository.playback(bvid, cid, qn, CdnOption.fromId(settings.cdnOption.value))
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

    private fun loadRelated() {
        viewModelScope.launch {
            repository.related(bvid)
                .onSuccess { items -> _state.update { it.copy(related = items) } }
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
