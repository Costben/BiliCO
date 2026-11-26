package erl.bilico.player

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import erl.bilico.platform.PlatformContext
import okhttp3.OkHttpClient
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

private val sharedMediaOkHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

@OptIn(UnstableApi::class)
@Stable
actual class BiliPlayer actual constructor(context: PlatformContext) {
    private val appContext = context.androidContext
    private val listeners = CopyOnWriteArrayList<BiliPlayerListener>()

    @Volatile
    internal var attachedTextureView: TextureView? = null

    @Volatile
    internal var attachedAspectLayout: AspectRatioFrameLayout? = null

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 1_500, 3_000)
                .build(),
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }

    private var _videoSize = VideoDimensions()
    internal var pixelWidthHeightRatio: Float = 1f
        private set

    private val internalListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listeners.forEach { it.onIsPlayingChanged(isPlaying) }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            listeners.forEach { it.onPlayIntentChanged(hasPlayIntent) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val mapped = mapState(playbackState)
            listeners.forEach {
                it.onPlaybackStateChanged(mapped)
                it.onPlayIntentChanged(hasPlayIntent)
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                _videoSize = VideoDimensions(videoSize.width, videoSize.height)
                pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
                attachedAspectLayout?.setAspectRatio(
                    (videoSize.width.toFloat() * pixelWidthHeightRatio) / videoSize.height.toFloat(),
                )
                listeners.forEach { it.onVideoSizeChanged(videoSize.width, videoSize.height) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            listeners.forEach { it.onPlayerError(describeExoError(error)) }
        }
    }

    init {
        exoPlayer.addListener(internalListener)
    }

    actual val currentPosition: Long
        get() = exoPlayer.currentPosition.coerceAtLeast(0L)

    actual val duration: Long
        get() = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it >= 0L } ?: 0L

    actual val bufferedPosition: Long
        get() = exoPlayer.bufferedPosition.coerceAtLeast(0L)

    actual val isPlaying: Boolean
        get() = exoPlayer.isPlaying

    actual val playWhenReady: Boolean
        get() = exoPlayer.playWhenReady

    actual val playbackState: Int
        get() = mapState(exoPlayer.playbackState)

    actual val speed: Float
        get() = exoPlayer.playbackParameters.speed

    actual var volume: Float
        get() = exoPlayer.volume
        set(value) {
            exoPlayer.volume = value.coerceIn(0f, 1f)
        }

    actual val videoSize: VideoDimensions
        get() = _videoSize

    actual fun setMedia(
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>,
        startPositionMs: Long,
        playWhenReady: Boolean,
        isLive: Boolean,
    ) {
        val httpFactory = OkHttpDataSource.Factory(sharedMediaOkHttpClient).apply {
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpFactory)

        val videoItem = MediaItem.Builder()
            .setUri(Uri.parse(videoUrl))
            .setMediaId(videoUrl)
            .build()

        val isHls = videoUrl.contains(".m3u8", ignoreCase = true)
        val mediaSource = if (isHls) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(videoItem)
        } else {
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(videoItem)
            if (audioUrl.isNullOrBlank()) {
                videoSource
            } else {
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(audioUrl)))
                MergingMediaSource(videoSource, audioSource)
            }
        }

        if (isLive) {
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.seekToDefaultPosition()
        } else {
            exoPlayer.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
        }
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()
    }

    actual fun clearMediaItems() {
        exoPlayer.clearMediaItems()
    }

    actual fun stop() {
        exoPlayer.stop()
    }

    actual fun play() {
        exoPlayer.play()
    }

    actual fun pause() {
        exoPlayer.pause()
    }

    actual fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
    }

    actual fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))
    }

    actual fun addListener(listener: BiliPlayerListener) {
        listeners.addIfAbsent(listener)
    }

    actual fun removeListener(listener: BiliPlayerListener) {
        listeners.remove(listener)
    }

    actual fun captureCurrentFrame(): ImageBitmap? {
        val tv = attachedTextureView ?: return null
        if (!tv.isAvailable || tv.width <= 0 || tv.height <= 0) return null
        return runCatching { tv.bitmap?.asImageBitmap() }.getOrNull()
    }

    actual fun release() {
        attachedTextureView?.let { exoPlayer.clearVideoTextureView(it) }
        attachedTextureView = null
        attachedAspectLayout = null
        exoPlayer.removeListener(internalListener)
        listeners.clear()
        exoPlayer.release()
    }

    private fun mapState(state: Int): Int = when (state) {
        Player.STATE_BUFFERING -> PlayerStates.STATE_BUFFERING
        Player.STATE_READY -> PlayerStates.STATE_READY
        Player.STATE_ENDED -> PlayerStates.STATE_ENDED
        else -> PlayerStates.STATE_IDLE
    }

    private fun describeExoError(error: PlaybackException): String {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                val host = cause.dataSpec.uri.host.orEmpty()
                val suffix = if (host.isBlank()) "" else "（$host）"
                return "播放失败：服务器返回 ${cause.responseCode}$suffix"
            }
            if (cause is HttpDataSource.HttpDataSourceException) {
                val host = cause.dataSpec.uri.host.orEmpty()
                val suffix = if (host.isBlank()) "" else "（$host）"
                val reason = when (val inner = cause.cause) {
                    is ConnectException -> "连接被拒绝"
                    is UnknownHostException -> "域名解析失败"
                    is SocketTimeoutException -> "连接超时"
                    null -> "读取失败"
                    else -> inner.javaClass.simpleName
                }
                return "播放失败：$reason$suffix"
            }
            cause = cause.cause
        }
        return "播放失败：${error.errorCodeName}"
    }
}

@OptIn(UnstableApi::class)
@Composable
actual fun BiliPlayerSurface(
    player: BiliPlayer,
    resizeMode: VideoResizeMode,
    modifier: Modifier,
) {
    val alphaProvider = LocalPlayerSurfaceAlpha.current
    DisposableEffect(player) {
        onDispose {
            player.attachedTextureView?.let { player.exoPlayer.clearVideoTextureView(it) }
            player.attachedTextureView = null
            player.attachedAspectLayout = null
        }
    }
    AndroidView(
        factory = { ctx ->
            val container = FrameLayout(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
            }
            val aspectLayout = AspectRatioFrameLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                )
            }
            val textureView = TextureView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    requestedFrameRate = 120f
                }
            }
            aspectLayout.addView(textureView)
            container.addView(aspectLayout)
            container.tag = aspectLayout to textureView
            player.attachedAspectLayout = aspectLayout
            player.attachedTextureView = textureView
            val dim = player.videoSize
            if (dim.width > 0 && dim.height > 0) {
                aspectLayout.setAspectRatio((dim.width.toFloat() * player.pixelWidthHeightRatio) / dim.height.toFloat())
            }
            player.exoPlayer.setVideoTextureView(textureView)
            container
        },
        update = { container ->
            @Suppress("UNCHECKED_CAST")
            val (aspectLayout, textureView) = container.tag as Pair<AspectRatioFrameLayout, TextureView>
            aspectLayout.resizeMode = when (resizeMode) {
                VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            val dim = player.videoSize
            if (dim.width > 0 && dim.height > 0) {
                aspectLayout.setAspectRatio((dim.width.toFloat() * player.pixelWidthHeightRatio) / dim.height.toFloat())
            }
            if (player.attachedTextureView !== textureView) {
                player.attachedAspectLayout = aspectLayout
                player.attachedTextureView = textureView
                player.exoPlayer.setVideoTextureView(textureView)
            }
            textureView.alpha = alphaProvider().coerceIn(0f, 1f)
        },
        onRelease = { container ->
            @Suppress("UNCHECKED_CAST")
            val (_, textureView) = container.tag as Pair<AspectRatioFrameLayout, TextureView>
            if (player.attachedTextureView === textureView) {
                player.exoPlayer.clearVideoTextureView(textureView)
                player.attachedTextureView = null
                player.attachedAspectLayout = null
            }
        },
        modifier = modifier,
    )
}
