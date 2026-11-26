package erl.bilico.feature.video

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.PlatformOrientation
import erl.bilico.platform.setScreenOrientation
import erl.bilico.platform.setSystemBarsVisible
import erl.bilico.platform.useEdgeSwipeBackOverlay
import erl.bilico.platform.useNativeFullscreenRotation
import erl.bilico.player.BiliPlayer
import erl.bilico.ui.BlurEffectsEnabled
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt

internal val LocalStageScale = staticCompositionLocalOf<(Size) -> Float?> { { null } }

internal val LocalStageOpaquePlayer = staticCompositionLocalOf { true }

internal val LocalStagePlayerVisible = staticCompositionLocalOf { true }

internal val LocalStageReturnHome = staticCompositionLocalOf<(() -> Unit)?> { null }

internal val CollapsedBarHeight = 52.dp

internal val LocalPortraitSlotHeight = staticCompositionLocalOf<MutableFloatState> {
    mutableFloatStateOf(0f)
}

@Composable
internal fun BoxScope.CollapsedBarSlot(
    visible: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    if (visible) content()
}

@Composable
internal fun LandscapeFlipEffect(
    landscapeSide: Int,
    fullscreen: Boolean,
    windowLandscape: Boolean,
    portraitStage: Boolean,
    surfaceViewPlayer: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    angle: Animatable<Float, AnimationVector1D>,
    reorient: Animatable<Float, AnimationVector1D>,
    context: PlatformContext,
    frameHolder: StageFrameHolder,
) {
    LaunchedEffect(landscapeSide, fullscreen, windowLandscape, portraitStage, surfaceViewPlayer) {
        if (useNativeFullscreenRotation) {
            reorient.snapTo(0f)
            if (!portraitStage && fullscreen && landscapeSide != 0) {
                val target = sideToDegrees(landscapeSide)
                if (target != angle.value) {
                    angle.snapTo(target)
                    setScreenOrientation(context, degreesToLandscapeOrientation(target))
                }
            }
            return@LaunchedEffect
        }
        if (portraitStage || !fullscreen || !windowLandscape ||
            landscapeSide == 0 || progress.value < 0.999f
        ) {
            return@LaunchedEffect
        }
        val target = sideToDegrees(landscapeSide)
        val delta = target - angle.value
        if (delta == 0f) {
            reorient.snapTo(0f)
            setScreenOrientation(context, degreesToLandscapeOrientation(target))
            return@LaunchedEffect
        }
        try {
            reorient.animateTo(
                delta,
                tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
            )
        } catch (e: CancellationException) {
            reorient.snapTo(0f)
            throw e
        }
        angle.snapTo(target)
        setScreenOrientation(context, degreesToLandscapeOrientation(target))
        reorient.snapTo(0f)
    }
}

@Composable
internal fun StageOrientationTransitionEffect(
    fullscreen: Boolean,
    portraitStage: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    angle: Animatable<Float, AnimationVector1D>,
    frameHolder: StageFrameHolder,
    context: PlatformContext,
    windowLandscape: () -> Boolean,
    sideDegrees: () -> Float,
    surfaceViewPlayer: () -> Boolean,
    onSyncStatusBar: () -> Unit,
) {
    LaunchedEffect(fullscreen, portraitStage, context) {
        if (useNativeFullscreenRotation && !portraitStage) {
            if (fullscreen) {
                val targetDegrees = sideDegrees()
                angle.snapTo(targetDegrees)
                progress.snapTo(0f)
                setSystemBarsVisible(context, false)
                setScreenOrientation(context, degreesToLandscapeOrientation(targetDegrees))
            } else {
                angle.snapTo(0f)
                progress.snapTo(0f)
                setScreenOrientation(context, PlatformOrientation.Portrait)
                setSystemBarsVisible(context, true)
                onSyncStatusBar()
            }
            return@LaunchedEffect
        }
        if (fullscreen) {
            setSystemBarsVisible(context, false)
            if (portraitStage) {
                angle.snapTo(0f)
                if (windowLandscape()) {
                    setScreenOrientation(context, PlatformOrientation.Portrait)
                    progress.snapTo(1f)
                    withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
                        snapshotFlow { windowLandscape() }.first { !it }
                    }
                    repeat(2) { withFrameNanos { } }
                } else {
                    if (progress.value <= 0.001f) {
                        repeat(2) { withFrameNanos { } }
                    }
                    progress.animateTo(
                        1f,
                        tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                    )
                }
                setScreenOrientation(context, PlatformOrientation.Portrait)
            } else if (!windowLandscape()) {
                angle.snapTo(sideDegrees())
                if (progress.value <= 0.001f) {
                    repeat(2) { withFrameNanos { } }
                }
                progress.animateTo(
                    1f,
                    tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                )
                setScreenOrientation(context, degreesToLandscapeOrientation(angle.value))
            } else {
                progress.snapTo(1f)
                setScreenOrientation(context, degreesToLandscapeOrientation(angle.value))
            }
        } else {
            if (windowLandscape()) {
                angle.snapTo(if (portraitStage) 0f else angle.value)
                progress.snapTo(1f)
                setScreenOrientation(context, PlatformOrientation.Portrait)
                withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
                    snapshotFlow { windowLandscape() }.first { !it }
                }
                repeat(2) { withFrameNanos { } }
            } else {
                if (portraitStage) angle.snapTo(0f)
                setScreenOrientation(context, PlatformOrientation.Portrait)
            }
            var barsShown = false
            progress.animateTo(0f, tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase)) {
                if (!barsShown && value <= SYSTEM_BARS_REVEAL_PROGRESS) {
                    barsShown = true
                    setSystemBarsVisible(context, true)
                }
            }
            if (!barsShown) setSystemBarsVisible(context, true)
            onSyncStatusBar()
        }
    }
}

private const val SYSTEM_BARS_REVEAL_PROGRESS = 0.5f

@Composable
internal fun StageWindowAttributesEffect(context: PlatformContext) {
    DisposableEffect(context) {
        onDispose {
            setScreenOrientation(context, PlatformOrientation.Portrait)
        }
    }
}

@Stable
internal class VideoBarBackdrops(
    val detail: LayerBackdrop?,
    val barMaterial: LayerBackdrop?,
)

@Composable
internal fun rememberStageVideoBackdrop(
    player: BiliPlayer,
    active: Boolean,
    isPlaying: Boolean,
): LayerBackdrop {
    // Android 上 TextureView 直接参与硬件 Canvas 合成，原生 rememberLayerBackdrop() 即可实时采集视频画面，
    // 无需每 250ms 触发 textureView.bitmap 同步读回或叠加 DstOver 黑底。
    if (!useEdgeSwipeBackOverlay) {
        return rememberLayerBackdrop()
    }
    var frame by remember(player) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(player, active, isPlaying) {
        if (!active) {
            frame = null
            return@LaunchedEffect
        }
        frame = player.captureCurrentFrame()
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            delay(250L)
            player.captureCurrentFrame()?.let { frame = it }
        }
    }
    // 显式提供 onDraw 走两趟录制（不把实时 AVPlayerLayer 吞进离屏层）：
    // 先 drawContent()，再用 DstOver 把当前视频帧和纯黑底铺回 UIKitView 的 BlendMode.Clear 透明洞里，
    // 让弹幕/控制栏保留在前景的同时，玻璃面板采集到真实视频画面，绝不出现透明空洞或单行长条模糊。
    return rememberLayerBackdrop {
        drawContent()
        val captured = frame
        if (captured != null) {
            drawVideoFrameDstOver(captured)
        }
        drawRect(Color.Black, blendMode = BlendMode.DstOver)
    }
}

private fun DrawScope.drawVideoFrameDstOver(frame: ImageBitmap) {
    val srcW = frame.width.toFloat()
    val srcH = frame.height.toFloat()
    if (srcW <= 0f || srcH <= 0f || size.width <= 0f || size.height <= 0f) return
    val scale = min(size.width / srcW, size.height / srcH)
    val dstW = (srcW * scale).roundToInt().coerceAtLeast(1)
    val dstH = (srcH * scale).roundToInt().coerceAtLeast(1)
    val dstX = ((size.width - dstW) * 0.5f).roundToInt()
    val dstY = ((size.height - dstH) * 0.5f).roundToInt()
    drawImage(
        image = frame,
        dstOffset = IntOffset(dstX, dstY),
        dstSize = IntSize(dstW, dstH),
        blendMode = BlendMode.DstOver,
    )
}

@Composable
internal fun rememberVideoBarBackdrops(pageBg: Color): VideoBarBackdrops {
    val detail = if (BlurEffectsEnabled) rememberLayerBackdrop() else null
    val barMaterial = if (BlurEffectsEnabled && detail != null) {
        rememberLayerBackdrop(upstream = detail) {
            drawRect(pageBg)
            drawContent()
        }
    } else {
        null
    }
    return remember(detail, barMaterial) {
        VideoBarBackdrops(detail = detail, barMaterial = barMaterial)
    }
}

@Composable
internal fun Modifier.stageCounterScale(): Modifier {
    val scaleOf = LocalStageScale.current
    return graphicsLayer {
        val s = scaleOf(size) ?: 1f
        transformOrigin = TransformOrigin.Center
        scaleX = 1f / s
        scaleY = 1f / s
    }
}

internal const val DEFAULT_VIDEO_ASPECT = 16f / 9f

internal fun fitContentWidth(aspect: Float, containerW: Float, containerH: Float): Float =
    min(containerW, containerH * aspect)

internal fun sideToDegrees(side: Int): Float = if (side < 0) -90f else 90f

internal fun degreesToLandscapeOrientation(degrees: Float): PlatformOrientation =
    if (degrees < 0f) PlatformOrientation.LandscapeLeft else PlatformOrientation.LandscapeRight

@Composable
internal fun rememberStableStatusBarHeightPx(): Float {
    val density = LocalDensity.current
    var stableTopPx by remember(density) { mutableFloatStateOf(0f) }
    val liveTop = WindowInsets.statusBars.getTop(density).toFloat()
    if (liveTop > stableTopPx) stableTopPx = liveTop
    return if (stableTopPx > 0f) {
        stableTopPx
    } else {
        val fallbackDp = if (useEdgeSwipeBackOverlay) 44.dp else 28.dp
        with(density) { fallbackDp.toPx() }
    }
}

@Composable
internal fun rememberStableNavBarBottomDp(): Dp {
    val density = LocalDensity.current
    var stableBottomPx by remember(density) { mutableIntStateOf(0) }
    val liveBottom = WindowInsets.navigationBars.getBottom(density)
    if (liveBottom > stableBottomPx) stableBottomPx = liveBottom
    return with(density) { stableBottomPx.toDp() }
}
