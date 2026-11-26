package erl.bilico.feature.pgc

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.model.isHdr
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.ReplyThreadController
import erl.bilico.feature.download.PgcDownloadDialog
import erl.bilico.feature.video.CollapsedVideoBar
import erl.bilico.feature.video.DanmakuController
import erl.bilico.feature.video.DanmakuOverlay
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.feature.video.FullscreenDanmakuComposer
import erl.bilico.feature.video.LocalPortraitSlotHeight
import erl.bilico.feature.video.LocalStageReturnHome
import erl.bilico.feature.video.PlaybackAttempts
import erl.bilico.feature.video.SubtitleController
import erl.bilico.feature.video.SubtitleOverlay
import erl.bilico.feature.video.VideoFullscreenStage
import erl.bilico.feature.video.VideoMessageToast
import erl.bilico.feature.video.VideoPlayer
import erl.bilico.feature.video.describePlaybackError
import erl.bilico.feature.video.playbackMediaId
import erl.bilico.feature.video.rememberDanmakuActions
import erl.bilico.feature.video.rememberLongPressSpeedController
import erl.bilico.feature.video.rememberPlayerFullscreenController
import erl.bilico.feature.video.rememberSeekPreviewController
import erl.bilico.feature.video.rememberVideoBarBackdrops
import erl.bilico.feature.video.resolveLongPressSpeedMode
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.platform.canEnterPip
import erl.bilico.platform.enterPipNow
import erl.bilico.player.BiliPlayerListener
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.PlayerStates
import erl.bilico.player.buildConfiguredBiliPlayer
import erl.bilico.player.releasePlaybackPlayer
import erl.bilico.player.rememberPlaybackPlayer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.LocalRouteTransition
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.feature.video.rememberStageVideoBackdrop
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PgcScreen(
    container: AppContainer,
    nav: NavState,
    seasonId: Long,
    epId: Long = 0L,
    startPositionMs: Long = 0L,
    offline: Boolean = false,
) {
    val viewModel: PgcViewModel = viewModel(key = "pgc-$seasonId-$epId-$startPositionMs-$offline") {
        PgcViewModel(
            repository = container.pgc,
            downloads = container.downloads,
            heartbeat = container.heartbeat,
            session = container.session,
            settings = container.settings,
            seasonId = seasonId,
            epId = epId,
            startPositionMs = startPositionMs,
            offline = offline,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

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
    val playerConicLight by container.settings.playerConicLight.collectAsStateWithLifecycle()
    val speedLocked by container.settings.speedLocked.collectAsStateWithLifecycle()
    val lockedSpeed by container.settings.lockedSpeed.collectAsStateWithLifecycle()
    val topBarLayoutFullscreen by container.settings.playerTopBar.fullscreen.collectAsStateWithLifecycle()
    val topBarLayoutPortrait by container.settings.playerTopBar.portrait.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope, container.blocks.snapshot) }
    val subtitleController = remember { SubtitleController(container.subtitles, scope) }

    val context = LocalPlatformContext.current
    val player = rememberPlaybackPlayer("pgc-$seasonId") { buildConfiguredBiliPlayer(context) }
    val playbackEntry = LocalNavEntry.current
    DisposableEffect(playbackEntry, seasonId) {
        playbackEntry?.onDiscard = { releasePlaybackPlayer("pgc-$seasonId") }
        onDispose { }
    }

    val episode = viewModel.currentEpisode
    var playerVideoAspect by remember(episode?.cid) { mutableFloatStateOf(0f) }
    val effectiveVideoAspect = playerVideoAspect.takeIf { it > 0f }
        ?: state.source?.aspect?.takeIf { it > 0f }
        ?: 0f
    var screenLocked by remember { mutableStateOf(false) }
    val fsController = rememberPlayerFullscreenController(
        videoAspect = effectiveVideoAspect,
        autoRotateMode = autoRotateMode,
        inPipMode = inPipMode || screenLocked,
    )
    val fullscreen = fsController.fullscreen
    val deviceSide = fsController.deviceSide
    val applyFullscreen = fsController.applyFullscreen
    val onScreenLockedChange: (Boolean) -> Unit = { locked ->
        if (screenLocked && !locked && fullscreen && !inPipMode && deviceSide == 0) applyFullscreen(true)
        screenLocked = locked
    }
    val routeTransition = LocalRouteTransition.current
    val blurActive by remember(routeTransition) {
        derivedStateOf {
            routeTransition == null ||
                (!routeTransition.exiting() && routeTransition.progress() >= 0.999f)
        }
    }
    val detailPager = rememberPagerState(pageCount = { 2 })
    val infoListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    val replyThread = remember(seasonId) { ReplyThreadController() }
    val replyListState = rememberLazyListState()
    val barBackdrops = rememberVideoBarBackdrops(pageContainerColor())
    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var castDialogVisible by remember { mutableStateOf(false) }
    var downloadDialogVisible by remember { mutableStateOf(false) }
    var danmakuComposing by rememberSaveable { mutableStateOf(false) }
    var danmakuDraft by rememberSaveable { mutableStateOf("") }
    var speed by rememberSaveable {
        mutableFloatStateOf(if (speedLocked) lockedSpeed else 1f)
    }
    val longPressSpeed = rememberLongPressSpeedController(
        baseSpeed = speed,
        boostPercent = longPressSpeedPercent,
    )
    val effectiveSpeed = longPressSpeed.effectiveSpeed

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val playbackAttempts = remember(player) { PlaybackAttempts(player) { effectiveSpeed } }

    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = episode?.title.orEmpty(),
        artworkUrl = episode?.cover?.ifBlank { null } ?: state.detail?.cover.orEmpty(),
        isPlaying = isPlaying,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
    )

    DisposableEffect(player) {
        val initialSize = player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            playerVideoAspect = initialSize.width.toFloat() / initialSize.height.toFloat()
        }
        val updatePlaying: (Boolean) -> Unit = { isPlaying = it }
        val listener = object : BiliPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaying(isPlaying)
            }

            override fun onVideoSizeChanged(width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    playerVideoAspect = width.toFloat() / height.toFloat()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == PlayerStates.STATE_BUFFERING
                if (state == PlayerStates.STATE_READY) {
                    viewModel.markPlaybackStarted()
                } else if (state == PlayerStates.STATE_ENDED) {
                    viewModel.flushHeartbeat(player.duration.coerceAtLeast(0L) / 1000, container.scope)
                    if (!container.sleepTimer.consumeAfterCurrentVideo(player)) {
                        when (playbackEndMode) {
                            PlaybackEndMode.OFF -> Unit
                            PlaybackEndMode.SINGLE_LOOP -> { player.seekTo(0L); player.play() }
                            PlaybackEndMode.LIST_LOOP -> {
                                if (!viewModel.playNextEpisode(loop = true)) { player.seekTo(0L); player.play() }
                            }
                            PlaybackEndMode.AUTO_NEXT -> viewModel.playNextEpisode()
                        }
                    }
                }
            }

            override fun onPlayerError(message: String) {
                if (!playbackAttempts.next()) playerError = describePlaybackError(message)
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
            player.removeListener(listener)
        }
    }

    LaunchedEffect(episode?.cid, episode?.aid, episode?.bvid, state.offlineMode) {
        val ep = episode ?: return@LaunchedEffect
        if (ep.cid <= 0L) return@LaunchedEffect
        danmakuController.attach(ep.cid)
        danmakuController.onPosition(player.currentPosition)
        subtitleController.attach(bvid = ep.bvid, aid = ep.aid, cid = ep.cid, preferLocal = state.offlineMode)
    }

    var loadedEpCid by remember(seasonId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.source) {
        val source = state.source ?: return@LaunchedEffect
        val currentEp = viewModel.currentEpisode
        val referer = if (currentEp != null && currentEp.epId > 0L) {
            "https://www.bilibili.com/bangumi/play/ep${currentEp.epId}"
        } else {
            "https://www.bilibili.com/bangumi/play/ss$seasonId"
        }
        val headers = container.client.mediaRequestHeaders().toMutableMap().apply { put("Referer", referer) }
        val resumeAt = viewModel.state.value.resumePositionMs
        val currentCid = currentEp?.cid ?: 0L
        val mediaId = playbackMediaId(currentEp?.bvid.orEmpty(), currentCid, source.videoUrl)
        if (player.playbackState != PlayerStates.STATE_IDLE && PlaybackBridge.activeMediaId == mediaId) {
            loadedEpCid = currentCid
            playbackAttempts.adopt(source, headers, mediaId)
            return@LaunchedEffect
        }
        val shouldPlay = when {
            loadedEpCid == null || player.playbackState == PlayerStates.STATE_IDLE ->
                container.settings.detailAutoPlay.value
            loadedEpCid != currentCid && player.playbackState == PlayerStates.STATE_ENDED ->
                true
            else -> player.playWhenReady
        }
        loadedEpCid = currentCid
        playerError = null
        PlaybackBridge.activeMediaId = mediaId
        playbackAttempts.start(source, headers, resumeAt, mediaId, playWhenReady = shouldPlay)
        if (resumeAt > 0L) viewModel.consumeResumePosition()
    }

    val playbackAccount by container.session.account.collectAsStateWithLifecycle()
    var playbackAccountKey by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    LaunchedEffect(playbackAccount) {
        val key = playbackAccount?.let { it.mid to it.vip }
        val previous = playbackAccountKey
        playbackAccountKey = key
        if (previous != null && previous != key) viewModel.refreshPlayback(positionMs)
    }

    LaunchedEffect(effectiveSpeed) {
        player.setPlaybackSpeed(effectiveSpeed)
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
            playbackAttempts.rememberPosition(positionMs)
            viewModel.reportHeartbeat(positionMs / 1000, player.isPlaying)
            danmakuController.onPosition(positionMs)
            delay(500)
        }
    }

    PlatformBackHandler(enabled = fullscreen && !screenLocked) { applyFullscreen(false) }

    var danmakuActionMessage by remember { mutableStateOf<String?>(null) }
    val message = danmakuActionMessage ?: state.message
    val sendDanmaku: () -> Unit = {
        val text = danmakuDraft.trim()
        val aid = episode?.aid ?: 0L
        val cid = episode?.cid ?: 0L
        when {
            text.isEmpty() || cid <= 0L -> Unit
            !container.session.isLoggedIn() -> danmakuActionMessage = "请先登录后再操作"
            else -> scope.launch {
                val position = player.currentPosition.coerceAtLeast(0L)
                container.danmaku.send(aid, cid, text, position)
                    .onSuccess { dmid ->
                        danmakuDraft = ""
                        danmakuComposing = false
                        val showAtMs = maxOf(position, player.currentPosition.coerceAtLeast(0L))
                        danmakuController.appendSelf(content = text, timeMs = showAtMs, serverId = dmid)
                        danmakuController.reload(position)
                        danmakuActionMessage = "弹幕已发送"
                    }
                    .onFailure { danmakuActionMessage = it.message ?: "发送弹幕失败" }
            }
        }
    }
    if (danmakuActionMessage != null) {
        LaunchedEffect(danmakuActionMessage) {
            delay(2600)
            danmakuActionMessage = null
        }
    }
    if (state.message != null) {
        LaunchedEffect(state.message) {
            delay(2600)
            viewModel.consumeMessage()
        }
    }

    @Composable
    fun RenderPgcDialogs(inlineInStage: Boolean) {
        if (danmakuSettingsDialogVisible) {
            DanmakuSettingsDialog(
                settings = danmakuSettings,
                repository = container.settings,
                onDismiss = { danmakuSettingsDialogVisible = false },
                inlineInStage = inlineInStage,
            )
        }
        val currentEp = viewModel.currentEpisode
        val castEpId = currentEp?.epId ?: epId
        val castCid = currentEp?.cid ?: 0L
        if (castDialogVisible && (castEpId > 0L || castCid > 0L)) {
            erl.bilico.feature.cast.CastSheet(
                request = erl.bilico.feature.cast.CastMediaRequest(
                    title = state.detail?.title.orEmpty(),
                    epId = castEpId,
                    cid = castCid,
                    qn = state.activeQuality,
                    initialPositionMs = player.currentPosition.coerceAtLeast(0L),
                ),
                inlineInStage = inlineInStage,
                onDismiss = { castDialogVisible = false },
            )
        }
        val detail = state.detail
        if (downloadDialogVisible && detail != null) {
            PgcDownloadDialog(
                container = container, detail = detail, currentEpisode = currentEp,
                activeQuality = state.activeQuality, initialQualities = state.source?.qualities.orEmpty(),
                inlineInStage = inlineInStage,
                onEnqueued = { if (it > 0) viewModel.showMessage("已加入离线缓存队列 ($it)") },
                onOpenDownloads = { applyFullscreen(false); nav.push(Route.Downloads) },
                onDismiss = { downloadDialogVisible = false },
            )
        }
    }

    val hdrSurface = state.source?.isHdr == true

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = deviceSide,
        inPipMode = inPipMode,
        darkMode = darkMode,
        onExitFullscreen = { applyFullscreen(false) },
        videoAspect = effectiveVideoAspect,
        portraitResizable = true,
        playing = isPlaying || isBuffering,
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
            val captureStageBackdrop = BlurEffectsEnabled && fullscreen && !hdrSurface
            val stageBackdropActive = captureStageBackdrop && (
                danmakuSettingsDialogVisible || castDialogVisible || downloadDialogVisible
                )
            val stageBackdrop = rememberStageVideoBackdrop(
                player = player,
                active = stageBackdropActive,
                isPlaying = isPlaying,
            )
            val returnHome = LocalStageReturnHome.current ?: { nav.popToMain() }
            VideoPlayer(
                player = player,
                title = state.detail?.title.orEmpty(),
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs.takeIf { it > 0L } ?: state.source?.durationMs ?: 0L,
                bufferedMs = bufferedMs,
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
                onSeek = { positionMs = it; player.seekTo(it) },
                onToggleFullscreen = { applyFullscreen(!fullscreen) },
                onFullscreenChange = applyFullscreen,
                hapticEnabled = gestureHaptic,
                volumeBrightnessEnabled = gestureVolBright,
                fullscreenHintEnabled = fullscreenGestureHint,
                conicLightEnabled = playerConicLight,
                qualities = state.source?.qualities.orEmpty(),
                activeQuality = state.activeQuality,
                onSelectQuality = { qn -> viewModel.selectQuality(qn, player.currentPosition) },
                speedLocked = speedLocked,
                onToggleSpeedLock = { container.settings.setSpeedLock(!speedLocked, speed) },
                onSelectSpeed = { value ->
                    longPressSpeed.onActiveChange(false)
                    speed = value
                    if (speedLocked) container.settings.setLockedSpeed(value)
                },
                topBarLayoutFullscreen = topBarLayoutFullscreen,
                topBarLayoutPortrait = topBarLayoutPortrait,
                onToggleTopBarItem = container.settings.playerTopBar::setVisible,
                onMoveTopBarItem = container.settings.playerTopBar::moveItem,
                onBack = { if (fullscreen) applyFullscreen(false) else nav.pop() },
                onRetry = viewModel::retryPlayback,
                modifier = Modifier.fillMaxSize().layerBackdropIf(captureStageBackdrop, stageBackdrop),
                inPipMode = inPipMode,
                danmakuEnabled = danmakuEnabled,
                onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                onDanmakuSettingsClick = { danmakuSettingsDialogVisible = true },
                subtitleLabel = subtitleController.buttonLabel,
                subtitleTracks = subtitleController.tracks,
                selectedSubtitleKey = subtitleController.selectedTrackKey,
                onSelectSubtitle = subtitleController::selectTrack,
                onEnterPip = if (canEnterPip(context)) { { enterPipNow(context) } } else null,
                onHome = returnHome,
                onCast = if ((viewModel.currentEpisode?.epId ?: epId) > 0L || (viewModel.currentEpisode?.cid ?: 0L) > 0L) {
                    { castDialogVisible = true }
                } else null,
                onDownload = if (state.detail != null) ({ downloadDialogVisible = true }) else null,
                playbackEndMode = playbackEndMode,
                onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
                longPressSpeed = longPressSpeed,
                longPressSpeedMode = resolveLongPressSpeedMode(adjustableLongPressSpeed, swipeDownLockLongPressSpeed),
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
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                danmakuActions = rememberDanmakuActions(
                    aid = episode?.aid ?: 0L,
                    cid = episode?.cid ?: 0L,
                    fullscreen = fullscreen,
                    positionMs = { player.currentPosition.coerceAtLeast(0L) },
                    isLoggedIn = { container.session.isLoggedIn() },
                    repository = container.danmaku,
                    controller = danmakuController,
                    scope = scope,
                    onShowMessage = { danmakuActionMessage = it },
                    onShowLoginHint = { danmakuActionMessage = "请先登录后再操作" },
                ),
                seekPreview = rememberSeekPreviewController(container.video, episode?.bvid.orEmpty(), episode?.cid ?: 0L),
            )
            if (fullscreen) {
                VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = true)
                CompositionLocalProvider(
                    LocalBackdrop provides if (BlurEffectsEnabled && !hdrSurface) stageBackdrop else null,
                ) {
                    RenderPgcDialogs(inlineInStage = true)
                }
            }
        },
        detailContent = {
            PgcDetailSection(
                container = container,
                nav = nav,
                state = state,
                aid = episode?.aid ?: 0L,
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
                onToggleFollow = viewModel::toggleFollow,
                onRetry = viewModel::load,
                onSelectEpisode = { index ->
                    viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
                    viewModel.selectEpisode(index)
                },
                onSelectSeason = { id -> nav.push(Route.Pgc(id, 0L)) },
                onDownload = if (state.detail != null) ({ downloadDialogVisible = true }) else null,
                onSeekCommentMs = { ms ->
                    player.seekTo(ms)
                    if (!player.isPlaying) player.play()
                },
                barBackdrops = barBackdrops,
                blurActive = blurActive,
                backEnabled = !fullscreen,
            )
        },
        portraitOverlay = {
            VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = false)
        },
        portraitCollapsedBar = {
            val slotHeight = LocalPortraitSlotHeight.current
            val returnHome = LocalStageReturnHome.current ?: { nav.popToMain() }
            CollapsedVideoBar(
                isPlaying = isPlaying,
                onBack = { nav.pop() },
                onHome = returnHome,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onExpand = { slotHeight.floatValue = 0f },
                playbackEndMode = playbackEndMode,
                onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
                onCast = if ((viewModel.currentEpisode?.epId ?: epId) > 0L || (viewModel.currentEpisode?.cid ?: 0L) > 0L) {
                    { castDialogVisible = true }
                } else {
                    null
                },
                onDownload = if (state.detail != null) ({ downloadDialogVisible = true }) else null,
                backdrop = barBackdrops.barMaterial,
            )
        },
    )

    if (!fullscreen) {
        RenderPgcDialogs(inlineInStage = false)
    }
}

private fun PgcUiState.qualityLabel(): String? {
    val source = source ?: return null
    val active = source.qualities.firstOrNull { it.quality == activeQuality }
    return active?.label?.takeIf { it.isNotBlank() } ?: "${activeQuality}P"
}
