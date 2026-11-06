package erl.bilico.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * ColorOS 17 分段胶囊的连续曲率轮廓（对应 COUISegmentButtonLayout）。
 *
 * 轮廓从端点起走 39.2 度圆弧，再平滑过渡到直边，直边在距端点 1.5 个半径处结束。
 * 三次贝塞尔在接缝处以圆弧的曲率相接、到直边时曲率为零，没有曲率突变。
 */
private const val TAIL_LENGTH = 1.5f
private const val ARC_HANDLE_1_Y = 0.7696933f
private const val ARC_HANDLE_2_X = 0.07949496f
private const val ARC_HANDLE_2_Y = 0.5464456f
private const val ARC_END_X = 0.2250555f
private const val ARC_END_Y = 0.3679707f
private const val EASE_HANDLE_1_X = 0.5251651f
private const val EASE_HANDLE_2_X = 0.9615864f

/** 把一段连续曲率胶囊追加到 [rect]，半径取矩形高度的一半。 */
private fun Path.addSegmentCapsule(rect: Rect) {
    val radius = rect.height / 2f
    if (radius <= 0f || rect.width <= 0f) return
    val tail = min(TAIL_LENGTH, rect.width / (2f * radius))
    val tailPx = tail * radius
    moveTo(rect.left + tailPx, rect.top)
    lineTo(rect.right - tailPx, rect.top)
    addCapsuleCornerToTip(rect.right, rect.top, -1f, 1f, radius, tail)
    addCapsuleCornerFromTip(rect.right, rect.bottom, -1f, -1f, radius, tail)
    lineTo(rect.left + tailPx, rect.bottom)
    addCapsuleCornerToTip(rect.left, rect.bottom, 1f, -1f, radius, tail)
    addCapsuleCornerFromTip(rect.left, rect.top, 1f, 1f, radius, tail)
    close()
}

private fun Path.addCapsuleCornerFromTip(
    cornerX: Float,
    cornerY: Float,
    signX: Float,
    signY: Float,
    radius: Float,
    tail: Float,
) {
    val scaleX = signX * radius
    val scaleY = signY * radius
    cubicTo(
        cornerX,
        cornerY + scaleY * ARC_HANDLE_1_Y,
        cornerX + scaleX * ARC_HANDLE_2_X,
        cornerY + scaleY * ARC_HANDLE_2_Y,
        cornerX + scaleX * ARC_END_X,
        cornerY + scaleY * ARC_END_Y,
    )
    cubicTo(
        cornerX + scaleX * EASE_HANDLE_1_X,
        cornerY,
        cornerX + scaleX * min(EASE_HANDLE_2_X, tail),
        cornerY,
        cornerX + scaleX * tail,
        cornerY,
    )
}

private fun Path.addCapsuleCornerToTip(
    cornerX: Float,
    cornerY: Float,
    signX: Float,
    signY: Float,
    radius: Float,
    tail: Float,
) {
    val scaleX = signX * radius
    val scaleY = signY * radius
    cubicTo(
        cornerX + scaleX * min(EASE_HANDLE_2_X, tail),
        cornerY,
        cornerX + scaleX * EASE_HANDLE_1_X,
        cornerY,
        cornerX + scaleX * ARC_END_X,
        cornerY + scaleY * ARC_END_Y,
    )
    cubicTo(
        cornerX + scaleX * ARC_HANDLE_2_X,
        cornerY + scaleY * ARC_HANDLE_2_Y,
        cornerX,
        cornerY + scaleY * ARC_HANDLE_1_Y,
        cornerX,
        cornerY + scaleY,
    )
}

object CouiSegmentCapsuleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply { addSegmentCapsule(Rect(Offset.Zero, size)) }
        return Outline.Generic(path)
    }
}

private const val SPRING_VALUE_THRESHOLD = 0.75f
private const val SPRING_VELOCITY_THRESHOLD = SPRING_VALUE_THRESHOLD * 62.5
private const val SPRING_UNBOUNDED = 2.14748365E9f

/** `COUISpringAnimation` 的逐帧实现：值有上下界，速度继续积分，停在边界上等力反向。 */
private class CouiSpring(
    initialValue: Float,
    bounce: Float,
    response: Float,
) {
    var value: Float = initialValue
        private set
    var velocity: Float = 0f
    var isRunning: Boolean = false
        private set
    var minValue: Float = -SPRING_UNBOUNDED
    var maxValue: Float = SPRING_UNBOUNDED

    private var target: Float = initialValue
    private var pendingTarget: Float = Float.NaN
    private var dampingRatio: Double = 0.0
    private var naturalFrequency: Double = 0.0

    init {
        configure(bounce, response)
    }

    fun configure(bounce: Float, response: Float) {
        dampingRatio = (1f - bounce).toDouble()
        naturalFrequency = 2.0 * PI / response
    }

    fun setStart(start: Float) {
        value = start
    }

    fun animateTo(finalPosition: Float) {
        if (isRunning) {
            pendingTarget = finalPosition
        } else {
            target = finalPosition
            isRunning = true
        }
    }

    fun snapTo(newValue: Float) {
        isRunning = false
        pendingTarget = Float.NaN
        value = newValue
        target = newValue
        velocity = 0f
    }

    fun step(deltaSeconds: Double): Boolean {
        if (!isRunning) return false
        var position = value.toDouble()
        var speed = velocity.toDouble()
        if (!pendingTarget.isNaN()) {
            val half = deltaSeconds / 2.0
            integrate(position, speed, half) { p, v ->
                position = p
                speed = v
            }
            target = pendingTarget
            pendingTarget = Float.NaN
            integrate(position, speed, half) { p, v ->
                position = p
                speed = v
            }
        } else {
            integrate(position, speed, deltaSeconds) { p, v ->
                position = p
                speed = v
            }
        }
        value = min(max(position.toFloat(), minValue), maxValue)
        velocity = speed.toFloat()
        val atRest = abs(velocity.toDouble()) < SPRING_VELOCITY_THRESHOLD &&
            abs((value - target).toDouble()) < SPRING_VALUE_THRESHOLD
        if (atRest) {
            value = target
            velocity = 0f
            isRunning = false
        }
        return atRest
    }

    private inline fun integrate(
        position: Double,
        speed: Double,
        seconds: Double,
        result: (Double, Double) -> Unit,
    ) {
        val displacement = position - target
        val zeta = dampingRatio
        val omega = naturalFrequency
        if (zeta == 1.0) {
            val a = speed + omega * displacement
            val b = displacement + a * seconds
            val decay = exp(-omega * seconds)
            result(b * decay + target, a * decay - omega * b * decay)
        } else {
            val damped = omega * sqrt(1.0 - zeta * zeta)
            val a = (zeta * omega * displacement + speed) / damped
            val decay = exp(-zeta * omega * seconds)
            val oscillation = decay * (cos(damped * seconds) * displacement + sin(damped * seconds) * a)
            val nextSpeed = -omega * oscillation * zeta +
                decay * (-damped * displacement * sin(damped * seconds) + a * damped * cos(damped * seconds))
            result(oscillation + target, nextSpeed)
        }
    }
}

/** 指示块最矮压到自身高度的这个比例。 */
private const val SEGMENT_SQUASH_RATIO = 0.9f
private const val SEGMENT_WIDTH_KICK = 1000f
private const val SEGMENT_HEIGHT_KICK = -500f

/** 指示块的滑动与挤压/拉伸：换选中项时位置弹簧带过去，宽度向外踢、高度向内压。 */
@Stable
private class CouiSegmentIndicatorMotion(
    center: Float,
    width: Float,
    height: Float,
) {
    var centerX: Float by mutableFloatStateOf(center)
        private set
    var width: Float by mutableFloatStateOf(width)
        private set
    var height: Float by mutableFloatStateOf(height)
        private set
    var active by mutableStateOf(false)
        private set

    private val position = CouiSpring(center, bounce = 0.1f, response = 0.4f)
    private val widthSpring = CouiSpring(width, bounce = 0.5f, response = 0.6f)
    private val heightSpring = CouiSpring(height, bounce = 0.5f, response = 0.6f)

    fun select(
        targetCenter: Float,
        targetWidth: Float,
        restHeight: Float,
        firstCenter: Float,
        lastCenter: Float,
        maxExtraWidth: Float,
    ) {
        position.configure(0.1f, 0.4f)
        position.minValue = min(firstCenter, lastCenter)
        position.maxValue = max(firstCenter, lastCenter)
        position.setStart(centerX.coerceIn(position.minValue, position.maxValue))
        position.animateTo(targetCenter.coerceIn(position.minValue, position.maxValue))

        val grows = targetWidth > width
        widthSpring.maxValue = targetWidth + maxExtraWidth
        widthSpring.configure(if (grows) 0.5f else 0.3f, 0.45f)
        widthSpring.setStart(width)
        widthSpring.velocity = SEGMENT_WIDTH_KICK
        widthSpring.animateTo(targetWidth)

        val squashed = restHeight * SEGMENT_SQUASH_RATIO
        heightSpring.minValue = squashed
        heightSpring.configure(0.5f, 0.45f)
        heightSpring.setStart(max(squashed, height))
        heightSpring.velocity = SEGMENT_HEIGHT_KICK
        heightSpring.animateTo(restHeight)

        active = true
    }

    suspend fun animate() {
        snapshotFlow { active }.collect { running ->
            if (!running) return@collect
            var lastFrame = -1L
            while (active) {
                withFrameNanos { frame ->
                    if (lastFrame >= 0L) {
                        val delta = (frame - lastFrame) / 1_000_000_000.0
                        advance(min(delta, 0.05))
                    }
                    lastFrame = frame
                }
            }
        }
    }

    private fun advance(deltaSeconds: Double) {
        if (position.isRunning) {
            position.step(deltaSeconds)
            centerX = position.value
        }
        if (widthSpring.isRunning) {
            widthSpring.step(deltaSeconds)
            width = widthSpring.value
        }
        if (heightSpring.isRunning) {
            heightSpring.step(deltaSeconds)
            height = heightSpring.value
        }
        active = position.isRunning || widthSpring.isRunning || heightSpring.isRunning
    }

    val isRunning: Boolean
        get() = position.isRunning || widthSpring.isRunning || heightSpring.isRunning

    /** 手指拖动时直接贴合：弹簧在跑的时候不接管，免得和换项动画抢位置。 */
    fun updateDrag(center: Float, dynamicWidth: Float, dynamicHeight: Float) {
        if (isRunning) return
        centerX = center
        width = dynamicWidth
        height = dynamicHeight
    }
}

private val SegmentPressSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

/**
 * 顶栏里的分段胶囊：不透明底 + 半透明指示块。
 * [labels] 均分宽度，选中项由 [selectedIndex] 控制，点击回调 [onSelect]。
 *
 * 传了 [pager] 时指示块跟着翻页走：拖动中每帧贴住手指，松手后由弹簧把位置补齐。
 *
 * 传了 [onReselect] 时，双击当前选中项会回调它（用于回顶刷新）；选中项因此不再响应单击。
 */
@Composable
fun SegmentCapsule(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    segmentWidth: Dp = 84.dp,
    pager: PagerState? = null,
    onReselect: (() -> Unit)? = null,
) {
    if (labels.isEmpty()) return
    val scheme = COUITheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val containerColor = if (dark) Color(0xFF353535) else Color(0xFFE2E3E4)
    val indicatorColor = if (dark) Color(0x33FFFFFF) else Color(0xB2FFFFFF)

    val density = LocalDensity.current
    val segmentWidthPx = with(density) { segmentWidth.toPx() }
    val restHeightPx = with(density) { height.toPx() }
    val padPx = with(density) { 4.dp.toPx() }

    fun centerFor(index: Int): Float = padPx + index * segmentWidthPx + segmentWidthPx / 2f
    val firstCenter = centerFor(0)
    val lastCenter = centerFor(labels.lastIndex)

    val initial = selectedIndex.coerceIn(0, labels.lastIndex)
    val motion = remember {
        CouiSegmentIndicatorMotion(
            center = centerFor(initial),
            width = segmentWidthPx,
            height = restHeightPx,
        )
    }
    LaunchedEffect(motion) { motion.animate() }

    var settled by remember { mutableIntStateOf(initial) }
    val selectTarget: (Int) -> Unit = { target ->
        if (target != settled) {
            settled = target
            motion.select(
                targetCenter = centerFor(target),
                targetWidth = segmentWidthPx,
                restHeight = restHeightPx,
                firstCenter = firstCenter,
                lastCenter = lastCenter,
                maxExtraWidth = with(density) { 16.dp.toPx() },
            )
        }
    }
    if (pager != null) {
        LaunchedEffect(pager.currentPage, segmentWidthPx) {
            selectTarget(pager.currentPage.coerceIn(0, labels.lastIndex))
        }
        // 拖动与惯性阶段每帧贴住翻页进度，而不是等停下来再追。
        LaunchedEffect(pager.currentPageOffsetFraction, pager.isScrollInProgress) {
            if (pager.isScrollInProgress && !motion.isRunning) {
                val pageFloat = (pager.currentPage + pager.currentPageOffsetFraction)
                    .coerceIn(0f, labels.lastIndex.toFloat())
                val stretch = sin(abs(pager.currentPageOffsetFraction) * PI.toFloat())
                    .coerceIn(0f, 1f)
                motion.updateDrag(
                    center = padPx + pageFloat * segmentWidthPx + segmentWidthPx / 2f,
                    dynamicWidth = segmentWidthPx + with(density) { 14.dp.toPx() } * stretch,
                    dynamicHeight = restHeightPx * (1f - 0.1f * stretch),
                )
            }
        }
    } else {
        LaunchedEffect(selectedIndex, segmentWidthPx) {
            selectTarget(selectedIndex.coerceIn(0, labels.lastIndex))
        }
    }

    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsStateCompat()
    val indicatorScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = SegmentPressSpring,
        label = "segmentIndicatorScale",
    )

    val effectivePage = ((motion.centerX - padPx - segmentWidthPx / 2f) / segmentWidthPx)
        .coerceIn(0f, labels.lastIndex.toFloat())

    BoxWithConstraints(
        modifier
            .height(height + 8.dp)
            .width(segmentWidth * labels.size + 8.dp)
            .clip(CouiSegmentCapsuleShape)
            .background(containerColor),
    ) {
        val drawW = with(density) { (motion.width * indicatorScale).toDp() }
        val drawH = with(density) { (motion.height * indicatorScale).toDp() }
        val drawCenterX = with(density) { motion.centerX.toDp() }

        Box(
            Modifier
                .offset(x = drawCenterX - drawW / 2f, y = (height + 8.dp - drawH) / 2f)
                .size(width = drawW, height = drawH)
                .clip(CouiSegmentCapsuleShape)
                .background(indicatorColor),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                val amount = (1f - abs(effectivePage - index)).coerceIn(0f, 1f)
                val reselect = onReselect
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            // 已选中那一项换成双击手势：单击不做任何事，双击通知外层回顶刷新。
                            if (reselect != null && index == settled) {
                                Modifier.pointerInput(index) {
                                    detectTapGestures(onDoubleTap = { reselect() })
                                }
                            } else {
                                Modifier.clickable(
                                    interactionSource = pressSource,
                                    indication = null,
                                    onClick = {
                                        selectTarget(index)
                                        onSelect(index)
                                    },
                                )
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (amount >= 0.5f) FontWeight.Medium else FontWeight.Normal,
                        color = lerp(scheme.onSurfaceSecondary, scheme.onSurface, amount),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** 分段胶囊内部的按压反馈：COUI 的指示层会在整块上画方角，这里只做缩放。 */
@Composable
private fun MutableInteractionSource.collectIsPressedAsStateCompat() =
    collectIsPressedAsState()
