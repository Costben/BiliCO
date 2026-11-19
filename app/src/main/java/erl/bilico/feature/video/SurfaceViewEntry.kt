package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import erl.bilico.ui.LocalNavEntry

/**
 * HDR 播放挂在 SurfaceView 上：它靠在窗口上挖洞露出底下的视频层，
 * 放进离屏图层后洞只挖在图层里，画面会消失，转场期间这一页就不能整页离屏绘制。
 */
@Composable
internal fun RegisterSurfaceViewEntry(hdrVideo: Boolean) {
    val navEntry = LocalNavEntry.current
    DisposableEffect(navEntry, hdrVideo) {
        if (hdrVideo) navEntry?.hasSurfaceView = true
        onDispose { if (hdrVideo) navEntry?.hasSurfaceView = false }
    }
}
