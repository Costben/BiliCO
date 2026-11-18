package erl.bilico.feature.video

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.isHdr
import erl.bilico.di.AppContainer
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.buildConfiguredExoPlayer
import erl.bilico.player.releasePlaybackPlayer
import erl.bilico.player.rememberPlaybackPlayer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized
import erl.bilico.ui.LocalRouteTransition
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    container: AppContainer,
    nav: NavState,
    bvid: String,
    startPositionMs: Long = 0L,
    initialCid: Long = 0L,
    commentRootId: Long = 0L,
    commentTargetId: Long = 0L,
) {
    // 一个播放页只建一份 ViewModel：连播换下一条、甚至退出再点进来，都复用同一份。
    // 以前把 bvid 拼进 key，每看一条就多留一份 ViewModel 和一条播放地址，全挂在 Activity 的
    // ViewModelStore 上永不释放，切十几条就把 Java 堆吃满闪退。
    val viewModel: VideoViewModel = viewModel(key = "video") {
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
    val currentState by viewModel.state.collectAsStateWithLifecycle()
    // 换片那一帧 ViewModel 里还是上一条的内容，先按空状态画骨架，避免闪出上一条的标题和封面。
    val state = if (viewModel.currentBvid == bvid) {
        currentState
    } else {
        VideoUiState(resumePositionMs = startPositionMs)
    }

    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val longPressSpeedPercent by container.settings.longPressSpeed.collectAsStateWithLifecycle()
    val adjustableLongPressSpeed by container.settings.adjustableLongPressSpeed.collectAsStateWithLifecycle()
    val swipeDownLockLongPressSpeed by container.settings.swipeDownLockLongPressSpeed.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by container.settings.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sponsorSkipMode by container.settings.sponsorSkipMode.collectAsStateWithLifecycle()
    val sponsorCategories by container.settings.sponsorCategories.collectAsStateWithLifecycle()
    val speedLocked by container.settings.speedLocked.collectAsStateWithLifecycle()
    val lockedSpeed by container.settings.lockedSpeed.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope) }
    val subtitleController = remember { SubtitleController(container.subtitles, scope) }
    val sponsorController = remember { SponsorBlockController(container.sponsorBlock, scope) }

    val routeTransition = LocalRouteTransition.current
    var stageReady by remember {
        mutableStateOf(
            routeTransition == null ||
                (!routeTransition.exiting() && routeTransition.progress() >= 0.999f),
        )
    }
    if (!stageReady && routeTransition != null) {
        LaunchedEffect(routeTransition) {
            snapshotFlow { !routeTransition.exiting() && routeTransition.progress() >= 0.999f }
                .first { it }
            stageReady = true
        }
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 点媒体条封面会重建 Activity。播放器跟着页面记住，重建时还是同一个，媒体条不会拿到已释放的实例。
    // 进场过渡期间先保留黑底播放器槽位与详情内容，落位后再挂载 ExoPlayer 与 PlayerView。
    // key 固定成 "video"：连播换下一条时接着用同一台播放器，换源由下面 setMediaSource 那一步做。
    val isTop = LocalRouteIsTop.current
    val isMinimized = LocalRouteMinimized.current
    val isActiveRoute = isTop || isMinimized
    // 缩进融合球时进度恒为 0、等不到「落位」，但球上的封面和播放状态要靠这一页的会话维持，
    // 所以最小化期间照旧保留播放器槽位与事件监听。
    val player = if (stageReady || isMinimized) rememberPlaybackPlayer("video") { buildConfiguredExoPlayer(context) } else null
    // 页面被弹出（最小化不算）时释放播放器：一台准备好的播放器会占住几十 MB 的加载缓冲。
    val playbackEntry = LocalNavEntry.current
    DisposableEffect(playbackEntry) {
        playbackEntry?.onDiscard = {
            val hasOtherVideo = nav.stack.any { it !== playbackEntry && it.route is Route.Video } ||
                (nav.minimizedEntry != null && nav.minimizedEntry !== playbackEntry && nav.minimizedEntry?.route is Route.Video)
            if (!hasOtherVideo) {
                releasePlaybackPlayer("video")
            }
        }
        onDispose { }
    }
    LaunchedEffect(isTop, bvid, startPositionMs, initialCid) {
        if (!isTop) return@LaunchedEffect
        if (nav.minimizedEntry?.route is Route.Video) {
            nav.dismissMinimized(pausePlayback = false)
        }
        if (viewModel.currentBvid != bvid) {
            player?.stop()
            player?.clearMediaItems()
        }
        viewModel.switchTo(bvid, startPositionMs, initialCid)
    }

    // 播放器解码上报的首帧宽高比（换视频或切分 P 时重置），再回退到接口预设的 dimension / dash 尺寸；0 表示仍未知。
    var playerVideoAspect by remember(bvid, state.selectedCid) { mutableFloatStateOf(0f) }
    val effectiveVideoAspect = playerVideoAspect.takeIf { it > 0f }
        ?: state.detail?.aspectFor(state.selectedCid)?.takeIf { it > 0f }
        ?: state.source?.aspect?.takeIf { it > 0f }
        ?: 0f
    val fsController = rememberPlayerFullscreenController(
        videoAspect = effectiveVideoAspect,
        autoRotateMode = autoRotateMode,
        inPipMode = inPipMode,
    )
    val fullscreen = fsController.fullscreen
    val deviceSide = fsController.deviceSide
    val applyFullscreen = fsController.applyFullscreen
    // 简介 / 评论两页横向翻。直接读 detailPager.currentPage 同步驱动页签，
    // 跟手无延迟，与私信页完全一致；点标签时开翻页动画。
    val detailPager = rememberPagerState(
        initialPage = if (commentRootId > 0L || commentTargetId > 0L) 1 else 0,
        pageCount = { DETAIL_PAGES.size },
    )
    val infoListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    var queueSheetVisible by remember { mutableStateOf(false) }
    var chapterDialogVisible by remember { mutableStateOf(false) }
    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var sponsorSubmitVisible by remember { mutableStateOf(false) }
    var coinDialogVisible by remember { mutableStateOf(false) }
    var favoriteFolders by remember { mutableStateOf<List<FavoriteFolder>?>(null) }
    var speed by rememberSaveable {
        mutableFloatStateOf(if (speedLocked) lockedSpeed else 1f)
    }
    val longPressSpeed = rememberLongPressSpeedController(
        baseSpeed = speed,
        boostPercent = longPressSpeedPercent,
    )
    val onSelectSpeed: (Float) -> Unit = { value ->
        longPressSpeed.onActiveChange(false)
        speed = value
        if (speedLocked) container.settings.setLockedSpeed(value)
    }
    val onToggleSpeedLock: () -> Unit = {
        container.settings.setSpeedLock(!speedLocked, speed)
    }
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
                val position = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
                container.danmaku.send(aid, cid, text, position)
                    .onSuccess { dmid ->
                        danmakuDraft = ""
                        danmakuComposing = false
                        val showAtMs = maxOf(position, player?.currentPosition?.coerceAtLeast(0L) ?: position)
                        danmakuController.appendSelf(content = text, timeMs = showAtMs, serverId = dmid)
                        danmakuController.reload(position)
                        viewModel.showMessage("弹幕已发送")
                    }
                    .onFailure { viewModel.showMessage(it.message ?: "发送弹幕失败") }
            }
        }
    }

    val effectiveSpeed = longPressSpeed.effectiveSpeed
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val headers = remember(bvid) { container.client.mediaRequestHeaders(bvid) }
    val dataSourceFactory = remember(headers) {
        OkHttpDataSource.Factory(container.client.mediaClient()).setDefaultRequestProperties(headers)
    }
    val playbackAttempts = remember(player) { player?.let { PlaybackAttempts(it) { effectiveSpeed } } }

    if (player != null) {
        BindPlaybackSession(
            player = player,
            title = state.detail?.title.orEmpty(),
            subtitle = state.detail?.upName.orEmpty(),
            artworkUrl = state.detail?.cover.orEmpty(),
            isPlaying = isPlaying,
            backgroundPlay = backgroundPlay,
            autoPip = autoPip,
        )

        if (isActiveRoute) {
            BindPlayerEvents(
                player = player,
                viewModel = viewModel,
                container = container,
                onPlayingChanged = { isPlaying = it },
                onBufferingChanged = { isBuffering = it },
                onError = { error ->
                    Log.w("BiliPlayer", describePlaybackError(error), error)
                    // 线路坏了先换一条，候选表走完才把错误抛给界面。
                    if (playbackAttempts?.next() != true) playerError = describePlaybackError(error)
                },
                onVideoAspectChanged = { playerVideoAspect = it },
                onPlaybackEnded = {
                    handleVideoPlaybackEnded(
                        mode = playbackEndMode,
                        player = player,
                        viewModel = viewModel,
                        nav = nav,
                        state = state,
                    )
                },
            )
        }
    }

    var routeInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(bvid, startPositionMs, initialCid) {
        nav.playQueue.sync(bvid)
        if (!routeInitialized) {
            routeInitialized = true
            return@LaunchedEffect
        }
        // 原地连播切到下一条时清掉上一条的播控与列表态，仅保留当前舞台与全屏朝向。
        player?.stop()
        player?.clearMediaItems()
        positionMs = 0L
        durationMs = 0L
        isPlaying = false
        isBuffering = false
        playerError = null
        longPressSpeed.onActiveChange(false)
        speed = if (container.settings.speedLocked.value) {
            container.settings.lockedSpeed.value
        } else {
            1f
        }
        danmakuComposing = false
        danmakuDraft = ""
        chapterDialogVisible = false
        danmakuSettingsDialogVisible = false
        coinDialogVisible = false
        favoriteFolders = null
        queueSheetVisible = false
        danmakuController.attach(0L)
        subtitleController.clear()
        if (player != null) sponsorController.clear(player)
        detailPager.scrollToPage(0)
        infoListState.scrollToItem(0)
        commentListState.scrollToItem(0)
    }

    LaunchedEffect(player, bvid, state.selectedCid, sponsorBlockEnabled, isActiveRoute) {
        if (player != null && isActiveRoute) {
            sponsorController.attach(bvid, state.selectedCid, sponsorBlockEnabled, player)
        }
    }

    DisposableEffect(player) {
        onDispose { if (player != null) sponsorController.restoreMuteIfNeeded(player) }
    }

    LaunchedEffect(state.selectedCid, state.detail?.aid) {
        val cid = state.selectedCid
        if (cid > 0L) {
            danmakuController.attach(cid)
            danmakuController.onPosition(player?.currentPosition ?: 0L)
            subtitleController.attach(
                bvid = bvid,
                aid = state.detail?.aid ?: 0L,
                cid = cid,
                onAuxiliaryLoaded = { info ->
                    val seekMs = viewModel.applyServerResumeHint(info.lastPlayCid, info.lastPlayTimeMs)
                    if (player != null && seekMs > 0L && player.playbackState != Player.STATE_IDLE && player.currentPosition < 3_000L) {
                        player.seekTo(seekMs)
                        viewModel.consumeResumePosition()
                    }
                },
            )
        }
    }

    LaunchedEffect(player, bvid, state.source, isTop) {
        // 只让栈顶这一页往共享播放器里装片源：压在下面的播放页还留在组合里，
        // 一旦它们也跟着状态变化重新装载，就会把正在播的那条顶掉、从头再来一遍。
        if (!isTop) return@LaunchedEffect
        val currentPlayer = player ?: return@LaunchedEffect
        val attempts = playbackAttempts ?: return@LaunchedEffect
        val source = state.source ?: return@LaunchedEffect
        val mediaId = playbackMediaId(bvid, state.selectedCid, source.videoUrl)
        // 缩进融合球、再从球里回来时这一页会被重建一次，播放器本身没换；
        // 此时再 setMediaSource 一次就会从 0 重新播。片源没变且播放器还装着它，就只登记候选表。
        if (currentPlayer.playbackState != Player.STATE_IDLE &&
            currentPlayer.currentMediaItem?.mediaId == mediaId
        ) {
            attempts.adopt(source, dataSourceFactory, mediaId)
            return@LaunchedEffect
        }
        playerError = null
        if (state.resumePositionMs <= 0L && source.lastPlayTimeMs >= 3_000L) {
            viewModel.applyServerResumeHint(source.lastPlayCid, source.lastPlayTimeMs)
        }
        val resumeAt = viewModel.state.value.resumePositionMs
        attempts.start(source, dataSourceFactory, resumeAt, mediaId)
        if (resumeAt > 0L) viewModel.consumeResumePosition()
    }

    // 切号 / 登录态变了：按当前清晰度重新取流，让刚生效的大会员档位回到清晰度菜单里。
    // 首帧拿到的登录态不重取，免得一进页面就白闪一下。
    val playbackAccount by container.session.account.collectAsStateWithLifecycle()
    var playbackAccountKey by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    LaunchedEffect(playbackAccount) {
        val key = playbackAccount?.let { it.mid to it.vip }
        val previous = playbackAccountKey
        playbackAccountKey = key
        if (previous != null && previous != key) viewModel.refreshPlayback(positionMs)
    }

    LaunchedEffect(player, effectiveSpeed) {
        player?.setPlaybackSpeed(effectiveSpeed)
    }

    LaunchedEffect(player, isActiveRoute) {
        val currentPlayer = player?.takeIf { isActiveRoute } ?: return@LaunchedEffect
        while (true) {
            positionMs = currentPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = currentPlayer.duration.coerceAtLeast(0L)
            playbackAttempts?.rememberPosition(positionMs)
            viewModel.reportHeartbeat(positionMs / 1000, currentPlayer.isPlaying)
            danmakuController.onPosition(positionMs)
            sponsorController.onPosition(
                positionMs = positionMs,
                player = currentPlayer,
                enabled = container.settings.sponsorBlockEnabled.value,
                skipMode = container.settings.sponsorSkipMode.value,
                enabledCategories = container.settings.sponsorCategories.value,
            )
            delay(500)
        }
    }

    // 宿主窗口的方向由 VideoFullscreenStage 统一接管：竖屏锁死，全屏时真正转成横屏。
    BackHandler(enabled = fullscreen) { applyFullscreen(false) }

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            delay(2600)
            viewModel.consumeMessage()
        }
    }

    val barBackdrops = rememberVideoBarBackdrops(pageContainerColor())

    // HDR 档位必须走 SurfaceView：TextureView 由 GPU 合成，HDR 画面会被当成 SDR 铺到屏幕上（发暗）。
    val hdrSurface = state.source?.isHdr == true

    val renderSponsorSubmit: (@Composable (Boolean) -> Unit)? =
        if (sponsorSubmitVisible && state.selectedCid > 0L) {
            { stageInline ->
                SponsorSubmitDialog(
                    bvid = bvid,
                    cid = state.selectedCid,
                    durationMs = durationMs,
                    currentPositionMs = { player?.currentPosition?.coerceAtLeast(0L) ?: positionMs },
                    controller = sponsorController,
                    sponsorUserIdProvider = container.settings::ensureSponsorUserId,
                    inlineInStage = stageInline,
                    onSuccess = { msg ->
                        sponsorSubmitVisible = false
                        viewModel.showMessage(msg)
                    },
                    onDismiss = { sponsorSubmitVisible = false },
                )
            }
        } else {
            null
        }

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = deviceSide,
        inPipMode = inPipMode,
        activity = activity,
        darkMode = darkMode,
        videoAspect = effectiveVideoAspect,
        portraitResizable = true,
        playing = isPlaying,
        surfaceViewPlayer = hdrSurface,
        canExpand = {
            val list = if (detailPager.currentPage == 0) infoListState else commentListState
            list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
        },
        playerContent = {
            if (player == null) {
                Box(Modifier.fillMaxSize().background(Color.Black))
            } else {
                val stageBackdrop = rememberLayerBackdrop()
                val anyStageDialogOpen = fullscreen &&
                    (danmakuSettingsDialogVisible || chapterDialogVisible || sponsorSubmitVisible)
                VideoPlayer(
                    player = player,
                    title = state.detail?.title.orEmpty(),
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs.takeIf { it > 0L } ?: state.source?.durationMs ?: 0L,
                    isBuffering = state.isPlaybackLoading || isBuffering,
                    errorMessage = state.playbackError ?: playerError,
                    qualityLabel = state.qualityLabel(),
                    source = state.source,
                    hdrVideo = hdrSurface,
                    speed = speed,
                    fullscreen = fullscreen,
                    portraitFullscreen = fullscreen && fsController.portraitStage,
                    onPlayPause = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    onSeek = { player.seekTo(it) },
                    onToggleFullscreen = { applyFullscreen(!fullscreen) },
                    onFullscreenChange = applyFullscreen,
                    hapticEnabled = gestureHaptic,
                    volumeBrightnessEnabled = gestureVolBright,
                    fullscreenHintEnabled = fullscreenGestureHint,
                    qualities = state.source?.qualities.orEmpty(),
                    activeQuality = state.activeQuality,
                    onSelectQuality = { qn -> viewModel.selectQuality(qn, player.currentPosition) },
                    speedLocked = speedLocked,
                    onToggleSpeedLock = onToggleSpeedLock,
                    onSelectSpeed = onSelectSpeed,
                    onBack = {
                        if (fullscreen) applyFullscreen(false) else nav.pop()
                    },
                    onRetry = viewModel::load,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdropIf(BlurEffectsEnabled && anyStageDialogOpen, stageBackdrop),
                    inPipMode = inPipMode,
                    danmakuEnabled = danmakuEnabled,
                    onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                    onDanmakuSettingsClick = { danmakuSettingsDialogVisible = true },
                    subtitleLabel = subtitleController.buttonLabel,
                    subtitleTracks = subtitleController.tracks,
                    selectedSubtitleKey = subtitleController.selectedTrackKey,
                    onSelectSubtitle = subtitleController::selectTrack,
                    onEnterPip = if (PlaybackBridge.canEnterPip(activity)) {
                        { PlaybackBridge.enterPipNow(activity) }
                    } else {
                        null
                    },
                    onHome = { nav.popToMain() },
                    onWatchLater = viewModel::addToWatchLater,
                    playbackEndMode = playbackEndMode,
                    onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
                    onShare = {
                        shareVideo(
                            context = context,
                            title = state.detail?.title.orEmpty(),
                            bvid = bvid,
                        )
                    },
                    longPressSpeed = longPressSpeed,
                    longPressSpeedMode = resolveLongPressSpeedMode(
                        adjustableLongPressSpeed,
                        swipeDownLockLongPressSpeed,
                    ),
                    chapters = subtitleController.chapters,
                    onChapterClick = { chapterDialogVisible = true },
                    sponsorSegments = if (sponsorBlockEnabled) {
                        sponsorController.segments.filter { it.category.id in sponsorCategories }
                    } else {
                        emptyList()
                    },
                    onSubmitSponsor = if (state.selectedCid > 0L) {
                        { sponsorSubmitVisible = true }
                    } else {
                        null
                    },
                    sponsorOverlay = { bottomPad, startPad ->
                        if (sponsorBlockEnabled && sponsorSkipMode >= 0) {
                            SponsorBlockPromptOverlay(
                                undoNotice = sponsorController.undoNotice,
                                promptSegment = sponsorController.promptSegment,
                                poiSegment = sponsorController.poiSegment,
                                bottomPad = bottomPad,
                                startPad = startPad,
                                onUndoSkip = { sponsorController.undoSkip(player) },
                                onSkipPrompt = { sponsorController.skipPromptSegment(player) },
                                onDismissPrompt = { sponsorController.dismissPromptSegment() },
                                onJumpPoi = { sponsorController.jumpToPoi(player) },
                                onDismissPoi = { sponsorController.dismissPoi() },
                            )
                        }
                    },
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
                    danmakuComposer = { onFocusChange ->
                        FullscreenDanmakuComposer(
                            draft = danmakuDraft,
                            onDraftChange = { danmakuDraft = it },
                            onSend = sendDanmaku,
                            onFocusChange = onFocusChange,
                        )
                    },
                    overlay = {
                        DanmakuOverlay(
                            player = player,
                            items = danmakuController.items,
                            isPlaying = isPlaying,
                            settings = danmakuSettings,
                        )
                    },
                    danmakuActions = rememberDanmakuActions(
                        aid = state.detail?.aid ?: 0L,
                        cid = state.selectedCid,
                        fullscreen = fullscreen,
                        positionMs = { player.currentPosition.coerceAtLeast(0L) },
                        isLoggedIn = { container.session.isLoggedIn() },
                        repository = container.danmaku,
                        controller = danmakuController,
                        scope = scope,
                        onShowMessage = viewModel::showMessage,
                        onShowLoginHint = viewModel::showLoginHint,
                    ),
                )
                if (fullscreen) {
                    VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = true)
                    CompositionLocalProvider(
                        LocalBackdrop provides if (BlurEffectsEnabled) stageBackdrop else null,
                    ) {
                        VideoScreenDialogs(
                            coinDialogVisible = false,
                            coined = state.coined,
                            onSelectCoin = {},
                            onDismissCoin = {},
                            favoriteFolders = null,
                            onSelectFavorite = {},
                            onDismissFavorite = {},
                            danmakuSettingsDialogVisible = danmakuSettingsDialogVisible,
                            danmakuSettings = danmakuSettings,
                            settingsRepository = container.settings,
                            onDismissDanmakuSettings = { danmakuSettingsDialogVisible = false },
                            chapterDialogVisible = chapterDialogVisible,
                            chapters = subtitleController.chapters,
                            positionMs = positionMs,
                            onSelectChapter = { chapter ->
                                chapterDialogVisible = false
                                player.seekTo(chapter.fromMs)
                                if (!player.isPlaying) player.play()
                            },
                            onDismissChapter = { chapterDialogVisible = false },
                            sponsorSubmitDialog = renderSponsorSubmit,
                            inlineInStage = true,
                        )
                    }
                }
            }
        },
        detailContent = {
            VideoDetailSection(
                container = container,
                nav = nav,
                state = state,
                detailPager = detailPager,
                infoListState = infoListState,
                commentListState = commentListState,
                danmakuEnabled = danmakuEnabled,
                danmakuComposing = danmakuComposing,
                danmakuDraft = danmakuDraft,
                onDanmakuDraftChange = { danmakuDraft = it },
                onDanmakuComposingChange = {
                    danmakuComposing = it
                    if (!it) danmakuDraft = ""
                },
                onSendDanmaku = sendDanmaku,
                onSelectPage = { page -> scope.launch { detailPager.animateScrollToPage(page) } },
                onRetry = viewModel::load,
                onSelectPart = { index ->
                    val currentPos = player?.currentPosition ?: 0L
                    viewModel.flushHeartbeat(currentPos / 1000, container.scope)
                    viewModel.selectPart(index, currentPos)
                },
                onSelectEpisode = { episode ->
                    val partIndex = state.detail?.pages?.indexOfFirst { it.cid == episode.cid } ?: -1
                    if (episode.bvid == bvid && partIndex >= 0) {
                        val currentPos = player?.currentPosition ?: 0L
                        viewModel.flushHeartbeat(currentPos / 1000, container.scope)
                        viewModel.selectPart(partIndex, currentPos)
                    } else {
                        nav.push(Route.Video(episode.bvid, 0L, episode.cid))
                    }
                },
                onOpenQueue = { queueSheetVisible = true },
                onOpenCoin = { coinDialogVisible = true },
                onOpenFavorite = {
                    if (!container.session.isLoggedIn()) {
                        viewModel.showLoginHint()
                    } else {
                        scope.launch {
                            container.favorites.folders(container.session.mid())
                                .onSuccess { favoriteFolders = it }
                                .onFailure { viewModel.showMessage(it.message ?: "收藏夹加载失败") }
                        }
                    }
                },
                onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
                onSeekCommentMs = { ms ->
                    player?.seekTo(ms)
                    if (player?.isPlaying == false) player.play()
                },
                onLike = viewModel::toggleLike,
                onDislike = viewModel::toggleDislike,
                onWatchLater = viewModel::addToWatchLater,
                onFollow = viewModel::toggleFollow,
                onOpenUser = { mid -> if (mid > 0L) nav.push(Route.Space(mid)) },
                onOpenVideo = { next -> nav.push(Route.Video(next)) },
                chapters = subtitleController.chapters,
                positionMs = positionMs,
                onSelectChapter = { chapter ->
                    player?.seekTo(chapter.fromMs)
                    if (player?.isPlaying == false) player.play()
                },
                onOpenChapters = { chapterDialogVisible = true },
                barBackdrops = barBackdrops,
                blurActive = stageReady,
                commentRootId = commentRootId,
                commentTargetId = commentTargetId,
            )
        },
        portraitOverlay = {
            VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = false)
        },
        portraitCollapsedBar = {
            val slotHeight = LocalPortraitSlotHeight.current
            CollapsedVideoBar(
                isPlaying = isPlaying,
                onBack = { nav.pop() },
                onHome = { nav.popToMain() },
                onPlayPause = {
                    val p = player ?: return@CollapsedVideoBar
                    if (p.isPlaying) p.pause() else p.play()
                },
                onExpand = { slotHeight.floatValue = 0f },
                onDanmakuSettings = { danmakuSettingsDialogVisible = true },
                onWatchLater = viewModel::addToWatchLater,
                playbackEndMode = playbackEndMode,
                onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
                onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
                backdrop = barBackdrops.barMaterial,
            )
        },
    )

    if (!fullscreen) {
        VideoScreenDialogs(
            coinDialogVisible = coinDialogVisible,
            coined = state.coined,
            onSelectCoin = { count -> coinDialogVisible = false; viewModel.coin(count) },
            onDismissCoin = { coinDialogVisible = false },
            favoriteFolders = favoriteFolders,
            onSelectFavorite = { mediaId -> favoriteFolders = null; viewModel.favorite(mediaId) },
            onDismissFavorite = { favoriteFolders = null },
            danmakuSettingsDialogVisible = danmakuSettingsDialogVisible,
            danmakuSettings = danmakuSettings,
            settingsRepository = container.settings,
            onDismissDanmakuSettings = { danmakuSettingsDialogVisible = false },
            chapterDialogVisible = chapterDialogVisible,
            chapters = subtitleController.chapters,
            positionMs = positionMs,
            onSelectChapter = { chapter ->
                chapterDialogVisible = false
                player?.seekTo(chapter.fromMs)
                if (player?.isPlaying == false) player.play()
            },
            onDismissChapter = { chapterDialogVisible = false },
            sponsorSubmitDialog = renderSponsorSubmit,
            inlineInStage = false,
        )
    }

    PlayQueueSheet(
        visible = queueSheetVisible,
        queue = nav.playQueue,
        onDismiss = { queueSheetVisible = false },
        onPlay = { queued ->
            queueSheetVisible = false
            if (queued.video.bvid != nav.playQueue.playingBvid) {
                viewModel.flushHeartbeat((player?.currentPosition ?: 0L) / 1000, container.scope)
                nav.playQueue.jumpTo(nav.playQueue.items.indexOf(queued))
                nav.replaceTop(Route.Video(queued.video.bvid, queued.startSec))
            }
        },
    )
}
