package erl.bilico.feature.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import erl.bilico.data.model.EmoteImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

internal sealed interface DanmakuSegment {
    data class TextSeg(val text: String) : DanmakuSegment
    data class EmoteSeg(val token: String, val url: String, val large: Boolean) : DanmakuSegment
}

private val EmoteTokenRegex = Regex("\\[[^\\]\\n\\r]{1,24}\\]")

internal fun parseDanmakuSegments(
    text: String,
    emotes: Map<String, EmoteImage>,
): List<DanmakuSegment> {
    if (emotes.isEmpty() || text.isEmpty()) return listOf(DanmakuSegment.TextSeg(text))
    val trimmed = text.trim()
    val standalone = emotes[trimmed]
    if (standalone != null && emotes.size == 1 && standalone.large) {
        return listOf(DanmakuSegment.EmoteSeg(trimmed, standalone.url, large = true))
    }
    val out = ArrayList<DanmakuSegment>(4)
    var cursor = 0
    for (match in EmoteTokenRegex.findAll(text)) {
        val emote = emotes[match.value] ?: continue
        if (match.range.first > cursor) {
            out += DanmakuSegment.TextSeg(text.substring(cursor, match.range.first))
        }
        out += DanmakuSegment.EmoteSeg(match.value, emote.url, emote.large)
        cursor = match.range.last + 1
    }
    if (out.isEmpty()) return listOf(DanmakuSegment.TextSeg(text))
    if (cursor < text.length) {
        out += DanmakuSegment.TextSeg(text.substring(cursor))
    }
    return out
}

internal fun emoteIconSizePx(fontPx: Float, large: Boolean): Float =
    if (large) fontPx * 1.55f else fontPx * 1.25f

internal fun emoteAdvancePx(fontPx: Float, large: Boolean): Float =
    emoteIconSizePx(fontPx, large) + fontPx * 0.14f

internal fun maxEmoteHeightPx(segments: List<DanmakuSegment>, fontPx: Float): Float {
    var best = 0f
    for (seg in segments) {
        if (seg is DanmakuSegment.EmoteSeg) {
            best = max(best, emoteIconSizePx(fontPx, seg.large))
        }
    }
    return best
}

internal fun drawDanmakuSegments(
    canvas: Canvas,
    segments: List<DanmakuSegment>,
    startX: Float,
    baseline: Float,
    centerY: Float,
    fontPx: Float,
    strokePx: Float,
    fillPaint: Paint,
    strokePaint: Paint,
    emoteLoader: DanmakuEmoteLoader,
    emotePaint: Paint,
    emoteRect: RectF,
): Boolean {
    var cursorX = startX
    var allLoaded = true
    for (seg in segments) {
        when (seg) {
            is DanmakuSegment.TextSeg -> {
                if (strokePx > 0.05f) canvas.drawText(seg.text, cursorX, baseline, strokePaint)
                canvas.drawText(seg.text, cursorX, baseline, fillPaint)
                cursorX += fillPaint.measureText(seg.text)
            }
            is DanmakuSegment.EmoteSeg -> {
                val iconSize = emoteIconSizePx(fontPx, seg.large)
                val advance = emoteAdvancePx(fontPx, seg.large)
                val bmp = emoteLoader.getOrLoad(seg.url, iconSize.toInt().coerceAtLeast(1))
                if (bmp != null) {
                    val left = cursorX + (advance - iconSize) * 0.5f
                    val top = centerY - iconSize * 0.5f
                    emoteRect.set(left, top, left + iconSize, top + iconSize)
                    canvas.drawBitmap(bmp, null, emoteRect, emotePaint)
                } else {
                    allLoaded = false
                    if (strokePx > 0.05f) canvas.drawText(seg.token, cursorX, baseline, strokePaint)
                    canvas.drawText(seg.token, cursorX, baseline, fillPaint)
                }
                cursorX += advance
            }
        }
    }
    return allLoaded
}

internal fun textPaint(paintStyle: Paint.Style, strokeColor: Int? = null): Paint = Paint(
    Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG,
).apply {
    style = paintStyle
    if (strokeColor != null) {
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = strokeColor
    }
}

internal data class SpriteKey(
    val text: String,
    val count: Int,
    val rgb: Int,
    val isSelf: Boolean = false,
    val emoteSig: String = "",
)

internal class DanmakuSprite(
    val bitmap: Bitmap,
    val padX: Float,
    val padY: Float,
)

internal fun buildDanmakuSprite(
    text: String,
    count: Int,
    measuredWidth: Float,
    rgb: Int,
    strokePx: Float,
    isSelf: Boolean,
    emotes: Map<String, EmoteImage>,
    density: Float,
    fillPaint: Paint,
    strokePaint: Paint,
    countFillPaint: Paint,
    countStrokePaint: Paint,
    selfBoxPaint: Paint,
    emoteLoader: DanmakuEmoteLoader,
    emotePaint: Paint,
    emoteRect: RectF,
): Pair<DanmakuSprite, Boolean> {
    val strokePad = ceil(strokePx + 2f)
    val boxPadX = if (isSelf) 6f * density else 0f
    val boxPadY = if (isSelf) 2f * density else 0f
    val padX = strokePad + boxPadX
    val basePadY = strokePad + boxPadY
    val ascent = -fillPaint.ascent()
    val descent = fillPaint.descent()
    val lineH = ascent + descent
    val segments = parseDanmakuSegments(text, emotes)
    val contentH = max(lineH, maxEmoteHeightPx(segments, fillPaint.textSize))
    val padY = basePadY + (contentH - lineH) * 0.5f
    val countText = if (count > 1) countLabel(count) else null
    countFillPaint.textSize = fillPaint.textSize * COUNT_FONT_SCALE
    countFillPaint.typeface = fillPaint.typeface
    countStrokePaint.textSize = countFillPaint.textSize
    countStrokePaint.typeface = fillPaint.typeface
    val countWidth = if (countText != null) countFillPaint.measureText(countText) else 0f
    val bmpW = ceil(measuredWidth + padX * 2f).toInt().coerceIn(1, 4096)
    val bmpH = ceil(contentH + basePadY * 2f).toInt().coerceIn(1, 512)
    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    val bmpCanvas = Canvas(bmp)
    val baseline = padY + ascent
    val centerY = padY + lineH * 0.5f
    if (strokePx > 0.05f) {
        strokePaint.strokeWidth = strokePx
        strokePaint.alpha = 224
        if (countText != null) {
            countStrokePaint.strokeWidth = strokePx
            countStrokePaint.alpha = 224
            bmpCanvas.drawText(countText, padX, baseline, countStrokePaint)
        }
    }
    fillPaint.color = rgb or (0xFF shl 24)
    if (countText != null) {
        countFillPaint.color = rgb or (0xFF shl 24)
        bmpCanvas.drawText(countText, padX, baseline, countFillPaint)
    }
    val allLoaded = drawDanmakuSegments(
        canvas = bmpCanvas,
        segments = segments,
        startX = padX + countWidth,
        baseline = baseline,
        centerY = centerY,
        fontPx = fillPaint.textSize,
        strokePx = strokePx,
        fillPaint = fillPaint,
        strokePaint = strokePaint,
        emoteLoader = emoteLoader,
        emotePaint = emotePaint,
        emoteRect = emoteRect,
    )
    if (isSelf) {
        val boxStroke = 1.5f * density
        val halfStroke = boxStroke * 0.5f
        val radius = 4f * density
        selfBoxPaint.strokeWidth = boxStroke
        val boxTop = max(halfStroke, basePadY - 1f * density)
        val boxBottom = minOf(bmpH - halfStroke, basePadY + contentH + 1f * density)
        bmpCanvas.drawRoundRect(
            halfStroke,
            boxTop,
            bmpW - halfStroke,
            boxBottom,
            radius,
            radius,
            selfBoxPaint,
        )
    }
    return DanmakuSprite(bmp, padX, padY) to allLoaded
}

/**
 * 异步加载直播间弹幕里的表情图：不阻塞 120Hz VSync 绘制线程，解码成软件 Bitmap 后触发重绘。
 */
internal class DanmakuEmoteLoader(
    private val context: Context,
    private val onLoaded: () -> Unit,
) {
    private val cache = object : LruCache<String, Bitmap>(MAX_EMOTES) {}
    private val loading = HashSet<String>()
    private val failed = HashSet<String>()
    private var scope: CoroutineScope? = null

    fun attach(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    fun detach() {
        scope = null
        loading.clear()
    }

    fun getOrLoad(url: String, sizePx: Int): Bitmap? {
        if (url.isBlank()) return null
        val key = "$sizePx:$url"
        cache.get(key)?.let { return it }
        if (key in loading || key in failed) return null
        val activeScope = scope ?: return null
        loading.add(key)
        activeScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val req = ImageRequest.Builder(context)
                        .data(url)
                        .size(sizePx, sizePx)
                        .allowHardware(false)
                        .build()
                    val res = SingletonImageLoader.get(context).execute(req)
                    ((res as? SuccessResult)?.image as? BitmapImage)?.bitmap
                }.getOrNull()
            }
            loading.remove(key)
            if (bmp != null) {
                cache.put(key, bmp)
                onLoaded()
            } else {
                failed.add(key)
            }
        }
        return null
    }

    private companion object {
        const val MAX_EMOTES = 128
    }
}
