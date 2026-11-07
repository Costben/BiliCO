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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import erl.bilico.ui.BiliCoTheme
import erl.bilico.ui.isDarkTheme
import erl.bilico.ui.pageContainerColor
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 标记当前是否正处于 450ms 横竖屏旋转缩放过渡途中。
 * 过渡期间播放器外框透明，仅由视频纹理与弹幕精灵在黑色渐变遮罩上旋转，无需任何旋转矩形离屏裁剪 FBO。
 */
internal val LocalStageTransitioning = compositionLocalOf { false }

/**
 * 读取忽略显隐状态的固定状态栏像素高度，避免全屏切换隐藏/显示系统栏时触发整页逐帧重排。
 */
@Composable
internal fun rememberStableStatusBarHeightPx(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val fromInsets = ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: 0
        if (fromInsets > 0) {
            fromInsets.toFloat()
        } else {
            val resId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) view.resources.getDimensionPixelSize(resId).toFloat() else with(density) { 28.dp.toPx() }
        }
    }
}

/**
 * 读取忽略显隐状态的固定导航栏底部内边距，防止进出全屏时因系统栏动画引发详情列表逐帧重测。
 */
@Composable
internal fun rememberStableNavBarBottomDp(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val bottomPx = ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?: 0
        with(density) { bottomPx.toDp() }
    }
}

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

    DisposableEffect(activity, dark) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.isAppearanceLightStatusBars = false
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
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

    LaunchedEffect(fullscreen, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            if (progress.value <= 0.001f) {
                angle.snapTo(if (currentSide < 0) -90f else 90f)
            }
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
            )
            // 旋转动画落稳后再收起系统栏，彻底杜绝系统栏 WindowInsets 动画在 450ms 转屏途中抢占合成与触发重排
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
            )
            controller?.show(WindowInsetsCompat.Type.systemBars())
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
    val stageTransitioning by remember {
        derivedStateOf { progress.value > 0.0005f && progress.value < 0.9995f }
    }

    val pageSurface = pageContainerColor()
    val statusBarPx = rememberStableStatusBarHeightPx()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (progress.value >= 0.9995f) {
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
                // 0: 下方简介 / 评论页——固定竖屏尺寸与单位矩阵，转屏期间不发生缩放旋转，消除中文字形图集每帧重栅格化。
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = 0f
                            translationY = statusBarPx + portVideoH
                            scaleX = 1f
                            scaleY = 1f
                            rotationZ = 0f
                        }
                        .background(pageSurface)
                        .drawWithContent {
                            if (progress.value < 0.9995f) {
                                drawContent()
                            }
                        },
                    content = detailContent,
                )

                // 1: 竖屏视频底槽与全屏黑色渐变遮罩，完全不触发离屏缓冲。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val p = progress.value
                            if (p < 0.9995f) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(0f, statusBarPx),
                                    size = Size(portW, portVideoH),
                                )
                            }
                            if (p > 0.0005f) {
                                drawRect(
                                    color = Color.Black.copy(alpha = p.coerceIn(0f, 1f)),
                                )
                            }
                        },
                )

                // 2: 顶部视频播放器——进退全屏期间按横屏目标帧布局一次，由 GPU 矩阵完成连续缩放 + ±90° 旋转，无旋转裁剪 FBO。
                CompositionLocalProvider(LocalStageTransitioning provides stageTransitioning) {
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
                                    clip = false
                                }
                            },
                        content = playerContent,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val detailPlaceable = measurables[0].measure(
                Constraints.fixed(detailMeasureW.coerceAtLeast(1), detailMeasureH.coerceAtLeast(1)),
            )
            val backdropPlaceable = measurables[1].measure(
                Constraints.fixed(constraints.maxWidth.coerceAtLeast(1), constraints.maxHeight.coerceAtLeast(1)),
            )
            val playerPlaceable = measurables[2].measure(
                Constraints.fixed(playerMeasureW.coerceAtLeast(1), playerMeasureH.coerceAtLeast(1)),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                detailPlaceable.place(0, 0)
                backdropPlaceable.place(0, 0)
                playerPlaceable.place(0, 0)
            }
        }

        if (!inLandscapeStage) {
            portraitOverlay()
        }
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
        BiliCoTheme(darkMode = 2) {
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
                        .widthIn(max = 420.dp)
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    content()
                }
            }
        }
    }
}
