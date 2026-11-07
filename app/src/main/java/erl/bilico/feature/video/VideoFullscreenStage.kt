package erl.bilico.feature.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ColorOS 17 视频转屏动画参数（`com.oplus.animation.OplusVideoRotateAnimation`）：
 * 450ms，COUIMoveEase (0.3, 0, 0.1, 1)。
 */
internal const val COLOROS_VIDEO_ROTATE_DURATION_MS = 450

internal val ColorOSMoveEase = CubicBezierEasing(0.3f, 0.0f, 0.1f, 1.0f)

/** 等系统真正转完屏的上限，超时就按当前窗口方向直接收尾。 */
private const val ORIENTATION_SETTLE_TIMEOUT_MS = 1500L

/** 退出全屏时缩回到这个进度就让系统栏开始滑入，不等动画完全结束。 */
private const val SYSTEM_BARS_REVEAL_PROGRESS = 0.5f


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
internal fun rememberStableNavBarBottomDp(): androidx.compose.ui.unit.Dp {
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
 * 播放页的横竖屏舞台：系统真横屏 + 自绘视频旋转动画。
 *
 * ColorOS 只对白名单里的应用（B 站、腾讯视频等，见 `sys_video_rotate_white_list.xml`）
 * 在转屏时把视频区域单独转出来，其他应用拿到的都是整窗淡入淡出。这里自己补上同一套动画：
 *
 * - 窗口的系统转屏动画设成 `SEAMLESS`：系统在新方向上直接接着合成当前画面，
 *   不再先截一张静止图盖住屏幕等 App 出首帧，转屏那一帧不会看到画面定住；
 * - 进入全屏：竖屏窗口里先把播放器从 16:9 位置放大并旋转 ±90° 铺满整屏，
 *   动画落稳后再向系统申请对应方向的横屏。最后一帧和横屏窗口的画面完全重合；
 * - 退出全屏：先申请竖屏，等窗口真的转回竖屏，从「旋转铺满」那一帧倒着缩回 16:9 原位。
 *
 * 播放器始终在同一个组合槽位里，PlayerView 不会被重建。
 */
@Composable
internal fun VideoFullscreenStage(
    fullscreen: Boolean,
    landscapeSide: Int,
    inPipMode: Boolean,
    activity: Activity?,
    darkMode: Int,
    playerContent: @Composable BoxScope.() -> Unit,
    detailContent: @Composable BoxScope.() -> Unit,
    portraitOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val dark = isDarkTheme(darkMode)
    val windowLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentWindowLandscape by rememberUpdatedState(windowLandscape)

    // 手动全屏时手机可能还竖着拿，沿用最近一次的横屏方向，默认听筒朝左。
    var preferredSide by remember { mutableIntStateOf(1) }
    if (landscapeSide != 0) preferredSide = landscapeSide
    val currentSide by rememberUpdatedState(preferredSide)

    val progress = remember { Animatable(if (fullscreen) 1f else 0f) }
    val angle = remember { Animatable(sideToDegrees(preferredSide)) }

    DisposableEffect(activity) {
        val window = activity?.window
        val oldRotation = window?.attributes?.rotationAnimation
        val oldCutout = window?.attributes?.layoutInDisplayCutoutMode
        window?.attributes = window.attributes.apply {
            rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
            // 横屏窗口要和竖屏整屏严格对调，刘海那一侧不能被系统让出去，否则转屏那一帧对不齐。
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (window != null && oldRotation != null && oldCutout != null) {
                window.attributes = window.attributes.apply {
                    rotationAnimation = oldRotation
                    layoutInDisplayCutoutMode = oldCutout
                }
            }
        }
    }

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

    LaunchedEffect(fullscreen, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            // 和 ColorOS 白名单转屏一样：开转前先收起状态栏和导航条，旋转途中画面上只剩视频。
            // 舞台用的是固定的系统栏高度，收起系统栏不会让播放器和详情页重排。
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            if (!currentWindowLandscape) {
                if (progress.value <= 0.001f) {
                    angle.snapTo(sideToDegrees(currentSide))
                    // 头一帧播放器要按横屏尺寸重排一次，画完再起动画。
                    repeat(2) { withFrameNanos { } }
                }
                progress.animateTo(1f, tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase))
            } else {
                progress.snapTo(1f)
            }
            activity?.requestedOrientation = degreesToOrientation(angle.value)
        } else {
            if (currentWindowLandscape) {
                // 从系统真横屏退出：先记下当前朝向，转回竖屏后从「旋转铺满」那一帧倒着放。
                angle.snapTo(displayRotationDegrees(activity) ?: angle.value)
                progress.snapTo(1f)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
                    snapshotFlow { currentWindowLandscape }.first { !it }
                }
                // 转回竖屏后的头一帧要整页重排，等它画完再起动画，否则前半段会被这一帧吞掉。
                repeat(2) { withFrameNanos { } }
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            // 缩回途中就放出系统栏：转过一半（约 140ms）开始滑入，和画面落位大致同时结束。
            var barsShown = false
            progress.animateTo(0f, tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase)) {
                if (!barsShown && value <= SYSTEM_BARS_REVEAL_PROGRESS) {
                    barsShown = true
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            if (!barsShown) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 横屏里把手机掉个头：直接申请对侧横屏，系统硬切即可，画面本来就铺满。
    LaunchedEffect(landscapeSide, fullscreen, windowLandscape) {
        if (fullscreen && windowLandscape && landscapeSide != 0 && progress.value >= 0.999f) {
            angle.snapTo(sideToDegrees(landscapeSide))
            activity?.requestedOrientation = degreesToOrientation(angle.value)
        }
    }

    // 转屏让窗口重新量 inset，横屏落定后再收一遍系统栏，防止被顶出来。
    LaunchedEffect(fullscreen, windowLandscape, activity) {
        if (fullscreen && windowLandscape) {
            activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                ?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (inPipMode) {
        Box(Modifier.fillMaxSize().background(Color.Black), content = playerContent)
        return
    }

    val inStage by remember { derivedStateOf { fullscreen || progress.value > 0.0005f } }
    val pageSurface = pageContainerColor()
    val statusBarPx = rememberStableStatusBarHeightPx()
    // 横屏窗口里详情页仍按竖屏尺寸测量，退出全屏时不用重排，滚动位置也不会跳。
    var portraitWidth by remember { mutableIntStateOf(0) }
    var portraitHeight by remember { mutableIntStateOf(0) }

    Layout(
        content = {
            // 0: 简介 / 评论，只在竖屏窗口且没铺满时绘制。
            Box(
                modifier = Modifier
                    .background(pageSurface)
                    .drawWithContent {
                        if (!windowLandscape && progress.value < 0.9995f) drawContent()
                    },
                content = detailContent,
            )

            // 1: 竖屏视频槽 + 全屏渐黑遮罩，压在详情页上面，只画纯色不开离屏缓冲。
            Box(
                modifier = Modifier.drawBehind {
                    val p = if (windowLandscape) 1f else progress.value
                    if (p < 0.9995f) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(0f, statusBarPx),
                            size = Size(size.width, min(size.width * 9f / 16f, size.height * 0.7f)),
                        )
                    }
                    if (p > 0.0005f) drawRect(Color.Black.copy(alpha = p.coerceIn(0f, 1f)))
                },
            )

            // 2: 播放器。竖屏窗口转屏途中按横屏尺寸布局一次，由 GPU 矩阵完成缩放 + ±90° 旋转。
            Box(
                modifier = Modifier.graphicsLayer {
                    if (windowLandscape || !inStage) {
                        transformOrigin = TransformOrigin.Center
                        translationX = 0f
                        translationY = if (windowLandscape) 0f else statusBarPx
                        scaleX = 1f
                        scaleY = 1f
                        rotationZ = 0f
                        clip = !windowLandscape
                    } else {
                        val portW = size.height
                        val portH = size.width
                        val videoH = min(portW * 9f / 16f, portH * 0.7f)
                        val p = progress.value
                        val s = lerp(videoH / portW.coerceAtLeast(1f), 1f, p).coerceAtLeast(0.01f)
                        transformOrigin = TransformOrigin.Center
                        translationX = portW * 0.5f - portH * 0.5f
                        translationY = lerp(statusBarPx + videoH * 0.5f, portH * 0.5f, p) - portW * 0.5f
                        scaleX = s
                        scaleY = s
                        rotationZ = p * angle.value
                        clip = false
                    }
                },
                content = playerContent,
            )

            // 3: 竖屏附加层（返回按钮等）。
            Box(
                modifier = Modifier.drawWithContent { if (!windowLandscape && !inStage) drawContent() },
                content = { if (!windowLandscape && !inStage) portraitOverlay() },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (windowLandscape || progress.value >= 0.9995f) {
                    drawRect(Color.Black)
                } else {
                    drawRect(pageSurface)
                    drawRect(Color.Black, size = Size(size.width, statusBarPx))
                }
            },
    ) { measurables, constraints ->
        val winW = constraints.maxWidth.coerceAtLeast(1)
        val winH = constraints.maxHeight.coerceAtLeast(1)
        if (!windowLandscape) {
            portraitWidth = winW
            portraitHeight = winH
        }
        val portW = if (portraitWidth > 0) portraitWidth else min(winW, winH)
        val portH = if (portraitHeight > 0) portraitHeight else maxOf(winW, winH)
        val videoH = min(portW * 9f / 16f, portH * 0.7f)
        val detailTop = (statusBarPx + videoH).roundToInt()

        val detail = measurables[0].measure(
            Constraints.fixed(portW, (portH - detailTop).coerceAtLeast(1)),
        )
        val backdrop = measurables[1].measure(Constraints.fixed(winW, winH))
        val player = measurables[2].measure(
            when {
                windowLandscape -> Constraints.fixed(winW, winH)
                inStage -> Constraints.fixed(portH, portW)
                else -> Constraints.fixed(portW, videoH.roundToInt().coerceAtLeast(1))
            },
        )
        val overlay = measurables[3].measure(Constraints.fixed(winW, winH))
        layout(winW, winH) {
            detail.place(0, if (windowLandscape) 0 else detailTop)
            backdrop.place(0, 0)
            player.place(0, 0)
            overlay.place(0, 0)
        }
    }
}

/** `+1` 听筒朝左 → 顺时针 90°；`-1` 听筒朝右 → 逆时针 90°。 */
private fun sideToDegrees(side: Int): Float = if (side < 0) -90f else 90f

private fun degreesToOrientation(degrees: Float): Int = if (degrees < 0f) {
    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
} else {
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}

private fun displayRotationDegrees(activity: Activity?): Float? {
    val display = activity?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) it.display else null
    } ?: return null
    return when (display.rotation) {
        Surface.ROTATION_90 -> 90f
        Surface.ROTATION_270 -> -90f
        else -> null
    }
}

/**
 * 播放器弹层宿主：竖屏时走标准 Dialog；全屏时直接嵌在横屏舞台里居中展示，
 * 避免 Dialog 自己再开一个窗口跟宿主抢方向。
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
