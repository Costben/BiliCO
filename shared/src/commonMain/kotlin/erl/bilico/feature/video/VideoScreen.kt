package erl.bilico.feature.video

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.SponsorCategory
import erl.bilico.data.model.VideoEpisode
import erl.bilico.data.model.isHdr
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.ReplyThreadController
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.platform.canEnterPip
import erl.bilico.platform.enterPipNow
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.PlayerStates
import erl.bilico.player.buildConfiguredBiliPlayer
import erl.bilico.player.hasPlayIntent
import erl.bilico.player.releasePlaybackPlayer
import erl.bilico.player.rememberPlaybackPlayer
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized
import erl.bilico.ui.LocalRouteTransition
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.SPONSOR_BADGE_CONFIRMED
import erl.bilico.ui.components.SPONSOR_BADGE_TEXT
import erl.bilico.ui.pageContainerColor
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
    commentMatchAuthor: String = "",
    commentMatchText: String = "",
    offline: Boolean = false,
    promoted: Boolean = false,
) {
    val viewModel: VideoViewModel = viewModel(key = "video") {
        VideoViewModel(
            container.video, container.downloads, container.heartbeat,
            container.interactions, container.session, container.settings,
            bvid, startPositionMs, initialCid, offline,
        )
    }
    val currentState by viewModel.state.collectAsStateWithLifecycle()
    var cachedUiState by remember(bvid) { mutableStateOf<VideoUiState?>(null) }
    var savedPositionMs by remember(bvid) { mutableLongStateOf(startPositionMs) }
    if (viewModel.currentBvid == bvid && currentState.detail != null) {
        SideEffect {
            cachedUiState = currentState.copy(
                source = null,
                isPlaybackLoading = false,
                playbackError = null,
            )
        }
    }
    val liveState = if (viewModel.currentBvid == bvid) currentState
    else cachedUiState ?: VideoUiState(resumePositionMs = savedPositionMs)

    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val longPressSpeedPercent by container.settings.longPressSpeed.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val speedLocked by container.settings.speedLocked.collectAsStateWithLifecycle()
    val lockedSpeed by container.settings.lockedSpeed.collectAsStateWithLifecycle()
    val showVideoDislike by container.settings.showVideoDislike.collectAsStateWithLifecycle()
    val sponsorBadgeEnabled by container.settings.sponsorBadgeEnabled.collectAsStateWithLifecycle()
    val promotionBadgeEnabled by container.settings.promotionBadgeEnabled.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope, container.blocks.snapshot) }
    val subtitleController = remember { SubtitleController(container.subtitles, scope) }
    val sponsorController = remember { SponsorBlockController(container.sponsorBlock, scope) }
    val clock = remember { VideoPlaybackClock() }

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
    val blurActive by remember(routeTransition) {
        derivedStateOf {
            routeTransition == null ||
                (!routeTransition.exiting() && routeTransition.progress() >= 0.999f)
        }
    }
    val state = rememberEnterState(bvid, liveState, stageReady)

    val context = LocalPlatformContext.current
    val isTop = LocalRouteIsTop.current
    val isMinimized = LocalRouteMinimized.current
    val isActiveRoute = isTop || isMinimized
    val playbackEntry = LocalNavEntry.current
    val staysInOrb = isMinimized || playbackEntry?.minimizingToOrb == true
    val player = if (stageReady || isMinimized) rememberPlaybackPlayer("video") { buildConfiguredBiliPlayer(context) } else null
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
    LaunchedEffect(isTop, bvid, startPositionMs, initialCid, offline) {
        if (!isTop) return@LaunchedEffect
        if (nav.minimizedEntry?.route is Route.Video) {
            nav.dismissMinimized(pausePlayback = false)
        }
        val cached = cachedUiState
        if (viewModel.currentBvid != bvid) {
            player?.stop()
            player?.clearMediaItems()
            PlaybackBridge.activeMediaId = ""
            viewModel.switchTo(
                bvid = bvid,
                startPositionMs = if (cached != null) savedPositionMs else startPositionMs,
                initialCid = initialCid,
                offline = offline,
                cachedState = cached,
            )
        } else {
            viewModel.switchTo(bvid, startPositionMs, initialCid, offline = offline)
        }
    }

    var playerVideoAspect by remember(bvid, state.selectedCid) { mutableFloatStateOf(0f) }
    val effectiveVideoAspect = playerVideoAspect.takeIf { it > 0f }
        ?: state.detail?.aspectFor(state.selectedCid)?.takeIf { it > 0f }
        ?: state.source?.aspect?.takeIf { it > 0f }
        ?: 0f
    var screenLocked by remember { mutableStateOf(false) }
    val fsController = rememberPlayerFullscreenController(
        videoAspect = effectiveVideoAspect,
        autoRotateMode = autoRotateMode,
        inPipMode = inPipMode || screenLocked,
        orientationEnabled = stageReady,
    )
    val fullscreen = fsController.fullscreen
    val deviceSide = fsController.deviceSide
    val applyFullscreen = fsController.applyFullscreen
    val onScreenLockedChange: (Boolean) -> Unit = { locked ->
        if (screenLocked && !locked && fullscreen && !inPipMode && deviceSide == 0) applyFullscreen(true)
        screenLocked = locked
    }
    val detailPager = rememberPagerState(
        initialPage = if (commentRootId > 0L || commentTargetId > 0L || commentMatchText.isNotBlank()) 1 else 0,
        pageCount = { DETAIL_PAGES.size },
    )
    val infoListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    val replyThread = remember(bvid) { ReplyThreadController() }
    val replyListState = rememberLazyListState()
    var queueSheetVisible by remember { mutableStateOf(false) }
    var chapterDialogVisible by remember { mutableStateOf(false) }
    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var sponsorSubmitVisible by remember { mutableStateOf(false) }
    var castDialogVisible by remember { mutableStateOf(false) }
    var downloadDialogVisible by remember { mutableStateOf(false) }
    var reportVideoVisible by remember { mutableStateOf(false) }
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
    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var playIntent by remember(player) { mutableStateOf(player?.hasPlayIntent == true) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isTop, staysInOrb) {
        if (!isTop) {
            val position = player?.currentPosition?.coerceAtLeast(0L) ?: clock.positionMs
            if (position > 0L) savedPositionMs = position
            if (!staysInOrb) isPlaying = false
            isBuffering = false
            longPressSpeed.onActiveChange(false)
        }
    }

    val playbackAttempts = remember(player) { player?.let { PlaybackAttempts(it) { effectiveSpeed } } }
    val (autoNextController, promptAutoNext) = rememberAutoNextRecommendController(
        bvid, isTop, inPipMode, container, nav,
    )

    if (player != null) {
        VideoPlaybackEffects(
            player = player, bvid = bvid, isTop = isTop, isActiveRoute = isActiveRoute,
            isPlaying = isPlaying, state = state, viewModel = viewModel,
            container = container, nav = nav, clock = clock,
            attempts = playbackAttempts, effectiveSpeed = effectiveSpeed,
            danmakuController = danmakuController, sponsorController = sponsorController,
            onPlayingChanged = { isPlaying = it }, onBufferingChanged = { isBuffering = it },
            onPlayIntentChanged = { playIntent = it },
            onError = { error ->
                if (playbackAttempts?.next() != true) playerError = describePlaybackError(error)
            },
            onClearError = { playerError = null }, onVideoAspectChanged = { playerVideoAspect = it },
            onPromptAutoNextRecommend = promptAutoNext, onCancelAutoNext = autoNextController::cancel,
        )
    }

    var routeInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(bvid, startPositionMs, initialCid) {
        nav.playQueue.sync(bvid)
        autoNextController.cancel()
        if (!routeInitialized) {
            routeInitialized = true
            return@LaunchedEffect
        }
        player?.stop()
        player?.clearMediaItems()
        PlaybackBridge.activeMediaId = ""
        clock.reset()
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
        replyThread.close()
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
                preferLocal = state.offlineMode,
                onAuxiliaryLoaded = { info ->
                    val seekMs = viewModel.applyServerResumeHint(info.lastPlayCid, info.lastPlayTimeMs)
                    if (player != null && seekMs > 0L && player.playbackState != PlayerStates.STATE_IDLE && player.currentPosition < 3_000L) {
                        player.seekTo(seekMs)
                        viewModel.consumeResumePosition()
                    }
                },
            )
        }
    }

    PlatformBackHandler(enabled = fullscreen && !screenLocked) { applyFullscreen(false) }

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            delay(2600)
            viewModel.consumeMessage()
        }
    }

    val barBackdrops = rememberVideoBarBackdrops(pageContainerColor())
    val hdrSurface = state.source?.isHdr == true

    val detailAid = state.detail?.aid ?: 0L
    val openSponsor = if (state.selectedCid > 0L) ({ sponsorSubmitVisible = true }) else null
    val openCast = if (state.selectedCid > 0L) ({ castDialogVisible = true }) else null
    val openDownload = if (state.detail != null) ({ downloadDialogVisible = true }) else null
    val openReportVideo = if (detailAid > 0L) ({ reportVideoVisible = true }) else null
    val downloadTasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val currentPartCached = remember(downloadTasks, bvid, state.selectedCid) {
        container.downloads.hasCompletedVideo(bvid, state.selectedCid)
    }
    val renderDownload = erl.bilico.feature.download.rememberVideoDownloadDialog(
        visible = downloadDialogVisible, container = container, detail = state.detail,
        selectedCid = state.selectedCid, activeQuality = state.activeQuality,
        qualities = state.source?.qualities.orEmpty(),
        onEnqueued = { if (it > 0) viewModel.showMessage("已加入离线缓存队列 ($it)") },
        onOpenDownloads = { applyFullscreen(false); nav.push(Route.Downloads) },
        onDismiss = { downloadDialogVisible = false },
    )
    val renderSponsorSubmit = rememberSponsorSubmitDialog(
        visible = sponsorSubmitVisible, bvid = bvid, cid = state.selectedCid, durationMs = clock.durationMs,
        currentPositionMs = { player?.currentPosition?.coerceAtLeast(0L) ?: clock.positionMs },
        controller = sponsorController, sponsorUserIdProvider = container.settings::ensureSponsorUserId,
        onSuccess = { msg -> sponsorSubmitVisible = false; viewModel.showMessage(msg) },
        onDismiss = { sponsorSubmitVisible = false },
    )
    val renderCast = rememberCastDialog(
        visible = castDialogVisible, bvid = bvid, cid = state.selectedCid, qn = state.activeQuality,
        title = state.detail?.title.orEmpty(),
        positionProvider = { player?.currentPosition?.coerceAtLeast(0L) ?: clock.positionMs },
        onDismiss = { castDialogVisible = false },
    )
    val renderReportVideo = rememberReportVideoDialog(
        visible = reportVideoVisible, aid = detailAid, bvid = bvid, title = state.detail?.title.orEmpty(),
        onDismiss = { reportVideoVisible = false },
    )

    val selectPart: (Int) -> Unit = { index ->
        autoNextController.cancel()
        val currentPos = player?.currentPosition ?: 0L
        viewModel.flushHeartbeat(currentPos / 1000, container.scope)
        viewModel.selectPart(index, currentPos)
    }
    val selectEpisode: (VideoEpisode) -> Unit = { episode ->
        autoNextController.cancel()
        val partIndex = state.detail?.pages?.indexOfFirst { it.cid == episode.cid } ?: -1
        if (episode.bvid == bvid && partIndex >= 0) selectPart(partIndex)
        else if (fullscreen) nav.replaceTop(Route.Video(episode.bvid, 0L, episode.cid))
        else nav.push(Route.Video(episode.bvid, 0L, episode.cid))
    }

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = deviceSide,
        inPipMode = inPipMode,
        darkMode = darkMode,
        onExitFullscreen = { applyFullscreen(false) },
        videoAspect = effectiveVideoAspect,
        portraitResizable = true,
        playing = playIntent,
        surfaceViewPlayer = hdrSurface,
        screenLocked = screenLocked,
        onScreenLockedChange = onScreenLockedChange,
        canExpand = {
            val list = when {
                replyThread.root != null -> replyListState
                detailPager.currentPage == 0 -> infoListState
                else -> commentListState
            }
            list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
        },
        playerContent = {
            if (player == null) {
                EmptyVideoPlayerSlot()
            } else {
                VideoPlayerSlot(
                    player = player,
                    clock = clock,
                    state = state,
                    container = container,
                    viewModel = viewModel,
                    nav = nav,
                    bvid = bvid,
                    fullscreen = fullscreen,
                    portraitFullscreen = fullscreen && fsController.portraitStage,
                    inPipMode = inPipMode,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playerError = playerError,
                    message = message,
                    hdrVideo = hdrSurface,
                    speed = speed,
                    speedLocked = speedLocked,
                    longPressSpeed = longPressSpeed,
                    onSelectSpeed = onSelectSpeed,
                    onToggleSpeedLock = onToggleSpeedLock,
                    danmakuEnabled = danmakuEnabled,
                    onOpenDanmakuSettings = { danmakuSettingsDialogVisible = true },
                    subtitleController = subtitleController,
                    danmakuController = danmakuController,
                    sponsorController = sponsorController,
                    danmakuDraft = danmakuDraft,
                    onDanmakuDraftChange = { danmakuDraft = it },
                    onSendDanmaku = sendDanmaku,
                    onSelectPart = selectPart, onSelectEpisode = selectEpisode,
                    danmakuSettingsDialogVisible = danmakuSettingsDialogVisible,
                    onDismissDanmakuSettings = { danmakuSettingsDialogVisible = false },
                    sponsorSubmitDialog = renderSponsorSubmit, onSubmitSponsor = openSponsor,
                    castDialog = renderCast, onCast = openCast,
                    downloadDialog = renderDownload, onDownload = openDownload,
                    reportVideoDialog = renderReportVideo, onReportVideo = openReportVideo,
                    onFullscreenChange = applyFullscreen,
                    onEnterPip = if (canEnterPip(context)) { { enterPipNow(context) } } else null,
                    autoNextController = autoNextController,
                    showAutoNextInPlayer = fullscreen || playbackEntry?.collapsedBarVisible != true,
                )
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
                replyThread = replyThread,
                replyListState = replyListState,
                danmakuEnabled = danmakuEnabled,
                danmakuComposing = danmakuComposing,
                danmakuDraft = danmakuDraft,
                onOpenDanmakuSettings = { danmakuSettingsDialogVisible = true },
                onDanmakuDraftChange = { danmakuDraft = it },
                onDanmakuComposingChange = {
                    danmakuComposing = it
                    if (!it) danmakuDraft = ""
                },
                onSendDanmaku = sendDanmaku,
                onSelectPage = { page -> scope.launch { detailPager.animateScrollToPage(page) } },
                onRetry = viewModel::load,
                onSelectPart = selectPart,
                onSelectEpisode = selectEpisode,
                onOpenQueue = { queueSheetVisible = true },
                onOpenCoin = { coinDialogVisible = true },
                onOpenFavorite = {
                    if (!container.session.isLoggedIn()) {
                        viewModel.showLoginHint()
                    } else {
                        scope.launch {
                            container.favorites.folders(container.session.mid(), state.detail?.aid)
                                .onSuccess { favoriteFolders = it }
                                .onFailure { viewModel.showMessage(it.message ?: "收藏夹加载失败") }
                        }
                    }
                },
                onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
                onDownload = openDownload,
                downloaded = currentPartCached,
                onSeekCommentMs = { ms ->
                    player?.let { clock.seekTo(it, ms) }
                    if (player?.isPlaying == false) player.play()
                },
                onLike = viewModel::toggleLike, onTriple = viewModel::triple,
                onDislike = viewModel::toggleDislike, showDislike = showVideoDislike,
                onWatchLater = viewModel::addToWatchLater, onFollow = viewModel::toggleFollow,
                onOpenUser = { if (it > 0L) nav.push(Route.Space(it)) },
                onOpenVideo = { nav.push(Route.Video(it)) },
                chapters = subtitleController.chapters, positionMsProvider = { clock.positionMs },
                onSelectChapter = {
                    player?.let { p -> clock.seekTo(p, it.fromMs) }
                    if (player?.isPlaying == false) player.play()
                },
                onOpenChapters = { chapterDialogVisible = true },
                barBackdrops = barBackdrops, blurActive = blurActive, backEnabled = !fullscreen,
                commentRootId = commentRootId, commentTargetId = commentTargetId,
                commentMatchAuthor = commentMatchAuthor, commentMatchText = commentMatchText,
                sponsorLabel = when {
                    !sponsorBadgeEnabled -> null
                    sponsorController.fullSponsorMark -> SPONSOR_BADGE_CONFIRMED
                    sponsorController.segments.any { it.category == SponsorCategory.Sponsor } ->
                        SPONSOR_BADGE_TEXT
                    else -> null
                },
                promotedBadge = promotionBadgeEnabled && promoted,
            )
        },
        portraitOverlay = {
            VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = false)
            if (!fullscreen && playbackEntry?.collapsedBarVisible == true) {
                PortraitCollapsedAutoNextOverlay(autoNextController, nav)
            }
        },
        portraitCollapsedBar = {
            VideoCollapsedBarSlot(
                player = player, isPlaying = isPlaying, container = container, nav = nav,
                bvid = bvid, state = state, backdrop = barBackdrops.barMaterial,
                onCast = openCast, onDownload = openDownload, onReportVideo = openReportVideo,
            )
        },
    )

    if (!fullscreen && (
            coinDialogVisible || favoriteFolders != null || danmakuSettingsDialogVisible ||
                chapterDialogVisible || renderSponsorSubmit != null ||
                renderCast != null || renderDownload != null || renderReportVideo != null
            )
    ) {
        VideoScreenDialogs(
            coinDialogVisible = coinDialogVisible, coined = state.coined,
            onSelectCoin = { coinDialogVisible = false; viewModel.coin(it) },
            onDismissCoin = { coinDialogVisible = false },
            favoriteFolders = favoriteFolders,
            onConfirmFavorite = { add, del, fav -> favoriteFolders = null; viewModel.favorite(add, del, fav) },
            onDismissFavorite = { favoriteFolders = null },
            onCreateFavoriteFolder = { title, intro, isPrivate ->
                container.favorites.createFolder(title, intro, isPrivate)
            },
            danmakuSettingsDialogVisible = danmakuSettingsDialogVisible,
            danmakuSettings = if (danmakuSettingsDialogVisible) container.settings.danmakuSettings.collectAsStateWithLifecycle().value else null,
            settingsRepository = container.settings,
            onDismissDanmakuSettings = { danmakuSettingsDialogVisible = false },
            chapterDialogVisible = chapterDialogVisible, chapters = subtitleController.chapters,
            positionMs = clock.positionMs,
            onSelectChapter = {
                chapterDialogVisible = false
                player?.let { p -> clock.seekTo(p, it.fromMs) }
                if (player?.isPlaying == false) player.play()
            },
            onDismissChapter = { chapterDialogVisible = false },
            sponsorSubmitDialog = renderSponsorSubmit, castDialog = renderCast,
            downloadDialog = renderDownload, reportVideoDialog = renderReportVideo,
            inlineInStage = false,
        )
    }

    PlayQueueSheet(
        visible = queueSheetVisible, queue = nav.playQueue, onDismiss = { queueSheetVisible = false },
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
