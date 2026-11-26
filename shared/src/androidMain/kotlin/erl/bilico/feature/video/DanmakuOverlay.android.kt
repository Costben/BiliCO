package erl.bilico.feature.video

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.player.BiliPlayer
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized

/**
 * Android 弹幕层宿主：把 [DanmakuSurfaceView] 放进播放器，播放期间由 Choreographer VSync 直接驱动亚像素渲染，
 * 播放期间的任何一帧都不经过 Compose 重组。
 */
@Composable
internal actual fun DanmakuOverlay(
    player: BiliPlayer?,
    items: List<DanmakuItem>,
    isPlaying: Boolean,
    settings: DanmakuSettings,
    modifier: Modifier,
    timeline: DanmakuTimeline?,
) {
    val isTop = LocalRouteIsTop.current
    val minimized = LocalRouteMinimized.current
    val active = isTop && !minimized
    val interaction = LocalDanmakuInteraction.current
    val exoPlayer = if (isTop) player?.exoPlayer else null

    AndroidView(
        factory = { context ->
            DanmakuSurfaceView(context).apply {
                this.player = exoPlayer
                this.timeline = timeline
                this.items = items
                applySettings(settings)
                selectedHit = interaction?.selectedHit
                playing = isPlaying && active
                visibility = if (active) View.VISIBLE else View.INVISIBLE
                interaction?.hitTest = { offset -> findDanmakuAt(offset.x, offset.y) }
            }
        },
        update = { view ->
            view.player = exoPlayer
            view.timeline = timeline
            view.items = items
            view.applySettings(settings)
            view.selectedHit = interaction?.selectedHit
            view.playing = isPlaying && active
            view.visibility = if (active) View.VISIBLE else View.INVISIBLE
            if (!isTop) view.evictSpriteCache()
            interaction?.hitTest = { offset -> view.findDanmakuAt(offset.x, offset.y) }
        },
        onRelease = { view ->
            view.player = null
            interaction?.hitTest = null
        },
        modifier = modifier,
    )
}
