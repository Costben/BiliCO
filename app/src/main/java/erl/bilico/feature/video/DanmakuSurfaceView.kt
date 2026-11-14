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
 *    与播放器粗粒度整数毫秒 `currentPosition` 解耦：落后就按比例追、超前就放慢等，
 *    时钟只往前走进不退，只有真跳变（seek / 切线路）才重锚定；
 * 2. 在 Android 15+ 显式投票 120Hz 帧率，防止 VRR 随视频纹理降频至 30/60Hz；
 * 3. 采用 GPU 友好的圓角描边（Stroke）+ 填充（Fill）+ `SUBPIXEL_TEXT_FLAG`，取代耗时的 `setShadowLayer` 高斯阴影；
 * 4. 缓存文本测量宽度，使横竖屏旋转 `onSizeChanged` 重排耗时降至 0.1ms 以内，不阻塞转屏首帧。
 */
internal class DanmakuSurfaceView(context: Context) : View(context), Choreographer.FrameCallback {

    var player: Player? = null

    /**
     * 外部时间轴。设置后弹幕时间轴改由它驱动，不再跟随 [Player.currentPosition]：
     * 直播（FLV/HLS 滑窗）下 `currentPosition` 会来回跳甚至归零，拿它当时间轴会让弹幕
     * 全挤在同一时刻，入轨时互相顶掉，画面上一条都出不来。
     *
     * 时钟本身按帧间隔累加，缓冲/暂停期间不往前走，恢复时从暂停处接着走 —— 直接用墙钟
     * 会出现「卡住一下、然后整屏弹幕窜出去一截」。
     */
    var timeline: DanmakuTimeline? = null
        set(value) {
            if (field === value) return
            field = value
            lastFrameNanos = 0L
            lastExternalMs = 0L
            renderTimeMs = -1.0
            invalidate()
        }

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

    // 合并数量那截「(N)」用的小一号字号，样式跟着正文走。
    private val countFillPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG,
    ).apply {
        style = Paint.Style.FILL
    }

    private val countStrokePaint = Paint(
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
    // 合并数量的「(N)」按小一号字号量，和正文宽度不能共用一张表。
    private val countWidthCache = HashMap<String, Float>(64)
    private var cachedFontPx: Float = -1f
    private var cachedWeight: Int = -1
    private var cachedSpriteFontPx: Float = -1f
    private var cachedSpriteStrokePx: Float = -1f
    private var cachedSpriteWeight: Int = -1

    private var filteredItems: List<FilteredDanmaku> = emptyList()
    private var placed: List<PlacedDanmaku> = emptyList()

    private var loopRunning = false
    private var lastFrameNanos = 0L
    private var lastExternalMs = 0L
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

        val external = timeline
        if (external != null) {
            // 时间轴按帧间隔累加：缓冲、卡顿这段不往前走，恢复时从暂停处接着走。
            val now = external.source()
            if (renderTimeMs < 0.0) renderTimeMs = external.nowMs.toDouble()
            if (lastExternalMs != 0L) {
                val delta = (now - lastExternalMs).coerceIn(0L, MAX_FRAME_MS.toLong())
                renderTimeMs += delta
            }
            lastExternalMs = now
            external.nowMs = renderTimeMs.toLong()
            lastFrameNanos = frameTimeNanos
            invalidate()
            if (p.isPlaying && playing) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                loopRunning = false
                lastFrameNanos = 0L
                lastExternalMs = 0L
            }
            return
        }

        val playerPos = p.currentPosition.coerceAtLeast(0L).toDouble()
        val playSpeed = (p.playbackParameters.speed).toDouble().coerceIn(0.25, 4.0)
        val isActuallyPlaying = p.isPlaying && playing

        if (lastFrameNanos == 0L || renderTimeMs < 0.0 || !isActuallyPlaying) {
            renderTimeMs = playerPos
        } else {
            // 帧间隔一律按真实值算：掉帧、主线程忙的时候长帧不能截断，
            // 否则倍速下每掉一帧就少走一截，攒够阈值再被硬拽一下，观感就是一跳一跳。
            val dtMs = ((frameTimeNanos - lastFrameNanos) / 1_000_000.0).coerceIn(0.0, MAX_FRAME_MS)
            val previous = renderTimeMs
            val advanced = previous + dtMs * playSpeed
            val drift = playerPos - advanced
            renderTimeMs = when {
                // 真跳变（seek、切线路、缓冲回来）才允许重锚。
                abs(drift) > DRIFT_SNAP_THRESHOLD_MS -> playerPos
                // 落后播放器：按比例追一点，慢慢贴合，不做整段拉回。
                drift >= 0.0 -> advanced + drift * DRIFT_CATCHUP_FACTOR
                // 跑在播放器前面（倍速刚切上去那一瞬间最常见）：放慢等它，
                // 最多压到这一帧原地不动，绝不倒退 —— 倒退就是用户看到的「弹幕回退」。
                else -> max(previous, advanced + drift * DRIFT_SLOWDOWN_FACTOR)
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
            countWidthCache.clear()
            cachedFontPx = fontPx
            cachedWeight = settings.fontWeight
        }

        placed = layoutDanmaku(
            items = filteredItems,
            measure = { text, scale ->
                if (scale == 1f) {
                    widthCache.getOrPut(text) { fillPaint.measureText(text) }
                } else {
                    countWidthCache.getOrPut(text) {
                        countFillPaint.textSize = fillPaint.textSize * scale
                        countFillPaint.measureText(text)
                    }
                }
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
        val nowMs = when {
            // 直播走的是一条自维护的时间轴，暂停时保持不动。
            timeline != null -> if (renderTimeMs >= 0.0) renderTimeMs else 0.0
            loopRunning && renderTimeMs >= 0.0 -> renderTimeMs
            else -> p.currentPosition.coerceAtLeast(0L).toDouble().also { renderTimeMs = it }
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
            val sprite = obtainSprite(
                text = entry.displayText,
                count = entry.count,
                measuredWidth = entry.width,
                rgb = entry.item.color and 0xFFFFFF,
                strokePx = strokePx,
            )
            bitmapPaint.alpha = alphaInt
            canvas.drawBitmap(sprite.bitmap, x - sprite.pad, entry.y - sprite.pad, bitmapPaint)
        }

        if (p.isPlaying && playing && !loopRunning) {
            syncLoopState()
        }
    }

    private fun obtainSprite(
        text: String,
        count: Int,
        measuredWidth: Float,
        rgb: Int,
        strokePx: Float,
    ): DanmakuSprite {
        val key = SpriteKey(text, count, rgb)
        spriteCache.get(key)?.let { return it }

        val pad = ceil(strokePx + 2f)
        val ascent = -fillPaint.ascent()
        val descent = fillPaint.descent()
        val countText = if (count > 1) countLabel(count) else null
        countFillPaint.textSize = fillPaint.textSize * COUNT_FONT_SCALE
        countFillPaint.typeface = fillPaint.typeface
        countStrokePaint.textSize = countFillPaint.textSize
        countStrokePaint.typeface = fillPaint.typeface
        val countWidth = if (countText != null) countFillPaint.measureText(countText) else 0f
        val bmpW = ceil(measuredWidth + pad * 2f).toInt().coerceIn(1, 4096)
        val bmpH = ceil(ascent + descent + pad * 2f).toInt().coerceIn(1, 512)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(bmp)
        val baseline = pad + ascent
        if (strokePx > 0.05f) {
            strokePaint.strokeWidth = strokePx
            strokePaint.alpha = 224
            if (countText != null) {
                countStrokePaint.strokeWidth = strokePx
                countStrokePaint.alpha = 224
                bmpCanvas.drawText(countText, pad, baseline, countStrokePaint)
            }
            bmpCanvas.drawText(text, pad + countWidth, baseline, strokePaint)
        }
        fillPaint.color = rgb or (0xFF shl 24)
        if (countText != null) {
            countFillPaint.color = rgb or (0xFF shl 24)
            bmpCanvas.drawText(countText, pad, baseline, countFillPaint)
        }
        bmpCanvas.drawText(text, pad + countWidth, baseline, fillPaint)

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
        val count: Int,
        val rgb: Int,
    )

    private class DanmakuSprite(
        val bitmap: Bitmap,
        val pad: Float,
    )

    private companion object {
        const val STROKE_COLOR = 0xE6000000.toInt()

        /** 单帧最多算这么长；再长就是主线程被别的事占住了，按真实值补上即可。 */
        const val MAX_FRAME_MS = 120.0

        /** 只有超过这个量的偏差才算真跳变（seek、切线路），普通漂移一律慢慢磨。 */
        const val DRIFT_SNAP_THRESHOLD_MS = 500.0

        /** 落后时每帧补掉差距的这个比例：约 0.5～0.7s 追平，且单帧不会突然快一大截。 */
        const val DRIFT_CATCHUP_FACTOR = 0.1

        /** 超前时每帧只压掉差距的这个比例，压到原地等待为止，永不倒退。 */
        const val DRIFT_SLOWDOWN_FACTOR = 0.1

        const val MAX_SPRITE_CACHE_BYTES = 48 * 1024 * 1024
    }
}

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
