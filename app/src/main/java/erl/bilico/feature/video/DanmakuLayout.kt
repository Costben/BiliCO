package erl.bilico.feature.video

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
        val key = item.content.trim()
        val prevResultIdx = recentIdxByText[key]
        if (prevResultIdx != null) {
            val prevItem = result[prevResultIdx].item
            if (item.timeMs - prevItem.timeMs <= MERGE_WINDOW_MS) {
                counts[prevResultIdx]++
                continue
            }
        }
        val newIdx = result.size
        recentIdxByText[key] = newIdx
        result.add(FilteredDanmaku(item, item.content))
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
    val lineHeight = fontPx * lineHeightRatio
    val effectiveArea = if (massiveMode) max(areaFraction, 0.75f) else areaFraction
    val areaHeight = (heightPx * effectiveArea).coerceIn(lineHeight, heightPx)
    val scrollLanes = max(1, ((areaHeight - fontPx) / lineHeight).toInt() + 1)
    val topLanes = max(1, scrollLanes / 3)
    val bottomLanes = max(1, scrollLanes / 3)
    val scrollFree = LongArray(scrollLanes) { Long.MIN_VALUE }
    // 每轨最后放进去的那条：起播时间和宽度，用来算它现在跑到哪、新弹幕会不会追上它。
    val scrollSince = LongArray(scrollLanes) { Long.MIN_VALUE }
    val scrollWidth = FloatArray(scrollLanes)
    val topFree = LongArray(topLanes) { Long.MIN_VALUE }
    val bottomFree = LongArray(bottomLanes) { Long.MIN_VALUE }
    val bottomBase = (heightPx - bottomInsetPx).coerceAtLeast(lineHeight)

    val result = ArrayList<PlacedDanmaku>(items.size)
    for (entry in items) {
        val item = entry.item
        val width = measure(entry.displayText, 1f) +
            if (entry.count > 1) measure(countLabel(entry.count), COUNT_FONT_SCALE) else 0f
        when (item.type) {
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
                    if (massiveMode) lane = earliestLane else continue
                }
                val occupancy = scrollDurationMs * (width + fontPx) / (widthPx + width)
                scrollFree[lane] = item.timeMs + occupancy.toLong()
                scrollSince[lane] = item.timeMs.toLong()
                scrollWidth[lane] = width
                result += PlacedDanmaku(item, entry.displayText, item.type, lane * lineHeight, width, lane, entry.count)
            }

            DanmakuType.TOP -> {
                var lane = -1
                for (index in topFree.indices) {
                    if (topFree[index] <= item.timeMs) {
                        lane = index
                        break
                    }
                }
                if (lane < 0) continue
                topFree[lane] = item.timeMs + FIXED_DURATION_MS
                result += PlacedDanmaku(item, entry.displayText, item.type, lane * lineHeight, width, lane, entry.count)
            }

            DanmakuType.BOTTOM -> {
                var lane = -1
                for (index in bottomFree.indices) {
                    if (bottomFree[index] <= item.timeMs) {
                        lane = index
                        break
                    }
                }
                if (lane < 0) continue
                bottomFree[lane] = item.timeMs + FIXED_DURATION_MS
                val y = bottomBase - (lane + 1) * lineHeight
                result += PlacedDanmaku(item, entry.displayText, item.type, y, width, lane, entry.count)
            }
        }
    }
    return result
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
