package erl.bilico.feature.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Surface
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
