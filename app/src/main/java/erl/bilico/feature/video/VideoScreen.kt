package erl.bilico.feature.video

import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
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
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope) }
    val subtitleController = remember { SubtitleController(container.subtitles, scope) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = remember { buildConfiguredExoPlayer(context) }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var activeLandscapeSide by rememberSaveable { mutableIntStateOf(1) }
    val deviceSide = rememberDeviceOrientationSide(autoRotateMode)
    // 用户主动退出全屏、而手机还横着时，先锁竖屏，等手机真的竖过来再交还给传感器。
    var manualFullscreenExit by rememberSaveable { mutableStateOf(false) }
    var lastManualActionTime by remember { mutableLongStateOf(0L) }
    var manualEnterAwaitingLandscape by remember { mutableStateOf(false) }
    val applyFullscreen: (Boolean) -> Unit = { value ->
        lastManualActionTime = SystemClock.uptimeMillis()
        if (value) {
            manualFullscreenExit = false
            manualEnterAwaitingLandscape = deviceSide == 0
            if (deviceSide != 0) activeLandscapeSide = deviceSide
            fullscreen = true
        } else {
            manualFullscreenExit = true
            manualEnterAwaitingLandscape = false
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
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = state.detail?.upName.orEmpty(),
        isPlaying = isPlaying,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
    )

    BindPlayerEvents(
        player = player,
        viewModel = viewModel,
        container = container,
        onPlayingChanged = { isPlaying = it },
        onBufferingChanged = { isBuffering = it },
        onError = { playerError = it },
        onVideoAspectChanged = { videoAspectRatio = it },
    )

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

    // 手机横过来就进全屏；只有「横屏转回竖屏」才退出。
    // 手势或按钮手动触发全屏后，享受冷却与物理朝向豁免保护，防止手机还没转过来就被强行切回。
    var wasLandscape by remember { mutableStateOf(deviceSide != 0) }
    LaunchedEffect(deviceSide, manualFullscreenExit, manualEnterAwaitingLandscape) {
        if (inPipMode) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        val inCooldown = now - lastManualActionTime < MANUAL_FULLSCREEN_COOLDOWN_MS

        if (deviceSide != 0) {
            activeLandscapeSide = deviceSide
            if (manualEnterAwaitingLandscape) {
                manualEnterAwaitingLandscape = false
            }
            if (manualFullscreenExit && !inCooldown) {
                manualFullscreenExit = false
            }
            if (!manualFullscreenExit && !inCooldown) {
                if (!fullscreen) fullscreen = true
            }
        } else {
            if (manualFullscreenExit) {
                manualFullscreenExit = false
            }
            if (wasLandscape && !inCooldown && !manualEnterAwaitingLandscape) {
                if (fullscreen) fullscreen = false
            }
        }
        wasLandscape = deviceSide != 0
    }

    // 宿主窗口恒定锁定竖屏：全屏旋转由应用内 VideoFullscreenStage 自绘纯几何 90° 缩放旋转完成，
    // 彻底杜绝系统 WindowManager 截屏淡入淡出转屏动画。
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    BackHandler(enabled = fullscreen) { applyFullscreen(false) }

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            delay(2600)
            viewModel.consumeMessage()
        }
    }

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = activeLandscapeSide,
        inPipMode = inPipMode,
        videoAspectRatio = videoAspectRatio,
        activity = activity,
        darkMode = darkMode,
        playerContent = {
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
                volumeBrightnessEnabled = gestureVolBright,
                fullscreenHintEnabled = fullscreenGestureHint,
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
            if (fullscreen) {
                VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = true)
                VideoScreenDialogs(
                    speedDialogVisible = speedDialogVisible,
                    speed = speed,
                    onSpeedChange = { value ->
                        speed = value
                        speedDialogVisible = false
                    },
                    onDismissSpeed = { speedDialogVisible = false },
                    subtitleDialogVisible = subtitleDialogVisible,
                    subtitleController = subtitleController,
                    onDismissSubtitle = { subtitleDialogVisible = false },
                    coinDialogVisible = false,
                    coined = state.coined,
                    onSelectCoin = {},
                    onDismissCoin = {},
                    favoriteFolders = null,
                    onSelectFavorite = {},
                    onDismissFavorite = {},
                    qualityDialogVisible = qualityDialogVisible,
                    state = state,
                    onSelectQuality = { qn ->
                        qualityDialogVisible = false
                        viewModel.selectQuality(qn, player.currentPosition)
                    },
                    onDismissQuality = { qualityDialogVisible = false },
                    inlineInStage = true,
                )
            }
        },
        detailContent = {
            Column(modifier = Modifier.fillMaxSize()) {
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
        },
        portraitOverlay = {
            VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = false)
        },
    )

    if (!fullscreen) {
        VideoScreenDialogs(
            speedDialogVisible = speedDialogVisible,
            speed = speed,
            onSpeedChange = { value ->
                speed = value
                speedDialogVisible = false
            },
            onDismissSpeed = { speedDialogVisible = false },
            subtitleDialogVisible = subtitleDialogVisible,
            subtitleController = subtitleController,
            onDismissSubtitle = { subtitleDialogVisible = false },
            coinDialogVisible = coinDialogVisible,
            coined = state.coined,
            onSelectCoin = { count ->
                coinDialogVisible = false
                viewModel.coin(count)
            },
            onDismissCoin = { coinDialogVisible = false },
            favoriteFolders = favoriteFolders,
            onSelectFavorite = { mediaId ->
                favoriteFolders = null
                viewModel.favorite(mediaId)
            },
            onDismissFavorite = { favoriteFolders = null },
            qualityDialogVisible = qualityDialogVisible,
            state = state,
            onSelectQuality = { qn ->
                qualityDialogVisible = false
                viewModel.selectQuality(qn, player.currentPosition)
            },
            onDismissQuality = { qualityDialogVisible = false },
            inlineInStage = false,
        )
    }
}
