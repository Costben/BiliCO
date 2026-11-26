package erl.bilico.feature.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.player.BiliPlayer
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized
import kotlin.math.abs
import kotlin.math.max

private class DanmakuTextPair(
    val stroke: TextLayoutResult?,
    val fill: TextLayoutResult,
)

/**
 * Pure Compose/Skia 弹幕渲染层：
 * 使用 [DanmakuTrackPlanner] / [LiveDanmakuBuffer] 做确定性预排轨与防追尾检测，
 * 每条弹幕从入屏到离屏固定在自己的轨道上，不会因新弹幕进入而跳行、闪烁或被提前覆盖。
 */
@Composable
internal fun DanmakuOverlay(
    player: BiliPlayer?,
    items: List<DanmakuItem>,
    isPlaying: Boolean,
    settings: DanmakuSettings,
    modifier: Modifier = Modifier.fillMaxSize(),
    timeline: DanmakuTimeline? = null,
) {
    val isTop = LocalRouteIsTop.current
    val minimized = LocalRouteMinimized.current
    val interaction = LocalDanmakuInteraction.current

    if (!settings.enabled || minimized || !isTop) {
        DisposableEffect(interaction) {
            interaction?.hitTest = null
            onDispose { }
        }
        return
    }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 512)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var currentTimeMs by remember { mutableDoubleStateOf((timeline?.nowMs ?: player?.currentPosition ?: 0L).toDouble()) }

    val scrollDurationMs = settings.scrollDurationMs
    val fixedDurationMs = FIXED_DURATION_MS
    val maxWindowMs = max(scrollDurationMs, fixedDurationMs).toDouble()
    val alphaBase = settings.opacity.coerceIn(10, 100) / 100f
    val fontWeight = FontWeight(settings.fontWeight.coerceIn(1, 9) * 100)
    val fontScale = settings.fontScale.coerceIn(50, 200) / 100f
    val baseFontPx = with(density) { (BASE_FONT_DP * fontScale).dp.toPx() }
    val bottomInsetPx = with(density) { BOTTOM_INSET_DP.dp.toPx() }
    val slopXPx = with(density) { 8.dp.toPx() }
    val slopYPx = with(density) { 6.dp.toPx() }

    val widthPx = canvasSize.width.toFloat()
    val heightPx = canvasSize.height.toFloat()

    val widthMeasureCache = remember(baseFontPx, fontWeight) { HashMap<String, Float>(512) }
    val measureWidth: (String, Float) -> Float = remember(baseFontPx, fontWeight, density, textMeasurer) {
        { text, scale ->
            val key = if (scale == 1f) text else "$scale|$text"
            widthMeasureCache[key] ?: run {
                val sp = with(density) { (baseFontPx * scale).toSp() }
                val measured = textMeasurer.measure(
                    text = text,
                    style = TextStyle(fontSize = sp, fontWeight = fontWeight),
                    maxLines = 1,
                ).size.width.toFloat()
                if (widthMeasureCache.size >= 1024) widthMeasureCache.clear()
                widthMeasureCache[key] = measured
                measured
            }
        }
    }

    val liveBuffer = remember { LiveDanmakuBuffer() }
    var prevLiveItems by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    var liveLayoutVersion by remember { mutableIntStateOf(0) }

    val placed: List<PlacedDanmaku> = remember(
        items,
        settings.allowScroll,
        settings.allowTop,
        settings.allowBottom,
        settings.allowColorful,
        settings.mergeDuplicates,
        settings.area,
        settings.lineHeight,
        settings.speed,
        settings.massiveMode,
        widthPx,
        heightPx,
        baseFontPx,
        timeline != null,
    ) {
        if (items.isEmpty() || widthPx <= 0f || heightPx <= 0f || baseFontPx <= 0f) {
            if (timeline != null) liveBuffer.reset()
            prevLiveItems = emptyList()
            emptyList()
        } else if (timeline != null) {
            val prev = prevLiveItems
            val updated = liveBuffer.updateIncremental(
                previous = prev,
                next = items,
                settings = settings,
                nowMs = currentTimeMs,
                measure = measureWidth,
                widthPx = widthPx,
                heightPx = heightPx,
                fontPx = baseFontPx,
                bottomInsetPx = bottomInsetPx,
            )
            prevLiveItems = items
            liveLayoutVersion++
            updated
        } else {
            val filtered = filterAndMergeDanmaku(items, settings)
            layoutDanmaku(
                items = filtered,
                measure = measureWidth,
                widthPx = widthPx,
                heightPx = heightPx,
                fontPx = baseFontPx,
                areaFraction = settings.areaFraction,
                lineHeightRatio = settings.lineHeightRatio,
                scrollDurationMs = scrollDurationMs,
                massiveMode = settings.massiveMode,
                bottomInsetPx = bottomInsetPx,
            )
        }
    }

    val layoutCache = remember(baseFontPx, fontWeight, alphaBase, settings.strokeWidth) {
        LinkedHashMap<String, DanmakuTextPair>(256)
    }

    DisposableEffect(interaction, placed, widthPx, scrollDurationMs, baseFontPx) {
        interaction?.hitTest = { offset ->
            findVisibleDanmakuAt(
                placed = placed,
                nowMs = currentTimeMs,
                screenWidth = widthPx,
                scrollDurationMs = scrollDurationMs,
                textHeightPx = baseFontPx * settings.lineHeightRatio,
                xPx = offset.x,
                yPx = offset.y,
                slopXPx = slopXPx,
                slopYPx = slopYPx,
            )
        }
        onDispose {
            interaction?.hitTest = null
        }
    }

    val active = isPlaying && !minimized && isTop
    LaunchedEffect(player, timeline, active, placed, maxWindowMs) {
        if (timeline != null) {
            var lastWall = 0L
            var lastTickNanos = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val wall = timeline.source()
                    if (active && lastWall > 0L && wall > lastWall) {
                        val delta = (wall - lastWall).coerceAtMost(100L)
                        timeline.nowMs += delta
                    }
                    lastWall = wall
                    if (lastTickNanos != 0L && nanos - lastTickNanos < 15_000_000L) {
                        return@withFrameNanos
                    }
                    lastTickNanos = nanos
                    val now = timeline.nowMs.toDouble()
                    if (abs(now - currentTimeMs) > 0.5 || active) {
                        currentTimeMs = now
                        frameTick = nanos
                    }
                }
            }
        } else {
            if (!active) {
                currentTimeMs = (player?.currentPosition ?: 0L).toDouble()
                frameTick++
                return@LaunchedEffect
            }
            var anchorPosMs = (player?.currentPosition ?: 0L).toDouble()
            var anchorNanos = 0L
            var lastTickNanos = 0L
            var hadVisible = false
            while (true) {
                withFrameNanos { nanos ->
                    if (lastTickNanos != 0L && nanos - lastTickNanos < 15_000_000L) {
                        return@withFrameNanos
                    }
                    lastTickNanos = nanos
                    val playerPos = (player?.currentPosition ?: 0L).toDouble()
                    val speed = (player?.speed ?: 1f).coerceIn(0.25f, 4f).toDouble()
                    if (anchorNanos == 0L) {
                        anchorPosMs = playerPos
                        anchorNanos = nanos
                    } else {
                        val predicted = anchorPosMs + ((nanos - anchorNanos) / 1_000_000.0) * speed
                        // AVPlayer currentPosition 有时会阶梯跳动；小偏差用本地单调时钟插值平滑推进，seek/大漂移立刻对齐。
                        if (abs(predicted - playerPos) > 220.0) {
                            anchorPosMs = playerPos
                            anchorNanos = nanos
                        } else {
                            // 轻微向真实播放器时钟靠拢，保证长期不漂移。
                            anchorPosMs += (playerPos - predicted) * 0.12
                        }
                    }
                    val now = anchorPosMs + ((nanos - anchorNanos) / 1_000_000.0) * speed
                    val endIdx = firstAfter(placed, now.toLong() + 1L)
                    val minStart = (now - maxWindowMs - FADE_MS).toLong()
                    val hasVisible = endIdx > 0 && placed[endIdx - 1].item.timeMs >= minStart
                    if (hasVisible || hadVisible) {
                        hadVisible = hasVisible
                        currentTimeMs = now
                        frameTick = nanos
                    }
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .clipToBounds(),
    ) {
        frameTick
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f || placed.isEmpty()) return@Canvas

        val now = currentTimeMs
        val scrollDur = scrollDurationMs.toDouble()
        val fixedDur = fixedDurationMs.toDouble()
        val maxDur = max(scrollDur, fixedDur)
        val endIdx = firstAfter(placed, now.toLong() + 1L)
        val minStart = (now - maxDur - FADE_MS).toLong()
        val startIdx = firstAfter(placed, minStart - 1L).coerceAtLeast(0)

        val strokePx = with(density) { settings.strokeWidthDp.dp.toPx() }
        val strokeDrawStyle = if (strokePx > 0.05f) {
            Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        } else {
            null
        }
        val strokeColor = Color(0xE6000000).copy(alpha = (alphaBase * 0.9f).coerceIn(0f, 1f))
        val selfBoxStrokePx = with(density) { 1.5.dp.toPx() }
        val selfBoxPadX = with(density) { 5.dp.toPx() }
        val selfBoxPadY = with(density) { 1.5.dp.toPx() }
        val selfBoxRadius = with(density) { 4.dp.toPx() }
        val bodySp = with(density) { baseFontPx.toSp() }

        for (i in startIdx until endIdx) {
            val entry = placed[i]
            val dur = if (entry.type == DanmakuType.SCROLL) scrollDur else fixedDur
            val elapsed = now - entry.item.timeMs.toDouble()
            if (elapsed < 0.0 || elapsed > dur) continue

            val fade = fadeAlpha(elapsed, dur)
            if (fade <= 0.01f) continue

            val x = if (entry.type == DanmakuType.SCROLL) {
                val fraction = (elapsed / dur).toFloat()
                width - (width + entry.width) * fraction
            } else {
                ((width - entry.width) * 0.5f).coerceAtLeast(4f)
            }
            if (x + entry.width < 0f || x > width) continue
            if (entry.y < 0f || entry.y + baseFontPx > height) continue

            val text = if (entry.count > 1) "${countLabel(entry.count)}${entry.displayText}" else entry.displayText
            val rgb = entry.item.color and 0xFFFFFF
            val cacheKey = "${entry.item.id}|${entry.count}|$rgb"
            val pair = layoutCache[cacheKey] ?: run {
                val fillStyle = TextStyle(
                    color = Color(rgb or 0xFF000000.toInt()).copy(alpha = alphaBase),
                    fontSize = bodySp,
                    fontWeight = fontWeight,
                )
                val fillLayout = textMeasurer.measure(text, style = fillStyle, maxLines = 1)
                val strokeLayout = strokeDrawStyle?.let { ds ->
                    val strokeStyle = TextStyle(
                        color = strokeColor,
                        fontSize = bodySp,
                        fontWeight = fontWeight,
                        drawStyle = ds,
                    )
                    textMeasurer.measure(text, style = strokeStyle, maxLines = 1)
                }
                DanmakuTextPair(stroke = strokeLayout, fill = fillLayout).also { measured ->
                    if (layoutCache.size >= 384) {
                        val oldest = layoutCache.keys.firstOrNull()
                        if (oldest != null) layoutCache.remove(oldest)
                    }
                    layoutCache[cacheKey] = measured
                }
            }
            val topLeft = Offset(x, entry.y)
            pair.stroke?.let { strokeLayout ->
                drawText(
                    textLayoutResult = strokeLayout,
                    topLeft = topLeft,
                    alpha = fade,
                )
            }
            drawText(
                textLayoutResult = pair.fill,
                topLeft = topLeft,
                alpha = fade,
            )
            if (entry.item.isSelf) {
                drawRoundRect(
                    color = Color(0xFF2196F3).copy(alpha = (alphaBase * fade).coerceIn(0f, 1f)),
                    topLeft = Offset(x - selfBoxPadX, entry.y - selfBoxPadY),
                    size = Size(
                        pair.fill.size.width + selfBoxPadX * 2f,
                        pair.fill.size.height + selfBoxPadY * 2f,
                    ),
                    cornerRadius = CornerRadius(selfBoxRadius, selfBoxRadius),
                    style = Stroke(width = selfBoxStrokePx),
                )
            }
        }
    }
}
