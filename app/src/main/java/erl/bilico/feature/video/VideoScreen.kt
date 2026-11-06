package erl.bilico.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import erl.bilico.di.AppContainer
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.feature.comment.CommentTab
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 视频页：16:9 播放器 + 简介/评论两个 tab。
 * 播放器随页面创建与销毁，全屏只切换方向并隐藏系统栏。
 */
@Composable
fun VideoScreen(
    container: AppContainer,
    nav: NavState,
    bvid: String,
    startPositionMs: Long = 0L,
) {
    val viewModel: VideoViewModel = viewModel(key = "video-$bvid-$startPositionMs") {
        VideoViewModel(
            repository = container.video,
            heartbeat = container.heartbeat,
            interactions = container.interactions,
            session = container.session,
            settings = container.settings,
            bvid = bvid,
            startPositionMs = startPositionMs,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuOpacity by container.settings.danmakuOpacity.collectAsStateWithLifecycle()
    val danmakuFontScale by container.settings.danmakuFontScale.collectAsStateWithLifecycle()
    val danmakuArea by container.settings.danmakuArea.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = remember { ExoPlayer.Builder(context).build() }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }
    var coinDialogVisible by remember { mutableStateOf(false) }
    var favoriteFolders by remember { mutableStateOf<List<FavoriteFolder>?>(null) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    viewModel.markPlaybackStarted()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.message ?: "播放失败"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(state.selectedCid) {
        val cid = state.selectedCid
        if (cid > 0L) {
            danmakuController.attach(cid)
            danmakuController.onPosition(player.currentPosition)
        }
    }

    LaunchedEffect(state.source) {
        val source = state.source ?: return@LaunchedEffect
        playerError = null
        val headers = container.client.mediaRequestHeaders(bvid)
        val dataSourceFactory = OkHttpDataSource.Factory(container.client.mediaClient())
            .setDefaultRequestProperties(headers)
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(source.videoUrl))
        val audioUrl = source.audioUrl
        val mediaSource = if (audioUrl.isNullOrBlank()) {
            videoSource
        } else {
            MergingMediaSource(
                videoSource,
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl)),
            )
        }
        player.setMediaSource(mediaSource)
        player.setPlaybackSpeed(speed)
        val resumeAt = state.resumePositionMs
        player.prepare()
        if (resumeAt > 0L) {
            player.seekTo(resumeAt)
        }
        player.playWhenReady = true
    }

    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed)
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            viewModel.reportHeartbeat(positionMs / 1000, player.isPlaying)
            danmakuController.onPosition(positionMs)
            delay(500)
        }
    }

    DisposableEffect(fullscreen) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            // 退出全屏固定回竖屏：部分 ROM 上 UNSPECIFIED 不会把已强制的横屏改回来。
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                ?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = fullscreen) { fullscreen = false }

    val configuration = LocalConfiguration.current
    val playerHeight = if (fullscreen) {
        configuration.screenHeightDp.dp
    } else {
        // 横屏时 16:9 会高于可用高度，此时按可用高度的 70% 收窄，保证 tab 和内容可见。
        minOf(
            configuration.screenWidthDp.dp * 9f / 16f,
            configuration.screenHeightDp.dp * 0.7f,
        )
    }

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            delay(2600)
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageContainerColor()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullscreen) Modifier else Modifier.statusBarsPadding()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playerHeight),
            ) {
                VideoPlayer(
                    player = player,
                    title = state.detail?.title.orEmpty(),
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs.takeIf { it > 0L } ?: state.source?.durationMs ?: 0L,
                    isBuffering = state.isPlaybackLoading || isBuffering,
                    errorMessage = state.playbackError ?: playerError,
                    qualityLabel = state.qualityLabel(),
                    speed = speed,
                    fullscreen = fullscreen,
                    onPlayPause = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    onSeek = { player.seekTo(it) },
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onQualityClick = { qualityDialogVisible = true },
                    onSpeedClick = { speedDialogVisible = true },
                    onBack = {
                        if (fullscreen) fullscreen = false else nav.pop()
                    },
                    onRetry = viewModel::load,
                    modifier = Modifier.fillMaxSize(),
                    danmakuEnabled = danmakuEnabled,
                    onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                    overlay = {
                        DanmakuOverlay(
                            player = player,
                            items = danmakuController.items,
                            isPlaying = isPlaying,
                            enabled = danmakuEnabled,
                            opacity = danmakuOpacity / 100f,
                            fontScale = danmakuFontScale / 100f,
                            areaFraction = when (danmakuArea) {
                                0 -> 0.25f
                                1 -> 0.5f
                                else -> 1f
                            },
                        )
                    },
                )
            }

            if (!fullscreen) {
                VideoTabRow(selected = tab, onSelect = { tab = it })

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                ) {
                    when (tab) {
                        0 -> VideoInfoTab(
                            detail = state.detail,
                            isLoading = state.isLoading,
                            error = state.error,
                            selectedCid = state.selectedCid,
                            onRetry = viewModel::load,
                            onSelectPart = { index ->
                                viewModel.selectPart(index, player.currentPosition)
                            },
                            onOpenUser = { mid -> if (mid > 0L) nav.push(Route.Space(mid)) },
                            liked = state.liked,
                            coined = state.coined,
                            favorited = state.favorited,
                            following = state.following,
                            onLike = viewModel::toggleLike,
                            onCoin = { coinDialogVisible = true },
                            onFavorite = {
                                if (!container.session.isLoggedIn()) {
                                    viewModel.showLoginHint()
                                } else {
                                    scope.launch {
                                        container.favorites.folders(container.session.mid())
                                            .onSuccess { favoriteFolders = it }
                                            .onFailure {
                                                viewModel.showMessage(
                                                    it.message ?: "收藏夹加载失败",
                                                )
                                            }
                                    }
                                }
                            },
                            onWatchLater = viewModel::addToWatchLater,
                            onFollow = viewModel::toggleFollow,
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> {
                            val aid = state.detail?.aid ?: 0L
                            if (aid > 0L) {
                                CommentTab(
                                    container = container,
                                    oid = aid,
                                    onOpenUser = { mid ->
                                        if (mid > 0L) nav.push(Route.Space(mid))
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (message != null) {
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = Color.White,
            )
        }
    }

    if (speedDialogVisible) {
        SpeedDialog(
            active = speed,
            onSelect = { value ->
                speed = value
                speedDialogVisible = false
            },
            onDismiss = { speedDialogVisible = false },
        )
    }

    if (coinDialogVisible) {
        CoinDialog(
            remaining = (2 - state.coined).coerceIn(1, 2),
            onSelect = { count ->
                coinDialogVisible = false
                viewModel.coin(count)
            },
            onDismiss = { coinDialogVisible = false },
        )
    }

    favoriteFolders?.let { folders ->
        FavoriteFolderDialog(
            folders = folders,
            onSelect = { mediaId ->
                favoriteFolders = null
                viewModel.favorite(mediaId)
            },
            onDismiss = { favoriteFolders = null },
        )
    }

    val source = state.source
    if (qualityDialogVisible && source != null) {
        QualityDialog(
            qualities = source.qualities,
            activeQuality = state.activeQuality,
            onSelect = { qn ->
                qualityDialogVisible = false
                viewModel.selectQuality(qn, player.currentPosition)
            },
            onDismiss = { qualityDialogVisible = false },
        )
    }
}

@Composable
private fun VideoTabRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEachIndexed { index, label ->
            val active = selected == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = COUITheme.textStyles.body1,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) {
                        COUITheme.colorScheme.onSurface
                    } else {
                        COUITheme.colorScheme.onSurfaceSecondary
                    },
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) COUITheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

private fun VideoUiState.qualityLabel(): String? {
    val source = source ?: return null
    val active = source.qualities.firstOrNull { it.quality == activeQuality }
    return active?.label?.takeIf { it.isNotBlank() } ?: "${activeQuality}P"
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private val TABS = listOf("简介", "评论")
