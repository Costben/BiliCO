package erl.bilico.feature.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import android.view.Choreographer
import android.view.View
import androidx.media3.common.Player
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.settings.DanmakuSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.abs
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
            liveBuffer.reset()
            if (value != null) rebuildFilteredAndLayout()
            invalidate()
        }

    var items: List<DanmakuItem> = emptyList()
        set(value) {
            if (field === value) return
            val previous = field
            field = value
            if (timeline != null) {
                onLiveItemsChanged(previous, value)
            } else {
                rebuildFilteredAndLayout()
            }
        }

    private var settings: DanmakuSettings = DanmakuSettings()

    var playing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            updateFrameRateVote()
            syncLoopState()
        }

    var selectedHit: DanmakuHit? = null
        set(value) {
            if (field === value) return
            field = value
            invalidate()
        }

    private val fillPaint = textPaint(Paint.Style.FILL)
    private val strokePaint = textPaint(Paint.Style.STROKE, STROKE_COLOR)
    // 合并数量那截「(N)」用的小一号字号，样式跟着正文走。
    private val countFillPaint = textPaint(Paint.Style.FILL)
    private val countStrokePaint = textPaint(Paint.Style.STROKE, STROKE_COLOR)

    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG,
    )
    private val emotePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val emoteRect = RectF()
    private val emoteLoader = DanmakuEmoteLoader(context) { invalidate() }
    private var viewScope: CoroutineScope? = null

    private val selfBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = SELF_BOX_COLOR
    }

    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x552196F3
    }

    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xCCFFFFFF.toInt()
    }

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
    private val liveBuffer = LiveDanmakuBuffer()

    private var loopRunning = false
    private var lastFrameNanos = 0L
    private var lastExternalMs = 0L
    private var renderTimeMs = -1.0
    private val highlightRect = RectF()

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
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            viewScope = it
            emoteLoader.attach(it)
        }
        updateFrameRateVote()
        syncLoopState()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        relayout()
    }

    private fun rebuildFilteredAndLayout() {
        if (timeline != null) {
            liveBuffer.reset()
            relayout()
            return
        }
        filteredItems = filterAndMergeDanmaku(items, settings)
        relayout()
    }

    private fun onLiveItemsChanged(previous: List<DanmakuItem>, next: List<DanmakuItem>) {
        if (next.isEmpty()) {
            liveBuffer.reset()
            placed = emptyList()
            syncLoopState()
            invalidate()
            return
        }
        val density = resources.displayMetrics.density
        val fontPx = syncMeasureFontPx()
        val nowMs = if (renderTimeMs >= 0.0) renderTimeMs else timeline?.nowMs?.toDouble() ?: 0.0
        placed = liveBuffer.updateIncremental(
            previous = previous,
            next = next,
            settings = settings,
            nowMs = nowMs,
            measure = ::measureDanmakuText,
            widthPx = width.toFloat(),
            heightPx = height.toFloat(),
            fontPx = fontPx,
            bottomInsetPx = BOTTOM_INSET_DP * density,
        )
        syncLoopState()
        invalidate()
    }

    private fun syncMeasureFontPx(): Float {
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
        return fontPx
    }

    private fun measureDanmakuText(text: String, scale: Float): Float =
        if (scale == 1f) {
            widthCache.getOrPut(text) { fillPaint.measureText(text) }
        } else {
            countWidthCache.getOrPut(text) {
                countFillPaint.textSize = fillPaint.textSize * scale
                countFillPaint.measureText(text)
            }
        }

    private fun relayout() {
        val liveMode = timeline != null
        val emptySource = if (liveMode) items.isEmpty() else filteredItems.isEmpty()
        if (width <= 0 || height <= 0 || emptySource) {
            if (liveMode) liveBuffer.reset()
            placed = emptyList()
            syncLoopState()
            invalidate()
            return
        }
        val density = resources.displayMetrics.density
        val fontPx = syncMeasureFontPx()
        placed = if (liveMode) {
            liveBuffer.rebuild(
                items = items,
                settings = settings,
                measure = ::measureDanmakuText,
                widthPx = width.toFloat(),
                heightPx = height.toFloat(),
                fontPx = fontPx,
                bottomInsetPx = BOTTOM_INSET_DP * density,
            )
        } else {
            layoutDanmaku(
                items = filteredItems,
                measure = ::measureDanmakuText,
                widthPx = width.toFloat(),
                heightPx = height.toFloat(),
                fontPx = fontPx,
                areaFraction = settings.areaFraction,
                lineHeightRatio = settings.lineHeightRatio,
                scrollDurationMs = settings.scrollDurationMs,
                massiveMode = settings.massiveMode,
                bottomInsetPx = BOTTOM_INSET_DP * density,
            )
        }
        syncLoopState()
        invalidate()
    }

    fun findDanmakuAt(xPx: Float, yPx: Float): DanmakuHit? {
        if (!settings.enabled || placed.isEmpty() || width <= 0 || height <= 0) return null
        val density = resources.displayMetrics.density
        syncMeasureFontPx()
        val textHeightPx = (-fillPaint.ascent() + fillPaint.descent()).coerceAtLeast(1f)
        return findVisibleDanmakuAt(
            placed = placed,
            nowMs = resolveDisplayTimeMs(),
            screenWidth = width.toFloat(),
            scrollDurationMs = settings.scrollDurationMs,
            textHeightPx = textHeightPx,
            xPx = xPx,
            yPx = yPx,
            slopXPx = 8f * density,
            slopYPx = 8f * density,
        )
    }

    private fun resolveDisplayTimeMs(): Double = when {
        timeline != null -> if (renderTimeMs >= 0.0) renderTimeMs else timeline?.nowMs?.toDouble() ?: 0.0
        loopRunning && renderTimeMs >= 0.0 -> renderTimeMs
        else -> (player?.currentPosition?.coerceAtLeast(0L)?.toDouble() ?: 0.0).also { renderTimeMs = it }
    }

    override fun onDraw(canvas: Canvas) {
        if (!settings.enabled || placed.isEmpty()) return
        val p = player ?: return
        val nowMs = resolveDisplayTimeMs()
        val pinned = selectedHit

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
            if (pinned != null && entry.item.id == pinned.item.id) continue
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
                isSelf = entry.item.isSelf,
                emotes = entry.item.emotes,
            )
            bitmapPaint.alpha = alphaInt
            canvas.drawBitmap(sprite.bitmap, x - sprite.padX, entry.y - sprite.padY, bitmapPaint)
        }

        // 只定格并高亮被选中的那一条，其余弹幕照常流动。
        if (pinned != null) {
            val padH = 6f * density
            val padV = 2f * density
            val r = 6f * density
            selectedStrokePaint.strokeWidth = 1.2f * density
            highlightRect.set(
                pinned.boundsPx.left - padH,
                pinned.boundsPx.top - padV,
                pinned.boundsPx.right + padH,
                pinned.boundsPx.bottom + padV,
            )
            canvas.drawRoundRect(highlightRect, r, r, selectedFillPaint)
            canvas.drawRoundRect(highlightRect, r, r, selectedStrokePaint)
            val sprite = obtainSprite(
                text = pinned.displayText,
                count = pinned.count,
                measuredWidth = pinned.boundsPx.width(),
                rgb = pinned.item.color and 0xFFFFFF,
                strokePx = strokePx,
                isSelf = pinned.item.isSelf,
                emotes = pinned.item.emotes,
            )
            bitmapPaint.alpha = (baseOpacity * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(
                sprite.bitmap,
                pinned.boundsPx.left - sprite.padX,
                pinned.boundsPx.top - sprite.padY,
                bitmapPaint,
            )
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
        isSelf: Boolean,
        emotes: Map<String, EmoteImage>,
    ): DanmakuSprite {
        val emoteSig = if (emotes.isEmpty()) "" else emotes.entries.joinToString("|") { "${it.key}=${it.value.url}" }
        val key = SpriteKey(text, count, rgb, isSelf, emoteSig)
        spriteCache.get(key)?.let { return it }
        val (sprite, allLoaded) = buildDanmakuSprite(
            text = text,
            count = count,
            measuredWidth = measuredWidth,
            rgb = rgb,
            strokePx = strokePx,
            isSelf = isSelf,
            emotes = emotes,
            density = resources.displayMetrics.density,
            fillPaint = fillPaint,
            strokePaint = strokePaint,
            countFillPaint = countFillPaint,
            countStrokePaint = countStrokePaint,
            selfBoxPaint = selfBoxPaint,
            emoteLoader = emoteLoader,
            emotePaint = emotePaint,
            emoteRect = emoteRect,
        )
        if (allLoaded) spriteCache.put(key, sprite)
        return sprite
    }

    override fun onDetachedFromWindow() {
        loopRunning = false
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(this)
        emoteLoader.detach()
        viewScope?.cancel()
        viewScope = null
        spriteCache.evictAll()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val STROKE_COLOR = 0xE6000000.toInt()
        const val SELF_BOX_COLOR = 0xFF2196F3.toInt()

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
