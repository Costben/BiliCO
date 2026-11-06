package erl.bilico.feature.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import kotlinx.coroutines.delay
import kotlin.math.max

private const val SCROLL_DURATION_MS = 8_000L
private const val FIXED_DURATION_MS = 4_000L
private const val FADE_MS = 300L

/**
 * 轻量弹幕层：Compose Canvas 画文本，位置每帧直接读播放器，不经过 500ms 轮询。
 * 行分配在弹幕内容变化时算一次（用字宽估算），绘制时只做二分查找和位移。
 */
@Composable
internal fun DanmakuOverlay(
    player: Player?,
    items: List<DanmakuItem>,
    isPlaying: Boolean,
    enabled: Boolean,
    opacity: Float,
    fontScale: Float,
    areaFraction: Float,
    modifier: Modifier = Modifier,
) {
    if (!enabled || items.isEmpty()) return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        if (widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

        val density = LocalDensity.current
        val fontPx = with(density) { BASE_FONT_SIZE.toPx() } * fontScale
        // 底栏控件常驻，底部弹幕整体上移，别被按钮压住。
        val bottomInsetPx = with(density) { BOTTOM_INSET.toPx() }
        val layout = remember(items, widthPx, heightPx, fontPx, areaFraction, bottomInsetPx) {
            buildLayout(items, widthPx, heightPx, fontPx, areaFraction, bottomInsetPx)
        }
        val measurer = rememberTextMeasurer(cacheSize = 1024)
        val fontSp = with(density) { fontPx.toSp() }
        val textStyle = remember(fontSp) {
            TextStyle(
                fontSize = fontSp,
                shadow = Shadow(Color(0xCC000000), Offset(1.5f, 1.5f), 2.5f),
            )
        }

        var framePosition by remember { mutableLongStateOf(player?.currentPosition ?: 0L) }

        LaunchedEffect(player, isPlaying) {
            val target = player ?: return@LaunchedEffect
            while (true) {
                val position = target.currentPosition.coerceAtLeast(0L)
                if (position != framePosition) framePosition = position
                if (isPlaying) withFrameNanos { } else delay(120)
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val now = framePosition
            if (layout.isEmpty() || now <= 0L) return@Canvas
            val end = firstAfter(layout, now)
            val minTime = now - SCROLL_DURATION_MS - FADE_MS
            for (index in end - 1 downTo 0) {
                val placed = layout[index]
                val startTime = placed.item.timeMs
                if (startTime < minTime) break
                val duration = if (placed.type == DanmakuType.SCROLL) {
                    SCROLL_DURATION_MS
                } else {
                    FIXED_DURATION_MS
                }
                val elapsed = now - startTime
                if (elapsed < 0L || elapsed > duration) continue
                val progress = elapsed.toFloat() / duration
                val x = if (placed.type == DanmakuType.SCROLL) {
                    size.width - progress * (size.width + placed.width)
                } else {
                    (size.width - placed.width) / 2f
                }
                val alpha = fadeAlpha(elapsed, duration) * opacity
                if (alpha <= 0.01f) continue
                drawText(
                    textMeasurer = measurer,
                    text = placed.item.content,
                    topLeft = Offset(x, placed.y),
                    style = textStyle.copy(
                        color = Color(0xFF000000L or placed.item.color.toLong()).copy(alpha = alpha),
                    ),
                    // 弹幕永远单行：宽度由文本自身决定，不让画布宽度把它折行。
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun fadeAlpha(elapsed: Long, duration: Long): Float {
    if (elapsed < FADE_MS) return elapsed.toFloat() / FADE_MS
    val remain = duration - elapsed
    if (remain < FADE_MS) return remain.toFloat() / FADE_MS
    return 1f
}

private data class PlacedDanmaku(
    val item: DanmakuItem,
    val type: DanmakuType,
    val y: Float,
    val width: Float,
)

private fun buildLayout(
    items: List<DanmakuItem>,
    widthPx: Float,
    heightPx: Float,
    fontPx: Float,
    areaFraction: Float,
    bottomInsetPx: Float,
): List<PlacedDanmaku> {
    if (items.isEmpty() || widthPx <= 0f || heightPx <= 0f || fontPx <= 0f) return emptyList()
    val lineHeight = fontPx * 1.35f
    val areaHeight = (heightPx * areaFraction).coerceIn(lineHeight, heightPx)
    val lanes = max(1, ((areaHeight - fontPx) / lineHeight).toInt() + 1)
    // 顶部弹幕和滚动弹幕共用同一条轨道：轨道被任一方占着就不能再放，
    // 否则一条居中固定的顶部弹幕会正好压在横穿画面的滚动弹幕上。
    val scrollFree = LongArray(lanes) { Long.MIN_VALUE }
    val fixedFree = LongArray(lanes) { Long.MIN_VALUE }
    val bottomFree = LongArray(max(1, lanes / 4)) { Long.MIN_VALUE }
    val topLanes = max(1, lanes / 4)
    val bottomBase = (heightPx - bottomInsetPx).coerceAtLeast(lineHeight)

    val result = ArrayList<PlacedDanmaku>(items.size)
    for (item in items) {
        val width = estimateWidth(item.content, fontPx)
        when (item.type) {
            DanmakuType.SCROLL -> {
                var lane = -1
                for (index in scrollFree.indices) {
                    if (scrollFree[index] <= item.timeMs && fixedFree[index] <= item.timeMs) {
                        lane = index
                        break
                    }
                }
                // 满屏时丢掉这条，比叠在一起可读。
                if (lane < 0) continue
                // 多留一个字宽：宽度是估算值，估小了上一行还没走完就被盖住。
                val occupancy = SCROLL_DURATION_MS * (width + fontPx) / (widthPx + width)
                scrollFree[lane] = item.timeMs + occupancy.toLong()
                result += PlacedDanmaku(item, item.type, lane * lineHeight, width)
            }

            DanmakuType.TOP -> {
                // 顶部弹幕只用最上面几条轨道，占满就丢掉，不往画面中间挤。
                var lane = -1
                for (index in 0 until topLanes) {
                    if (fixedFree[index] <= item.timeMs && scrollFree[index] <= item.timeMs) {
                        lane = index
                        break
                    }
                }
                if (lane < 0) continue
                fixedFree[lane] = item.timeMs + FIXED_DURATION_MS
                result += PlacedDanmaku(item, item.type, lane * lineHeight, width)
            }

            DanmakuType.BOTTOM -> {
                val lane = pickLane(bottomFree, item.timeMs)
                if (lane < 0) continue
                bottomFree[lane] = item.timeMs + FIXED_DURATION_MS
                val y = bottomBase - (lane + 1) * lineHeight - fontPx * 0.6f
                result += PlacedDanmaku(item, item.type, y.coerceAtLeast(0f), width)
            }
        }
    }
    return result
}

/** 取第一条空着的底部轨道；全占满就丢掉这条，不往上叠。 */
private fun pickLane(free: LongArray, time: Int): Int {
    for (index in free.indices) {
        if (free[index] <= time) return index
    }
    return -1
}

/** 中文字符按一个字号宽，ASCII 按 0.56；只用于分配轨道，绘制仍用真实排版宽度。 */
private fun estimateWidth(text: String, fontPx: Float): Float {
    var units = 0f
    for (char in text) {
        units += if (char.code >= 0x2E80) 0.98f else 0.56f
    }
    return units * fontPx
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

private val BASE_FONT_SIZE = 16.sp
private val BOTTOM_INSET = 52.dp
