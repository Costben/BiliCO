package erl.bilico.feature.video

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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
 * iOS Skia/Compose 弹幕渲染层：
 * - 先以不透明描边与填充测量，半透明绘制时用 `saveLayer` 整体施加透明度，避免描边透出字芯；
 * - 合并计数 `(N)` 按 [COUNT_FONT_SCALE] 缩放与布局宽度对齐，支持点选定格高亮与 120Hz ProMotion 帧率。
 */
@Composable
internal actual fun DanmakuOverlay(
    player: BiliPlayer?,
    items: List<DanmakuItem>,
    isPlaying: Boolean,
    settings: DanmakuSettings,
    modifier: Modifier,
    timeline: DanmakuTimeline?,
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
    var currentTimeMs by remember {
        mutableDoubleStateOf((timeline?.nowMs ?: player?.currentPosition ?: 0L).toDouble())
    }

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
    val bodySp = with(density) { baseFontPx.toSp() }
    val countSp = with(density) { (baseFontPx * COUNT_FONT_SCALE).toSp() }

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

    val placed: List<PlacedDanmaku> = remember(
        items,
        settings.allowScroll,
        settings.allowTop,
        settings.allowBottom,
        settings.allowColorful,
        settings.allowSpecial,
        settings.mergeDuplicates,
        settings.area,
        settings.lineHeight,
        settings.speed,
        settings.massiveMode,
        settings.fontWeight,
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

    val layoutCache = remember(baseFontPx, fontWeight, settings.strokeWidth) {
        LinkedHashMap<String, DanmakuTextPair>(256)
    }
    val layerPaint = remember { Paint() }

    DisposableEffect(interaction, placed, widthPx, scrollDurationMs, baseFontPx, settings.lineHeightRatio) {
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
            while (true) {
                withFrameNanos { nanos ->
                    val wall = timeline.source()
                    if (active && lastWall > 0L && wall > lastWall) {
                        val delta = (wall - lastWall).coerceAtMost(120L)
                        timeline.nowMs += delta
                    }
                    lastWall = wall
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
            var renderMs = (player?.currentPosition ?: 0L).toDouble()
            var lastFrameNanos = 0L
            var hadVisible = false
            while (true) {
                withFrameNanos { nanos ->
                    val playerPos = (player?.currentPosition ?: 0L).toDouble()
                    val speed = (player?.speed ?: 1f).coerceIn(0.25f, 4f).toDouble()
                    if (lastFrameNanos == 0L) {
                        renderMs = playerPos
                    } else {
                        val dtMs = ((nanos - lastFrameNanos) / 1_000_000.0).coerceIn(0.0, 120.0)
                        val previous = renderMs
                        val advanced = previous + dtMs * speed
                        val drift = playerPos - advanced
                        renderMs = when {
                            abs(drift) > 500.0 -> playerPos
                            drift >= 0.0 -> advanced + drift * 0.1
                            else -> max(previous, advanced + drift * 0.1)
                        }
                    }
                    lastFrameNanos = nanos
                    val endIdx = firstAfter(placed, renderMs.toLong() + 1L)
                    val minStart = (renderMs - maxWindowMs - FADE_MS).toLong()
                    val hasVisible = endIdx > 0 && placed[endIdx - 1].item.timeMs >= minStart
                    if (hasVisible || hadVisible) {
                        hadVisible = hasVisible
                        currentTimeMs = renderMs
                        frameTick = nanos
                    }
                }
            }
        }
    }

    val pinnedHit = interaction?.selectedHit

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
            Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        } else {
            null
        }
        val selfBoxStrokePx = with(density) { 1.5.dp.toPx() }
        val selfBoxPadX = with(density) { 5.dp.toPx() }
        val selfBoxPadY = with(density) { 1.5.dp.toPx() }
        val selfBoxRadius = with(density) { 4.dp.toPx() }

        fun obtainPair(displayText: String, count: Int, rgb: Int): DanmakuTextPair {
            val cacheKey = "$displayText|$count|$rgb"
            return layoutCache[cacheKey] ?: run {
                val annotated = buildDanmakuAnnotated(displayText, count, countSp)
                val fillStyle = TextStyle(
                    color = Color(rgb or 0xFF000000.toInt()),
                    fontSize = bodySp,
                    fontWeight = fontWeight,
                )
                val fillLayout = textMeasurer.measure(annotated, style = fillStyle, maxLines = 1)
                val strokeLayout = strokeDrawStyle?.let { ds ->
                    val strokeStyle = TextStyle(
                        color = Color(0xE0000000),
                        fontSize = bodySp,
                        fontWeight = fontWeight,
                        drawStyle = ds,
                    )
                    textMeasurer.measure(annotated, style = strokeStyle, maxLines = 1)
                }
                DanmakuTextPair(stroke = strokeLayout, fill = fillLayout).also { measured ->
                    if (layoutCache.size >= 384) {
                        val oldest = layoutCache.keys.firstOrNull()
                        if (oldest != null) layoutCache.remove(oldest)
                    }
                    layoutCache[cacheKey] = measured
                }
            }
        }

        for (i in startIdx until endIdx) {
            val entry = placed[i]
            if (pinnedHit != null && entry.item.id == pinnedHit.item.id) continue
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

            val pair = obtainPair(entry.displayText, entry.count, entry.item.color and 0xFFFFFF)
            val combinedAlpha = (alphaBase * fade).coerceIn(0f, 1f)
            drawDanmakuTextPair(
                pair = pair,
                topLeft = Offset(x, entry.y),
                strokePad = strokePx + 2f,
                alpha = combinedAlpha,
                layerPaint = layerPaint,
            )
            if (entry.item.isSelf) {
                drawRoundRect(
                    color = Color(0xFF2196F3).copy(alpha = combinedAlpha),
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

        if (pinnedHit != null) {
            val padH = with(density) { 6.dp.toPx() }
            val padV = with(density) { 2.dp.toPx() }
            val radius = with(density) { 6.dp.toPx() }
            val topLeft = Offset(pinnedHit.boundsPx.left - padH, pinnedHit.boundsPx.top - padV)
            val pillSize = Size(
                pinnedHit.boundsPx.width + padH * 2f,
                pinnedHit.boundsPx.height + padV * 2f,
            )
            drawRoundRect(
                color = Color(0x552196F3),
                topLeft = topLeft,
                size = pillSize,
                cornerRadius = CornerRadius(radius, radius),
            )
            drawRoundRect(
                color = Color(0xCCFFFFFF),
                topLeft = topLeft,
                size = pillSize,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = with(density) { 1.2.dp.toPx() }),
            )
            val pair = obtainPair(pinnedHit.displayText, pinnedHit.count, pinnedHit.item.color and 0xFFFFFF)
            drawDanmakuTextPair(
                pair = pair,
                topLeft = Offset(pinnedHit.boundsPx.left, pinnedHit.boundsPx.top),
                strokePad = strokePx + 2f,
                alpha = alphaBase,
                layerPaint = layerPaint,
            )
        }
    }
}

private fun buildDanmakuAnnotated(
    displayText: String,
    count: Int,
    countSp: TextUnit,
): AnnotatedString = if (count <= 1) {
    AnnotatedString(displayText)
} else {
    buildAnnotatedString {
        withStyle(SpanStyle(fontSize = countSp)) {
            append(countLabel(count))
        }
        append(displayText)
    }
}

private fun DrawScope.drawDanmakuTextPair(
    pair: DanmakuTextPair,
    topLeft: Offset,
    strokePad: Float,
    alpha: Float,
    layerPaint: Paint,
) {
    if (alpha >= 0.99f) {
        pair.stroke?.let { drawText(textLayoutResult = it, topLeft = topLeft) }
        drawText(textLayoutResult = pair.fill, topLeft = topLeft)
        return
    }
    val bounds = Rect(
        left = topLeft.x - strokePad,
        top = topLeft.y - strokePad,
        right = topLeft.x + pair.fill.size.width + strokePad,
        bottom = topLeft.y + pair.fill.size.height + strokePad,
    )
    layerPaint.alpha = alpha
    drawContext.canvas.saveLayer(bounds, layerPaint)
    pair.stroke?.let { drawText(textLayoutResult = it, topLeft = topLeft) }
    drawText(textLayoutResult = pair.fill, topLeft = topLeft)
    drawContext.canvas.restore()
}
