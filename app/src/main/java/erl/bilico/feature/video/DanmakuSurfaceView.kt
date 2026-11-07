package erl.bilico.feature.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import android.view.Choreographer
import android.view.View
import androidx.media3.common.Player
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import erl.bilico.data.settings.DanmakuSettings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * 120Hz 原生高刷弹幕渲染视图：
 * 1. 使用 [Choreographer.FrameCallback] 的纳秒级硬件 VSync 时间戳驱动连续浮点时钟 [renderTimeMs]，
 *    与播放器粗粒度整数毫秒 `currentPosition` 解耦（仅做 2% 微小漂移锁定，超 120ms 才重锚定），
 *    彻底消除弹幕被 24/30/60fps 视频帧率或整数毫秒截断拖慢的阶梯感；
 * 2. 在 Android 15+ 显式投票 120Hz 帧率，防止 VRR 随视频纹理降频至 30/60Hz；
 * 3. 采用 GPU 友好的圓角描边（Stroke）+ 填充（Fill）+ `SUBPIXEL_TEXT_FLAG`，取代耗时的 `setShadowLayer` 高斯阴影；
 * 4. 缓存文本测量宽度，使横竖屏旋转 `onSizeChanged` 重排耗时降至 0.1ms 以内，不阻塞转屏首帧。
 */
internal class DanmakuSurfaceView(context: Context) : View(context), Choreographer.FrameCallback {

    var player: Player? = null

    var items: List<DanmakuItem> = emptyList()
        set(value) {
            if (field === value) return
            field = value
            rebuildFilteredAndLayout()
        }

    private var settings: DanmakuSettings = DanmakuSettings()

    var playing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateFrameRateVote()
            syncLoopState()
        }

    private val fillPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG,
    ).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG,
    ).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = STROKE_COLOR
    }

    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG,
    )

    private val spriteCache = object : LruCache<SpriteKey, DanmakuSprite>(MAX_SPRITE_CACHE_BYTES) {
        override fun sizeOf(key: SpriteKey, value: DanmakuSprite): Int = value.bitmap.allocationByteCount
    }

    private val widthCache = HashMap<String, Float>(2048)
    private var cachedFontPx: Float = -1f
    private var cachedWeight: Int = -1
    private var cachedSpriteFontPx: Float = -1f
    private var cachedSpriteStrokePx: Float = -1f
    private var cachedSpriteWeight: Int = -1

    private var filteredItems: List<FilteredDanmaku> = emptyList()
    private var placed: List<PlacedDanmaku> = emptyList()

    private var loopRunning = false
    private var lastFrameNanos = 0L
    private var renderTimeMs = -1.0

    init {
        setWillNotDraw(false)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        updateTypeface(settings.fontWeight)
        updateFrameRateVote()
    }

    fun applySettings(newSettings: DanmakuSettings) {
        val old = settings
        if (old == newSettings) return
        settings = newSettings

        val filterChanged = old.allowScroll != newSettings.allowScroll ||
            old.allowTop != newSettings.allowTop ||
            old.allowBottom != newSettings.allowBottom ||
            old.allowColorful != newSettings.allowColorful ||
            old.allowSpecial != newSettings.allowSpecial ||
            old.mergeDuplicates != newSettings.mergeDuplicates

        val layoutChanged = filterChanged ||
            old.fontScale != newSettings.fontScale ||
            old.area != newSettings.area ||
            old.speed != newSettings.speed ||
            old.massiveMode != newSettings.massiveMode ||
            old.lineHeight != newSettings.lineHeight ||
            old.fontWeight != newSettings.fontWeight

        if (old.fontWeight != newSettings.fontWeight) {
            updateTypeface(newSettings.fontWeight)
        }

        if (filterChanged) {
            rebuildFilteredAndLayout()
        } else if (layoutChanged) {
            relayout()
        } else {
            invalidate()
        }

        if (old.enabled != newSettings.enabled) {
            updateFrameRateVote()
            syncLoopState()
            invalidate()
        }
    }

    private fun updateTypeface(weightStep: Int) {
        val tf = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, (weightStep.coerceIn(1, 9) * 100), false)
        } else {
            if (weightStep >= 6) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        fillPaint.typeface = tf
        strokePaint.typeface = tf
    }

    private fun updateFrameRateVote() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            requestedFrameRate = if (playing && settings.enabled) {
                120f
            } else {
                REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
            }
        }
    }

    private fun syncLoopState() {
        val shouldRun = isAttachedToWindow && playing && settings.enabled && placed.isNotEmpty()
        if (shouldRun && !loopRunning) {
            loopRunning = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        } else if (!shouldRun && loopRunning) {
            loopRunning = false
            lastFrameNanos = 0L
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!loopRunning) return
        val p = player
        if (!isAttachedToWindow || !settings.enabled || placed.isEmpty() || p == null) {
            loopRunning = false
            lastFrameNanos = 0L
            return
        }

        val playerPos = p.currentPosition.coerceAtLeast(0L).toDouble()
        val playSpeed = (p.playbackParameters.speed).toDouble().coerceIn(0.25, 4.0)
        val isActuallyPlaying = p.isPlaying && playing

        if (lastFrameNanos == 0L || renderTimeMs < 0.0 || !isActuallyPlaying) {
            renderTimeMs = playerPos
        } else {
            val dtMs = ((frameTimeNanos - lastFrameNanos) / 1_000_000.0).coerceIn(0.0, 50.0)
            renderTimeMs += dtMs * playSpeed
            val drift = playerPos - renderTimeMs
            if (abs(drift) > DRIFT_SNAP_THRESHOLD_MS) {
                renderTimeMs = playerPos
            } else {
                renderTimeMs += drift * DRIFT_CORRECTION_FACTOR
            }
        }
        lastFrameNanos = frameTimeNanos
        invalidate()

        if (isActuallyPlaying) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            loopRunning = false
            lastFrameNanos = 0L
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateFrameRateVote()
        syncLoopState()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        relayout()
    }

    private fun rebuildFilteredAndLayout() {
        filteredItems = filterAndMergeDanmaku(items, settings)
        relayout()
    }

    private fun relayout() {
        if (width <= 0 || height <= 0 || filteredItems.isEmpty()) {
            placed = emptyList()
            syncLoopState()
            invalidate()
            return
        }
        val density = resources.displayMetrics.density
        val fontPx = BASE_FONT_DP * density * (settings.fontScale / 100f)
        fillPaint.textSize = fontPx
        strokePaint.textSize = fontPx

        if (abs(cachedFontPx - fontPx) > 0.05f || cachedWeight != settings.fontWeight) {
            widthCache.clear()
            cachedFontPx = fontPx
            cachedWeight = settings.fontWeight
        }

        placed = layoutDanmaku(
            items = filteredItems,
            measure = { text ->
                widthCache.getOrPut(text) { fillPaint.measureText(text) }
            },
            widthPx = width.toFloat(),
            heightPx = height.toFloat(),
            fontPx = fontPx,
            areaFraction = settings.areaFraction,
            lineHeightRatio = settings.lineHeightRatio,
            scrollDurationMs = settings.scrollDurationMs,
            massiveMode = settings.massiveMode,
            bottomInsetPx = BOTTOM_INSET_DP * density,
        )
        syncLoopState()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!settings.enabled || placed.isEmpty()) return
        val p = player ?: return
        val nowMs = if (loopRunning && renderTimeMs >= 0.0) {
            renderTimeMs
        } else {
            p.currentPosition.coerceAtLeast(0L).toDouble().also { renderTimeMs = it }
        }

        val density = resources.displayMetrics.density
        val fontPx = BASE_FONT_DP * density * (settings.fontScale / 100f)
        val strokePx = settings.strokeWidthDp * density
        if (
            abs(cachedSpriteFontPx - fontPx) > 0.05f ||
            abs(cachedSpriteStrokePx - strokePx) > 0.05f ||
            cachedSpriteWeight != settings.fontWeight
        ) {
            spriteCache.evictAll()
            cachedSpriteFontPx = fontPx
            cachedSpriteStrokePx = strokePx
            cachedSpriteWeight = settings.fontWeight
        }
        val baseOpacity = (settings.opacity / 100f).coerceIn(0.1f, 1f)
        val screenWidth = width.toFloat()
        val scrollDuration = settings.scrollDurationMs.toDouble()
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
            val fade = fadeAlpha(elapsed, duration)
            if (fade <= 0.01f) continue

            val progress = (elapsed / duration).toFloat()
            val x = if (entry.type == DanmakuType.SCROLL) {
                screenWidth - progress * (screenWidth + entry.width)
            } else {
                (screenWidth - entry.width) * 0.5f
            }
            if (x + entry.width < 0f || x > screenWidth) continue

            val alphaInt = (fade * baseOpacity * 255f).toInt().coerceIn(0, 255)
            val sprite = obtainSprite(entry.displayText, entry.width, entry.item.color and 0xFFFFFF, strokePx)
            bitmapPaint.alpha = alphaInt
            canvas.drawBitmap(sprite.bitmap, x - sprite.pad, entry.y - sprite.pad, bitmapPaint)
        }

        if (p.isPlaying && playing && !loopRunning) {
            syncLoopState()
        }
    }

    private fun obtainSprite(
        text: String,
        measuredWidth: Float,
        rgb: Int,
        strokePx: Float,
    ): DanmakuSprite {
        val key = SpriteKey(text, rgb)
        spriteCache.get(key)?.let { return it }

        val pad = ceil(strokePx + 2f)
        val ascent = -fillPaint.ascent()
        val descent = fillPaint.descent()
        val bmpW = ceil(measuredWidth + pad * 2f).toInt().coerceIn(1, 4096)
        val bmpH = ceil(ascent + descent + pad * 2f).toInt().coerceIn(1, 512)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(bmp)
        val baseline = pad + ascent
        if (strokePx > 0.05f) {
            strokePaint.strokeWidth = strokePx
            strokePaint.alpha = 224
            bmpCanvas.drawText(text, pad, baseline, strokePaint)
        }
        fillPaint.color = rgb or (0xFF shl 24)
        bmpCanvas.drawText(text, pad, baseline, fillPaint)

        return DanmakuSprite(bmp, pad).also { spriteCache.put(key, it) }
    }

    override fun onDetachedFromWindow() {
        loopRunning = false
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(this)
        spriteCache.evictAll()
        super.onDetachedFromWindow()
    }

    private data class SpriteKey(
        val text: String,
        val rgb: Int,
    )

    private class DanmakuSprite(
        val bitmap: Bitmap,
        val pad: Float,
    )

    private companion object {
        const val STROKE_COLOR = 0xE6000000.toInt()
        const val DRIFT_SNAP_THRESHOLD_MS = 120.0
        const val DRIFT_CORRECTION_FACTOR = 0.02
        const val MAX_SPRITE_CACHE_BYTES = 16 * 1024 * 1024
    }
}

internal data class FilteredDanmaku(
    val item: DanmakuItem,
    val displayText: String,
)

internal data class PlacedDanmaku(
    val item: DanmakuItem,
    val displayText: String,
    val type: DanmakuType,
    val y: Float,
    val width: Float,
    val lane: Int,
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
            result[i] = FilteredDanmaku(orig.item, "${orig.item.content} ×$c")
        }
    }
    return result
}

internal fun layoutDanmaku(
    items: List<FilteredDanmaku>,
    measure: (String) -> Float,
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
    val topFree = LongArray(topLanes) { Long.MIN_VALUE }
    val bottomFree = LongArray(bottomLanes) { Long.MIN_VALUE }
    val bottomBase = (heightPx - bottomInsetPx).coerceAtLeast(lineHeight)

    val result = ArrayList<PlacedDanmaku>(items.size)
    for (entry in items) {
        val item = entry.item
        val width = measure(entry.displayText)
        when (item.type) {
            DanmakuType.SCROLL -> {
                var lane = -1
                var earliestLane = 0
                var earliestTime = Long.MAX_VALUE
                for (index in scrollFree.indices) {
                    val freeAt = scrollFree[index]
                    if (freeAt <= item.timeMs) {
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
                result += PlacedDanmaku(item, entry.displayText, item.type, lane * lineHeight, width, lane)
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
                result += PlacedDanmaku(item, entry.displayText, item.type, lane * lineHeight, width, lane)
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
                result += PlacedDanmaku(item, entry.displayText, item.type, y, width, lane)
            }
        }
    }
    return result
}

private fun firstAfter(layout: List<PlacedDanmaku>, time: Long): Int {
    var low = 0
    var high = layout.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (layout[mid].item.timeMs <= time) low = mid + 1 else high = mid
    }
    return low
}

private fun fadeAlpha(elapsed: Double, duration: Double): Float {
    if (elapsed < FADE_MS) return (elapsed / FADE_MS).toFloat()
    val remain = duration - elapsed
    if (remain < FADE_MS) return (remain / FADE_MS).toFloat()
    return 1f
}

internal const val LINE_HEIGHT_RATIO = 1.35f
internal const val BASE_FONT_DP = 15f
private const val SCROLL_DURATION_MS = 8_000L
private const val FIXED_DURATION_MS = 4_000L
private const val FADE_MS = 240.0
private const val MERGE_WINDOW_MS = 5_000
private const val BOTTOM_INSET_DP = 52f
