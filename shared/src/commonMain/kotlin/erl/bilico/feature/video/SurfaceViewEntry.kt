package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import erl.bilico.platform.platformNeedsSurfaceViewGuard
import erl.bilico.ui.LocalNavEntry

/**
 * Android HDR 播放挂在 SurfaceView 上：它靠在窗口上挖洞露出底下的视频层，
 * 放进离屏图层后洞只挖在图层里，画面会消失，转场期间与弹窗模糊需标记保护。
 * iOS 使用 AVPlayerLayer 合成，无挖洞限制。
 */
@Composable
internal fun RegisterSurfaceViewEntry(hdrVideo: Boolean) {
    if (!platformNeedsSurfaceViewGuard) return
    val navEntry = LocalNavEntry.current
    DisposableEffect(navEntry, hdrVideo) {
        if (hdrVideo) navEntry?.hasSurfaceView = true
        onDispose { if (hdrVideo) navEntry?.hasSurfaceView = false }
    }
}
