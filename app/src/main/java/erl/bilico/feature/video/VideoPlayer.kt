package erl.bilico.feature.video

import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
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
import erl.bilico.data.util.Formatters
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized
import erl.bilico.ui.components.NavHomeOutlineIcon
import erl.bilico.ui.components.PiliGlyph
import erl.bilico.ui.components.PipIcon
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.More
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
    qualities: List<QualityOption> = emptyList(),
    activeQuality: Int = 0,
    onSelectQuality: (Int) -> Unit = {},
    speedLocked: Boolean = false,
    onToggleSpeedLock: () -> Unit = {},
    onSelectSpeed: (Float) -> Unit = {},
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
    playbackEndMode: PlaybackEndMode = PlaybackEndMode.AUTO_NEXT,
    onSelectPlaybackEndMode: ((PlaybackEndMode) -> Unit)? = null,
    longPressSpeed: LongPressSpeedController? = null,
    longPressSpeedMode: LongPressSpeedMode = LongPressSpeedMode.FIXED,
    liveMode: Boolean = false,
    statusText: String? = null,
    chapters: List<VideoChapter> = emptyList(),
    onChapterClick: (() -> Unit)? = null,
    sponsorSegments: List<SponsorSegment> = emptyList(),
    onSubmitSponsor: (() -> Unit)? = null,
    sponsorOverlay: @Composable BoxScope.(bottomPad: Dp, startPad: Dp) -> Unit = { _, _ -> },
    subtitleOverlay: @Composable (controlsVisible: Boolean) -> Unit = {},
    overlay: @Composable () -> Unit = {},
    danmakuComposer: (@Composable (onFocusChange: (Boolean) -> Unit) -> Unit)? = null,
    danmakuActions: DanmakuActionCallbacks? = null,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var activeMenu by remember { mutableStateOf(PlayerPopupMenu.NONE) }
    var lastBottomMenu by remember { mutableStateOf(PlayerPopupMenu.SPEED) }
    var statsVisible by remember { mutableStateOf(false) }
    var selectedDanmaku by remember { mutableStateOf<DanmakuHit?>(null) }
    val danmakuInteraction = remember { DanmakuInteractionHost() }
    var stageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var speedEndMargin by remember { mutableStateOf(120.dp) }
    var qualityEndMargin by remember { mutableStateOf(64.dp) }
    var subtitleEndMargin by remember { mutableStateOf(176.dp) }
    // 正在输入弹幕：这时不能让控件条按 4.5 秒自动收，否则键盘还在、框先没了。
    var composerFocused by remember { mutableStateOf(false) }
    var prevFullscreen by remember { mutableStateOf(fullscreen) }

    LaunchedEffect(fullscreen) {
        if (prevFullscreen != fullscreen) {
            prevFullscreen = fullscreen
            controlsVisible = false
            activeMenu = PlayerPopupMenu.NONE
            statsVisible = false
            selectedDanmaku = null
        }
    }
    LaunchedEffect(inPipMode, danmakuActions == null) {
        if (inPipMode || danmakuActions == null) selectedDanmaku = null
    }
    danmakuInteraction.selectedHit = selectedDanmaku

    LaunchedEffect(controlsVisible, activeMenu, composerFocused, isPlaying, isBuffering, errorMessage) {
        if (
            controlsVisible && activeMenu == PlayerPopupMenu.NONE && !composerFocused &&
            isPlaying && !isBuffering && errorMessage == null
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
    // 只有横屏全屏才挂弹幕输入框：竖屏全屏画面本来就窄，页面下面还有那条固定的输入框。
    val showStageComposer = fullscreen && !portraitFullscreen && !liveMode && danmakuComposer != null
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
    Box(stageModifier.onGloballyPositioned { stageCoords = it }) {
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
                    // 舞台用替身帧顶替期间要把实时画面藏起来，否则它会从替身帧底下露出来。
                    view.visibility = if (minimized || frozenVideo) View.INVISIBLE else View.VISIBLE
                },
                modifier = Modifier.fillMaxSize(),
            )
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
                longPressSpeed = longPressSpeed,
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

        if (showChrome) {
            // 照 COUI 顶栏：左边最多一个按钮（返回），其余都在右端，首页紧挨着更多菜单。
            // 竖屏全屏画面只有屏幕宽，单行会把两组挤在一起，所以拆成上下两行，右组单独靠右。
            val leadingActions: @Composable RowScope.() -> Unit = {
                // 竖屏全屏下第一行只留标题：返回挪到下面的「只剩顶栏」状态里，
                // 全屏时画面就是全部，这个键也基本按不到。
                if (!portraitFullscreen) {
                    PlayerIconButton(COUIIcons.Back, "返回", onBack)
                }
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    style = COUITheme.textStyles.body2,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val trailingActions: @Composable RowScope.() -> Unit = {
                // 竖屏非全屏时弹幕开关在简介那一行的胶囊右端，弹幕设置收在右侧更多菜单里；全屏时两者都在顶栏。
                if (fullscreen) {
                    if (onDanmakuSettingsClick != null) {
                        PlayerGlyphButton(
                            glyph = PiliGlyph.DmSettings,
                            label = "弹幕设置",
                            onClick = {
                                controlsVisible = false
                                onDanmakuSettingsClick()
                            },
                        )
                    }
                    PlayerGlyphButton(
                        glyph = if (danmakuEnabled) PiliGlyph.DmOn else PiliGlyph.DmOff,
                        label = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                        onClick = onToggleDanmaku,
                        tint = if (danmakuEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                    )
                }
                if (!fullscreen && onHome != null) {
                    PlayerIconButton(NavHomeOutlineIcon, "首页", onHome)
                }
                if (onEnterPip != null) {
                    PlayerIconButton(PipIcon, "小窗", onEnterPip)
                }
                if (fullscreen && onHome != null) {
                    PlayerIconButton(NavHomeOutlineIcon, "首页", onHome)
                }
                // 「视频信息统计」在任何视频页都能用，菜单因此恒定存在。
                if (fullscreen) {
                    PlayerIconButton(
                        COUIIcons.More,
                        "更多",
                        onClick = {
                            activeMenu = if (activeMenu == PlayerPopupMenu.MORE) {
                                PlayerPopupMenu.NONE
                            } else {
                                PlayerPopupMenu.MORE
                            }
                        },
                    )
                } else {
                    PlayerMoreMenu(
                        onDanmakuSettings = onDanmakuSettingsClick,
                        onWatchLater = onWatchLater,
                        onShare = onShare,
                        playbackEndMode = playbackEndMode,
                        onSelectPlaybackEndMode = onSelectPlaybackEndMode,
                        onSubmitSponsor = onSubmitSponsor,
                        onShowStats = { statsVisible = true },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        // 两行顶栏比单行高得多，第二行下方就是画面，单段渐变淡到那里已经几乎看不见，
                        // 所以多给一段并留出淡出余量，让白色图标始终压在暗底上。
                        if (portraitFullscreen) {
                            Brush.verticalGradient(
                                listOf(Color(0xE6000000), Color(0xB3000000), Color.Transparent),
                            )
                        } else {
                            Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
                        },
                    )
                    .padding(
                        PaddingValues(
                            start = horizontalBarPad,
                            end = horizontalBarPad,
                            top = 8.dp + portraitTopPad,
                            bottom = if (portraitFullscreen) 20.dp else 8.dp,
                        ),
                    ),
            ) {
                if (portraitFullscreen) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            content = leadingActions,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            content = trailingActions,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leadingActions()
                        trailingActions()
                    }
                }
            }
        }

        PlayerStateOverlay(
            errorMessage = errorMessage,
            isBuffering = isBuffering,
            inPipMode = inPipMode,
            onRetry = onRetry,
        )

        if (!inPipMode && !liveMode) {
            val bottomChromePad = when {
                !showChrome -> 16.dp
                chapters.isNotEmpty() -> 110.dp
                else -> 76.dp
            }
            sponsorOverlay(bottomChromePad, horizontalBarPad)
        }

        if (showChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 全屏发弹幕时软键盘会把底栏顶走，整条跟着键盘抬起来。
                    .imePadding()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
                    )
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
                        sponsorSegments = sponsorSegments,
                        chapters = chapters,
                        onSeek = onSeek,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerIconButton(
                        icon = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                        label = if (isPlaying) "暂停" else "播放",
                        onClick = onPlayPause,
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
                                text = Formatters.formatDuration(positionMs / 1000),
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
                    if (!liveMode && chapters.isNotEmpty() && onChapterClick != null) {
                        ControlLabel(text = "分段", onClick = onChapterClick)
                    }
                    PlayerBottomTrailingControls(
                        subtitleLabel = subtitleLabel,
                        speed = speed,
                        qualityLabel = qualityLabel,
                        fullscreen = fullscreen,
                        activeMenu = activeMenu,
                        onOpenMenu = { menu ->
                            if (menu != PlayerPopupMenu.MORE) lastBottomMenu = menu
                            activeMenu = menu
                        },
                        onDismissMenu = { activeMenu = PlayerPopupMenu.NONE },
                        speedLocked = speedLocked,
                        onToggleSpeedLock = onToggleSpeedLock,
                        speedMenuItems = speedMenuItems,
                        qualityMenuItems = qualityMenuItems,
                        subtitleMenuItems = subtitleMenuItems,
                        stageCoords = stageCoords,
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
                activeMenu = activeMenu,
                lastBottomMenu = lastBottomMenu,
                onDismiss = { activeMenu = PlayerPopupMenu.NONE },
                hdrVideo = hdrVideo,
                menuBackdrop = menuBackdrop,
                horizontalBarPad = horizontalBarPad,
                onDanmakuSettingsClick = onDanmakuSettingsClick,
                onHideControls = { controlsVisible = false },
                playbackEndMode = playbackEndMode,
                onSelectPlaybackEndMode = onSelectPlaybackEndMode,
                onWatchLater = onWatchLater,
                onShare = onShare,
                onSubmitSponsor = onSubmitSponsor,
                onShowStats = {
                    activeMenu = PlayerPopupMenu.NONE
                    statsVisible = true
                },
                speedMenuItems = speedMenuItems,
                qualityMenuItems = qualityMenuItems,
                subtitleMenuItems = subtitleMenuItems,
                speedEndMargin = speedEndMargin,
                qualityEndMargin = qualityEndMargin,
                subtitleEndMargin = subtitleEndMargin,
                speedLocked = speedLocked,
                onToggleSpeedLock = onToggleSpeedLock,
            )
        }

        val activeHit = selectedDanmaku
        if (activeHit != null && danmakuActions != null && !inPipMode) {
            DanmakuActionToolbar(activeHit, danmakuActions, { selectedDanmaku = null }, Modifier.fillMaxSize())
        }

        if (statsVisible) {
            PlayerStatsDialog(
                player = player,
                source = source,
                inlineInStage = fullscreen,
                onDismiss = { statsVisible = false },
            )
        }
    }
}
