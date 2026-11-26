package erl.bilico.feature.video

import erl.bilico.platform.currentTimeMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.repository.DownloadRepository
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
    val disliked: Boolean = false,
    val coined: Int = 0,
    val favorited: Boolean = false,
    val following: Boolean = false,
    val upFollowers: Long? = null,
    val upOfficialType: Int = -1,
    val upOfficialDesc: String = "",
    val interactionBusy: Boolean = false,
    val offlineMode: Boolean = false,
)

class VideoViewModel(
    private val repository: VideoRepository,
    private val downloads: DownloadRepository,
    private val heartbeat: HeartbeatReporter,
    private val interactions: InteractionRepository,
    private val session: SessionRepository,
    private val settings: SettingsRepository,
    bvid: String,
    startPositionMs: Long = 0L,
    initialCid: Long = 0L,
    offline: Boolean = false,
) : ViewModel() {

    // 一个播放页只留一份 ViewModel，连播换下一条时原地换参数，不再按 bvid 新建。
    private var bvid: String = bvid
    private var initialCid: Long = initialCid
    private var startPositionMs: Long = startPositionMs.coerceAtLeast(0L)
    private var offline: Boolean = offline

    private val _state = MutableStateFlow(
        VideoUiState(resumePositionMs = this.startPositionMs, offlineMode = offline),
    )
    val state: StateFlow<VideoUiState> = _state.asStateFlow()

    private val interactionDelegate = VideoInteractionDelegate(interactions, session, viewModelScope, _state)

    /** 当前这份状态属于哪一条视频，界面据此判断是不是还没切过来。 */
    val currentBvid: String get() = bvid

    private var loadJob: Job? = null
    private var playbackJob: Job? = null
    private var startTs = 0L
    private var lastHeartbeatAt = 0L
    private var initialResumeSettled = this.startPositionMs > 0L

    init {
        load()
    }

    /**
     * 同一页里换下一条视频或从下级视频返回时恢复本页状态：
     * 若带有上次加载好的静态详情快照，则直接恢复简介、推荐和互动态并仅重取播放流。
     */
    fun switchTo(
        bvid: String,
        startPositionMs: Long = 0L,
        initialCid: Long = 0L,
        offline: Boolean = false,
        cachedState: VideoUiState? = null,
    ) {
        val start = startPositionMs.coerceAtLeast(0L)
        if (bvid == this.bvid && initialCid == this.initialCid &&
            start == this.startPositionMs && offline == this.offline && cachedState == null
        ) {
            return
        }
        if (bvid == this.bvid && offline == this.offline && _state.value.detail != null && cachedState == null) return
        this.bvid = bvid
        this.initialCid = initialCid
        this.startPositionMs = start
        this.offline = offline
        loadJob?.cancel()
        playbackJob?.cancel()
        startTs = 0L
        lastHeartbeatAt = 0L
        initialResumeSettled = start > 0L
        val cachedDetail = cachedState?.detail
        if (cachedState != null && cachedDetail != null) {
            _state.value = cachedState.copy(
                source = null,
                isLoading = false,
                error = null,
                isPlaybackLoading = true,
                playbackError = null,
                resumePositionMs = start,
                message = null,
                offlineMode = offline,
            )
            val targetCid = cachedState.selectedCid.takeIf { it > 0L } ?: cachedDetail.cid
            loadPlayback(
                qn = cachedState.requestedQuality.takeIf { it > 0 } ?: settings.defaultQuality.value,
                cid = targetCid,
                notifyDowngrade = false,
                positionMs = start,
            )
            return
        }
        _state.value = VideoUiState(resumePositionMs = start, offlineMode = offline)
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val resolved = resolveVideoDetail(bvid, offline, repository, downloads)
            if (resolved.detail != null) {
                applyLoadedDetail(resolved.detail, fromOffline = resolved.fromOffline)
                resolved.notice?.let { msg -> _state.update { it.copy(message = msg) } }
            } else {
                _state.update { it.copy(isLoading = false, error = resolved.error) }
            }
        }
    }

    private fun applyLoadedDetail(detail: VideoDetail, fromOffline: Boolean) {
        val targetCid = initialCid.takeIf { cid -> cid > 0L && detail.pages.any { it.cid == cid } }
            ?: detail.cid
        val index = detail.pages.indexOfFirst { it.cid == targetCid }.coerceAtLeast(0)
        _state.update {
            it.copy(
                detail = detail,
                isLoading = false,
                selectedCid = targetCid,
                selectedPartIndex = index,
                offlineMode = fromOffline,
            )
        }
        if (!fromOffline) {
            interactionDelegate.loadRelation(detail.aid, detail.upMid)
            interactionDelegate.loadUpFollowers(detail.upMid)
            loadRelated()
        }
        loadPlayback(qn = settings.defaultQuality.value, cid = targetCid, notifyDowngrade = false)
    }

    fun selectPart(index: Int, positionMs: Long) {
        val detail = _state.value.detail ?: return
        val part = detail.pages.getOrNull(index) ?: return
        if (part.cid == _state.value.selectedCid) return
        initialResumeSettled = true
        _state.update { it.copy(selectedPartIndex = index, selectedCid = part.cid) }
        loadPlayback(qn = _state.value.requestedQuality, cid = part.cid, notifyDowngrade = false, positionMs = 0L)
    }

    fun playNextPart(loop: Boolean = false): Boolean {
        val pages = _state.value.detail?.pages ?: return false
        val rawNext = _state.value.selectedPartIndex + 1
        val nextIndex = if (loop && pages.size > 1) rawNext % pages.size else rawNext
        return playPartAt(nextIndex)
    }

    fun playPartByCid(cid: Long): Boolean {
        if (cid <= 0L || cid == _state.value.selectedCid) return false
        val index = _state.value.detail?.pages?.indexOfFirst { it.cid == cid } ?: -1
        return playPartAt(index)
    }

    private fun playPartAt(index: Int): Boolean {
        val nextPart = _state.value.detail?.pages?.getOrNull(index) ?: return false
        initialResumeSettled = true
        _state.update {
            it.copy(
                selectedPartIndex = index,
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
        loadPlayback(qn = qn, cid = current.selectedCid, notifyDowngrade = true, positionMs = positionMs)
    }

    /**
     * 账号变了（切号 / 登录 / 退出）之后按当前清晰度重新取一次流。
     *
     * 大会员档位能不能用，取决于 playurl 返回的 dash 列表；已经拿在手里的那条流不会自己升级。
     * 这里只按用户当前选的清晰度重取（不自动跳到 4K），顺带把清晰度菜单里的档位刷新一遍，
     * 位置沿用播放器当前进度。
     */
    fun refreshPlayback(positionMs: Long) {
        val current = _state.value
        if (current.selectedCid <= 0L || current.source == null) return
        loadPlayback(
            qn = current.requestedQuality,
            cid = current.selectedCid,
            notifyDowngrade = false,
            positionMs = positionMs.coerceAtLeast(0L),
        )
    }

    fun markPlaybackStarted() {
        startTs = currentTimeMillis() / 1000
        lastHeartbeatAt = 0L
    }

    /** 界面每 500ms 调一次，内部按 15 秒节流。 */
    fun reportHeartbeat(playedSec: Long, isPlaying: Boolean) {
        val detail = _state.value.detail ?: return
        if (!isPlaying || startTs <= 0L) return
        val now = currentTimeMillis()
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
        // 连播时这条在上一条的收尾里才发出去，bvid 必须当场取，不能等协程跑起来再读。
        val targetBvid = bvid
        if (cid <= 0L || ts <= 0L || playedSec <= 0L) return
        externalScope.launch {
            heartbeat.report(
                bvid = targetBvid,
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

    fun toggleLike() = interactionDelegate.toggleLike()
    fun toggleDislike() = interactionDelegate.toggleDislike()
    fun coin(count: Int) = interactionDelegate.coin(count)
    fun favorite(
        addMediaIds: List<Long>,
        delMediaIds: List<Long>,
        nowFavorited: Boolean,
    ) = interactionDelegate.favorite(addMediaIds, delMediaIds, nowFavorited)
    fun triple() = interactionDelegate.triple()
    fun addToWatchLater() = interactionDelegate.addToWatchLater()
    fun toggleFollow() = interactionDelegate.toggleFollow()

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
            val resolved = resolveVideoPlayback(
                bvid = bvid,
                cid = cid,
                qn = qn,
                offlineMode = _state.value.offlineMode,
                notifyDowngrade = notifyDowngrade,
                cdnOptionId = settings.cdnOption.value,
                repository = repository,
                downloads = downloads,
            )
            if (resolved.source != null) {
                _state.update {
                    it.copy(
                        source = resolved.source,
                        isPlaybackLoading = false,
                        activeQuality = resolved.source.quality,
                        message = resolved.notice ?: it.message,
                    )
                }
            } else {
                _state.update { it.copy(isPlaybackLoading = false, playbackError = resolved.error) }
            }
        }
    }

    private fun loadRelated() {
        viewModelScope.launch {
            repository.related(bvid)
                .onSuccess { items -> _state.update { it.copy(related = items) } }
        }
    }

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 15_000L
    }
}
