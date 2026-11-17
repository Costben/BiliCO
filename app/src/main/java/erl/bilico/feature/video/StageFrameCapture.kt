package erl.bilico.feature.video

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 抓一帧当前画面。回调传 null 表示这次没抓到，调用方退回原来的硬切；
 * 第二个参数表示这张图是不是整幅画面（而不是按控件尺寸取的缩略），是的话就不用再补抓。
 */
@Stable
internal class StageFrameSource {
    var capture: ((callback: (bitmap: Bitmap?, fullFrame: Boolean) -> Unit) -> Unit)? = null
}

internal val LocalStageFrameSource = staticCompositionLocalOf { StageFrameSource() }

/**
 * 转场期间画面由一张静止帧顶替。播放器要把 SurfaceView 藏起来，
 * 否则系统单独合成的实时画面会从替身帧底下露出来。
 */
internal val LocalStageFrozenVideo = compositionLocalOf { false }

/** 抓帧等的上限：正常一两帧就回来，超了就按原来的硬切走。 */
internal const val STAGE_FRAME_CAPTURE_TIMEOUT_MS = 250L

/** 补抓连续这么多次都没变大就认定缓冲区不会再放大，别一直卡着。 */
private const val REFINE_MAX_MISSES = 3

/**
 * 等 Surface 跟着新布局放大再补抓的上限。
 * 冻结之后布局才切到舞台尺寸，SurfaceView 的缓冲区要再晚一两帧才跟上；
 * 等不到就用第一张，宁可糊一点也不能一直卡着。
 */
internal const val REFINE_TIMEOUT_MS = 280L

/**
 * 一次转场用的替身帧。SurfaceView 的画面由系统单独合成，Compose 的旋转作用不到它，
 * 所以转场时先抓一帧画在舞台上走同一套旋转缩放，等窗口真的转过去再放回实时画面。
 */
@Stable
internal class StageFrameHolder {
    val source = StageFrameSource()

    /** null = 没在顶替，实时画面照常显示。 */
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    val frozen: Boolean get() = image != null

    /** 这一帧是不是已经是整幅画面，是的话不用再补抓。 */
    private var fullFrame = false

    /** 抓一帧当替身。没抓到就当没这回事，调用方接着走硬切。 */
    suspend fun freeze(): Boolean {
        val frame = grabFrame() ?: return false
        fullFrame = frame.second
        image = frame.first.asImageBitmap()
        return true
    }

    /**
     * 补抓一张更高分辨的替身帧。
     *
     * 进横屏全屏时第一张是竖屏槽位大小（1080×607 之类），铺到整屏会明显发糊：
     * 布局已经换成整屏尺寸、SurfaceView 的缓冲区跟着放大之后再抓一次，换掉第一张。
     * 尺寸没变大说明缓冲区还没跟着布局换，抓回来还是那一张；整片纯黑说明刚换尺寸还没画出第一帧。
     * 两种都丢掉——宁可糊一点，也不能黑。
     *
     * @return 换掉没有
     */
    suspend fun refine(timeoutMs: Long = REFINE_TIMEOUT_MS): Boolean {
        val current = image ?: return false
        // 第一张就是整幅画面（解码器按原始分辨率出图）时，缓冲区不会再变大，补抓没有意义。
        if (fullFrame) return false
        return withTimeoutOrNull(timeoutMs) {
            var misses = 0
            var refined = false
            while (!refined && misses < REFINE_MAX_MISSES) {
                refined = tryRefine(current)
                if (!refined) {
                    misses++
                    withFrameNanos { }
                }
            }
            refined
        } ?: false
    }

    private suspend fun tryRefine(current: ImageBitmap): Boolean {
        val bitmap = grabFrame()?.first ?: return false
        if (bitmap.width <= current.width && bitmap.height <= current.height) return false
        if (bitmap.isBlankFrame()) return false
        image = bitmap.asImageBitmap()
        return true
    }

    private suspend fun grabFrame(): Pair<Bitmap, Boolean>? {
        val capture = source.capture ?: return null
        return withTimeoutOrNull(STAGE_FRAME_CAPTURE_TIMEOUT_MS) {
            suspendCancellableCoroutine<Pair<Bitmap, Boolean>?> { continuation ->
                capture { frame, fullFrame ->
                    if (frame == null) {
                        if (continuation.isActive) continuation.resume(null)
                    } else if (continuation.isActive) {
                        continuation.resume(frame to fullFrame)
                    }
                }
            }
        }
    }

    fun unfreeze() {
        image = null
        fullFrame = false
    }

    /**
     * 让替身帧把这 450ms 走完，再把画面交回实时播放：
     * [animate] 跑旋转缩放，[rotate] 申请窗口朝向，[settle] 等窗口真的转过去。
     * 中途被打断（退出全屏、又转了回来）也必须放回实时画面。
     */
    suspend fun animateFrozen(
        animate: suspend () -> Unit,
        rotate: suspend () -> Unit,
        settle: suspend () -> Unit,
    ) {
        try {
            animate()
            rotate()
            settle()
        } finally {
            unfreeze()
        }
    }
}

@Composable
internal fun rememberStageFrameHolder(): StageFrameHolder = remember { StageFrameHolder() }

/** 8×8 抽样全黑：Surface 刚换尺寸时抓到的就是这个，拿它顶替会闪一下黑。 */
private fun Bitmap.isBlankFrame(): Boolean {
    val stepX = (width / 8).coerceAtLeast(1)
    val stepY = (height / 8).coerceAtLeast(1)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            if (getPixel(x, y) and 0x00FFFFFF != 0) return false
            x += stepX
        }
        y += stepY
    }
    return true
}

/** 替身帧画在播放器图层最上面：播放器自己的黑底和控件都在它下面。 */
@Composable
internal fun StageFrozenFrame(holder: StageFrameHolder) {
    val bitmap = holder.image ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}
