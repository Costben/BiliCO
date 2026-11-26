package erl.bilico.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectZero
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSNull
import platform.Foundation.NSNumber
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
private class PlayerContainerView(
    private val playerLayer: AVPlayerLayer,
) : UIView(frame = CGRectZero.readValue()) {
    private var lastWidth = -1.0
    private var lastHeight = -1.0

    init {
        backgroundColor = UIColor.blackColor
        clipsToBounds = true
        // 手势 100% 由上层 Compose PlayerGestureLayer 处理，禁用原生 UIKit 命中测试，
        // 避免手指滑到视频区域时触发 UIKitView 触摸仲裁延迟与 CoreAnimation 同步闪烁。
        userInteractionEnabled = false
        playerLayer.contentsScale = UIScreen.mainScreen.scale
        val noAction = NSNull.`null`()
        playerLayer.actions = mapOf(
            "bounds" to noAction,
            "position" to noAction,
            "frame" to noAction,
            "opacity" to noAction,
            "hidden" to noAction,
        )
        layer.addSublayer(playerLayer)
    }

    /**
     * 只有尺寸真的变了才动 [playerLayer]。
     *
     * 之前每次布局都无条件写一遍 frame，而且 `BiliPlayerSurface` 的 update 每帧都在
     * `setNeedsLayout()`，于是系统音量 HUD、转场这类 UIKit 动画窗口里，播放图层会一遍遍
     * 重新起隐式动画，画面看着一闪一闪。尺寸没变就直接返回，别的什么也不做。
     */
    override fun layoutSubviews() {
        super.layoutSubviews()
        val w = CGRectGetWidth(bounds)
        val h = CGRectGetHeight(bounds)
        if (w == lastWidth && h == lastHeight) return
        lastWidth = w
        lastHeight = h
        val animDuration = UIView.inheritedAnimationDuration
        CATransaction.begin()
        if (animDuration > 0.0) {
            CATransaction.setAnimationDuration(animDuration)
        } else {
            CATransaction.setDisableActions(true)
        }
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun BiliPlayerSurface(
    player: BiliPlayer,
    resizeMode: VideoResizeMode,
    modifier: Modifier,
) {
    val playerLayer = remember(player) {
        AVPlayerLayer.playerLayerWithPlayer(player.avPlayer)
    }
    val alphaProvider = LocalPlayerSurfaceAlpha.current
    UIKitView(
        factory = {
            PlayerContainerView(playerLayer).apply {
                playerLayer.videoGravity = when (resizeMode) {
                    VideoResizeMode.FIT -> AVLayerVideoGravityResizeAspect
                    VideoResizeMode.ZOOM -> AVLayerVideoGravityResizeAspectFill
                    VideoResizeMode.FILL -> AVLayerVideoGravityResize
                }
                val nativeAlpha = alphaProvider().coerceIn(0f, 1f)
                alpha = nativeAlpha.toDouble()
                hidden = nativeAlpha <= 0.01f
            }
        },
        update = { view ->
            val gravity = when (resizeMode) {
                VideoResizeMode.FIT -> AVLayerVideoGravityResizeAspect
                VideoResizeMode.ZOOM -> AVLayerVideoGravityResizeAspectFill
                VideoResizeMode.FILL -> AVLayerVideoGravityResize
            }
            if (playerLayer.videoGravity != gravity) playerLayer.videoGravity = gravity
            val nativeAlpha = alphaProvider().coerceIn(0f, 1f).toDouble()
            if (view.alpha != nativeAlpha) view.alpha = nativeAlpha
            val hide = nativeAlpha <= 0.01
            if (view.hidden != hide) view.hidden = hide
        },
        properties = UIKitInteropProperties(
            interactionMode = null,
            isNativeAccessibilityEnabled = false,
        ),
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
internal fun createBiliPlayerVideoOutput(): AVPlayerItemVideoOutput {
    val attrs: Map<Any?, *> = mapOf(
        kCVPixelBufferPixelFormatTypeKey to NSNumber(unsignedInt = kCVPixelFormatType_32BGRA),
    )
    return AVPlayerItemVideoOutput(pixelBufferAttributes = attrs)
}

@OptIn(ExperimentalForeignApi::class)
internal fun captureBiliPlayerFrame(
    avPlayer: AVPlayer,
    videoOutput: AVPlayerItemVideoOutput?,
): ImageBitmap? {
    val output = videoOutput ?: return null
    val item = avPlayer.currentItem ?: return null
    if (item.status != AVPlayerItemStatusReadyToPlay) return null
    val time = avPlayer.currentTime()
    val pixelBuffer = output.copyPixelBufferForItemTime(time, itemTimeForDisplay = null) ?: return null
    try {
        if (CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly) != 0) return null
        try {
            val srcW = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val srcH = CVPixelBufferGetHeight(pixelBuffer).toInt()
            val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
            val baseAddr = CVPixelBufferGetBaseAddress(pixelBuffer) ?: return null
            if (srcW <= 0 || srcH <= 0 || bytesPerRow < srcW * 4) return null

            val maxDim = 240
            val scale = maxOf(srcW, srcH).toFloat() / maxDim.toFloat()
            val dstW = if (scale > 1f) (srcW / scale).toInt().coerceAtLeast(1) else srcW
            val dstH = if (scale > 1f) (srcH / scale).toInt().coerceAtLeast(1) else srcH
            val outBytes = ByteArray(dstW * dstH * 4)
            val srcPtr = baseAddr.reinterpret<ByteVar>()

            var dstIdx = 0
            for (y in 0 until dstH) {
                val sy = (y.toLong() * srcH / dstH).toInt().coerceIn(0, srcH - 1)
                val rowBase = sy * bytesPerRow
                for (x in 0 until dstW) {
                    val sx = (x.toLong() * srcW / dstW).toInt().coerceIn(0, srcW - 1)
                    val off = rowBase + sx * 4
                    outBytes[dstIdx] = srcPtr[off]
                    outBytes[dstIdx + 1] = srcPtr[off + 1]
                    outBytes[dstIdx + 2] = srcPtr[off + 2]
                    outBytes[dstIdx + 3] = 0xFF.toByte()
                    dstIdx += 4
                }
            }
            val info = ImageInfo(
                width = dstW,
                height = dstH,
                colorType = ColorType.BGRA_8888,
                alphaType = ColorAlphaType.OPAQUE,
            )
            return Image.makeRaster(info, outBytes, dstW * 4).toComposeImageBitmap()
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        }
    } finally {
        CVPixelBufferRelease(pixelBuffer)
    }
}
