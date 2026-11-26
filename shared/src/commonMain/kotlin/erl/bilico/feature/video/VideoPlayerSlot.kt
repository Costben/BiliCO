package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.VideoEpisode
import erl.bilico.di.AppContainer
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.player.BiliPlayer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop

@Composable
internal fun BoxScope.VideoPlayerSlot(
    player: BiliPlayer,
    clock: VideoPlaybackClock,
    state: VideoUiState,
    container: AppContainer,
    viewModel: VideoViewModel,
    nav: NavState,
    bvid: String,
    fullscreen: Boolean,
    portraitFullscreen: Boolean,
    inPipMode: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playerError: String?,
    message: String?,
    hdrVideo: Boolean,
    speed: Float,
    speedLocked: Boolean,
    longPressSpeed: LongPressSpeedController?,
    onSelectSpeed: (Float) -> Unit,
    onToggleSpeedLock: () -> Unit,
    danmakuEnabled: Boolean,
    onOpenDanmakuSettings: () -> Unit,
    subtitleController: SubtitleController,
    danmakuController: DanmakuController,
    sponsorController: SponsorBlockController,
    danmakuDraft: String,
    onDanmakuDraftChange: (String) -> Unit,
    onSendDanmaku: () -> Unit,
    onSelectPart: (Int) -> Unit,
    onSelectEpisode: (VideoEpisode) -> Unit,
    danmakuSettingsDialogVisible: Boolean,
    onDismissDanmakuSettings: () -> Unit,
    sponsorSubmitDialog: (@Composable (Boolean) -> Unit)?,
    onSubmitSponsor: (() -> Unit)?,
    castDialog: (@Composable (Boolean) -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    downloadDialog: (@Composable (Boolean) -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    reportVideoDialog: (@Composable (Boolean) -> Unit)? = null,
    onReportVideo: (() -> Unit)? = null,
    onFullscreenChange: (Boolean) -> Unit,
    onEnterPip: (() -> Unit)?,
    autoNextController: AutoNextRecommendController? = null,
    showAutoNextInPlayer: Boolean = true,
) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val captureStageBackdrop = BlurEffectsEnabled && fullscreen && !hdrVideo
    val stageBackdropActive = captureStageBackdrop && (
        danmakuSettingsDialogVisible ||
            sponsorSubmitDialog != null ||
            castDialog != null ||
            downloadDialog != null ||
            reportVideoDialog != null
        )
    val stageBackdrop = rememberStageVideoBackdrop(
        player = player,
        active = stageBackdropActive,
        isPlaying = isPlaying,
    )

    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val adjustableLongPressSpeed by container.settings.adjustableLongPressSpeed.collectAsStateWithLifecycle()
    val swipeDownLockLongPressSpeed by container.settings.swipeDownLockLongPressSpeed.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val playerConicLight by container.settings.playerConicLight.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by container.settings.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sponsorSkipMode by container.settings.sponsorSkipMode.collectAsStateWithLifecycle()
    val sponsorCategories by container.settings.sponsorCategories.collectAsStateWithLifecycle()
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()
    val topBarLayoutFullscreen by container.settings.playerTopBar.fullscreen.collectAsStateWithLifecycle()
    val topBarLayoutPortrait by container.settings.playerTopBar.portrait.collectAsStateWithLifecycle()

    val returnHome = LocalStageReturnHome.current ?: { nav.popToMain() }
    val episodesMenuItems = rememberEpisodesMenuItems(
        detail = state.detail,
        selectedCid = state.selectedCid,
        onSelectPart = onSelectPart,
        onSelectEpisode = onSelectEpisode,
    )
    val seekPreview = rememberSeekPreviewController(
        repository = container.video,
        bvid = if (state.offlineMode) "" else bvid,
        cid = state.selectedCid,
    )

    VideoPlayer(
        player = player,
        title = state.detail?.title.orEmpty(),
        isPlaying = isPlaying,
        positionMs = 0L,
        durationMs = clock.durationMs.takeIf { it > 0L } ?: state.source?.durationMs ?: 0L,
        bufferedMs = 0L,
        positionMsProvider = { clock.positionMs },
        bufferedMsProvider = { clock.bufferedMs },
        isBuffering = state.isPlaybackLoading || isBuffering,
        errorMessage = state.playbackError ?: playerError,
        qualityLabel = state.qualityLabel(),
        source = state.source,
        hdrVideo = hdrVideo,
        speed = speed,
        fullscreen = fullscreen,
        portraitFullscreen = portraitFullscreen,
        onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
        onSeek = { clock.seekTo(player, it) },
        onToggleFullscreen = { onFullscreenChange(!fullscreen) },
        onFullscreenChange = onFullscreenChange,
        hapticEnabled = gestureHaptic,
        volumeBrightnessEnabled = gestureVolBright,
        fullscreenHintEnabled = fullscreenGestureHint,
        conicLightEnabled = playerConicLight,
        qualities = state.source?.qualities.orEmpty(),
        activeQuality = state.activeQuality,
        onSelectQuality = { qn -> viewModel.selectQuality(qn, player.currentPosition) },
        speedLocked = speedLocked,
        onToggleSpeedLock = onToggleSpeedLock,
        onSelectSpeed = onSelectSpeed,
        topBarLayoutFullscreen = topBarLayoutFullscreen,
        topBarLayoutPortrait = topBarLayoutPortrait,
        onToggleTopBarItem = container.settings.playerTopBar::setVisible,
        onMoveTopBarItem = container.settings.playerTopBar::moveItem,
        onBack = { if (fullscreen) onFullscreenChange(false) else nav.pop() },
        onRetry = viewModel::load,
        modifier = Modifier
            .fillMaxSize()
            .layerBackdropIf(captureStageBackdrop, stageBackdrop),
        inPipMode = inPipMode,
        danmakuEnabled = danmakuEnabled,
        onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
        onDanmakuSettingsClick = onOpenDanmakuSettings,
        subtitleLabel = subtitleController.buttonLabel,
        subtitleTracks = subtitleController.tracks,
        selectedSubtitleKey = subtitleController.selectedTrackKey,
        onSelectSubtitle = subtitleController::selectTrack,
        onEnterPip = onEnterPip,
        onHome = returnHome,
        onWatchLater = viewModel::addToWatchLater,
        playbackEndMode = playbackEndMode,
        onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
        onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
        onCast = onCast,
        onDownload = onDownload,
        onReportVideo = onReportVideo,
        longPressSpeed = longPressSpeed,
        longPressSpeedMode = resolveLongPressSpeedMode(
            adjustableLongPressSpeed,
            swipeDownLockLongPressSpeed,
        ),
        chapters = subtitleController.chapters,
        episodesMenuItems = episodesMenuItems,
        sponsorSegments = if (sponsorBlockEnabled) {
            sponsorController.segments.filter { it.category.id in sponsorCategories }
        } else {
            emptyList()
        },
        onSubmitSponsor = onSubmitSponsor,
        onJumpSponsorSegment = { segment -> sponsorController.jumpToSegment(player, segment) },
        sponsorOverlay = { bottomPad, startPad ->
            if (sponsorBlockEnabled && sponsorSkipMode >= 0) {
                SponsorBlockPromptOverlay(
                    undoNotice = sponsorController.undoNotice,
                    promptSegment = sponsorController.promptSegment,
                    poiSegment = sponsorController.poiSegment,
                    poiRemainingSeconds = sponsorController.poiRemainingSeconds,
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
        autoNextOverlay = { bottomPad, endPad ->
            if (showAutoNextInPlayer && autoNextController != null) {
                AutoNextRecommendOverlay(
                    video = autoNextController.target,
                    remainingSeconds = autoNextController.remainingSeconds,
                    totalSeconds = autoNextController.totalSeconds,
                    fullscreen = fullscreen,
                    bottomPad = bottomPad,
                    endPad = endPad,
                    onPlayNow = {
                        autoNextController.consumeTarget()?.let { next ->
                            nav.replaceTop(Route.Video(next.bvid, 0L))
                        }
                    },
                    onDismiss = autoNextController::cancel,
                )
            }
        },
        subtitleOverlay = { chromeVisible ->
            SubtitleOverlay(
                cues = subtitleController.cues,
                positionMs = 0L,
                positionMsProvider = { clock.positionMs },
                fullscreen = fullscreen,
                controlsVisible = chromeVisible,
                inPip = inPipMode,
                modifier = Modifier.fillMaxSize(),
            )
        },
        danmakuComposer = { onFocusChange ->
            FullscreenDanmakuComposer(
                draft = danmakuDraft,
                onDraftChange = onDanmakuDraftChange,
                onSend = onSendDanmaku,
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
        seekPreview = seekPreview,
    )

    if (fullscreen) {
        VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = true)
        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled && !hdrVideo) stageBackdrop else null,
        ) {
            VideoScreenDialogs(
                coinDialogVisible = false,
                coined = state.coined,
                onSelectCoin = {},
                onDismissCoin = {},
                favoriteFolders = null,
                onConfirmFavorite = { _, _, _ -> },
                onDismissFavorite = {},
                danmakuSettingsDialogVisible = danmakuSettingsDialogVisible,
                danmakuSettings = danmakuSettings,
                settingsRepository = container.settings,
                onDismissDanmakuSettings = onDismissDanmakuSettings,
                sponsorSubmitDialog = sponsorSubmitDialog,
                castDialog = castDialog,
                downloadDialog = downloadDialog,
                reportVideoDialog = reportVideoDialog,
                inlineInStage = true,
            )
        }
    }
}

@Composable
internal fun EmptyVideoPlayerSlot(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.background(Color.Black))
}

@Composable
internal fun VideoCollapsedBarSlot(
    player: BiliPlayer?,
    isPlaying: Boolean,
    container: AppContainer,
    nav: NavState,
    bvid: String,
    state: VideoUiState,
    backdrop: Backdrop?,
    onCast: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onReportVideo: (() -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val slotHeight = LocalPortraitSlotHeight.current
    val returnHome = LocalStageReturnHome.current ?: { nav.popToMain() }
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()
    CollapsedVideoBar(
        isPlaying = isPlaying,
        onBack = { nav.pop() },
        onHome = returnHome,
        onPlayPause = {
            val p = player ?: return@CollapsedVideoBar
            if (p.isPlaying) p.pause() else p.play()
        },
        onExpand = { slotHeight.floatValue = 0f },
        playbackEndMode = playbackEndMode,
        onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
        onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
        onCast = onCast,
        onDownload = onDownload,
        onReportVideo = onReportVideo,
        backdrop = backdrop,
    )
}
