package erl.bilico.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.OrientationEventListener
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.di.AppContainer
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 手动/手势切换全屏后的重力感应冷却保护时长 (毫秒)
 */
internal const val MANUAL_FULLSCREEN_COOLDOWN_MS = 3000L

internal fun VideoUiState.qualityLabel(): String? {
    val source = source ?: return null
    val active = source.qualities.firstOrNull { it.quality == activeQuality }
    return active?.label?.takeIf { it.isNotBlank() } ?: "${activeQuality}P"
}

internal val DETAIL_PAGES = listOf("info", "comment")

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 物理横竖屏方向，带 500ms 持续确认防抖、双门限迟滞与前后台生命周期启停：
 * - `0`：竖屏
 * - `+1`：逆时针横屏（听筒在左，约 270°，对应自绘顺时针旋转 `+90°`）
 * - `-1`：顺时针反向横屏（听筒在右，约 90°，对应自绘逆时针旋转 `-90°`）
 */
@Composable
internal fun rememberDeviceOrientationSide(mode: Int): Int {
    if (mode == 0) return 0
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var side by remember { mutableIntStateOf(0) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var pendingRunnable by remember { mutableStateOf<Runnable?>(null) }
    var pendingTarget by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(context, lifecycleOwner, mode) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val delta = when (mode) {
                    1 -> 30 // 迟钝防误触：60..120 / 240..300
                    2 -> 38 // 标准：52..128 / 232..308
                    else -> 45 // 灵敏：45..135 / 225..315
                }
                val inStandardLandscape = orientation in (270 - delta)..(270 + delta)
                val inReverseLandscape = orientation in (90 - delta)..(90 + delta)
                val inPortrait = orientation in 0..(90 - delta - 10) ||
                    orientation in (360 - (90 - delta - 10))..359 ||
                    orientation in (90 + delta + 10)..(270 - delta - 10)

                val target: Int? = when {
                    inStandardLandscape -> 1
                    inReverseLandscape -> -1
                    inPortrait -> 0
                    else -> null
                }

                if (target == null || target == side) {
                    pendingRunnable?.let { handler.removeCallbacks(it) }
                    pendingRunnable = null
                    pendingTarget = null
                } else if (pendingTarget != target) {
                    pendingRunnable?.let { handler.removeCallbacks(it) }
                    pendingTarget = target
                    val run = Runnable {
                        side = target
                        pendingRunnable = null
                        pendingTarget = null
                    }
                    pendingRunnable = run
                    handler.postDelayed(run, 500L)
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (listener.canDetectOrientation()) listener.enable()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    pendingRunnable?.let { handler.removeCallbacks(it) }
                    pendingRunnable = null
                    pendingTarget = null
                    listener.disable()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            listener.canDetectOrientation()
        ) {
            listener.enable()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pendingRunnable?.let { handler.removeCallbacks(it) }
            listener.disable()
        }
    }
    return side
}

internal fun shareVideo(context: Context, title: String, bvid: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title\nhttps://www.bilibili.com/video/$bvid")
    }
    context.startActivity(Intent.createChooser(intent, "分享视频"))
}

@Composable
internal fun VideoScreenDialogs(
    speedDialogVisible: Boolean,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismissSpeed: () -> Unit,
    subtitleDialogVisible: Boolean,
    subtitleController: SubtitleController,
    onDismissSubtitle: () -> Unit,
    coinDialogVisible: Boolean,
    coined: Int,
    onSelectCoin: (Int) -> Unit,
    onDismissCoin: () -> Unit,
    favoriteFolders: List<FavoriteFolder>?,
    onSelectFavorite: (Long) -> Unit,
    onDismissFavorite: () -> Unit,
    qualityDialogVisible: Boolean,
    state: VideoUiState,
    onSelectQuality: (Int) -> Unit,
    onDismissQuality: () -> Unit,
    danmakuSettingsDialogVisible: Boolean = false,
    danmakuSettings: DanmakuSettings? = null,
    settingsRepository: SettingsRepository? = null,
    onDismissDanmakuSettings: () -> Unit = {},
    inlineInStage: Boolean = false,
) {
    if (speedDialogVisible) {
        SpeedDialog(
            active = speed,
            onSelect = onSpeedChange,
            onDismiss = onDismissSpeed,
            inlineInStage = inlineInStage,
        )
    }

    if (subtitleDialogVisible) {
        SubtitleDialog(
            tracks = subtitleController.tracks,
            selectedKey = subtitleController.selectedTrackKey,
            onSelect = { key ->
                subtitleController.selectTrack(key)
                onDismissSubtitle()
            },
            onDismiss = onDismissSubtitle,
            inlineInStage = inlineInStage,
        )
    }

    if (danmakuSettingsDialogVisible && danmakuSettings != null && settingsRepository != null) {
        DanmakuSettingsDialog(
            settings = danmakuSettings,
            repository = settingsRepository,
            onDismiss = onDismissDanmakuSettings,
            inlineInStage = inlineInStage,
        )
    }

    if (!inlineInStage && coinDialogVisible) {
        CoinDialog(
            remaining = (2 - coined).coerceIn(1, 2),
            onSelect = onSelectCoin,
            onDismiss = onDismissCoin,
        )
    }

    if (!inlineInStage) {
        favoriteFolders?.let { folders ->
            FavoriteFolderDialog(
                folders = folders,
                onSelect = onSelectFavorite,
                onDismiss = onDismissFavorite,
            )
        }
    }

    val source = state.source
    if (qualityDialogVisible && source != null) {
        QualityDialog(
            qualities = source.qualities,
            activeQuality = state.activeQuality,
            onSelect = onSelectQuality,
            onDismiss = onDismissQuality,
            inlineInStage = inlineInStage,
        )
    }
}

/** 一次播放尝试：视频轨 + 可选的音频轨。换线时整对一起换，避免音画来自不同线路。 */
internal data class PlaybackAttempt(
    val videoUrl: String,
    val audioUrl: String?,
)

/**
 * 把 [PlaybackSource] 里排好序的候选地址摊成尝试表。
 * 视频候选多于音频时复用最后一条音频，反之亦然。
 */
internal fun PlaybackSource.streamAttempts(): List<PlaybackAttempt> {
    val videos = (listOf(videoUrl) + videoBackupUrls).filter { it.isNotBlank() }
    val audios = (listOfNotNull(audioUrl) + audioBackupUrls).filter { it.isNotBlank() }
    if (videos.isEmpty()) return emptyList()
    val count = maxOf(videos.size, audios.size)
    return List(count) { index ->
        PlaybackAttempt(
            videoUrl = videos.getOrElse(index) { videos.last() },
            audioUrl = audios.getOrNull(index) ?: audios.lastOrNull(),
        )
    }
}

internal fun mediaSourceFor(
    factory: OkHttpDataSource.Factory,
    attempt: PlaybackAttempt,
): MediaSource {
    val video = ProgressiveMediaSource.Factory(factory)
        .createMediaSource(MediaItem.fromUri(attempt.videoUrl))
    val audioUrl = attempt.audioUrl
    if (audioUrl.isNullOrBlank()) return video
    return MergingMediaSource(
        video,
        ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(audioUrl)),
    )
}

internal fun ExoPlayer.loadAttempt(
    factory: OkHttpDataSource.Factory,
    attempt: PlaybackAttempt,
    speed: Float,
    seekMs: Long,
) {
    setMediaSource(mediaSourceFor(factory, attempt))
    setPlaybackSpeed(speed)
    prepare()
    if (seekMs > 0L) seekTo(seekMs)
    playWhenReady = true
}

/**
 * 播放失败时按候选表就地换线。PCDN 节点会拒绝连接或迟迟不响应，
 * 换一条常规 CDN 通常立刻恢复，不用重新请求 playurl。
 */
internal class PlaybackAttempts(
    private val player: ExoPlayer,
    private val speed: () -> Float,
) {
    private var attempts: List<PlaybackAttempt> = emptyList()
    private var factory: OkHttpDataSource.Factory? = null
    private var index = 0

    val count: Int get() = attempts.size

    fun start(source: PlaybackSource, factory: OkHttpDataSource.Factory, seekMs: Long) {
        this.factory = factory
        attempts = source.streamAttempts()
        index = 0
        val first = attempts.firstOrNull() ?: return
        player.loadAttempt(factory, first, speed(), seekMs)
    }

    /** 还有下一条可用线路就切过去，返回是否切换成功。 */
    fun next(): Boolean {
        val factory = factory ?: return false
        val next = index + 1
        if (next >= attempts.size) return false
        index = next
        player.loadAttempt(factory, attempts[next], speed(), player.currentPosition)
        return true
    }
}

/** 把 ExoPlayer 的「Source error」翻成能定位问题的原因。 */
internal fun describePlaybackError(error: PlaybackException): String {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            val host = cause.dataSpec.uri.host.orEmpty()
            return "播放失败：服务器返回 ${cause.responseCode}" + hostSuffix(host)
        }
        if (cause is HttpDataSource.HttpDataSourceException) {
            val host = cause.dataSpec.uri.host.orEmpty()
            val reason = when (val inner = cause.cause) {
                is ConnectException -> "连接被拒绝"
                is UnknownHostException -> "域名解析失败"
                is SocketTimeoutException -> "连接超时"
                null -> "读取失败"
                else -> inner.javaClass.simpleName
            }
            return "播放失败：$reason" + hostSuffix(host)
        }
        cause = cause.cause
    }
    return "播放失败：${error.errorCodeName}"
}

private fun hostSuffix(host: String): String = if (host.isBlank()) "" else "（$host）"

@Composable
internal fun BindPlayerEvents(
    player: Player,
    viewModel: VideoViewModel,
    container: AppContainer,
    onPlayingChanged: (Boolean) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onError: (PlaybackException) -> Unit,
    onVideoAspectChanged: (Float) -> Unit = {},
) {
    // 监听器只注册一次，回调要用最新值，否则换线时会读到首次组合时的空候选表。
    val currentOnPlaying by rememberUpdatedState(onPlayingChanged)
    val currentOnBuffering by rememberUpdatedState(onBufferingChanged)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnAspect by rememberUpdatedState(onVideoAspectChanged)

    DisposableEffect(player) {
        val initialSize = player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            currentOnAspect(
                (initialSize.width * initialSize.pixelWidthHeightRatio) / initialSize.height,
            )
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                currentOnPlaying(playing)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    currentOnAspect(
                        (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height,
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                currentOnBuffering(playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    viewModel.markPlaybackStarted()
                } else if (playbackState == Player.STATE_ENDED) {
                    viewModel.flushHeartbeat(player.duration.coerceAtLeast(0L) / 1000, container.scope)
                    viewModel.playNextPart()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentOnError(error)
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
            player.removeListener(listener)
            player.release()
        }
    }
}

@Composable
internal fun BoxScope.VideoMessageToast(
    message: String?,
    inPipMode: Boolean,
    inFullscreen: Boolean = false,
) {
    if (message != null && !inPipMode) {
        Text(
            text = message,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (inFullscreen) Modifier else Modifier.navigationBarsPadding())
                .padding(bottom = if (inFullscreen) 64.dp else 96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = COUITheme.textStyles.footnote1,
            color = Color.White,
        )
    }
}
