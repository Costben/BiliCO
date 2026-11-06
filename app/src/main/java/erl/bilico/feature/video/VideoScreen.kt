package erl.bilico.feature.video

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.CommentTab
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.buildConfiguredExoPlayer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    container: AppContainer,
    nav: NavState,
    bvid: String,
    startPositionMs: Long = 0L,
    initialCid: Long = 0L,
) {
    val viewModel: VideoViewModel = viewModel(key = "video-$bvid-$startPositionMs-$initialCid") {
        VideoViewModel(
            repository = container.video,
            heartbeat = container.heartbeat,
            interactions = container.interactions,
            session = container.session,
            settings = container.settings,
            bvid = bvid,
            startPositionMs = startPositionMs,
            initialCid = initialCid,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuOpacity by container.settings.danmakuOpacity.collectAsStateWithLifecycle()
    val danmakuFontScale by container.settings.danmakuFontScale.collectAsStateWithLifecycle()
    val danmakuArea by container.settings.danmakuArea.collectAsStateWithLifecycle()
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope) }
    val subtitleController = remember { SubtitleController(container.subtitles, scope) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = remember { buildConfiguredExoPlayer(context) }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    // 用户主动退出全屏、而手机还横着时，先锁竖屏，等手机真的竖过来再交还给传感器。
    var manualFullscreenExit by rememberSaveable { mutableStateOf(false) }
    val applyFullscreen: (Boolean) -> Unit = { value ->
        if (value) {
            manualFullscreenExit = false
            fullscreen = true
        } else {
            manualFullscreenExit = true
            fullscreen = false
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 简介 / 评论两页横向翻；点标签翻页，滑完一页再把标签写回去。
    // 回写必须等翻页停稳：动画途中 currentPage 会先经过另一页，写回去会把动画打断在半路。
    val detailPager = rememberPagerState(initialPage = tab, pageCount = { DETAIL_PAGES.size })
    LaunchedEffect(tab) {
        if (detailPager.settledPage != tab) detailPager.animateScrollToPage(tab)
    }
    LaunchedEffect(detailPager) {
        snapshotFlow { detailPager.settledPage }
            .distinctUntilChanged()
            .collect { tab = it }
    }
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }
    var subtitleDialogVisible by remember { mutableStateOf(false) }
    var coinDialogVisible by remember { mutableStateOf(false) }
    var favoriteFolders by remember { mutableStateOf<List<FavoriteFolder>?>(null) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var longPressSpeed by remember { mutableStateOf(false) }
    var danmakuComposing by rememberSaveable { mutableStateOf(false) }
    var danmakuDraft by rememberSaveable { mutableStateOf("") }

    val sendDanmaku: () -> Unit = {
        val text = danmakuDraft.trim()
        val aid = state.detail?.aid ?: 0L
        val cid = state.selectedCid
        when {
            text.isEmpty() -> Unit
            !container.session.isLoggedIn() -> viewModel.showLoginHint()
            else -> scope.launch {
                val position = player.currentPosition.coerceAtLeast(0L)
                container.danmaku.send(aid, cid, text, position)
                    .onSuccess {
                        danmakuDraft = ""
                        danmakuComposing = false
                        danmakuController.reload(position)
                        viewModel.showMessage("弹幕已发送")
                    }
                    .onFailure { viewModel.showMessage(it.message ?: "发送弹幕失败") }
            }
        }
    }

    val boostSpeed = if (speed >= 2f) 3f else 2f
    val effectiveSpeed = if (longPressSpeed) boostSpeed else speed

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = state.detail?.upName.orEmpty(),
        isPlaying = isPlaying,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
    )

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    viewModel.markPlaybackStarted()
                } else if (playbackState == Player.STATE_ENDED) {
                    viewModel.flushHeartbeat(player.duration.coerceAtLeast(0L) / 1000, container.scope)
                    viewModel.playNextPart()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.message ?: "播放失败"
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(state.selectedCid, state.detail?.aid) {
        val cid = state.selectedCid
        if (cid > 0L) {
            danmakuController.attach(cid)
            danmakuController.onPosition(player.currentPosition)
            subtitleController.attach(
                bvid = bvid,
                aid = state.detail?.aid ?: 0L,
                cid = cid,
                onAuxiliaryLoaded = { info ->
                    val seekMs = viewModel.applyServerResumeHint(info.lastPlayCid, info.lastPlayTimeMs)
                    if (seekMs > 0L && player.playbackState != Player.STATE_IDLE && player.currentPosition < 3_000L) {
                        player.seekTo(seekMs)
                        viewModel.consumeResumePosition()
                    }
                },
            )
        }
    }

    LaunchedEffect(state.source) {
        val source = state.source ?: return@LaunchedEffect
        playerError = null
        if (state.resumePositionMs <= 0L && source.lastPlayTimeMs >= 3_000L) {
            viewModel.applyServerResumeHint(source.lastPlayCid, source.lastPlayTimeMs)
        }
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
        player.setPlaybackSpeed(effectiveSpeed)
        val resumeAt = viewModel.state.value.resumePositionMs
        player.prepare()
        if (resumeAt > 0L) {
            player.seekTo(resumeAt)
            viewModel.consumeResumePosition()
        }
        player.playWhenReady = true
    }

    LaunchedEffect(effectiveSpeed) {
        player.setPlaybackSpeed(effectiveSpeed)
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
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { }
    }

    // 手机横过来就进全屏；只有「横屏转回竖屏」才退出，竖屏本身不构成退出的理由，
    // 否则手势上滑进全屏会被这条立刻抹掉。
    val deviceLandscape = rememberDeviceLandscape()
    var wasLandscape by remember { mutableStateOf(deviceLandscape) }
    LaunchedEffect(deviceLandscape, manualFullscreenExit) {
        if (inPipMode) return@LaunchedEffect
        if (deviceLandscape && !manualFullscreenExit) {
            if (!fullscreen) fullscreen = true
        } else if (!deviceLandscape && wasLandscape) {
            if (manualFullscreenExit) manualFullscreenExit = false
            if (fullscreen) fullscreen = false
        }
        wasLandscape = deviceLandscape
    }

    // 全屏时锁横屏把屏幕转过去；主动退出时锁竖屏，其余时候跟着传感器走。
    LaunchedEffect(fullscreen, manualFullscreenExit) {
        if (inPipMode) return@LaunchedEffect
        activity?.requestedOrientation = when {
            fullscreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            manualFullscreenExit -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // 离开播放页就锁回竖屏：别的页面没有横屏布局，转过去会散架。
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                ?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = fullscreen) { applyFullscreen(false) }

    val fullViewport = fullscreen || inPipMode
    val configuration = LocalConfiguration.current
    val playerHeight = if (fullViewport) {
        configuration.screenHeightDp.dp
    } else {
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
        // 竖屏非全屏时，视频上方那条状态栏恒为纯黑，不跟主题走。
        if (!fullViewport) {
            PlayerStatusBar(activity = activity, darkMode = darkMode)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullViewport) Modifier else Modifier.statusBarsPadding()),
        ) {
            Box(
                modifier = if (inPipMode) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(playerHeight)
                },
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
                    onToggleFullscreen = { applyFullscreen(!fullscreen) },
                    onFullscreenChange = applyFullscreen,
                    hapticEnabled = gestureHaptic,
                    onQualityClick = { qualityDialogVisible = true },
                    onSpeedClick = { speedDialogVisible = true },
                    onBack = {
                        if (fullscreen) applyFullscreen(false) else nav.pop()
                    },
                    onRetry = viewModel::load,
                    modifier = Modifier.fillMaxSize(),
                    inPipMode = inPipMode,
                    danmakuEnabled = danmakuEnabled,
                    onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                    subtitleLabel = subtitleController.buttonLabel,
                    onSubtitleClick = { subtitleDialogVisible = true },
                    onEnterPip = if (PlaybackBridge.canEnterPip(activity)) {
                        { PlaybackBridge.enterPipNow(activity) }
                    } else {
                        null
                    },
                    onHome = { nav.popToMain() },
                    onWatchLater = viewModel::addToWatchLater,
                    onShare = {
                        shareVideo(
                            context = context,
                            title = state.detail?.title.orEmpty(),
                            bvid = bvid,
                        )
                    },
                    longPressSpeedActive = longPressSpeed,
                    longPressSpeedValue = boostSpeed,
                    onLongPressSpeedChange = { longPressSpeed = it },
                    subtitleOverlay = { chromeVisible ->
                        SubtitleOverlay(
                            cues = subtitleController.cues,
                            positionMs = positionMs,
                            fullscreen = fullscreen,
                            controlsVisible = chromeVisible,
                            inPip = inPipMode,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
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

            if (!fullViewport) {
                VideoDetailBar(
                    selected = tab,
                    onSelect = { tab = it },
                    pager = detailPager,
                    danmakuEnabled = danmakuEnabled,
                    onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                    composing = danmakuComposing,
                    draft = danmakuDraft,
                    onDraftChange = { danmakuDraft = it },
                    onComposingChange = {
                        danmakuComposing = it
                        if (!it) danmakuDraft = ""
                    },
                    onSend = sendDanmaku,
                )

                HorizontalPager(
                    state = detailPager,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    beyondViewportPageCount = 0,
                    key = { DETAIL_PAGES[it] },
                ) { page ->
                    when (page) {
                        0 -> VideoInfoTab(
                            detail = state.detail,
                            isLoading = state.isLoading,
                            error = state.error,
                            selectedCid = state.selectedCid,
                            onRetry = viewModel::load,
                            onSelectPart = { index ->
                                viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
                                viewModel.selectPart(index, player.currentPosition)
                            },
                            onOpenUser = { mid -> if (mid > 0L) nav.push(Route.Space(mid)) },
                            onOpenVideo = { next -> nav.push(Route.Video(next)) },
                            related = state.related,
                            container = container,
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

        if (message != null && !inPipMode) {
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

    if (subtitleDialogVisible) {
        SubtitleDialog(
            tracks = subtitleController.tracks,
            selectedKey = subtitleController.selectedTrackKey,
            onSelect = { key ->
                subtitleController.selectTrack(key)
                subtitleDialogVisible = false
            },
            onDismiss = { subtitleDialogVisible = false },
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

private val DETAIL_PAGES = listOf("info", "comment")
