package erl.bilico.feature.video

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.runtime.MutableState
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView

/**
 * 从播放器窗口里抓一帧静止画面，供 SurfaceView 的转场顶替用。
 *
 * 关键在于源尺寸：Surface 的缓冲区不一定等于控件尺寸。解码器按视频原始分辨率出图时，
 * 缓冲区是 3840×2160 这类整幅画面，SurfaceView 只是把它缩放铺到控件上；
 * 这时按控件尺寸取，拿到的只是缓冲区左上角的一块，替身帧铺满整屏就成了「放大的一个角」。
 * 所以视频比控件大时先按视频分辨率试，失败再退回控件尺寸。
 *
 * @param bufferIsVideoSized 上一次试出来的结果：true 缓冲区是整幅画面，false 就是控件尺寸，
 *   null 还没试过。缓冲区尺寸整个播放期间不变，试一次就够，不必每帧都浪费一次拷贝。
 */
internal fun capturePlayerFrame(
    playerView: PlayerView?,
    videoSize: VideoSize,
    bufferIsVideoSized: MutableState<Boolean?>,
    onResult: (bitmap: Bitmap?, fullFrame: Boolean) -> Unit,
) {
    val surfaceView = playerView?.videoSurfaceView as? SurfaceView
    val surface = surfaceView?.holder?.surface
    val viewWidth = surfaceView?.width ?: 0
    val viewHeight = surfaceView?.height ?: 0
    if (surfaceView == null || surface == null || !surface.isValid ||
        viewWidth <= 0 || viewHeight <= 0
    ) {
        onResult(null, false)
        return
    }
    val probeVideoSize = bufferIsVideoSized.value != false &&
        videoSize.width > viewWidth && videoSize.height > viewHeight
    if (probeVideoSize) {
        copySurface(surface, videoSize.width, videoSize.height) { bitmap ->
            if (bitmap != null) {
                bufferIsVideoSized.value = true
                onResult(bitmap, true)
            } else {
                bufferIsVideoSized.value = false
                copySurface(surface, viewWidth, viewHeight) { onResult(it, false) }
            }
        }
    } else {
        copySurface(surface, viewWidth, viewHeight) { onResult(it, false) }
    }
}

/** 拷贝缓冲区里 (0,0)-(width,height) 这一块；超出缓冲区或拷贝失败回调 null。 */
private fun copySurface(surface: Surface, width: Int, height: Int, onDone: (Bitmap?) -> Unit) {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    runCatching {
        PixelCopy.request(
            surface,
            Rect(0, 0, width, height),
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    onDone(bitmap)
                } else {
                    bitmap.recycle()
                    onDone(null)
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }.onFailure {
        bitmap.recycle()
        onDone(null)
    }
}
