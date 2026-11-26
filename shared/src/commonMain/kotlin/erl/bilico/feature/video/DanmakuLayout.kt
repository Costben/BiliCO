package erl.bilico.feature.video

import androidx.compose.ui.geometry.Rect
import kotlin.concurrent.Volatile
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import erl.bilico.data.settings.DanmakuSettings
import kotlin.math.max

internal data class FilteredDanmaku(
    val item: DanmakuItem,
    val displayText: String,
    /** 合并了多少条同文弹幕；大于 1 时前面会画一个更小字号的「(N)」。 */
    val count: Int = 1,
)

internal data class PlacedDanmaku(
    val item: DanmakuItem,
    val displayText: String,
    val type: DanmakuType,
    val y: Float,
    val width: Float,
    val lane: Int,
    val count: Int = 1,
)

internal fun filterAndMergeDanmaku(
    items: List<DanmakuItem>,
    settings: DanmakuSettings,
    recentTexts: MutableMap<String, Int>? = null,
): List<FilteredDanmaku> {
    if (items.isEmpty()) return emptyList()
    val filtered = ArrayList<DanmakuItem>(items.size)
    for (item in items) {
        when (item.type) {
            DanmakuType.SCROLL -> if (!settings.allowScroll) continue
            DanmakuType.TOP -> if (!settings.allowTop) continue
            DanmakuType.BOTTOM -> if (!settings.allowBottom) continue
        }
        val rgb = item.color and 0xFFFFFF
        if (!settings.allowColorful && rgb != 0xFFFFFF) continue
        filtered.add(item)
    }
    if (!settings.mergeDuplicates || filtered.isEmpty()) {
        return filtered.map { FilteredDanmaku(it, it.content) }
    }
    val result = ArrayList<FilteredDanmaku>(filtered.size)
    val recentIdxByText = HashMap<String, Int>(256)
    val counts = IntArray(filtered.size) { 1 }
    for (item in filtered) {
        if (item.isSelf) {
            result.add(FilteredDanmaku(item, item.content))
            continue
        }
        val key = item.content.trim()
        val prevResultIdx = recentIdxByText[key]
        if (prevResultIdx != null) {
            val prevItem = result[prevResultIdx].item
            if (item.timeMs - prevItem.timeMs <= MERGE_WINDOW_MS) {
                counts[prevResultIdx]++
                recentTexts?.set(key, item.timeMs)
                continue
            }
        }
        // 直播增量批次内只跳过跨批次已入屏的重复文案，避免回改在屏弹幕宽度引发重排顶掉后续弹幕。
        val prevCommittedTime = recentTexts?.get(key)
        if (prevCommittedTime != null && item.timeMs - prevCommittedTime <= MERGE_WINDOW_MS) {
            recentTexts[key] = item.timeMs
            continue
        }
        val newIdx = result.size
        recentIdxByText[key] = newIdx
        recentTexts?.set(key, item.timeMs)
        result.add(FilteredDanmaku(item, item.content))
    }
    if (recentTexts != null && recentTexts.size > 512) {
        val cutoff = filtered.last().timeMs - MERGE_WINDOW_MS
        val iterator = recentTexts.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value < cutoff) iterator.remove()
        }
    }
    for (i in result.indices) {
        val c = counts[i]
        if (c > 1) {
            val orig = result[i]
            result[i] = FilteredDanmaku(orig.item, orig.displayText, count = c)
        }
    }
    return result
}

internal class DanmakuTrackPlanner(
    private val widthPx: Float,
    heightPx: Float,
    private val fontPx: Float,
    areaFraction: Float,
    lineHeightRatio: Float = LINE_HEIGHT_RATIO,
    private val scrollDurationMs: Long = SCROLL_DURATION_MS,
    private val massiveMode: Boolean = false,
    bottomInsetPx: Float,
) {
    private val lineHeight = fontPx * lineHeightRatio
    private val effectiveArea = if (massiveMode) max(areaFraction, 0.75f) else areaFraction
    private val areaHeight = (heightPx * effectiveArea).coerceIn(lineHeight, heightPx)
    private val scrollLanes = max(1, ((areaHeight - fontPx) / lineHeight).toInt() + 1)
    private val topLanes = max(1, scrollLanes / 3)
    private val bottomLanes = max(1, scrollLanes / 3)
    private val scrollFree = LongArray(scrollLanes) { Long.MIN_VALUE }
    // 每轨最后放进去的那条：起播时间和宽度，用来算它现在跑到哪、新弹幕会不会追上它。
    private val scrollSince = LongArray(scrollLanes) { Long.MIN_VALUE }
    private val scrollWidth = FloatArray(scrollLanes)
    private val topFree = LongArray(topLanes) { Long.MIN_VALUE }
    private val bottomFree = LongArray(bottomLanes) { Long.MIN_VALUE }
    private val bottomBase = (heightPx - bottomInsetPx).coerceAtLeast(lineHeight)

    fun placeNext(
        entry: FilteredDanmaku,
        /** 按 [fontScale] 倍字号量文本宽度：正文是 1，合并数量那截是 [COUNT_FONT_SCALE]。 */
        measure: (String, Float) -> Float,
    ): PlacedDanmaku? {
        val item = entry.item
        val segments = parseDanmakuSegments(entry.displayText, item.emotes)
        val bodyWidth = if (segments.size == 1 && segments[0] is DanmakuSegment.TextSeg) {
            measure(entry.displayText, 1f)
        } else {
            var sum = 0f
            for (seg in segments) {
                sum += when (seg) {
                    is DanmakuSegment.TextSeg -> measure(seg.text, 1f)
                    is DanmakuSegment.EmoteSeg -> emoteAdvancePx(fontPx, seg.large)
                }
            }
            sum
        }
        val width = bodyWidth +
            if (entry.count > 1) measure(countLabel(entry.count), COUNT_FONT_SCALE) else 0f
        return when (item.type) {
            DanmakuType.SCROLL -> {
                var lane = -1
                var earliestLane = 0
                var earliestTime = Long.MAX_VALUE
                for (index in scrollFree.indices) {
                    val freeAt = scrollFree[index]
                    // 前一条得整条进屏，且不能被这条更快的长弹幕追上。
                    val fits = freeAt <= item.timeMs && !scrollOvertakes(
                        timeMs = item.timeMs.toLong(),
                        previousSinceMs = scrollSince[index],
                        previousWidth = scrollWidth[index],
                        width = width,
                        screenWidth = widthPx,
                        durationMs = scrollDurationMs,
                    )
                    if (fits) {
                        lane = index
                        break
                    }
                    if (freeAt < earliestTime) {
                        earliestTime = freeAt
                        earliestLane = index
                    }
                }
                if (lane < 0) {
                    if (massiveMode || item.isSelf) lane = earliestLane else return null
                }
                val occupancy = scrollDurationMs * (width + fontPx) / (widthPx + width)
                scrollFree[lane] = item.timeMs + occupancy.toLong()
                scrollSince[lane] = item.timeMs.toLong()
                scrollWidth[lane] = width
                PlacedDanmaku(item, entry.displayText, item.type, lane * lineHeight, width, lane, entry.count)
            }

            DanmakuType.TOP -> {
                var lane = -1
                for (index in topFree.indices) {
                    if (topFree[index] <= item.timeMs) {
                        lane = index
                        break
                    }
                }
                if (lane < 0) return null
                topFree[lane] = item.timeMs + FIXED_DURATION_MS
                PlacedDanmaku(item, entry.displayText, item.type, lane * lineHeight, width, lane, entry.count)
            }

            DanmakuType.BOTTOM -> {
                var lane = -1
                for (index in bottomFree.indices) {
                    if (bottomFree[index] <= item.timeMs) {
                        lane = index
                        break
                    }
                }
                if (lane < 0) return null
                bottomFree[lane] = item.timeMs + FIXED_DURATION_MS
                val y = bottomBase - (lane + 1) * lineHeight
                PlacedDanmaku(item, entry.displayText, item.type, y, width, lane, entry.count)
            }
        }
    }
}

internal fun layoutDanmaku(
    items: List<FilteredDanmaku>,
    /** 按 [fontScale] 倍字号量文本宽度：正文是 1，合并数量那截是 [COUNT_FONT_SCALE]。 */
    measure: (String, Float) -> Float,
    widthPx: Float,
    heightPx: Float,
    fontPx: Float,
    areaFraction: Float,
    lineHeightRatio: Float = LINE_HEIGHT_RATIO,
    scrollDurationMs: Long = SCROLL_DURATION_MS,
    massiveMode: Boolean = false,
    bottomInsetPx: Float,
): List<PlacedDanmaku> {
    if (items.isEmpty() || widthPx <= 0f || heightPx <= 0f || fontPx <= 0f) return emptyList()
    val planner = DanmakuTrackPlanner(
        widthPx = widthPx,
        heightPx = heightPx,
        fontPx = fontPx,
        areaFraction = areaFraction,
        lineHeightRatio = lineHeightRatio,
        scrollDurationMs = scrollDurationMs,
        massiveMode = massiveMode,
        bottomInsetPx = bottomInsetPx,
    )
    val result = ArrayList<PlacedDanmaku>(items.size)
    for (entry in items) {
        planner.placeNext(entry, measure)?.let(result::add)
    }
    return result
}

/**
 * 直播流增量弹幕布局缓冲：
 * 只给新到达的弹幕（`id > lastId`）接着既有轨道状态追加入轨，不再因为头部裁剪过期条目或新来重复文案
 * 而从头重排整屏，保证已在屏幕上滚动的弹幕既不会跳轨、也不会还没播完就突然消失。
 */
internal class LiveDanmakuBuffer {
    private val placedLive = ArrayList<PlacedDanmaku>()
    private var planner: DanmakuTrackPlanner? = null
    private var firstId = Long.MIN_VALUE
    private var lastId = Long.MIN_VALUE
    private val recentTexts = HashMap<String, Int>(256)

    fun reset() {
        placedLive.clear()
        planner = null
        firstId = Long.MIN_VALUE
        lastId = Long.MIN_VALUE
        recentTexts.clear()
    }

    fun rebuild(
        items: List<DanmakuItem>,
        settings: DanmakuSettings,
        measure: (String, Float) -> Float,
        widthPx: Float,
        heightPx: Float,
        fontPx: Float,
        bottomInsetPx: Float,
    ): List<PlacedDanmaku> {
        reset()
        if (items.isEmpty() || widthPx <= 0f || heightPx <= 0f || fontPx <= 0f) return emptyList()
        val newPlanner = DanmakuTrackPlanner(
            widthPx = widthPx,
            heightPx = heightPx,
            fontPx = fontPx,
            areaFraction = settings.areaFraction,
            lineHeightRatio = settings.lineHeightRatio,
            scrollDurationMs = settings.scrollDurationMs,
            massiveMode = settings.massiveMode,
            bottomInsetPx = bottomInsetPx,
        )
        planner = newPlanner
        for (entry in filterAndMergeDanmaku(items, settings, recentTexts)) {
            newPlanner.placeNext(entry, measure)?.let(placedLive::add)
        }
        firstId = items.first().id
        lastId = items.last().id
        return ArrayList(placedLive)
    }

    fun updateIncremental(
        previous: List<DanmakuItem>,
        next: List<DanmakuItem>,
        settings: DanmakuSettings,
        nowMs: Double,
        measure: (String, Float) -> Float,
        widthPx: Float,
        heightPx: Float,
        fontPx: Float,
        bottomInsetPx: Float,
    ): List<PlacedDanmaku> {
        if (next.isEmpty()) {
            reset()
            return emptyList()
        }
        val activePlanner = planner
        val canAppend = activePlanner != null &&
            widthPx > 0f &&
            heightPx > 0f &&
            fontPx > 0f &&
            previous.isNotEmpty() &&
            next.first().id >= firstId &&
            next.last().id >= lastId
        if (!canAppend) {
            return rebuild(next, settings, measure, widthPx, heightPx, fontPx, bottomInsetPx)
        }

        firstId = next.first().id
        var startIdx = next.size
        for (i in next.lastIndex downTo 0) {
            if (next[i].id <= lastId) {
                startIdx = i + 1
                break
            }
            if (i == 0) startIdx = 0
        }
        if (startIdx < next.size) {
            val newEntries = filterAndMergeDanmaku(
                items = next.subList(startIdx, next.size),
                settings = settings,
                recentTexts = recentTexts,
            )
            for (entry in newEntries) {
                activePlanner.placeNext(entry, measure)?.let(placedLive::add)
            }
            lastId = next.last().id
        }
        val maxDuration = max(settings.scrollDurationMs.toDouble(), FIXED_DURATION_MS.toDouble())
        val expireBefore = (nowMs - maxDuration - FADE_MS - 1_000.0).toLong()
        while (placedLive.isNotEmpty() && placedLive.first().item.timeMs < expireBefore) {
            placedLive.removeAt(0)
        }
        return ArrayList(placedLive)
    }
}

/**
 * 判断宽度为 [width] 的新滚动弹幕放进 [previousSinceMs] 起播、宽 [previousWidth] 的那条所在轨道后，
 * 会不会在它整条离屏前追上并盖住它。
 *
 * 所有滚动弹幕共用同一个时长，长弹幕因为要多走自身宽度那段距离，速度更快。两条都以
 * `(屏宽 + 宽度) / 时长` 前进时，新弹幕左缘从屏宽处追上前一条右缘所需的时间内，
 * 前一条是否已经离屏，等价于 `(p + w1) * w2 <= 屏宽 * (屏宽 - p)`（p 为前一条左缘当前 x）。
 * 这是 canvas_danmaku（PiliPlus 使用的弹幕库）`_scrollCanAddToTrack` 的第二条判据。
 */
private fun scrollOvertakes(
    timeMs: Long,
    previousSinceMs: Long,
    previousWidth: Float,
    width: Float,
    screenWidth: Float,
    durationMs: Long,
): Boolean {
    if (previousSinceMs == Long.MIN_VALUE || width <= previousWidth) return false
    if (durationMs <= 0L || screenWidth <= 0f) return false
    val previousTravel = screenWidth + previousWidth
    val previousLeft = screenWidth - (timeMs - previousSinceMs) * previousTravel / durationMs
    val previousRight = previousLeft + previousWidth
    if (previousRight <= 0f) return false
    return previousRight * width > screenWidth * (screenWidth - previousLeft)
}

/** 合并数量的括号截比正文小一号，照 PiliPlus（canvas_danmaku）用的 0.6 倍。 */
internal const val COUNT_FONT_SCALE = 0.6f

/** 合并数量画在正文前面：`(3)正文`。 */
internal fun countLabel(count: Int): String = "($count)"

internal const val LINE_HEIGHT_RATIO = 1.35f
internal const val BASE_FONT_DP = 15f
internal const val SCROLL_DURATION_MS = 8_000L
internal const val FIXED_DURATION_MS = 4_000L
internal const val FADE_MS = 240.0
internal const val BOTTOM_INSET_DP = 52f
private const val MERGE_WINDOW_MS = 5_000

/**
 * 直播弹幕时间轴：墙钟只用来量帧间隔，弹幕时间按帧间隔累加，
 * 缓冲、后台、暂停这些空档不会计入，恢复时从暂停处接着走。
 */
internal class DanmakuTimeline {
    /** 单调时钟，毫秒。 */
    var source: () -> Long = { 0L }

    /** 弹幕时间轴上的「现在」，由渲染循环每帧写入；入队方按它打时间戳。 */
    @Volatile
    var nowMs: Long = 0L
}

internal fun firstAfter(layout: List<PlacedDanmaku>, time: Long): Int {
    var low = 0
    var high = layout.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (layout[mid].item.timeMs <= time) low = mid + 1 else high = mid
    }
    return low
}

internal fun fadeAlpha(elapsed: Double, duration: Double): Float {
    if (elapsed < FADE_MS) return (elapsed / FADE_MS).toFloat()
    val remain = duration - elapsed
    if (remain < FADE_MS) return (remain / FADE_MS).toFloat()
    return 1f
}

/**
 * 按与 `onDraw` 完全一致的时间与位置算法从最前层往后查找命中的在屏弹幕。
 */
internal fun findVisibleDanmakuAt(
    placed: List<PlacedDanmaku>,
    nowMs: Double,
    screenWidth: Float,
    scrollDurationMs: Long,
    textHeightPx: Float,
    xPx: Float,
    yPx: Float,
    slopXPx: Float,
    slopYPx: Float,
): DanmakuHit? {
    if (placed.isEmpty() || screenWidth <= 0f) return null
    val scrollDuration = scrollDurationMs.toDouble()
    val fixedDuration = FIXED_DURATION_MS.toDouble()
    val maxDuration = max(scrollDuration, fixedDuration)
    val end = firstAfter(placed, nowMs.toLong() + 1L)
    val minTime = (nowMs - maxDuration - FADE_MS).toLong()

    for (index in end - 1 downTo 0) {
        val entry = placed[index]
        val startTime = entry.item.timeMs
        if (startTime < minTime) break
        val duration = if (entry.type == DanmakuType.SCROLL) scrollDuration else fixedDuration
        val elapsed = nowMs - startTime.toDouble()
        if (elapsed < 0.0 || elapsed > duration) continue
        if (fadeAlpha(elapsed, duration) <= 0.01f) continue

        val progress = (elapsed / duration).toFloat()
        val x = if (entry.type == DanmakuType.SCROLL) {
            screenWidth - progress * (screenWidth + entry.width)
        } else {
            (screenWidth - entry.width) * 0.5f
        }
        if (x + entry.width < 0f || x > screenWidth) continue
        if (
            xPx in (x - slopXPx)..(x + entry.width + slopXPx) &&
            yPx in (entry.y - slopYPx)..(entry.y + textHeightPx + slopYPx)
        ) {
            return DanmakuHit(
                item = entry.item,
                displayText = entry.displayText,
                count = entry.count,
                boundsPx = Rect(x, entry.y, x + entry.width, entry.y + textHeightPx),
            )
        }
    }
    return null
}
