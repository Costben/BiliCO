package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import erl.bilico.data.model.DanmakuItem

/**
 * 弹幕层的宿主：只是把 [DanmakuSurfaceView] 放进播放器，播放期间的任何一帧都不经过 Compose。
 * 只有换段（items 变化）、改设置、暂停/继续才会走一次重组。
 */
@Composable
internal fun DanmakuOverlay(
    player: Player?,
    items: List<DanmakuItem>,
    isPlaying: Boolean,
    enabled: Boolean,
    opacity: Float,
    fontScale: Float,
    areaFraction: Float,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            DanmakuSurfaceView(context).apply {
                this.player = player
                this.items = items
                danmakuEnabled = enabled
                danmakuOpacity = opacity
                danmakuFontScale = fontScale
                danmakuAreaFraction = areaFraction
                playing = isPlaying
            }
        },
        update = { view ->
            view.player = player
            view.items = items
            view.danmakuEnabled = enabled
            view.danmakuOpacity = opacity
            view.danmakuFontScale = fontScale
            view.danmakuAreaFraction = areaFraction
            view.playing = isPlaying
        },
        modifier = modifier,
    )
}
