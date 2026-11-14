package erl.bilico.feature.video

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import erl.bilico.R
import erl.bilico.data.model.QualityOption
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiStagePopupMenu
import erl.bilico.ui.components.FullscreenExitIcon
import erl.bilico.ui.components.FullscreenIcon
import erl.bilico.ui.components.PiliGlyph
import erl.bilico.ui.components.PipIcon
import erl.bilico.ui.components.PlayerScrim
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Home
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
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    inPipMode: Boolean = false,
    danmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit = {},
    onDanmakuSettingsClick: (() -> Unit)? = null,
    subtitleLabel: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onWatchLater: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    autoPlayNext: Boolean = true,
    onToggleAutoPlayNext: (() -> Unit)? = null,
    longPressSpeedActive: Boolean = false,
    longPressSpeedValue: Float = 2f,
    onLongPressSpeedChange: (Boolean) -> Unit = {},
    liveMode: Boolean = false,
    statusText: String? = null,
    sponsorSegments: List<SponsorSegment> = emptyList(),
    sponsorOverlay: @Composable BoxScope.(bottomPad: Dp, startPad: Dp) -> Unit = { _, _ -> },
    subtitleOverlay: @Composable (controlsVisible: Boolean) -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var fullscreenMoreOpen by remember { mutableStateOf(false) }
    var prevFullscreen by remember { mutableStateOf(fullscreen) }

    LaunchedEffect(fullscreen) {
        if (prevFullscreen != fullscreen) {
            prevFullscreen = fullscreen
            controlsVisible = false
            fullscreenMoreOpen = false
        }
    }

    LaunchedEffect(controlsVisible, fullscreenMoreOpen, isPlaying, isBuffering, errorMessage) {
        if (controlsVisible && !fullscreenMoreOpen && isPlaying && !isBuffering && errorMessage == null) {
            delay(4500)
            controlsVisible = false
        }
    }

    // 只看「要不要显示控件」这一个开关。暂停、缓冲、网络抖动都不能把控件顶回来，
    // 否则点一下屏幕看到控件纹丝不动，像是点不动。
    val showChrome = !inPipMode && (controlsVisible || errorMessage != null)
    LaunchedEffect(fullscreen, showChrome) {
        if (!fullscreen || !showChrome) fullscreenMoreOpen = false
    }
    val horizontalBarPad = if (fullscreen) 24.dp else 12.dp
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
    Box(if (LocalStageOpaquePlayer.current) modifier.background(Color.Black) else modifier) {
        AndroidView(
            factory = { context ->
                (LayoutInflater.from(context)
                    .inflate(R.layout.view_exo_texture_player, null, false) as PlayerView).apply {
                    useController = false
                    this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        requestedFrameRate = 120f
                        videoSurfaceView?.requestedFrameRate = 120f
                    }
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        overlay()

        subtitleOverlay(showChrome)

        if (!inPipMode) {
            PlayerGestureLayer(
                positionMs = positionMs,
                durationMs = durationMs,
                seekEnabled = !liveMode,
                longPressSpeedActive = longPressSpeedActive,
                longPressSpeedValue = longPressSpeedValue,
                fullscreen = fullscreen,
                hapticEnabled = hapticEnabled,
                volumeBrightnessEnabled = volumeBrightnessEnabled,
                fullscreenHintEnabled = fullscreenHintEnabled,
                onTap = { controlsVisible = !controlsVisible },
                onDoubleTap = {
                    // 双击是播放/暂停，结果必须看得见：暂停时把控件亮出来。
                    controlsVisible = true
                    onPlayPause()
                },
                onLongPressSpeedChange = onLongPressSpeedChange,
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
                if (onEnterPip != null) {
                    PlayerIconButton(PipIcon, "小窗", onEnterPip)
                }
                if (fullscreen && onHome != null) {
                    PlayerIconButton(COUIIcons.Home, "首页", onHome)
                }
                if (onWatchLater != null || onShare != null || onDanmakuSettingsClick != null) {
                    if (fullscreen) {
                        PlayerIconButton(COUIIcons.More, "更多", onClick = { fullscreenMoreOpen = !fullscreenMoreOpen })
                    } else {
                        PlayerMoreMenu(
                            onDanmakuSettings = onDanmakuSettingsClick,
                            onWatchLater = onWatchLater,
                            onShare = onShare,
                            autoPlayNext = autoPlayNext,
                            onToggleAutoPlayNext = onToggleAutoPlayNext,
                        )
                    }
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

        if (errorMessage != null && !inPipMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = errorMessage,
                    style = COUITheme.textStyles.footnote1,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "重试",
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PlayerScrim)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    style = COUITheme.textStyles.button,
                    color = Color.White,
                )
            }
        } else if (isBuffering && !inPipMode) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        if (!inPipMode && !liveMode) {
            sponsorOverlay(if (showChrome) 76.dp else 16.dp, horizontalBarPad)
        }

        if (showChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
                    )
                    .padding(horizontal = horizontalBarPad, vertical = 6.dp),
            ) {
                if (!liveMode) {
                    PlayerProgress(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        sponsorSegments = sponsorSegments,
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
                    Spacer(Modifier.weight(1f))
                    if (subtitleLabel != null && onSubtitleClick != null) {
                        ControlLabel(text = subtitleLabel, onClick = onSubtitleClick)
                    }
                    ControlLabel(
                        text = speedLabel(speed),
                        onClick = onSpeedClick,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    ControlLabel(
                        text = qualityLabel ?: "清晰度",
                        onClick = onQualityClick,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    PlayerIconButton(
                        icon = if (fullscreen) FullscreenExitIcon else FullscreenIcon,
                        label = if (fullscreen) "退出全屏" else "全屏",
                        onClick = onToggleFullscreen,
                    )
                }
            }
        }

        if (fullscreen) {
            CouiStagePopupMenu(
                show = fullscreenMoreOpen,
                onDismiss = { fullscreenMoreOpen = false },
                topMargin = 54.dp,
                endMargin = horizontalBarPad,
                items = buildList {
                    onDanmakuSettingsClick?.let { openDm ->
                        add(
                            CouiMenuItem(
                                label = "弹幕设置",
                                onClick = {
                                    controlsVisible = false
                                    openDm()
                                },
                            ),
                        )
                    }
                    onToggleAutoPlayNext?.let { add(autoPlayNextMenuItem(autoPlayNext, it)) }
                    onWatchLater?.let { add(CouiMenuItem("稍后再看", it)) }
                    onShare?.let { add(CouiMenuItem("分享", it)) }
                },
            )
        }
    }
}

@Composable
private fun ControlLabel(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = COUITheme.textStyles.footnote1,
        color = Color.White,
    )
}

internal fun speedLabel(speed: Float): String = when (speed) {
    0.5f -> "0.5x"
    0.75f -> "0.75x"
    1f -> "1.0x"
    1.25f -> "1.25x"
    1.5f -> "1.5x"
    2f -> "2.0x"
    2.5f -> "2.5x"
    3f -> "3.0x"
    4f -> "4.0x"
    else -> "${speed}x"
}
