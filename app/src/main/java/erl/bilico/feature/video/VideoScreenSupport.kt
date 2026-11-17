package erl.bilico.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.OrientationEventListener
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.di.AppContainer
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    val isTop = LocalRouteIsTop.current
    if (mode == 0 || !isTop) return 0
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

@Stable
internal class PlayerFullscreenController(
    val fullscreen: Boolean,
    val portraitStage: Boolean,
    val deviceSide: Int,
    val applyFullscreen: (Boolean) -> Unit,
)

/**
 * 统一管理播放页全屏与重力自动横屏：
 * 只有已确认宽高比 >= 1 的横屏视频才响应手机横握自动进/退全屏；
 * 未知比例（<= 0）或竖屏视频（< 1）一律不触发自动横屏，保持在竖屏内。
 */
@Composable
internal fun rememberPlayerFullscreenController(
    videoAspect: Float,
    autoRotateMode: Int,
    inPipMode: Boolean,
): PlayerFullscreenController {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val windowLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val portraitStage = isPortraitVideoStage(videoAspect, fullscreen && windowLandscape)
    val allowAutoLandscape = videoAspect >= 1f
    val deviceSide = rememberDeviceOrientationSide(autoRotateMode)
    // 用户主动退出全屏、而手机还横着时，先锁竖屏，等手机真的竖过来再交还给传感器。
    var manualFullscreenExit by rememberSaveable { mutableStateOf(false) }
    var lastManualActionTime by remember { mutableLongStateOf(0L) }
    var manualEnterAwaitingLandscape by remember { mutableStateOf(false) }
    val applyFullscreen: (Boolean) -> Unit = { value ->
        lastManualActionTime = SystemClock.uptimeMillis()
        manualFullscreenExit = !value
        manualEnterAwaitingLandscape = value && allowAutoLandscape && deviceSide == 0
        fullscreen = value
    }

    var wasLandscape by remember { mutableStateOf(deviceSide != 0) }
    LaunchedEffect(
        deviceSide,
        manualFullscreenExit,
        manualEnterAwaitingLandscape,
        allowAutoLandscape,
        inPipMode,
    ) {
        if (inPipMode) return@LaunchedEffect
        if (!allowAutoLandscape) {
            manualEnterAwaitingLandscape = false
            wasLandscape = deviceSide != 0
            return@LaunchedEffect
        }
        val now = SystemClock.uptimeMillis()
        val inCooldown = lastManualActionTime > 0L &&
            now - lastManualActionTime < MANUAL_FULLSCREEN_COOLDOWN_MS
        if (deviceSide != 0) {
            if (manualEnterAwaitingLandscape) manualEnterAwaitingLandscape = false
            if (manualFullscreenExit && !inCooldown) manualFullscreenExit = false
            if (!manualFullscreenExit && !inCooldown && !fullscreen) fullscreen = true
        } else {
            if (manualFullscreenExit) manualFullscreenExit = false
            if (wasLandscape && !inCooldown && !manualEnterAwaitingLandscape && fullscreen) {
                fullscreen = false
            }
        }
        wasLandscape = deviceSide != 0
    }

    return PlayerFullscreenController(
        fullscreen = fullscreen,
        portraitStage = portraitStage,
        deviceSide = deviceSide,
        applyFullscreen = applyFullscreen,
    )
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
    chapterDialogVisible: Boolean = false,
    chapters: List<VideoChapter> = emptyList(),
    positionMs: Long = 0L,
    onSelectChapter: (VideoChapter) -> Unit = {},
    onDismissChapter: () -> Unit = {},
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

    if (chapterDialogVisible && chapters.isNotEmpty()) {
        ChapterDialog(
            chapters = chapters,
            positionMs = positionMs,
            onSelectChapter = onSelectChapter,
            onDismiss = onDismissChapter,
            inlineInStage = inlineInStage,
        )
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


@Composable
internal fun BindPlayerEvents(
    player: Player,
    viewModel: VideoViewModel,
    container: AppContainer,
    onPlayingChanged: (Boolean) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onError: (PlaybackException) -> Unit,
    onVideoAspectChanged: (Float) -> Unit = {},
    onPlaybackEnded: (() -> Unit)? = null,
) {
    // 监听器只注册一次，回调要用最新值，否则换线时会读到首次组合时的空候选表。
    val currentOnPlaying by rememberUpdatedState(onPlayingChanged)
    val currentOnBuffering by rememberUpdatedState(onBufferingChanged)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnAspect by rememberUpdatedState(onVideoAspectChanged)
    val currentOnEnded by rememberUpdatedState(onPlaybackEnded)
    val currentViewModel by rememberUpdatedState(viewModel)

    DisposableEffect(player) {
        currentOnPlaying(player.isPlaying)
        currentOnBuffering(player.playbackState == Player.STATE_BUFFERING)
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
                    currentViewModel.markPlaybackStarted()
                } else if (playbackState == Player.STATE_ENDED) {
                    currentViewModel.flushHeartbeat(player.duration.coerceAtLeast(0L) / 1000, container.scope)
                    val ended = currentOnEnded
                    if (ended != null) ended() else currentViewModel.playNextPart()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentOnError(error)
            }
        }
        player.addListener(listener)
        onDispose {
            currentViewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
            player.removeListener(listener)
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
                .clip(io.github.suqi8.coui.kmp.squircle.SquircleShape(8.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = COUITheme.textStyles.footnote1,
            color = Color.White,
        )
    }
}

/** 根据自动联播模式处理视频播放结束后的动作：停在末尾、单曲循环、列表循环或自动连播下一条。 */
internal fun handleVideoPlaybackEnded(
    mode: PlaybackEndMode,
    player: ExoPlayer,
    viewModel: VideoViewModel,
    nav: NavState,
    state: VideoUiState,
) {
    // 播完了就把这条的续播点清掉：留着的话再进来会直接在片尾。
    player.currentMediaItem?.mediaId?.let(PlaybackPositions::forget)
    when (mode) {
        PlaybackEndMode.OFF -> Unit
        PlaybackEndMode.SINGLE_LOOP -> {
            player.seekTo(0L)
            player.play()
        }
        PlaybackEndMode.LIST_LOOP -> {
            if (!viewModel.playNextPart(loop = true)) {
                val queued = nav.playQueue.next(loop = true)
                if (queued != null) {
                    nav.replaceTop(Route.Video(queued.video.bvid, queued.startSec))
                } else {
                    player.seekTo(0L)
                    player.play()
                }
            }
        }
        PlaybackEndMode.AUTO_NEXT -> {
            if (!viewModel.playNextPart()) {
                val queued = nav.playQueue.next()
                if (queued != null) {
                    nav.replaceTop(Route.Video(queued.video.bvid, queued.startSec))
                } else {
                    state.related.firstOrNull()?.let { next ->
                        nav.replaceTop(Route.Video(next.bvid, 0L))
                    }
                }
            }
        }
    }
}
