package erl.bilico.feature.video

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import erl.bilico.data.model.SponsorActionType
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
