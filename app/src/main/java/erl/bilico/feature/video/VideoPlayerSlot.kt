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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import erl.bilico.data.model.VideoEpisode
import erl.bilico.di.AppContainer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop

/**
 * 视频舞台上的播放器与它自带的一整套浮层。
 *
 * 只在播放器挂上之后才组合：展开动画期间这里是空的，播放器专属的设置订阅
 * （手势、长按倍速、弹幕样式、SponsorBlock 分类）也就不会提前开跑。
 */
@Composable
internal fun BoxScope.VideoPlayerSlot(
    player: ExoPlayer,
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
    reportVideoDialog: (@Composable (Boolean) -> Unit)? = null,
    onReportVideo: (() -> Unit)? = null,
    onFullscreenChange: (Boolean) -> Unit,
    onEnterPip: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stageBackdrop = rememberLayerBackdrop()

    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val adjustableLongPressSpeed by container.settings.adjustableLongPressSpeed.collectAsStateWithLifecycle()
    val swipeDownLockLongPressSpeed by container.settings.swipeDownLockLongPressSpeed.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by container.settings.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sponsorSkipMode by container.settings.sponsorSkipMode.collectAsStateWithLifecycle()
    val sponsorCategories by container.settings.sponsorCategories.collectAsStateWithLifecycle()
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()
    val topBarLayoutFullscreen by container.settings.playerTopBar.fullscreen.collectAsStateWithLifecycle()
    val topBarLayoutPortrait by container.settings.playerTopBar.portrait.collectAsStateWithLifecycle()

    val anyStageDialogOpen = fullscreen &&
        (danmakuSettingsDialogVisible || sponsorSubmitDialog != null ||
            castDialog != null || reportVideoDialog != null)
    val episodesMenuItems = rememberEpisodesMenuItems(
        detail = state.detail,
        selectedCid = state.selectedCid,
        onSelectPart = onSelectPart,
        onSelectEpisode = onSelectEpisode,
    )

    VideoPlayer(
        player = player,
        title = state.detail?.title.orEmpty(),
        isPlaying = isPlaying,
        positionMs = clock.positionMs,
        durationMs = clock.durationMs.takeIf { it > 0L } ?: state.source?.durationMs ?: 0L,
        isBuffering = state.isPlaybackLoading || isBuffering,
        errorMessage = state.playbackError ?: playerError,
        qualityLabel = state.qualityLabel(),
        source = state.source,
        hdrVideo = hdrVideo,
        speed = speed,
        fullscreen = fullscreen,
        portraitFullscreen = portraitFullscreen,
        onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
        onSeek = { player.seekTo(it) },
        onToggleFullscreen = { onFullscreenChange(!fullscreen) },
        onFullscreenChange = onFullscreenChange,
        hapticEnabled = gestureHaptic,
        volumeBrightnessEnabled = gestureVolBright,
        fullscreenHintEnabled = fullscreenGestureHint,
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
            .layerBackdropIf(BlurEffectsEnabled && anyStageDialogOpen, stageBackdrop),
        inPipMode = inPipMode,
        danmakuEnabled = danmakuEnabled,
        onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
        onDanmakuSettingsClick = onOpenDanmakuSettings,
        subtitleLabel = subtitleController.buttonLabel,
        subtitleTracks = subtitleController.tracks,
        selectedSubtitleKey = subtitleController.selectedTrackKey,
        onSelectSubtitle = subtitleController::selectTrack,
        onEnterPip = onEnterPip,
        onHome = { nav.popToMain() },
        onWatchLater = viewModel::addToWatchLater,
        playbackEndMode = playbackEndMode,
        onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
        onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
        onCast = onCast,
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
                positionMs = clock.positionMs,
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
                onDismissDanmakuSettings = onDismissDanmakuSettings,
                sponsorSubmitDialog = sponsorSubmitDialog,
                castDialog = castDialog,
                reportVideoDialog = reportVideoDialog,
                inlineInStage = true,
            )
        }
    }
}

/** 播放器槽位还没挂上时的黑底占位。 */
@Composable
internal fun EmptyVideoPlayerSlot(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.background(Color.Black))
}

/** 竖屏视频收成一条横栏时的那条栏（这里只管画，显隐由舞台决定）。 */
@Composable
internal fun VideoCollapsedBarSlot(
    player: ExoPlayer?,
    isPlaying: Boolean,
    container: AppContainer,
    nav: NavState,
    bvid: String,
    state: VideoUiState,
    backdrop: LayerBackdrop?,
    onCast: (() -> Unit)? = null,
    onReportVideo: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val slotHeight = LocalPortraitSlotHeight.current
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()
    CollapsedVideoBar(
        isPlaying = isPlaying,
        onBack = { nav.pop() },
        onHome = { nav.popToMain() },
        onPlayPause = {
            val p = player ?: return@CollapsedVideoBar
            if (p.isPlaying) p.pause() else p.play()
        },
        onExpand = { slotHeight.floatValue = 0f },
        playbackEndMode = playbackEndMode,
        onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
        onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
        onCast = onCast,
        onReportVideo = onReportVideo,
        backdrop = backdrop,
    )
}
