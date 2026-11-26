package erl.bilico.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.QualityOption
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.model.SubtitleTrack
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.settings.PlayerTopBarLayout
import erl.bilico.data.settings.PlayerTopBarMode
import erl.bilico.di.LocalAppContainer
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.readSystemVolume
import erl.bilico.platform.showToast
import erl.bilico.platform.writeSystemVolume
import erl.bilico.player.BiliPlayer
import erl.bilico.player.BiliPlayerSurface
import erl.bilico.player.SleepTimerState
import erl.bilico.player.VideoResizeMode
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteKeepSurface
import erl.bilico.ui.LocalRouteMinimized
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.PlayerStateOverlay
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import kotlinx.coroutines.delay

@Composable
internal fun VideoPlayer(
    player: BiliPlayer,
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long = 0L,
    isBuffering: Boolean,
    errorMessage: String?,
    qualityLabel: String?,
    source: PlaybackSource? = null,
    hdrVideo: Boolean = false,
    speed: Float,
    fullscreen: Boolean,
    portraitFullscreen: Boolean = false,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = { if (it) onToggleFullscreen() },
    hapticEnabled: Boolean = true,
    volumeBrightnessEnabled: Boolean = true,
    fullscreenHintEnabled: Boolean = true,
    conicLightEnabled: Boolean = true,
    qualities: List<QualityOption> = emptyList(),
    activeQuality: Int = 0,
    onSelectQuality: (Int) -> Unit = {},
    speedLocked: Boolean = false,
    onToggleSpeedLock: () -> Unit = {},
    onSelectSpeed: (Float) -> Unit = {},
    topBarLayoutFullscreen: PlayerTopBarLayout = PlayerTopBarLayout(),
    topBarLayoutPortrait: PlayerTopBarLayout = PlayerTopBarLayout(),
    onToggleTopBarItem: ((PlayerTopBarMode, String, Boolean) -> Unit)? = null,
    onMoveTopBarItem: ((PlayerTopBarMode, Int, Int) -> Unit)? = null,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    inPipMode: Boolean = false,
    danmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit = {},
    onDanmakuSettingsClick: (() -> Unit)? = null,
    subtitleLabel: String? = null,
    subtitleTracks: List<SubtitleTrack> = emptyList(),
    selectedSubtitleKey: String? = null,
    onSelectSubtitle: (String?) -> Unit = {},
    onEnterPip: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onWatchLater: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onReportVideo: (() -> Unit)? = null,
    playbackEndMode: PlaybackEndMode = PlaybackEndMode.AUTO_NEXT,
    onSelectPlaybackEndMode: ((PlaybackEndMode) -> Unit)? = null,
    longPressSpeed: LongPressSpeedController? = null,
    longPressSpeedMode: LongPressSpeedMode = LongPressSpeedMode.FIXED,
    liveMode: Boolean = false,
    statusText: String? = null,
    audioOnly: Boolean = false,
    onToggleAudioOnly: (() -> Unit)? = null,
    onSelectLine: (() -> Unit)? = null,
    superChatCount: Int = 0,
    onSuperChatsClick: (() -> Unit)? = null,
    audioOnlyOverlay: (@Composable BoxScope.() -> Unit)? = null,
    chapters: List<VideoChapter> = emptyList(),
    episodesMenuItems: List<CouiMenuItem> = emptyList(),
    sponsorSegments: List<SponsorSegment> = emptyList(),
    onSubmitSponsor: (() -> Unit)? = null,
    onJumpSponsorSegment: ((SponsorSegment) -> Unit)? = null,
    sponsorOverlay: @Composable BoxScope.(bottomPad: Dp, startPad: Dp) -> Unit = { _, _ -> },
    autoNextOverlay: @Composable BoxScope.(bottomPad: Dp, endPad: Dp) -> Unit = { _, _ -> },
    subtitleOverlay: @Composable (controlsVisible: Boolean) -> Unit = {},
    overlay: @Composable () -> Unit = {},
    danmakuComposer: (@Composable (onFocusChange: (Boolean) -> Unit) -> Unit)? = null,
    danmakuActions: DanmakuActionCallbacks? = null,
    seekPreview: SeekPreviewController? = null,
    onScreenLockedChange: ((Boolean) -> Unit)? = null,
) {
    val container = LocalAppContainer.current
    val platformContext = LocalPlatformContext.current
    val sleepTimerController = container?.sleepTimer
    val sleepTimerState = sleepTimerController?.state?.collectAsStateWithLifecycle()?.value ?: SleepTimerState()
    val detailAutoPlay = container?.settings?.detailAutoPlay?.collectAsStateWithLifecycle()?.value ?: true
    val zoomState = rememberPlayerZoomState()
    val haptic = rememberCouiHaptic()
    val screenLock = rememberPlayerScreenLockState(fullscreen = fullscreen, inPipMode = inPipMode)
    var controlsVisible by remember { mutableStateOf(!liveMode && !detailAutoPlay) }
    var activeMenu by remember { mutableStateOf(PlayerPopupMenu.NONE) }
    var lastBottomMenu by remember { mutableStateOf(PlayerPopupMenu.SPEED) }
    var statsVisible by remember { mutableStateOf(false) }
    var topBarSettingsVisible by remember { mutableStateOf(false) }
    var sponsorAssistVisible by remember { mutableStateOf(false) }
    var sleepTimerVisible by remember { mutableStateOf(false) }
    var selectedDanmaku by remember { mutableStateOf<DanmakuHit?>(null) }
    val danmakuInteraction = remember { DanmakuInteractionHost() }
    var stageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var episodesEndMargin by remember { mutableStateOf(224.dp) }
    var speedEndMargin by remember { mutableStateOf(120.dp) }
    var qualityEndMargin by remember { mutableStateOf(64.dp) }
    var subtitleEndMargin by remember { mutableStateOf(176.dp) }
    var composerFocused by remember { mutableStateOf(false) }
    var scrubbingPositionMs by remember { mutableStateOf<Long?>(null) }
    var prevFullscreen by remember { mutableStateOf(fullscreen) }

    LaunchedEffect(fullscreen) {
        if (prevFullscreen != fullscreen) {
            prevFullscreen = fullscreen
            controlsVisible = false
            screenLock.reset()
            activeMenu = PlayerPopupMenu.NONE
            statsVisible = false
            topBarSettingsVisible = false
            sponsorAssistVisible = false
            sleepTimerVisible = false
            selectedDanmaku = null
            zoomState.reset()
        }
    }
    LaunchedEffect(source?.videoUrl) {
        zoomState.reset()
    }
    LaunchedEffect(inPipMode, danmakuActions == null) {
        if (inPipMode || danmakuActions == null) selectedDanmaku = null
    }
    danmakuInteraction.selectedHit = selectedDanmaku

    LaunchedEffect(
        controlsVisible,
        activeMenu,
        composerFocused,
        scrubbingPositionMs != null,
        isPlaying,
        isBuffering,
        errorMessage,
    ) {
        if (
            controlsVisible && activeMenu == PlayerPopupMenu.NONE && !composerFocused &&
            scrubbingPositionMs == null && isPlaying && !isBuffering && errorMessage == null
        ) {
            delay(4500)
            controlsVisible = false
        }
    }

    val effectiveLocked = fullscreen && !inPipMode && screenLock.locked
    val stageLockObserver = LocalStageScreenLockObserver.current
    val currentOnScreenLockedChange by rememberUpdatedState(onScreenLockedChange)
    val currentStageLockObserver by rememberUpdatedState(stageLockObserver)
    LaunchedEffect(effectiveLocked) {
        currentOnScreenLockedChange?.invoke(effectiveLocked)
        currentStageLockObserver?.invoke(effectiveLocked)
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnScreenLockedChange?.invoke(false)
            currentStageLockObserver?.invoke(false)
        }
    }
    val showChrome = !inPipMode && !effectiveLocked && (controlsVisible || errorMessage != null)
    LaunchedEffect(fullscreen, showChrome) {
        if (!showChrome) activeMenu = PlayerPopupMenu.NONE
        if (showChrome && !liveMode) seekPreview?.ensureLoaded(positionMs, durationMs)
    }
    val horizontalBarPad = if (fullscreen) 24.dp else 12.dp
    val topBarMode = if (fullscreen) PlayerTopBarMode.FULLSCREEN else PlayerTopBarMode.PORTRAIT
    val topBarItems = if (fullscreen) {
        topBarLayoutFullscreen.visibleItems(topBarMode)
    } else {
        topBarLayoutPortrait.visibleItems(topBarMode)
    }
    val openTopBarSettings: (() -> Unit)? = onToggleTopBarItem?.let {
        { controlsVisible = false; activeMenu = PlayerPopupMenu.NONE; topBarSettingsVisible = true }
    }
    val openSleepTimer: (() -> Unit)? = sleepTimerController?.let {
        { controlsVisible = false; activeMenu = PlayerPopupMenu.NONE; sleepTimerVisible = true }
    }
    val toggleDetailAutoPlay: (() -> Unit)? = if (liveMode) null else container?.settings?.let { settings ->
        {
            val next = !settings.detailAutoPlay.value
            settings.setDetailAutoPlay(next)
            showToast(platformContext, if (next) "已开启详情页自动播放" else "已关闭详情页自动播放")
        }
    }
    val showStageComposer = fullscreen && !portraitFullscreen && danmakuComposer != null
    val portraitTopPad = if (portraitFullscreen) {
        with(LocalDensity.current) { rememberStableStatusBarHeightPx().toDp() }
    } else {
        0.dp
    }

    val captureMenuBackdrop = BlurEffectsEnabled && fullscreen && !hdrVideo
    val menuBackdropActive = captureMenuBackdrop && (
        activeMenu != PlayerPopupMenu.NONE ||
            statsVisible ||
            sponsorAssistVisible ||
            topBarSettingsVisible ||
            sleepTimerVisible
        )
    val menuBackdrop = rememberStageVideoBackdrop(
        player = player,
        active = menuBackdropActive,
        isPlaying = isPlaying,
    )
    val isTop = LocalRouteIsTop.current
    val keepSurface = LocalRouteKeepSurface.current
    val minimized = LocalRouteMinimized.current
    val frozenVideo = LocalStageFrozenVideo.current
    val stagePlayerVisible = LocalStagePlayerVisible.current
    RoutePreviewCrossfadePauseEffect(player = player, entry = LocalNavEntry.current)
    val keepScreenOn = isTop && !inPipMode && rememberPlaybackScreenOn(player)
    KeepScreenOnEffect(keepScreenOn)

    val speedMenuItems = rememberSpeedMenuItems(
        activeSpeed = speed,
        onSelectSpeed = { onSelectSpeed(it); activeMenu = PlayerPopupMenu.NONE },
    )
    val qualityMenuItems = rememberQualityMenuItems(
        qualities = qualities,
        activeQuality = activeQuality,
        onSelectQuality = { onSelectQuality(it); activeMenu = PlayerPopupMenu.NONE },
    )
    val subtitleMenuItems = rememberSubtitleMenuItems(
        tracks = subtitleTracks,
        selectedKey = selectedSubtitleKey,
        onSelectSubtitle = { onSelectSubtitle(it); activeMenu = PlayerPopupMenu.NONE },
    )
    val stageOpaquePlayer = LocalStageOpaquePlayer.current
    val stageVideoAspect = LocalStageVideoAspect.current
    val stageModifier = if (stageOpaquePlayer && stagePlayerVisible) modifier.background(Color.Black) else modifier
    RegisterSurfaceViewEntry(hdrVideo)

    Box(stageModifier.onGloballyPositioned { stageCoords = it }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdropIf(captureMenuBackdrop, menuBackdrop),
        ) {
            if (!minimized && !frozenVideo && stagePlayerVisible && !audioOnly && keepSurface) {
                Box(
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    BiliPlayerSurface(
                        player = player,
                        resizeMode = VideoResizeMode.FIT,
                        modifier = Modifier
                            .fitVideoSurfaceAspect(stageVideoAspect)
                            .graphicsLayer {
                                scaleX = zoomState.scale
                                scaleY = zoomState.scale
                                translationX = zoomState.offsetX
                                translationY = zoomState.offsetY
                                rotationZ = zoomState.rotationDegrees
                            },
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(if (stagePlayerVisible && stageOpaquePlayer) Color.Black else Color.Transparent),
                )
            }

            if (audioOnly && audioOnlyOverlay != null) {
                audioOnlyOverlay()
            }

            CompositionLocalProvider(LocalDanmakuInteraction provides danmakuInteraction) {
                overlay()
            }

            subtitleOverlay(showChrome)

            if (!inPipMode) {
                if (effectiveLocked) {
                    PlayerLockedTouchShield(
                        onTap = screenLock::toggleHint,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PlayerGestureLayer(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        seekEnabled = !liveMode,
                        longPressSpeed = if (liveMode) null else longPressSpeed,
                        longPressSpeedMode = longPressSpeedMode,
                        zoomState = if (audioOnly) null else zoomState,
                        seekPreview = if (liveMode) null else seekPreview,
                        chapters = chapters,
                        onSeekStart = { if (!liveMode) seekPreview?.ensureLoaded(positionMs, durationMs) },
                        fullscreen = fullscreen,
                        hapticEnabled = hapticEnabled,
                        volumeBrightnessEnabled = volumeBrightnessEnabled,
                        fullscreenHintEnabled = fullscreenHintEnabled,
                        onTap = { offset ->
                            val hit = if (selectedDanmaku == null && danmakuActions != null) {
                                danmakuInteraction.hitTest?.invoke(offset)
                            } else {
                                null
                            }
                            when {
                                selectedDanmaku != null -> selectedDanmaku = null
                                hit != null -> {
                                    selectedDanmaku = hit
                                    controlsVisible = false
                                }
                                else -> controlsVisible = !controlsVisible
                            }
                        },
                        onDoubleTap = {
                            controlsVisible = true
                            onPlayPause()
                        },
                        onSeek = onSeek,
                        onFullscreenChange = onFullscreenChange,
                        volumeProvider = { readSystemVolume() },
                        onVolumeChange = { writeSystemVolume(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            PlayerStatsFloatingOverlay(
                player = player, source = source, fullscreen = fullscreen,
                inPipMode = inPipMode, modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(tween(200, easing = ColorOSMoveEase)) +
                    slideInVertically(tween(200, easing = ColorOSMoveEase)) { -it / 3 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 3 },
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                PlayerTopBar(
                    title = title, items = topBarItems, fullscreen = fullscreen,
                    portraitFullscreen = portraitFullscreen, danmakuEnabled = danmakuEnabled,
                    audioOnly = audioOnly, horizontalBarPad = horizontalBarPad, portraitTopPad = portraitTopPad,
                    sleepTimerState = sleepTimerState, detailAutoPlay = detailAutoPlay,
                    actions = PlayerTopBarActions(
                        onBack = onBack, onWatchLater = onWatchLater,
                        onDanmakuSettings = onDanmakuSettingsClick?.let { open -> { controlsVisible = false; open() } },
                        onToggleDanmaku = onToggleDanmaku, onShare = onShare,
                        onCast = onCast?.let { cast -> { controlsVisible = false; cast() } },
                        onShowStats = { controlsVisible = false; statsVisible = true },
                        onOpenSponsor = if (onJumpSponsorSegment != null || onSubmitSponsor != null) {
                            { controlsVisible = false; sponsorAssistVisible = true }
                        } else null,
                        onOpenSleepTimer = openSleepTimer,
                        onToggleDetailAutoPlay = toggleDetailAutoPlay,
                        onHome = onHome, onToggleAudioOnly = onToggleAudioOnly, onEnterPip = onEnterPip,
                        onOpenMoreMenu = {
                            activeMenu = if (activeMenu == PlayerPopupMenu.MORE) PlayerPopupMenu.NONE else PlayerPopupMenu.MORE
                        },
                        portraitMoreMenu = {
                            PlayerMoreMenu(
                                onShare = onShare, onCast = onCast, onDownload = onDownload, onSelectLine = onSelectLine,
                                audioOnly = audioOnly, onToggleAudioOnly = onToggleAudioOnly,
                                onReportVideo = onReportVideo, playbackEndMode = playbackEndMode,
                                onSelectPlaybackEndMode = onSelectPlaybackEndMode,
                                onSubmitSponsor = onSubmitSponsor, onShowStats = { statsVisible = true },
                                onOpenTopBarSettings = openTopBarSettings,
                                sleepTimerSummary = sleepTimerState.summary, onOpenSleepTimer = openSleepTimer,
                                detailAutoPlay = detailAutoPlay, onToggleDetailAutoPlay = toggleDetailAutoPlay,
                            )
                        },
                    ),
                )
            }

            PlayerStateOverlay(errorMessage = errorMessage, isBuffering = isBuffering, inPipMode = inPipMode, onRetry = onRetry)

            if (!inPipMode && !liveMode) {
                val bottomChromePad = when {
                    !showChrome -> 16.dp
                    chapters.isNotEmpty() -> 110.dp
                    else -> 76.dp
                }
                sponsorOverlay(bottomChromePad, horizontalBarPad)
                autoNextOverlay(bottomChromePad, horizontalBarPad)
            }

            if (!inPipMode && !audioOnly && !effectiveLocked) {
                PlayerZoomOverlay(
                    zoom = zoomState,
                    showChrome = showChrome,
                    topOffset = portraitTopPad,
                    hideResetPill = longPressSpeed?.active == true,
                )
            }

            if (fullscreen && !inPipMode) {
                val lockStartPad = rememberScreenLockStartPad(horizontalBarPad)
                PlayerScreenLockButton(
                    visible = if (effectiveLocked) screenLock.hintVisible else (showChrome && activeMenu == PlayerPopupMenu.NONE && !composerFocused),
                    locked = effectiveLocked,
                    startPad = lockStartPad,
                    onToggleLock = {
                        if (hapticEnabled) haptic(CouiHapticEffect.Short)
                        if (screenLock.locked) {
                            screenLock.unlock()
                            controlsVisible = true
                        } else {
                            screenLock.lock()
                            controlsVisible = false
                            activeMenu = PlayerPopupMenu.NONE
                            selectedDanmaku = null
                            longPressSpeed?.onActiveChange?.invoke(false)
                        }
                    },
                )
            }

            val activeScrubMs = scrubbingPositionMs
            if (!inPipMode && !liveMode && showChrome && activeScrubMs != null && durationMs > 0L) {
                val previewBottomPad = if (chapters.isNotEmpty()) 112.dp else 82.dp
                SeekPreviewFloatingOverlay(
                    scrubbingPositionMs = activeScrubMs,
                    durationMs = durationMs,
                    preview = seekPreview,
                    chapters = chapters,
                    fullscreen = fullscreen,
                    bottomPadding = previewBottomPad,
                    horizontalPad = horizontalBarPad,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            PlayerBottomChrome(
                showChrome = showChrome,
                liveMode = liveMode,
                fullscreen = fullscreen,
                horizontalBarPad = horizontalBarPad,
                chapters = chapters,
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                scrubbingPositionMs = scrubbingPositionMs,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                conicLightEnabled = conicLightEnabled,
                holdToSpeedUp = longPressSpeed?.active == true,
                hapticEnabled = hapticEnabled,
                sponsorSegments = sponsorSegments,
                statusText = statusText,
                showStageComposer = showStageComposer,
                danmakuComposer = danmakuComposer,
                onComposerFocusedChange = { composerFocused = it },
                onScrubPositionChange = { pos ->
                    if (pos != null) seekPreview?.ensureLoaded(pos, durationMs)
                    scrubbingPositionMs = pos
                },
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                subtitleLabel = subtitleLabel,
                speed = speed,
                qualityLabel = qualityLabel,
                onSelectLine = onSelectLine,
                onSuperChatsClick = onSuperChatsClick?.let { open -> { controlsVisible = false; open() } },
                superChatCount = superChatCount,
                activeMenu = activeMenu,
                onOpenMenu = { menu -> if (menu != PlayerPopupMenu.MORE) lastBottomMenu = menu; activeMenu = menu },
                onDismissMenu = { activeMenu = PlayerPopupMenu.NONE },
                speedLocked = speedLocked,
                onToggleSpeedLock = onToggleSpeedLock,
                episodesMenuItems = episodesMenuItems,
                speedMenuItems = speedMenuItems,
                qualityMenuItems = qualityMenuItems,
                subtitleMenuItems = subtitleMenuItems,
                stageCoords = stageCoords,
                onEpisodesEndChange = { episodesEndMargin = it },
                onSubtitleEndChange = { subtitleEndMargin = it },
                onSpeedEndChange = { speedEndMargin = it },
                onQualityEndChange = { qualityEndMargin = it },
                onToggleFullscreen = onToggleFullscreen,
            )
        }

        if (fullscreen) {
            PlayerStagePopupMenus(
                activeMenu = activeMenu, lastBottomMenu = lastBottomMenu,
                onDismiss = { activeMenu = PlayerPopupMenu.NONE }, hdrVideo = hdrVideo,
                menuBackdrop = menuBackdrop, horizontalBarPad = horizontalBarPad,
                portraitFullscreen = portraitFullscreen, portraitTopPad = portraitTopPad,
                onHideControls = { controlsVisible = false }, playbackEndMode = playbackEndMode,
                onSelectPlaybackEndMode = onSelectPlaybackEndMode,
                onShare = onShare, onCast = onCast, onDownload = onDownload, onSelectLine = onSelectLine,
                audioOnly = audioOnly, onToggleAudioOnly = onToggleAudioOnly,
                onReportVideo = onReportVideo, onSubmitSponsor = onSubmitSponsor,
                onShowStats = { activeMenu = PlayerPopupMenu.NONE; statsVisible = true },
                onOpenTopBarSettings = openTopBarSettings,
                sleepTimerSummary = sleepTimerState.summary,
                onOpenSleepTimer = openSleepTimer,
                detailAutoPlay = detailAutoPlay,
                onToggleDetailAutoPlay = toggleDetailAutoPlay,
                episodesMenuItems = episodesMenuItems, speedMenuItems = speedMenuItems,
                qualityMenuItems = qualityMenuItems, subtitleMenuItems = subtitleMenuItems,
                episodesEndMargin = episodesEndMargin, speedEndMargin = speedEndMargin,
                qualityEndMargin = qualityEndMargin, subtitleEndMargin = subtitleEndMargin,
                speedLocked = speedLocked, onToggleSpeedLock = onToggleSpeedLock,
            )
        }

        val activeHit = selectedDanmaku
        if (activeHit != null && danmakuActions != null && !inPipMode) {
            DanmakuActionToolbar(activeHit, danmakuActions, { selectedDanmaku = null }, Modifier.fillMaxSize())
        }

        CompositionLocalProvider(LocalBackdrop provides if (BlurEffectsEnabled && fullscreen && !hdrVideo) menuBackdrop else null) {
            PlayerStageDialogs(
                player = player, source = source, fullscreen = fullscreen,
                portraitFullscreen = portraitFullscreen,
                statsVisible = statsVisible, onDismissStats = { statsVisible = false },
                sponsorAssistVisible = sponsorAssistVisible, sponsorSegments = sponsorSegments,
                onJumpSponsorSegment = onJumpSponsorSegment, onSubmitSponsor = onSubmitSponsor,
                onDismissSponsorAssist = { sponsorAssistVisible = false },
                topBarSettingsVisible = topBarSettingsVisible,
                topBarLayoutFullscreen = topBarLayoutFullscreen, topBarLayoutPortrait = topBarLayoutPortrait,
                onToggleTopBarItem = onToggleTopBarItem, onMoveTopBarItem = onMoveTopBarItem,
                onDismissTopBarSettings = { topBarSettingsVisible = false },
            )
            if (sleepTimerVisible && sleepTimerController != null) {
                SleepTimerDialog(
                    controller = sleepTimerController,
                    onDismiss = { sleepTimerVisible = false },
                    inlineInStage = fullscreen,
                )
            }
        }
    }
}
