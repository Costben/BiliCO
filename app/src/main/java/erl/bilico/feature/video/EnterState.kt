package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import erl.bilico.ui.LocalRouteMinimized

/**
 * 展开动画期间界面定格在进场第一帧：新视频的详情、推荐在动画中途回来时先不上屏，
 * 等落位后再一次性换上，免得整页重排压在 350ms 的转场里。再开同一条时第一帧就是完整内容。
 */
@Composable
internal fun rememberEnterState(bvid: String, liveState: VideoUiState, stageReady: Boolean): VideoUiState {
    val frozen = remember(bvid) { liveState }
    return if (stageReady || LocalRouteMinimized.current) liveState else frozen
}
