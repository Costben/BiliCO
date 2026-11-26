package erl.bilico.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import erl.bilico.platform.PlatformContext

val LocalPlayerSurfaceAlpha = staticCompositionLocalOf<() -> Float> { { 1f } }

object PlayerStates {
    const val STATE_IDLE = 1
    const val STATE_BUFFERING = 2
    const val STATE_READY = 3
    const val STATE_ENDED = 4
}

data class VideoDimensions(
    val width: Int = 0,
    val height: Int = 0,
)

enum class VideoResizeMode {
    FIT,
    ZOOM,
    FILL,
}

interface BiliPlayerListener {
    fun onIsPlayingChanged(isPlaying: Boolean) {}
    fun onPlayIntentChanged(hasPlayIntent: Boolean) {}
    fun onPlaybackStateChanged(state: Int) {}
    fun onVideoSizeChanged(width: Int, height: Int) {}
    fun onPlayerError(message: String) {}
}

val BiliPlayer.hasPlayIntent: Boolean
    get() = playWhenReady && playbackState != PlayerStates.STATE_ENDED

@Stable
expect class BiliPlayer(context: PlatformContext) {
    val currentPosition: Long
    val duration: Long
    val bufferedPosition: Long
    val isPlaying: Boolean
    val playWhenReady: Boolean
    val playbackState: Int
    val speed: Float
    var volume: Float
    val videoSize: VideoDimensions

    fun setMedia(
        videoUrl: String,
        audioUrl: String? = null,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L,
        playWhenReady: Boolean = true,
        isLive: Boolean = false,
    )

    fun clearMediaItems()
    fun stop()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun addListener(listener: BiliPlayerListener)
    fun removeListener(listener: BiliPlayerListener)
    fun captureCurrentFrame(): ImageBitmap?
    fun release()
}

@Composable
expect fun BiliPlayerSurface(
    player: BiliPlayer,
    resizeMode: VideoResizeMode = VideoResizeMode.FIT,
    modifier: Modifier = Modifier,
)
