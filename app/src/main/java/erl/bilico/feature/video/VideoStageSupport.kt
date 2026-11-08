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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import erl.bilico.ui.BiliCoTheme
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
 * 播放器要不要把画面拉成满宽再裁掉下面。
 * 详情页里的横屏视频收窄时开着：槽位变矮只裁画面，宽度始终等于屏幕宽。
 */
internal val LocalCropVideoWidth = staticCompositionLocalOf { false }

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
