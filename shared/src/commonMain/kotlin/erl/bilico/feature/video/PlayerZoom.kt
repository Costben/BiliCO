package erl.bilico.feature.video

import erl.bilico.platform.formatFixedDecimal
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.ui.components.COUISmoothCapsuleShape
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * 播放器双指缩放、平移与旋转状态：
 * - 双指捏合支持放大与缩小（0.5x ~ 4.0x）、双指拖动平移焦点，以及顺/逆时针自由旋转；
 * - 单指手势不参与画面平移或旋转，因此在缩放/旋转状态下依然可以正常单指滑动进度条、亮度与音量。
 */
@Stable
internal class PlayerZoomState {
    var scale: Float by mutableFloatStateOf(1f)
        private set
    var offsetX: Float by mutableFloatStateOf(0f)
        private set
    var offsetY: Float by mutableFloatStateOf(0f)
        private set
    var rotationDegrees: Float by mutableFloatStateOf(0f)
        private set
    var pinching: Boolean by mutableStateOf(false)
        private set

    val isZoomed: Boolean
        get() = abs(scale - 1f) > 0.01f ||
            abs(offsetX) > 1f ||
            abs(offsetY) > 1f ||
            abs(rotationDegrees) > 0.5f

    fun onPinchStart() {
        pinching = true
    }

    fun applyPinch(
        zoomChange: Float,
        panChange: Offset,
        rotationChangeDeg: Float,
        centroid: Offset,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        if (!zoomChange.isFinite() || zoomChange <= 0f) return
        val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        val ratio = if (scale > 0.001f) newScale / scale else 1f
        val rawX = (offsetX - (centroid.x - centerX)) * ratio + (centroid.x - centerX) + panChange.x
        val rawY = (offsetY - (centroid.y - centerY)) * ratio + (centroid.y - centerY) + panChange.y
        val maxOffsetX = (viewWidth * (newScale - 1f) / 2f).coerceAtLeast(0f)
        val maxOffsetY = (viewHeight * (newScale - 1f) / 2f).coerceAtLeast(0f)
        scale = newScale
        offsetX = rawX.coerceIn(-maxOffsetX, maxOffsetX)
        offsetY = rawY.coerceIn(-maxOffsetY, maxOffsetY)
        if (rotationChangeDeg.isFinite() && rotationChangeDeg != 0f) {
            rotationDegrees = normalizeAngle(rotationDegrees + rotationChangeDeg)
        }
    }

    fun onPinchEnd() {
        pinching = false
        val snappedRot = snapCardinalAngle(normalizeAngle(rotationDegrees))
        val snappedScale = if (scale in SNAP_RESET_LOW..SNAP_RESET_HIGH) {
            1f
        } else {
            scale.coerceIn(MIN_SCALE, MAX_SCALE)
        }
        if (snappedScale == 1f && snappedRot == 0f) {
            reset()
        } else {
            scale = snappedScale
            rotationDegrees = snappedRot
            if (snappedScale <= 1f) {
                offsetX = 0f
                offsetY = 0f
            }
        }
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotationDegrees = 0f
        pinching = false
    }

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val SNAP_RESET_LOW = 0.96f
        private const val SNAP_RESET_HIGH = 1.04f
        private const val MAX_SCALE = 4.0f
        private const val ROTATION_SNAP_THRESHOLD_DEG = 8f
        internal const val ROTATION_UNLOCK_THRESHOLD_DEG = 8f

        private fun normalizeAngle(deg: Float): Float {
            var a = (deg + 180f) % 360f
            if (a <= 0f) a += 360f
            return a - 180f
        }

        private fun snapCardinalAngle(deg: Float): Float = when {
            abs(deg) <= ROTATION_SNAP_THRESHOLD_DEG -> 0f
            abs(deg - 90f) <= ROTATION_SNAP_THRESHOLD_DEG -> 90f
            abs(deg + 90f) <= ROTATION_SNAP_THRESHOLD_DEG -> -90f
            abs(abs(deg) - 180f) <= ROTATION_SNAP_THRESHOLD_DEG -> 180f
            else -> deg
        }
    }
}

@Composable
internal fun rememberPlayerZoomState(): PlayerZoomState = remember { PlayerZoomState() }

/**
 * 处理双指捏合缩放、双指平移与双指旋转，直到所有手指离开屏幕。
 * 旋转带 8° 启动死区，避免纯捏合缩放时画面轻微歪斜；消费全部多指事件，防止结束后误触单击或进度条。
 */
internal suspend fun AwaitPointerEventScope.handlePinchZoom(
    initialEvent: PointerEvent,
    zoom: PlayerZoomState,
    viewWidth: Float,
    viewHeight: Float,
) {
    val initialPressed = initialEvent.changes.filter { it.pressed }
    if (initialPressed.size < 2) return
    var prevDist = (initialPressed[0].position - initialPressed[1].position).getDistance()
    var prevCentroid = (initialPressed[0].position + initialPressed[1].position) / 2f
    var prevAngle = angleBetween(initialPressed[0].position, initialPressed[1].position)
    var hasPrevAngle = true
    var rotationUnlocked = abs(zoom.rotationDegrees) > 0.5f
    var pendingRotDeg = 0f

    initialEvent.changes.forEach { it.consume() }
    zoom.onPinchStart()
    try {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val p0 = pressed[0].position
                val p1 = pressed[1].position
                val dist = (p0 - p1).getDistance()
                val centroid = (p0 + p1) / 2f
                val angle = angleBetween(p0, p1)
                if (prevDist > 1f && dist > 1f && hasPrevAngle) {
                    val deltaDeg = shortestAngleDeltaDeg(prevAngle, angle)
                    val applyRotDeg = if (rotationUnlocked) {
                        deltaDeg
                    } else {
                        pendingRotDeg += deltaDeg
                        if (abs(pendingRotDeg) >= PlayerZoomState.ROTATION_UNLOCK_THRESHOLD_DEG) {
                            rotationUnlocked = true
                            val carry = pendingRotDeg
                            pendingRotDeg = 0f
                            carry
                        } else {
                            0f
                        }
                    }
                    zoom.applyPinch(
                        zoomChange = dist / prevDist,
                        panChange = centroid - prevCentroid,
                        rotationChangeDeg = applyRotDeg,
                        centroid = centroid,
                        viewWidth = viewWidth,
                        viewHeight = viewHeight,
                    )
                }
                prevDist = dist
                prevCentroid = centroid
                prevAngle = angle
                hasPrevAngle = true
            } else {
                // 松开其中一根手指后重置双指基准，等待最后一根手指抬起，不误触单指进度。
                prevDist = 0f
                hasPrevAngle = false
            }
        }
    } finally {
        zoom.onPinchEnd()
    }
}

private fun angleBetween(a: Offset, b: Offset): Double =
    atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())

private fun shortestAngleDeltaDeg(prevRad: Double, nextRad: Double): Float {
    var diff = nextRad - prevRad
    while (diff > PI) diff -= 2.0 * PI
    while (diff <= -PI) diff += 2.0 * PI
    return (diff * 180.0 / PI).toFloat()
}

/** 缩放/旋转进行中的实时提示与变换后的「还原画面」按钮。 */
@Composable
internal fun BoxScope.PlayerZoomOverlay(
    zoom: PlayerZoomState,
    showChrome: Boolean,
    topOffset: Dp,
    hideResetPill: Boolean,
) {
    if (zoom.pinching) {
        val percent = (zoom.scale * 100f).roundToInt().coerceAtLeast(50)
        val rot = zoom.rotationDegrees.roundToInt()
        val label = if (abs(rot) > 0) "$percent% · ${rot}°" else "$percent%"
        Text(
            text = label,
            modifier = Modifier
                .align(Alignment.Center)
                .clip(SquircleShape(10.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("player_zoom_badge"),
            style = COUITheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    } else if (zoom.isZoomed && !hideResetPill) {
        val hasScale = abs(zoom.scale - 1f) > 0.01f
        val rot = zoom.rotationDegrees.roundToInt()
        val detail = buildString {
            if (hasScale) append("${formatFixedDecimal((zoom.scale).toDouble(), 1)}x")
            if (abs(rot) > 0) {
                if (isNotEmpty()) append(" · ")
                append("${rot}°")
            }
        }
        val label = if (detail.isNotEmpty()) "还原画面 $detail" else "还原画面"
        val topPad = if (showChrome) topOffset + 52.dp else topOffset + 12.dp
        Text(
            text = label,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPad)
                .clip(COUISmoothCapsuleShape)
                .background(Color(0xB8141416))
                .border(0.8.dp, Color.White.copy(alpha = 0.18f), COUISmoothCapsuleShape)
                .clickable { zoom.reset() }
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .testTag("player_zoom_reset"),
            style = COUITheme.textStyles.footnote2,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}
