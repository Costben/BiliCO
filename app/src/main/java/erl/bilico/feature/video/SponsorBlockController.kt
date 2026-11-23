package erl.bilico.feature.video

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import erl.bilico.data.model.SponsorActionType
import erl.bilico.data.model.SponsorCategory
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.repository.SponsorBlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class UndoSkipNotice(
    val segment: SponsorSegment,
    val fromMs: Long,
    val expiresAtMs: Long,
)

internal data class SponsorSubmitDraft(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val category: SponsorCategory = SponsorCategory.Sponsor,
    val actionType: SponsorActionType = SponsorActionType.Skip,
)

/**
 * 空降助手播放控制器：按 `(bvid, cid)` 拉取社区标记片段，驱动进度条分段染色、自动/手动跳过、静音与撤销回退。
 */
internal class SponsorBlockController(
    private val repository: SponsorBlockRepository,
    private val scope: CoroutineScope,
) {
    var segments: List<SponsorSegment> by mutableStateOf(emptyList())
        private set

    var promptSegment: SponsorSegment? by mutableStateOf(null)
        private set

    var undoNotice: UndoSkipNotice? by mutableStateOf(null)
        private set

    var poiSegment: SponsorSegment? by mutableStateOf(null)
        private set

    var submitDraft by mutableStateOf(SponsorSubmitDraft())
        private set

    private var currentBvid = ""
    private var currentCid = 0L
    private var fetchJob: Job? = null
    private val handledUuids = HashSet<String>()
    private val dismissedPoiUuids = HashSet<String>()
    private var mutedBySponsor = false
    private var savedVolume = 1f

    fun attach(bvid: String, cid: Long, enabled: Boolean, player: Player) {
        if (!enabled) {
            clear(player)
            return
        }
        if (bvid == currentBvid && cid == currentCid) return
        clear(player)
        if (bvid.isBlank() || cid <= 0L) return
        currentBvid = bvid
        currentCid = cid
        fetchJob = scope.launch {
            repository.skipSegments(bvid, cid)
                .onSuccess { list ->
                    if (currentBvid == bvid && currentCid == cid) {
                        segments = list
                    }
                }
        }
    }

    fun clear(player: Player) {
        fetchJob?.cancel()
        fetchJob = null
        currentBvid = ""
        currentCid = 0L
        segments = emptyList()
        promptSegment = null
        undoNotice = null
        poiSegment = null
        submitDraft = SponsorSubmitDraft()
        handledUuids.clear()
        dismissedPoiUuids.clear()
        restoreMuteIfNeeded(player)
    }

    fun onPosition(
        positionMs: Long,
        player: Player,
        enabled: Boolean,
        skipMode: Int,
        enabledCategories: Set<String>,
    ) {
        if (!enabled || segments.isEmpty()) {
            promptSegment = null
            poiSegment = null
            undoNotice = null
            restoreMuteIfNeeded(player)
            return
        }

        val now = SystemClock.uptimeMillis()
        undoNotice?.let { notice ->
            if (now >= notice.expiresAtMs) undoNotice = null
        }

        val active = segments.filter { it.category.id in enabledCategories }

        val inMute = skipMode != MODE_MARK_ONLY && active.any { seg ->
            seg.actionType == SponsorActionType.Mute &&
                positionMs >= seg.startMs &&
                positionMs < seg.endMs
        }
        if (inMute) {
            if (!mutedBySponsor) {
                savedVolume = player.volume
                player.volume = 0f
                mutedBySponsor = true
            }
        } else {
            restoreMuteIfNeeded(player)
        }

        val hitSkip = if (skipMode == MODE_MARK_ONLY) {
            null
        } else {
            active.firstOrNull { seg ->
                if (seg.actionType != SponsorActionType.Skip || seg.uuid in handledUuids) return@firstOrNull false
                val tailGuard = (seg.endMs - SKIP_END_GUARD_MS).coerceAtLeast(seg.startMs + MIN_HIT_SPAN_MS)
                positionMs >= seg.startMs && positionMs < tailGuard
            }
        }

        if (hitSkip != null) {
            if (skipMode == MODE_AUTO_SKIP) {
                handledUuids.add(hitSkip.uuid)
                player.seekTo(hitSkip.endMs)
                promptSegment = null
                undoNotice = UndoSkipNotice(
                    segment = hitSkip,
                    fromMs = positionMs,
                    expiresAtMs = now + UNDO_SHOW_MS,
                )
            } else {
                promptSegment = hitSkip
            }
        } else {
            promptSegment = null
        }

        poiSegment = if (skipMode == MODE_MARK_ONLY) {
            null
        } else {
            active.firstOrNull { seg ->
                seg.actionType == SponsorActionType.Poi &&
                    seg.uuid !in dismissedPoiUuids &&
                    positionMs < seg.startMs - POI_LEAD_MS
            }
        }
    }

    fun skipPromptSegment(player: Player) {
        val seg = promptSegment ?: return
        val fromMs = player.currentPosition.coerceAtLeast(0L)
        handledUuids.add(seg.uuid)
        promptSegment = null
        player.seekTo(seg.endMs)
        undoNotice = UndoSkipNotice(
            segment = seg,
            fromMs = fromMs,
            expiresAtMs = SystemClock.uptimeMillis() + UNDO_SHOW_MS,
        )
    }

    fun dismissPromptSegment() {
        val seg = promptSegment ?: return
        handledUuids.add(seg.uuid)
        promptSegment = null
    }

    /**
     * 空降助手里的快捷跳转：精彩时刻跳到标记点，其余跳过/静音类片段直接跳到片段结尾（跳过该段）。
     */
    fun jumpToSegment(player: Player, segment: SponsorSegment) {
        val isPoi = segment.actionType == SponsorActionType.Poi || segment.category == SponsorCategory.PoiHighlight
        val targetMs = if (isPoi) {
            dismissedPoiUuids.add(segment.uuid)
            segment.startMs
        } else {
            handledUuids.add(segment.uuid)
            restoreMuteIfNeeded(player)
            segment.endMs
        }
        promptSegment = null
        poiSegment = null
        player.seekTo(targetMs.coerceAtLeast(0L))
    }

    fun jumpToPoi(player: Player) {
        val seg = poiSegment ?: return
        dismissedPoiUuids.add(seg.uuid)
        poiSegment = null
        player.seekTo(seg.startMs)
    }

    fun dismissPoi() {
        val seg = poiSegment ?: return
        dismissedPoiUuids.add(seg.uuid)
        poiSegment = null
    }

    fun undoSkip(player: Player) {
        val notice = undoNotice ?: return
        handledUuids.add(notice.segment.uuid)
        undoNotice = null
        player.seekTo(notice.fromMs)
    }

    fun restoreMuteIfNeeded(player: Player) {
        if (!mutedBySponsor) return
        player.volume = savedVolume
        mutedBySponsor = false
    }

    fun updateSubmitDraft(draft: SponsorSubmitDraft) {
        submitDraft = draft
    }

    fun clearSubmitDraft() {
        submitDraft = SponsorSubmitDraft()
    }

    suspend fun submitSegment(
        bvid: String,
        cid: Long,
        videoDurationMs: Long,
        startMs: Long,
        endMs: Long,
        category: SponsorCategory,
        actionType: SponsorActionType,
        userId: String,
        userAgent: String,
        currentPosMs: Long,
    ): Result<Unit> {
        val result = repository.submitSegment(
            bvid = bvid,
            cid = cid,
            videoDurationMs = videoDurationMs,
            startMs = startMs,
            endMs = endMs,
            category = category,
            actionType = actionType,
            userId = userId,
            userAgent = userAgent,
        )
        return result.map { created ->
            if (currentBvid == bvid && currentCid == cid) {
                created.forEach { seg ->
                    if (currentPosMs in seg.startMs..seg.endMs) {
                        handledUuids.add(seg.uuid)
                    }
                }
                val existingIds = segments.mapTo(HashSet()) { it.uuid }
                val merged = (segments + created.filter { it.uuid !in existingIds })
                    .sortedBy { it.startMs }
                segments = merged
                clearSubmitDraft()
                scope.launch {
                    repository.skipSegments(bvid, cid, bypassCache = true).onSuccess { refreshed ->
                        if (currentBvid == bvid && currentCid == cid && refreshed.isNotEmpty()) {
                            segments = refreshed
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val MODE_AUTO_SKIP = 0
        const val MODE_MANUAL_PROMPT = 1
        const val MODE_MARK_ONLY = 2
        const val SKIP_END_GUARD_MS = 400L
        const val MIN_HIT_SPAN_MS = 400L
        const val POI_LEAD_MS = 1_500L
        const val UNDO_SHOW_MS = 4_000L
    }
}
