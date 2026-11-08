package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.settings.DanmakuSettings

/**
 * 弹幕层的宿主：把 [DanmakuSurfaceView] 放进播放器，播放期间由 Choreographer VSync 直接驱动亚像素渲染，
 * 播放期间的任何一帧都不经过 Compose 重组。
 * 点播传 null 由播放器位置驱动；直播传 [DanmakuTimeline]，由它自己维护时间轴。
 */
@Composable
internal fun DanmakuOverlay(
    player: Player?,
    items: List<DanmakuItem>,
    isPlaying: Boolean,
    settings: DanmakuSettings,
    modifier: Modifier = Modifier,
    timeline: DanmakuTimeline? = null,
) {
    AndroidView(
        factory = { context ->
            DanmakuSurfaceView(context).apply {
                this.player = player
                this.timeline = timeline
                this.items = items
                applySettings(settings)
                playing = isPlaying
            }
        },
        update = { view ->
            view.player = player
            view.timeline = timeline
            view.items = items
            view.applySettings(settings)
            view.playing = isPlaying
        },
        modifier = modifier,
    )
}
