package erl.bilico.feature.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.media3.common.Player
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import kotlin.math.max

/**
 * 弹幕层是一个自绘 View，不是 Compose 画布。
 *
 * 旧项目（danmaku-engine 的 DanmakuRenderView）也是这条路线：弹幕有自己的帧循环和自己的
 * 显示列表，播放期间每秒刷新的只有这一块，Compose 那边一次重组都不会发生。
 * 帧节奏由 onDraw 里的 postInvalidateOnAnimation 自己续，位置直接读播放器，不写任何快照状态。
 */
internal class DanmakuSurfaceView(context: Context) : View(context) {

    var player: Player? = null

    var items: List<DanmakuItem> = emptyList()
        set(value) {
            if (field === value) return
            field = value
            relayout()
        }

    var danmakuEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var danmakuOpacity: Float = 1f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var danmakuFontScale: Float = 1f
        set(value) {
            if (field == value) return
            field = value
            relayout()
        }

    var danmakuAreaFraction: Float = 0.25f
        set(value) {
            if (field == value) return
            field = value
            relayout()
        }

    var playing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) postInvalidateOnAnimation()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        setShadowLayer(SHADOW_RADIUS, SHADOW_DX, SHADOW_DY, SHADOW_COLOR)
    }

    private var placed: List<PlacedDanmaku> = emptyList()

    init {
        setWillNotDraw(false)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        relayout()
    }

    private fun relayout() {
        if (width <= 0 || height <= 0 || items.isEmpty()) {
            placed = emptyList()
            return
        }
        val fontPx = BASE_FONT_DP * resources.displayMetrics.density * danmakuFontScale
        paint.textSize = fontPx
        placed = layoutDanmaku(
            items = items,
            measure = { paint.measureText(it) },
            widthPx = width.toFloat(),
            heightPx = height.toFloat(),
            fontPx = fontPx,
            areaFraction = danmakuAreaFraction,
            bottomInsetPx = BOTTOM_INSET_DP * resources.displayMetrics.density,
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!danmakuEnabled || placed.isEmpty()) return
        val now = player?.currentPosition ?: return
        paint.alpha = (danmakuOpacity.coerceIn(0f, 1f) * 255).toInt()
        val screenWidth = width.toFloat()
        val end = firstAfter(placed, now)
        val minTime = now - SCROLL_DURATION_MS - FADE_MS
        for (index in end - 1 downTo 0) {
            val entry = placed[index]
            val startTime = entry.item.timeMs
            if (startTime < minTime) break
            val duration = if (entry.type == DanmakuType.SCROLL) {
                SCROLL_DURATION_MS
            } else {
                FIXED_DURATION_MS
            }
            val elapsed = now - startTime
            if (elapsed < 0L || elapsed > duration) continue
            val fade = fadeAlpha(elapsed, duration)
            if (fade <= 0.01f) continue
            paint.color = entry.item.color
            paint.alpha = (fade * danmakuOpacity.coerceIn(0f, 1f) * 255).toInt()
            val progress = elapsed.toFloat() / duration
            val x = if (entry.type == DanmakuType.SCROLL) {
                screenWidth - progress * (screenWidth + entry.width)
            } else {
                (screenWidth - entry.width) / 2f
            }
            canvas.drawText(entry.item.content, x, entry.y - paint.ascent(), paint)
        }
        // 只有播放中才续帧：暂停后停在最后一帧，不空转。
        if (player?.isPlaying == true) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        placed = emptyList()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SHADOW_RADIUS = 2.5f
        const val SHADOW_DX = 1.5f
        const val SHADOW_DY = 1.5f
        const val SHADOW_COLOR = 0xCC000000.toInt()
    }
}

internal data class PlacedDanmaku(
    val item: DanmakuItem,
    val type: DanmakuType,
    val y: Float,
    val width: Float,
    val lane: Int,
)

/**
 * 轨道分配照旧项目的分层规则：滚动、顶部、底部各有一个独立轨道池，互不占位
 * （danmaku-engine 里 ScrollLayer / TopCenterLayer / BottomCenterLayer 就是各自持有一组 line）。
 * 池子满了就丢掉这条，绝不往上叠；轨道宽度用真实排版宽度，不用估算。
 */
internal fun layoutDanmaku(
    items: List<DanmakuItem>,
    measure: (String) -> Float,
    widthPx: Float,
    heightPx: Float,
    fontPx: Float,
    areaFraction: Float,
    bottomInsetPx: Float,
): List<PlacedDanmaku> {
    if (items.isEmpty() || widthPx <= 0f || heightPx <= 0f || fontPx <= 0f) return emptyList()
    val lineHeight = fontPx * LINE_HEIGHT_RATIO
    val areaHeight = (heightPx * areaFraction).coerceIn(lineHeight, heightPx)
    val scrollLanes = max(1, ((areaHeight - fontPx) / lineHeight).toInt() + 1)
    val topLanes = max(1, scrollLanes / 4)
    val bottomLanes = max(1, scrollLanes / 4)
    val scrollFree = LongArray(scrollLanes) { Long.MIN_VALUE }
    val topFree = LongArray(topLanes) { Long.MIN_VALUE }
    val bottomFree = LongArray(bottomLanes) { Long.MIN_VALUE }
    val bottomBase = (heightPx - bottomInsetPx).coerceAtLeast(lineHeight)

    val result = ArrayList<PlacedDanmaku>(items.size)
    for (item in items) {
        val width = measure(item.content)
        when (item.type) {
            DanmakuType.SCROLL -> {
                var lane = -1
                for (index in scrollFree.indices) {
                    if (scrollFree[index] <= item.timeMs) {
                        lane = index
                        break
                    }
                }
                if (lane < 0) continue
                // 上一段完全离开右边之后再放，留一个字宽余量。
                val occupancy = SCROLL_DURATION_MS * (width + fontPx) / (widthPx + width)
                scrollFree[lane] = item.timeMs + occupancy.toLong()
                result += PlacedDanmaku(item, item.type, lane * lineHeight, width, lane)
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
                result += PlacedDanmaku(item, item.type, lane * lineHeight, width, lane)
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
                result += PlacedDanmaku(item, item.type, y, width, lane)
            }
        }
    }
    return result
}

/** 返回第一条时间大于 [time] 的弹幕下标。 */
private fun firstAfter(layout: List<PlacedDanmaku>, time: Long): Int {
    var low = 0
    var high = layout.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (layout[mid].item.timeMs <= time) low = mid + 1 else high = mid
    }
    return low
}

private fun fadeAlpha(elapsed: Long, duration: Long): Float {
    if (elapsed < FADE_MS) return elapsed.toFloat() / FADE_MS
    val remain = duration - elapsed
    if (remain < FADE_MS) return remain.toFloat() / FADE_MS
    return 1f
}

internal const val LINE_HEIGHT_RATIO = 1.25f
internal const val BASE_FONT_DP = 15f
private const val SCROLL_DURATION_MS = 8_000L
private const val FIXED_DURATION_MS = 4_000L
private const val FADE_MS = 300L
private const val BOTTOM_INSET_DP = 52f
