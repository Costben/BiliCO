package erl.bilico.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * 是否挂载左侧 28dp 边缘滑动返回浮层：
 * - iOS 没有系统级预测性返回手势，由 Compose 边缘手势浮层驱动跟手返回；
 * - Android 由系统预测性返回手势（Predictive Back）原生驱动，不叠放触摸浮层。
 */
expect val useEdgeSwipeBackOverlay: Boolean

/** 平台无关的预测性返回手势帧事件。 */
data class PlatformBackEvent(
    val touchX: Float,
    val touchY: Float,
    val progress: Float,
    val frameTimeMillis: Long = 0L,
)

/**
 * 平台预测性返回手势监听：
 * - Android 接入系统 `PredictiveBackHandler`（与 `LocalNavigationEventDispatcherOwner` 联动）；
 * - iOS 为空实现（返回手势由 `EdgeSwipeBackOverlay` 与 `PlatformBackHandler` 驱动）。
 */
@Composable
expect fun PlatformPredictiveBackHandler(
    enabled: Boolean = true,
    onBack: suspend (progress: Flow<PlatformBackEvent>) -> Unit,
)

/**
 * 非跟手式的平台返回键监听（例如全屏看图浮层关闭）：
 * - Android 接入系统 `BackHandler`，仅在 `enabled = true` 时启用，不破坏根页面系统预测性返回；
 * - iOS 为空实现。
 */
@Composable
expect fun PlatformBackActionHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)

/** 当前平台设备是否支持画中画（PiP）。 */
expect fun canEnterPip(context: PlatformContext): Boolean

/** 立即尝试进入画中画模式。 */
expect fun enterPipNow(context: PlatformContext): Boolean

/**
 * 将当前播放页与播放状态同步到平台画中画参数（Android 12+ 自动进 PiP 与操作按钮）。
 */
@Composable
expect fun SyncPictureInPictureParams(
    activePlayback: Boolean,
    isPlaying: Boolean,
    autoPip: Boolean,
)
