package erl.bilico.feature.video

import android.view.LayoutInflater
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import erl.bilico.R
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.QualityOption
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.model.SubtitleTrack
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.settings.PlayerTopBarLayout
import erl.bilico.data.settings.PlayerTopBarMode
import erl.bilico.data.util.Formatters
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.PlayerStateOverlay
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

/**
 * 只用 PlayerView 做渲染，手势、字幕与控件全部由 Compose 叠加，保证与 COUI 主题一致。
 *
 * 控件排布照 PiliPlus：顶部左边返回（全屏再多一个回首页），右边弹幕开关、小窗、更多；
 * 底部进度条下面一行是播放键、两行时间，右边字幕 / 倍速 / 清晰度 / 全屏。
 */
@Composable
internal fun VideoPlayer(
    player: Player,
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
    /** 顶栏配置：全屏与竖屏各一份，决定按钮的排列顺序与哪些被隐藏。 */
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
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var activeMenu by remember { mutableStateOf(PlayerPopupMenu.NONE) }
    var lastBottomMenu by remember { mutableStateOf(PlayerPopupMenu.SPEED) }
    var statsVisible by remember { mutableStateOf(false) }
    var topBarSettingsVisible by remember { mutableStateOf(false) }
    var sponsorAssistVisible by remember { mutableStateOf(false) }
    var selectedDanmaku by remember { mutableStateOf<DanmakuHit?>(null) }
    val danmakuInteraction = remember { DanmakuInteractionHost() }
    var stageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var episodesEndMargin by remember { mutableStateOf(224.dp) }
    var speedEndMargin by remember { mutableStateOf(120.dp) }
    var qualityEndMargin by remember { mutableStateOf(64.dp) }
    var subtitleEndMargin by remember { mutableStateOf(176.dp) }
    // 正在输入弹幕或拖动进度条：这时不能让控件条按 4.5 秒自动收。
    var composerFocused by remember { mutableStateOf(false) }
    var scrubbingPositionMs by remember { mutableStateOf<Long?>(null) }
    var prevFullscreen by remember { mutableStateOf(fullscreen) }

    LaunchedEffect(fullscreen) {
        if (prevFullscreen != fullscreen) {
            prevFullscreen = fullscreen
            controlsVisible = false
            activeMenu = PlayerPopupMenu.NONE
            statsVisible = false
            topBarSettingsVisible = false
            sponsorAssistVisible = false
            selectedDanmaku = null
        }
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

    // 只看「要不要显示控件」这一个开关。暂停、缓冲、网络抖动都不能把控件顶回来，
    // 否则点一下屏幕看到控件纹丝不动，像是点不动。
    val showChrome = !inPipMode && (controlsVisible || errorMessage != null)
    LaunchedEffect(fullscreen, showChrome) {
        if (!showChrome) activeMenu = PlayerPopupMenu.NONE
    }
    val horizontalBarPad = if (fullscreen) 24.dp else 12.dp
    // 顶栏按当前模式取一份配置：全屏（横屏 / 竖屏视频全屏）与竖屏各管各的。
    val topBarMode = if (fullscreen) PlayerTopBarMode.FULLSCREEN else PlayerTopBarMode.PORTRAIT
    val topBarItems = if (fullscreen) {
        topBarLayoutFullscreen.visibleItems(topBarMode)
    } else {
        topBarLayoutPortrait.visibleItems(topBarMode)
    }
    // 三个点里的「顶栏设置」；没有挂设置回调时不出现这一项。
    val openTopBarSettings: (() -> Unit)? = if (onToggleTopBarItem != null) {
        {
            controlsVisible = false
            activeMenu = PlayerPopupMenu.NONE
            topBarSettingsVisible = true
        }
    } else {
        null
    }
    // 只有横屏全屏才挂弹幕输入框：竖屏全屏画面本来就窄，页面下面也有聊天区。
    val showStageComposer = fullscreen && !portraitFullscreen && danmakuComposer != null
    // 竖屏全屏时顶栏贴着屏幕顶端，得让开状态栏和挖孔，否则第一行会被挖孔吃进去。
    // 用固定高度而不是实时 inset：全屏收系统栏不会让这两行跟着上下跳。
    val portraitTopPad = if (portraitFullscreen) {
        with(LocalDensity.current) { rememberStableStatusBarHeightPx().toDp() }
    } else {
        0.dp
    }

    // 黑底由全屏舞台决定：横屏转场靠它撑住整窗，竖屏全屏则让画面浮在页面上。
    // 画面一律按原始比例 FIT：槽位多高画面就多高，宽度跟着缩，整幅画面始终完整可见。
    val resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    val menuBackdrop = rememberLayerBackdrop()
    // SurfaceView 的画面不进 Compose 的绘制层，抓出来的背景只有一块黑，不如直接退成不模糊。
    val captureMenuBackdrop = BlurEffectsEnabled && fullscreen && activeMenu != PlayerPopupMenu.NONE && !hdrVideo
    val isTop = LocalRouteIsTop.current
    val minimized = LocalRouteMinimized.current
    val keepScreenOn = isTop && !inPipMode && rememberPlaybackScreenOn(player)
    // 舞台转场用一张静止帧顶替实时画面（SurfaceView 转不动）：在这里交出抓帧入口。
    val frozenVideo = LocalStageFrozenVideo.current
    val frameSource = LocalStageFrameSource.current
    val surfaceHost = remember { mutableStateOf<PlayerView?>(null) }
    // Surface 的缓冲区不一定等于控件尺寸：解码器按视频原始分辨率出图时缓冲区更大，
    // 这时按控件尺寸取只能拿到左上角一块，替身帧铺满整屏就会变成「放大的一个角」。
    val videoSizedBuffer = remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(frameSource) {
        frameSource.capture = { onResult ->
            capturePlayerFrame(
                playerView = surfaceHost.value,
                videoSize = player.videoSize,
                bufferIsVideoSized = videoSizedBuffer,
                onResult = onResult,
            )
        }
        onDispose { frameSource.capture = null }
    }

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
    val stageModifier = if (LocalStageOpaquePlayer.current) modifier.background(Color.Black) else modifier
    RegisterSurfaceViewEntry(hdrVideo)
    val screenModifier = if (keepScreenOn) Modifier.keepScreenOn() else Modifier
    Box(stageModifier.then(screenModifier).onGloballyPositioned { stageCoords = it }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdropIf(captureMenuBackdrop, menuBackdrop),
        ) {
        key(hdrVideo) {
            AndroidView(
                factory = { context ->
                    val layout = if (hdrVideo) {
                        R.layout.view_exo_surface_player
                    } else {
                        R.layout.view_exo_texture_player
                    }
                    (LayoutInflater.from(context)
                        .inflate(layout, null, false) as PlayerView).apply {
                        useController = false
                        this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                            requestedFrameRate = 120f
                            videoSurfaceView?.requestedFrameRate = 120f
                        }
                    }
                },
                update = { view ->
                    surfaceHost.value = view
                    view.player = if (isTop) player else null
                    view.resizeMode = resizeMode
                    // 舞台用替身帧或黑听纯音频期间把视频画面隐藏，避免帧残留或黑屏闪烁。
                    view.visibility = if (minimized || frozenVideo || audioOnly) View.INVISIBLE else View.VISIBLE
                },
                modifier = Modifier.fillMaxSize(),
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
            PlayerGestureLayer(
                positionMs = positionMs,
                durationMs = durationMs,
                seekEnabled = !liveMode,
                longPressSpeed = if (liveMode) null else longPressSpeed,
                longPressSpeedMode = longPressSpeedMode,
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
                    // 双击是播放/暂停，结果必须看得见：暂停时把控件亮出来。
                    controlsVisible = true
                    onPlayPause()
                },
                onSeek = onSeek,
                onFullscreenChange = onFullscreenChange,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(200, easing = ColorOSMoveEase)) +
                slideInVertically(tween(200, easing = ColorOSMoveEase)) { -it / 3 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlayerTopBar(
                title = title,
                items = topBarItems,
                fullscreen = fullscreen,
                portraitFullscreen = portraitFullscreen,
                danmakuEnabled = danmakuEnabled,
                audioOnly = audioOnly,
                horizontalBarPad = horizontalBarPad,
                portraitTopPad = portraitTopPad,
                actions = PlayerTopBarActions(
                    onBack = onBack,
                    onWatchLater = onWatchLater,
                    onDanmakuSettings = onDanmakuSettingsClick?.let { open ->
                        {
                            controlsVisible = false
                            open()
                        }
                    },
                    onToggleDanmaku = onToggleDanmaku,
                    onShare = onShare,
                    onCast = onCast?.let { cast ->
                        {
                            controlsVisible = false
                            cast()
                        }
                    },
                    onShowStats = {
                        controlsVisible = false
                        statsVisible = true
                    },
                    onOpenSponsor = if (onJumpSponsorSegment != null || onSubmitSponsor != null) {
                        {
                            controlsVisible = false
                            sponsorAssistVisible = true
                        }
                    } else {
                        null
                    },
                    onHome = onHome,
                    onToggleAudioOnly = onToggleAudioOnly,
                    onEnterPip = onEnterPip,
                    onOpenMoreMenu = {
                        activeMenu = if (activeMenu == PlayerPopupMenu.MORE) {
                            PlayerPopupMenu.NONE
                        } else {
                            PlayerPopupMenu.MORE
                        }
                    },
                    portraitMoreMenu = {
                        PlayerMoreMenu(
                            onShare = onShare, onCast = onCast, onDownload = onDownload, onSelectLine = onSelectLine,
                            audioOnly = audioOnly, onToggleAudioOnly = onToggleAudioOnly,
                            onReportVideo = onReportVideo, playbackEndMode = playbackEndMode,
                            onSelectPlaybackEndMode = onSelectPlaybackEndMode,
                            onSubmitSponsor = onSubmitSponsor, onShowStats = { statsVisible = true },
                            onOpenTopBarSettings = openTopBarSettings,
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

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(200, easing = ColorOSMoveEase)) +
                slideInVertically(tween(200, easing = ColorOSMoveEase)) { it / 3 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 全屏发弹幕时软键盘会把底栏顶走，整条跟着键盘抬起来。
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(horizontal = horizontalBarPad, vertical = 6.dp),
            ) {
                if (!liveMode && chapters.isNotEmpty()) {
                    PlayerChapterStrip(
                        chapters = chapters,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSelectChapter = { chapter ->
                            onSeek(chapter.fromMs)
                            if (!isPlaying) onPlayPause()
                        },
                    )
                }
                if (!liveMode) {
                    PlayerProgress(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        bufferedMs = bufferedMs,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        conicLightEnabled = conicLightEnabled,
                        holdToSpeedUp = longPressSpeed?.active == true,
                        hapticEnabled = hapticEnabled,
                        sponsorSegments = sponsorSegments,
                        chapters = chapters,
                        onScrubPositionChange = { scrubbingPositionMs = it },
                        onSeek = onSeek,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerIconButton(
                        icon = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                        label = if (isPlaying) "暂停" else "播放",
                        onClick = onPlayPause,
                        buttonSize = if (fullscreen) 40.dp else 36.dp,
                        iconPadding = if (fullscreen) 9.dp else 7.dp,
                    )
                    if (liveMode) {
                        Text(
                            text = statusText.orEmpty(),
                            modifier = Modifier.padding(start = 6.dp),
                            style = COUITheme.textStyles.footnote2,
                            color = Color.White,
                        )
                    } else {
                        Column(modifier = Modifier.padding(start = 2.dp)) {
                            Text(
                                text = Formatters.formatDuration((scrubbingPositionMs ?: positionMs) / 1000),
                                style = COUITheme.textStyles.footnote2,
                                color = Color.White,
                                lineHeight = 15.sp,
                            )
                            Text(
                                text = Formatters.formatDuration(durationMs / 1000),
                                style = COUITheme.textStyles.footnote2,
                                color = Color.White.copy(alpha = 0.6f),
                                lineHeight = 15.sp,
                            )
                        }
                    }
                    if (showStageComposer) {
                        Box(
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { danmakuComposer { composerFocused = it } }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    PlayerBottomTrailingControls(
                        subtitleLabel = subtitleLabel, speed = speed, qualityLabel = qualityLabel,
                        fullscreen = fullscreen, liveMode = liveMode, onSelectLine = onSelectLine,
                        onSuperChatsClick = onSuperChatsClick?.let { open -> { controlsVisible = false; open() } },
                        superChatCount = superChatCount, activeMenu = activeMenu,
                        onOpenMenu = { menu -> if (menu != PlayerPopupMenu.MORE) lastBottomMenu = menu; activeMenu = menu },
                        onDismissMenu = { activeMenu = PlayerPopupMenu.NONE },
                        speedLocked = speedLocked, onToggleSpeedLock = onToggleSpeedLock,
                        episodesMenuItems = episodesMenuItems, speedMenuItems = speedMenuItems,
                        qualityMenuItems = qualityMenuItems, subtitleMenuItems = subtitleMenuItems,
                        stageCoords = stageCoords,
                        onEpisodesEndChange = { episodesEndMargin = it },
                        onSubtitleEndChange = { subtitleEndMargin = it },
                        onSpeedEndChange = { speedEndMargin = it },
                        onQualityEndChange = { qualityEndMargin = it },
                        onToggleFullscreen = onToggleFullscreen,
                    )
                }
            }
        }
        }

        if (fullscreen) {
            PlayerStagePopupMenus(
                activeMenu = activeMenu, lastBottomMenu = lastBottomMenu,
                onDismiss = { activeMenu = PlayerPopupMenu.NONE }, hdrVideo = hdrVideo,
                menuBackdrop = menuBackdrop, horizontalBarPad = horizontalBarPad,
                onHideControls = { controlsVisible = false }, playbackEndMode = playbackEndMode,
                onSelectPlaybackEndMode = onSelectPlaybackEndMode,
                onShare = onShare, onCast = onCast, onDownload = onDownload, onSelectLine = onSelectLine,
                audioOnly = audioOnly, onToggleAudioOnly = onToggleAudioOnly,
                onReportVideo = onReportVideo, onSubmitSponsor = onSubmitSponsor,
                onShowStats = { activeMenu = PlayerPopupMenu.NONE; statsVisible = true },
                onOpenTopBarSettings = openTopBarSettings,
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

        PlayerStageDialogs(
            player = player,
            source = source,
            fullscreen = fullscreen,
            statsVisible = statsVisible,
            onDismissStats = { statsVisible = false },
            sponsorAssistVisible = sponsorAssistVisible,
            sponsorSegments = sponsorSegments,
            onJumpSponsorSegment = onJumpSponsorSegment,
            onSubmitSponsor = onSubmitSponsor,
            onDismissSponsorAssist = { sponsorAssistVisible = false },
            topBarSettingsVisible = topBarSettingsVisible,
            topBarLayoutFullscreen = topBarLayoutFullscreen,
            topBarLayoutPortrait = topBarLayoutPortrait,
            onToggleTopBarItem = onToggleTopBarItem,
            onMoveTopBarItem = onMoveTopBarItem,
            onDismissTopBarSettings = { topBarSettingsVisible = false },
        )
    }
}
