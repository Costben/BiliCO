package erl.bilico.feature.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import erl.bilico.ui.BiliCoTheme
import erl.bilico.ui.BlurEffectsEnabled
import io.github.suqi8.coui.kmp.basic.GradientStrokeStyle
import io.github.suqi8.coui.kmp.basic.InnerShadowStyle
import io.github.suqi8.coui.kmp.basic.couiGlassSurface
import io.github.suqi8.coui.kmp.basic.materialShadowEdge
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * 舞台给播放器图层的缩放（按播放器图层自身尺寸求值）。
 * 播放器里要保持真实大小的浮层（比如「已退出全屏」提示）用 [Modifier.stageCounterScale] 抵消掉它。
 */
internal val LocalStageScale = staticCompositionLocalOf<(Size) -> Float?> { { null } }

/**
 * 转场期间播放器要不要自带不透明黑底。
 * 横屏旋转靠这层黑底撑住整窗；竖屏全屏是等比放大，带黑底会在画面外露出一条黑带。
 */
internal val LocalStageOpaquePlayer = staticCompositionLocalOf { true }

/** 竖屏视频收到底时留下的那条顶栏的高度。 */
internal val CollapsedBarHeight = 52.dp

/**
 * 竖屏视频槽位当前的高度（px）。0 表示还没量过，也就是完整高度。
 * 顶栏里那颗「继续播放」把它写回 0，就把视频重新展开。
 */
internal val LocalPortraitSlotHeight = staticCompositionLocalOf<MutableFloatState> {
    mutableFloatStateOf(0f)
}

/** 收起进度到底、详情页顶部只剩那条顶栏时才画它；读的是一个布尔，滑动过程中不会反复重组。 */
@Composable
internal fun BoxScope.CollapsedBarSlot(
    visible: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    if (visible) content()
}

/**
 * 横屏全屏里把手机掉个头（听筒左右互换）。
 *
 * 播放器铺满整个横屏窗口，所以在当前窗口里把画面转 180° 在物理上正好跟着手机一起掉头；
 * 转完再申请对侧横屏，逐帧等显示真的翻过去，最后把补转的角度归零——前后两帧画面重合，
 * 用户不会再看到「画面一直保持正立、突然上下颠倒」那一下硬切。
 *
 * SurfaceView 的画面由系统单独合成，Compose 的旋转作用不到它，得先抓一帧静止画面顶上去。
 */
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
    activity: Activity?,
    frameHolder: StageFrameHolder,
) {
    LaunchedEffect(landscapeSide, fullscreen, windowLandscape, portraitStage, surfaceViewPlayer) {
        if (portraitStage || !fullscreen || !windowLandscape ||
            landscapeSide == 0 || progress.value < 0.999f
        ) {
            return@LaunchedEffect
        }
        val target = sideToDegrees(landscapeSide)
        val delta = target - angle.value
        if (delta == 0f) {
            // 又转回原来的朝向：没有要补的旋转，只把窗口对齐一下。
            reorient.snapTo(0f)
            activity?.requestedOrientation = degreesToOrientation(target)
            return@LaunchedEffect
        }
        if (!SystemRotationAnimation.appDriven) {
            // 回落：不自绘这半圈补转，直接把朝向摆过去申请对侧横屏，
            // 让系统转屏动画把这次掉头演完，免得又和自绘的那半圈叠在一起。
            reorient.snapTo(0f)
            angle.snapTo(target)
            activity?.requestedOrientation = degreesToOrientation(target)
            return@LaunchedEffect
        }
        if (surfaceViewPlayer) {
            if (!frameHolder.freeze()) {
                // 抓不到画面就退回原来的硬切。
                reorient.snapTo(0f)
                angle.snapTo(target)
                activity?.requestedOrientation = degreesToOrientation(target)
                return@LaunchedEffect
            }
            try {
                frameHolder.animateFrozen(
                    animate = {
                        reorient.animateTo(
                            delta,
                            tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                        )
                    },
                    rotate = {
                        angle.snapTo(target)
                        activity?.requestedOrientation = degreesToOrientation(target)
                    },
                    settle = {
                        waitForDisplayRotation(activity, target)
                        // 归零要在显示真的翻过去之后：新坐标里的 0° 正好接上刚才的 180°。
                        // 之后再多等两帧，让 SurfaceView 的缓冲区跟着新窗口尺寸重排，再放回实时画面。
                        reorient.snapTo(0f)
                        repeat(2) { withFrameNanos { } }
                    },
                )
            } finally {
                reorient.snapTo(0f)
            }
            return@LaunchedEffect
        }
        try {
            reorient.animateTo(
                delta,
                tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
            )
        } catch (e: CancellationException) {
            // 转动途中退出全屏或又转了回去：补转的角度必须归零，否则播放器会歪着。
            reorient.snapTo(0f)
            throw e
        }
        angle.snapTo(target)
        activity?.requestedOrientation = degreesToOrientation(target)
        // 逐帧等到显示真的翻过去再归零：这一步差一帧，画面上就会闪一下上下颠倒。
        waitForDisplayRotation(activity, target)
        reorient.snapTo(0f)
    }
}

/** 逐帧等到显示真的转到 [degrees]，超时就按当前画面收尾。 */
private suspend fun waitForDisplayRotation(activity: Activity?, degrees: Float) {
    withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
        while (displayRotationDegrees(activity) != degrees) {
            withFrameNanos { }
        }
    }
}

/**
 * 全屏舞台的横竖屏编排：进入全屏先把播放器转起来再申请横屏，退出全屏先等窗口转回竖屏再缩回槽位。
 *
 * SurfaceView（HDR）的画面由系统单独合成，Compose 转不动它，那几段转场改成抓一帧静止画面顶替：
 * 动画走完、窗口真的转过去之后再放回实时画面，中间不会出现「画面直接跳过去」的硬切。
 */
@Composable
internal fun StageOrientationTransitionEffect(
    fullscreen: Boolean,
    portraitStage: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    angle: Animatable<Float, AnimationVector1D>,
    frameHolder: StageFrameHolder,
    activity: Activity?,
    windowLandscape: () -> Boolean,
    sideDegrees: () -> Float,
    surfaceViewPlayer: () -> Boolean,
    darkStatusIcons: () -> Boolean,
) {
    LaunchedEffect(fullscreen, portraitStage, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            // 和 ColorOS 白名单转屏一样：开转前先收起状态栏和导航条，旋转途中画面上只剩视频。
            // 舞台用的是固定的系统栏高度，收起系统栏不会让播放器和详情页重排。
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            // 起跳点就是当前槽位高度（用户拖到哪就从哪长起来），这里不改高度：
            // 改回去会让竖屏视频先跳一下再放大。转场本身按 slotMetrics 换算缩放。
            if (portraitStage) {
                // 竖屏视频：不转窗口，播放器从当前槽位等比放大到整屏。
                angle.snapTo(0f)
                if (windowLandscape()) {
                    // 上一个视频还是横屏全屏时切到竖屏视频：先转回竖屏，直接落位。
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    progress.snapTo(1f)
                    withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
                        snapshotFlow { windowLandscape() }.first { !it }
                    }
                    repeat(2) { withFrameNanos { } }
                } else {
                    if (progress.value <= 0.001f) {
                        // 头一帧播放器要按整屏尺寸重排一次，画完再起动画。
                        repeat(2) { withFrameNanos { } }
                    }
                    progress.animateTo(
                        1f,
                        tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                    )
                }
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else if (!windowLandscape()) {
                angle.snapTo(sideDegrees())
                if (!SystemRotationAnimation.appDriven) {
                    // 回落：不自绘「旋转 + 放大」，直接把画面摆成横屏全屏再申请朝向，
                    // 让系统自己的转屏动画完成这次转身，屏幕上只留一个动画。
                    progress.snapTo(1f)
                    activity?.requestedOrientation = degreesToOrientation(angle.value)
                    return@LaunchedEffect
                }
                if (surfaceViewPlayer() && progress.value <= 0.001f && frameHolder.freeze()) {
                    // SurfaceView 转不动：让替身帧把这段旋转走完，等窗口真转过去再交回实时画面。
                    frameHolder.animateFrozen(
                        animate = {
                            // 第一张是按竖屏槽位尺寸抓的（1080×607 之类），铺到整屏会明显发糊。
                            // 冻结之后布局才换成横屏舞台尺寸，等 SurfaceView 的缓冲区跟着放大，
                            // 补抓一张同样位置的清晰画面换上去，动画期间显示的就是整屏分辨率。
                            frameHolder.refine()
                            progress.animateTo(
                                1f,
                                tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                            )
                        },
                        rotate = { activity?.requestedOrientation = degreesToOrientation(angle.value) },
                        settle = {
                            withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
                                snapshotFlow { windowLandscape() }.first { it }
                            }
                            repeat(2) { withFrameNanos { } }
                        },
                    )
                } else {
                    // 从竖屏全屏直接换成横屏视频：画面已经在整屏，直接把朝向摆正再转窗口。
                    // SurfaceView 也走这条：它不参与自绘转场，那 450ms 里画面既不动也不转，
                    // 白等一场；直接铺满并立刻申请横屏，让系统的无缝转屏接上。
                    if (progress.value <= 0.001f) {
                        // 头一帧播放器要按横屏尺寸重排一次，画完再起动画。
                        repeat(2) { withFrameNanos { } }
                    }
                    if (surfaceViewPlayer()) {
                        progress.snapTo(1f)
                    } else {
                        progress.animateTo(
                            1f,
                            tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                        )
                    }
                    activity?.requestedOrientation = degreesToOrientation(angle.value)
                }
            } else {
                progress.snapTo(1f)
                activity?.requestedOrientation = degreesToOrientation(angle.value)
            }
        } else {
            var frozenForExit = false
            try {
                if (windowLandscape()) {
                    // 从系统真横屏退出：若当前是横屏视频则先记下朝向并从「旋转铺满」倒着缩回；
                    // 若已切到竖屏视频仍先等窗口彻底转回竖屏，避免竖屏动画在横屏尺寸里走偏。
                    angle.snapTo(if (portraitStage) 0f else displayRotationDegrees(activity) ?: angle.value)
                    // 还在横屏窗口里的时候抓帧：此时画面位置就是用户看到的位置。
                    frozenForExit = !portraitStage && surfaceViewPlayer() && frameHolder.freeze()
                    progress.snapTo(1f)
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
                        snapshotFlow { windowLandscape() }.first { !it }
                    }
                    // 转回竖屏后的头一帧要整页重排，等它画完再起动画，否则前半段会被这一帧吞掉。
                    repeat(2) { withFrameNanos { } }
                } else {
                    if (portraitStage) angle.snapTo(0f)
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
                // 转屏时窗口属性会被系统重新下发一遍，状态栏图标颜色可能被打回成跟随主题的深色，
                // 在纯黑的播放页顶部就看不见了。落回竖屏后再按「视频有没有收到底」定一次。
                controller?.isAppearanceLightStatusBars = darkStatusIcons()
            } finally {
                if (frozenForExit) frameHolder.unfreeze()
            }
        }
    }
}

/** 退出全屏时缩回到这个进度就让系统栏开始滑入，不等动画完全结束。 */
private const val SYSTEM_BARS_REVEAL_PROGRESS = 0.5f

/**
 * 全屏舞台需要的窗口属性：系统转屏动画设成 `SEAMLESS`（新方向直接接着合成当前画面，
 * 不先截一张静止图盖住屏幕），横屏窗口与竖屏整屏严格对调（刘海那一侧不被系统让出去）。
 *
 * `SEAMLESS` 只在应用自绘转屏时才有意义（见 [SystemRotationAnimation]）：别的 ROM 不认这个属性，
 * 设了也压不住系统转屏，反而和应用自己那一套叠成两个动画，所以那边干脆不碰它，留给系统演。
 */
@Composable
internal fun StageWindowAttributesEffect(activity: Activity?) {
    DisposableEffect(activity) {
        val window = activity?.window
        val oldRotation = window?.attributes?.rotationAnimation
        val oldCutout = window?.attributes?.layoutInDisplayCutoutMode
        window?.attributes = window.attributes.apply {
            if (SystemRotationAnimation.appDriven) {
                rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
            }
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
}

@Stable
internal class VideoBarBackdrops(
    val detail: LayerBackdrop?,
    val barMaterial: LayerBackdrop?,
)

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

/**
 * 抵消舞台转场缩放：转屏途中跟着视频一起转，但大小始终等于竖屏视频页上的大小，
 * 收尾那一帧不会从缩小状态突然弹回原尺寸。必须挂在和播放器一样大的容器上。
 */
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

/** 视频宽高比未知时按 16:9 处理，和竖屏详情页的播放器槽位一致。 */
internal const val DEFAULT_VIDEO_ASPECT = 16f / 9f

/** 视频按 FIT 排进给定容器后实际占用的宽度。 */
internal fun fitContentWidth(aspect: Float, containerW: Float, containerH: Float): Float =
    min(containerW, containerH * aspect)

/** `+1` 听筒朝左 → 顺时针 90°；`-1` 听筒朝右 → 逆时针 90°。 */
internal fun sideToDegrees(side: Int): Float = if (side < 0) -90f else 90f

internal fun degreesToOrientation(degrees: Float): Int = if (degrees < 0f) {
    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
} else {
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}

internal fun displayRotationDegrees(activity: Activity?): Float? {
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
 * 播放器弹层宿主：竖屏时走标准 Dialog；全屏时直接嵌在横屏舞台里居中展示，
 * 避免 Dialog 自己再开一个窗口跟宿主抢方向。
 */
@Composable
internal fun PlayerModalHost(
    inlineInStage: Boolean,
    onDismiss: () -> Unit,
    horizontalPadding: Dp = 24.dp,
    cornerRadius: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    if (!inlineInStage) {
        val colors = COUITheme.colorScheme
        val textStyles = COUITheme.textStyles
        val dimens = COUITheme.dimens
        var show by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { show = true }
        OverlayDialog(
            show = show,
            outsideMargin = DpSize(16.dp, 8.dp),
            cornerRadius = cornerRadius,
            onDismissRequest = onDismiss,
        ) {
            COUITheme(colors = colors, textStyles = textStyles, dimens = dimens) {
                content()
            }
        }
    } else {
        BackHandler(onBack = onDismiss)
        BiliCoTheme(darkMode = 2) {
            val scheme = COUITheme.colorScheme
            val dialogShape = SquircleShape(
                cornerRadius = cornerRadius,
                weight = DialogDefaults.CornerWeight,
            )
            val panelBg = DialogDefaults.backgroundColor()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.windowDimming)
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGestures { } }
                        .couiGlassSurface(
                            backdrop = if (BlurEffectsEnabled) LocalBackdrop.current else null,
                            shape = dialogShape,
                            containerColor = panelBg,
                            fallback = Modifier.squircleSurface(
                                color = panelBg,
                                cornerRadius = cornerRadius,
                                weight = DialogDefaults.CornerWeight,
                            ),
                            blurRadius = DialogDefaults.blurRadius(),
                            blendColors = DialogDefaults.blurColors(),
                        )
                        .materialShadowEdge(
                            stroke = DialogDefaults.stroke(),
                            cornerWeight = DialogDefaults.CornerWeight,
                            cornerRadius = cornerRadius,
                            edgeStyle = GradientStrokeStyle.EDGE_STYLE_2,
                            shadowStyle = InnerShadowStyle.SHADOW_STYLE_0,
                        )
                        .clip(dialogShape),
                ) {
                    content()
                }
            }
        }
    }
}
