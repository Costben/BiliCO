package erl.bilico.feature.dynamic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.lerp
import erl.bilico.ui.ExpandEasing
import erl.bilico.ui.ImageOrigins
import erl.bilico.ui.ThumbBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

internal const val MaxZoom = 5f
internal const val DoubleTapZoom = 2.5f
internal const val LongImageAspect = 9f / 22f
internal const val FlingMinVelocity = 350f
internal const val MinZoom = 0.5f
internal const val ReboundResistance = 0.5f
internal const val MaxDecodePixels = 12_000_000
internal const val ZoomEpsilon = 0.01f
internal const val ZoomMillis = 220
internal const val ChromeFadeStart = 0.6f
internal const val LoadFadeMillis = 200

private const val DragMinScale = 0.7f
private val DragScaleEasing = CubicBezierEasing(0f, 0f, 0.4f, 1f)

internal enum class GestureMode {
    UNDECIDED,
    ZOOM_PAN,
    DISMISS,
    PAGER,
}

/**
 * 长图的进出场映射：把图层坐标里的 [windowStart] 区域映射到屏幕坐标的 [destStart]，
 * 中途同步过渡到 [windowEnd] → [destEnd]。长图铺满屏幕后整图比屏幕高，
 * 只能拿「当前可见的那一块」当动画源，否则缩放整图会露出图片中段。
 */
internal class ViewerRectMapping(
    val windowStart: Rect,
    val windowEnd: Rect,
    val destStart: Rect,
    val destEnd: Rect,
)

/** 拖拽退出时的共享状态：支持任意方向拖动与甩出退出，取消时平滑回弹、退出时飞回原缩略图。 */
@Stable
internal class DragDismissState {
    var active by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var exiting by mutableStateOf(false)
    var dragOffset by mutableStateOf(Offset.Zero)
    var pivot by mutableStateOf(Offset.Zero)
    var boxSize by mutableStateOf(IntSize.Zero)

    val exitProgress = Animatable(0f)
    val exitTranslation = Animatable(Offset.Zero, Offset.VectorConverter)
    val exitBgAlpha = Animatable(1f)
    var exitStartScale by mutableFloatStateOf(1f)
    var exitStartTranslation by mutableStateOf(Offset.Zero)
    var exitHasThumb by mutableStateOf(false)
    var exitThumb: ThumbBounds? by mutableStateOf(null)
    var exitAspect: Float? by mutableStateOf(null)
    var exitMapping: ViewerRectMapping? by mutableStateOf(null)
    var exitFlyTarget by mutableStateOf(Offset.Zero)
    var animJob: Job? = null

    private fun refSpan(): Float =
        minOf(boxSize.width, boxSize.height).toFloat().coerceAtLeast(1f)

    fun currentScale(): Float {
        val d = dragOffset.getDistance()
        val scaleRange = refSpan() * 0.9f
        val t = DragScaleEasing.transform((d / scaleRange).coerceIn(0f, 1f))
        return lerp(1f, DragMinScale, t)
    }

    fun currentBgAlpha(): Float {
        val d = dragOffset.getDistance()
        val alphaRange = refSpan() * 0.75f
        return (1f - d / alphaRange).coerceIn(0f, 1f)
    }

    fun currentTranslation(): Offset {
        val s = currentScale()
        return dragOffset + pivot * (1f - s)
    }

    fun startDrag(focus: Offset) {
        animJob?.cancel()
        exitMapping = null
        pivot = focus - Offset(boxSize.width / 2f, boxSize.height / 2f)
        dragOffset = Offset.Zero
        active = true
        dragging = true
    }

    fun reanchor(focus: Offset) {
        val curScale = currentScale()
        val curTrans = currentTranslation()
        val newPivot = focus - Offset(boxSize.width / 2f, boxSize.height / 2f)
        pivot = newPivot
        dragOffset = curTrans - newPivot * (1f - curScale)
    }

    fun cancelActive() {
        animJob?.cancel()
        dragging = false
        active = false
        dragOffset = Offset.Zero
        exitMapping = null
    }
}

/** 看图页内跨页共享的状态：根宿主坐标、放大态、各图实测比例以及最后一次看到的缩略图位置。 */
@Stable
internal class ViewerFrame {
    var host: LayoutCoordinates? = null
    var zoomed by mutableStateOf(false)
    val dismiss = DragDismissState()
    val aspects = mutableStateMapOf<String, Float>()
    val thumbs = HashMap<String, ThumbBounds>()
}

internal fun handleDismissRelease(
    velocity: Velocity,
    dismiss: DragDismissState,
    frame: ViewerFrame,
    origins: ImageOrigins,
    url: String?,
    density: Float,
    scope: CoroutineScope,
    imageSize: ComposeSize? = null,
    imageTopLeft: Offset? = null,
    onCloseImmediate: () -> Unit,
) {
    if (dismiss.exiting) return
    dismiss.dragging = false
    val dragPos = dismiss.dragOffset
    val dist = dragPos.getDistance()
    val vel = Offset(velocity.x, velocity.y)
    val speed = vel.getDistance()

    val flingThreshold = 200f * density
    val distanceThreshold = 80f * density
    val backGuard = 36f * density
    val backFlingThreshold = 320f * density
    val dot = dragPos.x * vel.x + dragPos.y * vel.y
    val isFlingBack = dist > backGuard && speed >= backFlingThreshold && dot < -0.6f * dist * speed
    val shouldDismiss = !isFlingBack && (speed >= flingThreshold || dist >= distanceThreshold)

    if (!shouldDismiss) {
        dismiss.animJob?.cancel()
        dismiss.animJob = scope.launch {
            animate(
                typeConverter = Offset.VectorConverter,
                initialValue = dismiss.dragOffset,
                targetValue = Offset.Zero,
                initialVelocity = vel,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 500f,
                    visibilityThreshold = Offset(0.5f, 0.5f),
                ),
            ) { value, _ ->
                dismiss.dragOffset = value
            }
            dismiss.dragOffset = Offset.Zero
            dismiss.active = false
        }
        return
    }

    dismiss.animJob?.cancel()
    dismiss.exiting = true
    val startScale = dismiss.currentScale()
    val startTranslation = dismiss.currentTranslation()
    val startBgAlpha = dismiss.currentBgAlpha()

    val host = frame.host
    val live = if (url != null && host != null && !frame.zoomed) {
        origins.boundsOf(url, host, density)?.also { frame.thumbs[url] = it }
    } else {
        null
    }
    val thumb = live ?: url?.takeIf { !frame.zoomed }?.let { frame.thumbs[it] }
    val aspect = url?.let { frame.aspects[it] ?: thumb?.aspect }

    val w = dismiss.boxSize.width.toFloat()
    val h = dismiss.boxSize.height.toFloat()
    val effectiveAspect = aspect ?: thumb?.rect?.let { if (it.height > 0f) it.width / it.height else null }
    val restFrame = if (w > 0f && h > 0f && effectiveAspect != null) restSize(w, h, effectiveAspect) else null
    val longImage = restFrame != null && restFrame.height > h + 0.5f
    // 长图整图比屏幕高，靠「当前可见区域 → 缩略图裁切区域」的矩形映射回缩；
    // 拿不到图片实际落点时才退回淡出。
    val hasThumb = thumb != null && restFrame != null &&
        (!longImage || (imageSize != null && imageTopLeft != null))

    dismiss.exitStartScale = startScale
    dismiss.exitStartTranslation = startTranslation

    dismiss.animJob = scope.launch {
        dismiss.exitBgAlpha.snapTo(startBgAlpha)
        dismiss.exitProgress.snapTo(0f)

        if (hasThumb) {
            val source = thumb.rect
            val targetTranslation = Offset(source.center.x - w / 2f, source.center.y - h / 2f)
            val maxFlingSpeed = 1200f * density
            val clampedVel = if (speed > maxFlingSpeed && speed > 0f) {
                vel * (maxFlingSpeed / speed)
            } else {
                vel
            }
            dismiss.exitThumb = thumb
            dismiss.exitAspect = effectiveAspect
            dismiss.exitHasThumb = true
            dismiss.exitMapping = if (longImage && imageSize != null && imageTopLeft != null) {
                longImageMapping(
                    boxSize = dismiss.boxSize,
                    imageSize = imageSize,
                    imageTopLeft = imageTopLeft,
                    imageAspect = effectiveAspect ?: (imageSize.width / imageSize.height),
                    thumbRect = source,
                    startScale = startScale,
                    startTranslation = startTranslation,
                )
            } else {
                null
            }
            dismiss.exitTranslation.snapTo(startTranslation)

            coroutineScope {
                launch {
                    dismiss.exitBgAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                }
                launch {
                    dismiss.exitProgress.animateTo(1f, tween(320, easing = ExpandEasing))
                }
                launch {
                    dismiss.exitTranslation.animateTo(
                        targetValue = targetTranslation,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 700f,
                            visibilityThreshold = Offset(0.5f, 0.5f),
                        ),
                        initialVelocity = clampedVel,
                    )
                }
            }
        } else {
            origins.viewing = null
            val dir = when {
                speed >= flingThreshold && speed > 0f -> vel / speed
                dist > 1f -> dragPos / dist
                else -> Offset(0f, 1f)
            }
            val flyDistance = minOf(w, h).coerceAtLeast(1f) * 0.42f
            dismiss.exitHasThumb = false
            dismiss.exitMapping = null
            dismiss.exitFlyTarget = startTranslation + dir * flyDistance

            coroutineScope {
                launch {
                    dismiss.exitBgAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                }
                launch {
                    dismiss.exitProgress.animateTo(
                        1f,
                        tween(240, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                    )
                }
            }
        }
        onCloseImmediate()
    }
}

internal fun GraphicsLayerScope.applyDismissDrag(d: DragDismissState) {
    val scale = d.currentScale()
    val trans = d.currentTranslation()
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = scale
    scaleY = scale
    translationX = trans.x
    translationY = trans.y
    shape = RectangleShape
    clip = false
}

internal fun GraphicsLayerScope.applyDismissExit(d: DragDismissState) {
    val p = d.exitProgress.value
    val mapping = d.exitMapping
    if (mapping != null && size.width > 0f && size.height > 0f) {
        applyViewerMapping(
            mapping = mapping,
            progress = p,
            radius = lerp(0f, d.exitThumb?.cornerRadius ?: 0f, p),
        )
        return
    }
    if (!d.exitHasThumb || size.width <= 0f || size.height <= 0f) {
        val curScale = lerp(d.exitStartScale, d.exitStartScale * 0.78f, p)
        alpha = (1f - p).coerceIn(0f, 1f)
        transformOrigin = TransformOrigin.Center
        scaleX = curScale
        scaleY = curScale
        translationX = lerp(d.exitStartTranslation.x, d.exitFlyTarget.x, p)
        translationY = lerp(d.exitStartTranslation.y, d.exitFlyTarget.y, p)
        shape = RectangleShape
        clip = false
        return
    }
    val thumb = d.exitThumb!!
    val source = thumb.rect
    val rest = (d.exitAspect ?: (source.width / source.height)).let {
        restSize(size.width, size.height, it)
    }
    val cover = max(source.width / rest.width, source.height / rest.height)
    val curScale = lerp(d.exitStartScale, cover, p)
    val trans = d.exitTranslation.value
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = curScale
    scaleY = curScale
    translationX = trans.x
    translationY = trans.y
    shape = CenteredWindow(
        width = lerp(size.width * d.exitStartScale, source.width, p) / curScale,
        height = lerp(size.height * d.exitStartScale, source.height, p) / curScale,
        radius = lerp(0f, thumb.cornerRadius, p) / curScale,
    )
    clip = true
}

/**
 * 长图进出场的矩形映射：把图层坐标里的窗口矩形按进度插值，并整体缩放平移到屏幕目标矩形。
 * 缩略图用 `ContentScale.Crop` 居中裁切，所以终点窗口取图像的中心裁切块，落地时和缩略图对得上。
 */
internal fun longImageMapping(
    boxSize: IntSize,
    imageSize: ComposeSize,
    imageTopLeft: Offset,
    imageAspect: Float,
    thumbRect: Rect,
    startScale: Float,
    startTranslation: Offset,
): ViewerRectMapping {
    val w = boxSize.width.toFloat().coerceAtLeast(1f)
    val h = boxSize.height.toFloat().coerceAtLeast(1f)
    val targetAspect = (thumbRect.width / thumbRect.height.coerceAtLeast(1f))
        .takeIf { it > 0f } ?: imageAspect
    val crop = if (imageAspect < targetAspect) {
        val cropHeight = imageSize.width / targetAspect
        Rect(0f, (imageSize.height - cropHeight) / 2f, imageSize.width, (imageSize.height + cropHeight) / 2f)
    } else {
        val cropWidth = imageSize.height * targetAspect
        Rect((imageSize.width - cropWidth) / 2f, 0f, (imageSize.width + cropWidth) / 2f, imageSize.height)
    }
    val windowEnd = Rect(
        imageTopLeft.x + crop.left,
        imageTopLeft.y + crop.top,
        imageTopLeft.x + crop.right,
        imageTopLeft.y + crop.bottom,
    )
    val halfW = w * startScale / 2f
    val halfH = h * startScale / 2f
    val center = Offset(w / 2f + startTranslation.x, h / 2f + startTranslation.y)
    return ViewerRectMapping(
        windowStart = Rect(0f, 0f, w, h),
        windowEnd = windowEnd,
        destStart = Rect(center.x - halfW, center.y - halfH, center.x + halfW, center.y + halfH),
        destEnd = thumbRect,
    )
}

private fun GraphicsLayerScope.applyViewerMapping(
    mapping: ViewerRectMapping,
    progress: Float,
    radius: Float,
) {
    val window = lerpRect(mapping.windowStart, mapping.windowEnd, progress)
    val dest = lerpRect(mapping.destStart, mapping.destEnd, progress)
    val scaleXNow = dest.width / window.width.coerceAtLeast(0.001f)
    val scaleYNow = dest.height / window.height.coerceAtLeast(0.001f)
    alpha = 1f
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = scaleXNow
    scaleY = scaleYNow
    translationX = dest.left - scaleXNow * window.left
    translationY = dest.top - scaleYNow * window.top
    val scaleAvg = ((scaleXNow + scaleYNow) / 2f).coerceAtLeast(0.001f)
    shape = RectWindow(
        left = window.left,
        top = window.top,
        width = window.width,
        height = window.height,
        radius = radius / scaleAvg,
    )
    clip = true
}

private fun lerpRect(from: Rect, to: Rect, t: Float): Rect = Rect(
    lerp(from.left, to.left, t),
    lerp(from.top, to.top, t),
    lerp(from.right, to.right, t),
    lerp(from.bottom, to.bottom, t),
)

internal fun GraphicsLayerScope.settle() {
    alpha = 1f
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    shape = RectangleShape
    clip = false
}

/** 把画面按缩略图框的中心裁切插值到整屏（或相反）；长图走矩形映射，其余淡入微缩。 */
internal fun GraphicsLayerScope.openFrame(
    progress: Float,
    thumb: ThumbBounds?,
    aspect: Float?,
    imageSize: ComposeSize? = null,
    imageTopLeft: Offset? = null,
) {
    if (size.width <= 0f || size.height <= 0f) {
        settle()
        return
    }
    val source = thumb?.rect
    val rest = (aspect ?: source?.let { it.width / it.height })
        ?.let { restSize(size.width, size.height, it) }
    val longImage = rest != null && rest.height > size.height + 0.5f
    if (longImage && source != null && imageSize != null && imageTopLeft != null) {
        val exit = longImageMapping(
            boxSize = IntSize(size.width.toInt(), size.height.toInt()),
            imageSize = imageSize,
            imageTopLeft = imageTopLeft,
            imageAspect = aspect ?: (imageSize.width / imageSize.height),
            thumbRect = source,
            startScale = 1f,
            startTranslation = Offset.Zero,
        )
        // 进场方向与退场相反：缩略图的裁切块 → 当前可见的整屏。
        applyViewerMapping(
            mapping = ViewerRectMapping(
                windowStart = exit.windowEnd,
                windowEnd = exit.windowStart,
                destStart = exit.destEnd,
                destEnd = exit.destStart,
            ),
            progress = progress,
            radius = lerp(thumb.cornerRadius, 0f, progress),
        )
        return
    }
    if (source == null || rest == null || rest.height > size.height + 0.5f) {
        settle()
        alpha = progress
        scaleX = lerp(0.92f, 1f, progress)
        scaleY = scaleX
        return
    }
    val width = size.width
    val height = size.height
    val cover = max(source.width / rest.width, source.height / rest.height)
    val scale = lerp(cover, 1f, progress)
    alpha = 1f
    transformOrigin = TransformOrigin.Center
    scaleX = scale
    scaleY = scale
    translationX = lerp(source.center.x, width / 2f, progress) - width / 2f
    translationY = lerp(source.center.y, height / 2f, progress) - height / 2f
    shape = CenteredWindow(
        width = lerp(source.width, width, progress) / scale,
        height = lerp(source.height, height, progress) / scale,
        radius = lerp(thumb.cornerRadius, 0f, progress) / scale,
    )
    clip = true
}

private class CenteredWindow(
    private val width: Float,
    private val height: Float,
    private val radius: Float,
) : Shape {
    override fun createOutline(
        size: ComposeSize,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        return Outline.Rounded(RoundRect(left, top, left + width, top + height, CornerRadius(radius)))
    }
}

/** 图层坐标里的任意矩形窗口；长图映射需要把窗口跟着内容一起平移，不能用居中版本。 */
private class RectWindow(
    private val left: Float,
    private val top: Float,
    private val width: Float,
    private val height: Float,
    private val radius: Float,
) : Shape {
    override fun createOutline(
        size: ComposeSize,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(
        RoundRect(left, top, left + width, top + height, CornerRadius(radius)),
    )
}

private fun fitSize(boxWidth: Float, boxHeight: Float, aspect: Float): ComposeSize =
    if (aspect > boxWidth / boxHeight) {
        ComposeSize(boxWidth, boxWidth / aspect)
    } else {
        ComposeSize(boxHeight * aspect, boxHeight)
    }

internal fun restSize(boxWidth: Float, boxHeight: Float, aspect: Float): ComposeSize =
    if (aspect < LongImageAspect) ComposeSize(boxWidth, boxWidth / aspect) else fitSize(boxWidth, boxHeight, aspect)

/** 只消费自己认领的手势，横向翻页等未接管的触摸继续留给上层 HorizontalPager。 */
internal suspend fun PointerInputScope.detectZoomAndPan(
    onStart: () -> Unit = {},
    onRelease: (velocity: Velocity, taken: Boolean) -> Unit = { _, _ -> },
    onGesture: (focus: Offset, pan: Offset, direction: Offset, zoom: Float, pointers: Int) -> Boolean,
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        var taken = false
        val tracker = VelocityTracker()
        val touchSlop = viewConfiguration.touchSlop
        val down = awaitFirstDown(requireUnconsumed = false)
        var trackedId = down.id
        tracker.addPointerInputChange(down)
        onStart()
        do {
            val event = awaitPointerEvent()
            val tracked = event.changes.firstOrNull { it.id == trackedId } ?: event.changes.firstOrNull()
            if (tracked != null) {
                if (tracked.id != trackedId) {
                    tracker.resetTracking()
                    trackedId = tracked.id
                }
                tracker.addPointerInputChange(tracked)
            }
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                var direction = panChange
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val zoomMotion = abs(1f - zoom) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > touchSlop || pan.getDistance() > touchSlop) {
                        pastTouchSlop = true
                        direction = pan
                    }
                }
                if (pastTouchSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                    val pointers = event.changes.count { it.pressed }
                    val focus = event.calculateCentroid(useCurrent = false)
                    if (onGesture(focus, panChange, direction, zoomChange, pointers)) {
                        taken = true
                        event.changes.fastForEach { change: PointerInputChange ->
                            if (change.positionChanged()) change.consume()
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
        onRelease(tracker.calculateVelocity(), taken)
    }
}
