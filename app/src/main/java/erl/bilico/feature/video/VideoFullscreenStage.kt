package erl.bilico.feature.video

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import erl.bilico.ui.isDarkTheme
import erl.bilico.ui.pageContainerColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 自绘全屏旋转舞台：完全绕过系统默认的窗口淡入淡出转屏动画。
 *
 * 复刻 ColorOS 17 `OplusVideoRotateAnimation` 的纯几何过渡（450ms + COUIMoveEase 0.3, 0, 0.1, 1）：
 * 顶部播放器与下方详情列表作为同一刚体平面联动——进入全屏时从竖屏尺寸同步放大至铺满整块物理屏并旋转 ±90°，
 * 退出全屏时从横屏整屏同步缩小并旋转 ±90° 回到竖屏原位，全程 0 透明度交叉淡入淡出，且中间帧 0 重组 / 0 重排。
 */
@Composable
internal fun VideoFullscreenStage(
    fullscreen: Boolean,
    landscapeSide: Int,
    inPipMode: Boolean,
    videoAspectRatio: Float,
    activity: Activity?,
    darkMode: Int,
    playerContent: @Composable BoxScope.() -> Unit,
    detailContent: @Composable BoxScope.() -> Unit,
    portraitOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val dark = isDarkTheme(darkMode)

    DisposableEffect(activity, fullscreen) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.isAppearanceLightStatusBars = false
        if (fullscreen) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }

    DisposableEffect(activity, dark) {
        onDispose {
            val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            controller?.isAppearanceLightStatusBars = !dark
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (inPipMode) {
        Box(Modifier.fillMaxSize().background(Color.Black), content = playerContent)
        return
    }

    val currentSide by rememberUpdatedState(landscapeSide)
    val progress = remember { Animatable(if (fullscreen) 1f else 0f) }
    val angle = remember { Animatable(if (landscapeSide < 0) -90f else 90f) }

    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            if (progress.value <= 0.001f) {
                angle.snapTo(if (currentSide < 0) -90f else 90f)
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
            )
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
            )
        }
    }

    LaunchedEffect(landscapeSide, fullscreen) {
        if (fullscreen && landscapeSide != 0 && progress.value > 0.001f) {
            val targetDeg = if (landscapeSide < 0) -90f else 90f
            if (abs(angle.targetValue - targetDeg) > 1f) {
                angle.animateTo(
                    targetValue = targetDeg,
                    animationSpec = tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                )
            }
        }
    }

    val inLandscapeStage by remember {
        derivedStateOf { fullscreen || progress.value > 0.0005f }
    }

    val pageSurface = pageContainerColor()
    val density = LocalDensity.current
    val liveStatusTop = WindowInsets.statusBars.getTop(density).toFloat()
    var statusBarPx by remember { mutableFloatStateOf(liveStatusTop) }
    if (!fullscreen && liveStatusTop > 0f) {
        statusBarPx = liveStatusTop
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val p = progress.value
                if (p > 0.0005f) {
                    drawRect(Color.Black)
                } else {
                    drawRect(pageSurface)
                    if (statusBarPx > 0f) {
                        drawRect(Color.Black, size = Size(size.width, statusBarPx))
                    }
                }
            },
    ) {
        val portW = constraints.maxWidth.toFloat()
        val portH = constraints.maxHeight.toFloat()
        if (portW <= 0f || portH <= 0f) return@BoxWithConstraints

        val portVideoH = min(portW * 9f / 16f, portH * 0.7f)
        val detailH = (portH - statusBarPx - portVideoH).coerceAtLeast(1f)

        val playerMeasureW = if (inLandscapeStage) portH.roundToInt() else portW.roundToInt()
        val playerMeasureH = if (inLandscapeStage) portW.roundToInt() else portVideoH.roundToInt()
        val detailMeasureW = portW.roundToInt()
        val detailMeasureH = detailH.roundToInt()

        Layout(
            content = {
                // 0: 下方简介 / 评论页——固定竖屏尺寸，背景挂在旋转后的 graphicsLayer 内侧以避免未旋转外层发生水平截断；全屏期间跳过绘制、0 重排。
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val p = progress.value
                            if (p <= 0.0005f) {
                                transformOrigin = TransformOrigin.Center
                                translationX = 0f
                                translationY = statusBarPx + portVideoH
                                scaleX = 1f
                                scaleY = 1f
                                rotationZ = 0f
                            } else {
                                val thetaDeg = p * angle.value
                                val thetaRad = Math.toRadians(thetaDeg.toDouble())
                                val visW = lerp(portW, portH, p)
                                val visH = lerp(portVideoH, portW, p)
                                val cx = portW * 0.5f
                                val cy = lerp(statusBarPx + portVideoH * 0.5f, portH * 0.5f, p)
                                val dScale = visW / portW.coerceAtLeast(1f)
                                val dist = visH * 0.5f + detailH * dScale * 0.5f
                                val dcx = cx - dist * sin(thetaRad).toFloat()
                                val dcy = cy + dist * cos(thetaRad).toFloat()

                                transformOrigin = TransformOrigin.Center
                                translationX = dcx - portW * 0.5f
                                translationY = dcy - detailH * 0.5f
                                scaleX = dScale
                                scaleY = dScale
                                rotationZ = thetaDeg
                            }
                        }
                        .background(pageSurface)
                        .drawWithContent {
                            if (progress.value < 0.9995f) {
                                drawContent()
                            }
                        },
                    content = detailContent,
                )

                // 1: 顶部视频播放器——进退全屏期间按横屏目标帧布局一次，由 GPU 矩阵完成连续缩放 + ±90° 旋转 + 硬件矩形裁剪。
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            if (!inLandscapeStage) {
                                transformOrigin = TransformOrigin.Center
                                translationX = 0f
                                translationY = statusBarPx
                                scaleX = 1f
                                scaleY = 1f
                                rotationZ = 0f
                                clip = true
                            } else {
                                val p = progress.value
                                val thetaDeg = p * angle.value
                                val visW = lerp(portW, portH, p)
                                val visH = lerp(portVideoH, portW, p)
                                val cx = portW * 0.5f
                                val cy = lerp(statusBarPx + portVideoH * 0.5f, portH * 0.5f, p)

                                val boxAspect0 = portW / portVideoH.coerceAtLeast(1f)
                                val safeAspect = videoAspectRatio.takeIf { it.isFinite() && it > 0.1f } ?: boxAspect0
                                val s0 = if (safeAspect <= boxAspect0 + 0.01f) {
                                    portVideoH / portW.coerceAtLeast(1f)
                                } else {
                                    portW / min(portH, portW * safeAspect).coerceAtLeast(1f)
                                }
                                val s = lerp(s0, 1f, p).coerceAtLeast(0.01f)

                                transformOrigin = TransformOrigin.Center
                                translationX = cx - portH * 0.5f
                                translationY = cy - portW * 0.5f
                                scaleX = s
                                scaleY = s
                                rotationZ = thetaDeg

                                if (p < 0.999f) {
                                    val clipW = (visW / s).coerceIn(1f, portH)
                                    val clipH = (visH / s).coerceIn(1f, portW)
                                    shape = CenterClipRectShape(clipW, clipH)
                                    clip = true
                                } else {
                                    clip = false
                                }
                            }
                        },
                    content = playerContent,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val detailPlaceable = measurables[0].measure(
                Constraints.fixed(detailMeasureW.coerceAtLeast(1), detailMeasureH.coerceAtLeast(1)),
            )
            val playerPlaceable = measurables[1].measure(
                Constraints.fixed(playerMeasureW.coerceAtLeast(1), playerMeasureH.coerceAtLeast(1)),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                detailPlaceable.place(0, 0)
                playerPlaceable.place(0, 0)
            }
        }

        if (!inLandscapeStage) {
            portraitOverlay()
        }
    }
}

private data class CenterClipRectShape(
    val clipW: Float,
    val clipH: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val left = ((size.width - clipW) * 0.5f).coerceAtLeast(0f)
        val top = ((size.height - clipH) * 0.5f).coerceAtLeast(0f)
        val right = (size.width - left).coerceAtLeast(left)
        val bottom = (size.height - top).coerceAtLeast(top)
        return Outline.Rectangle(Rect(left, top, right, bottom))
    }
}

/**
 * 播放器弹层宿主：竖屏时走标准 Dialog；全屏自绘旋转时直接嵌在旋转后的横屏层内居中展示，
 * 方向与横屏画面一致且不会惊动系统状态栏 / 导航栏重新弹出。
 */
@Composable
internal fun PlayerModalHost(
    inlineInStage: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!inlineInStage) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            content()
        }
    } else {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x73000000))
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                content()
            }
        }
    }
}
