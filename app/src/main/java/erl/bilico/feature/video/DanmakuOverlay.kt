package erl.bilico.feature.video

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized

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
    val isTop = LocalRouteIsTop.current
    val minimized = LocalRouteMinimized.current
    val active = isTop && !minimized
    val interaction = LocalDanmakuInteraction.current
    AndroidView(
        factory = { context ->
            DanmakuSurfaceView(context).apply {
                this.player = if (isTop) player else null
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
            view.player = if (isTop) player else null
            view.timeline = timeline
            view.items = items
            view.applySettings(settings)
            view.selectedHit = interaction?.selectedHit
            view.playing = isPlaying && active
            view.visibility = if (active) View.VISIBLE else View.INVISIBLE
            if (!isTop) view.evictSpriteCache()
            interaction?.hitTest = { offset -> view.findDanmakuAt(offset.x, offset.y) }
        },
        onRelease = {
            interaction?.hitTest = null
        },
        modifier = modifier,
    )
}
