package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import erl.bilico.data.model.QualityOption
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.FullscreenExitIcon
import erl.bilico.ui.components.FullscreenIcon
import erl.bilico.ui.components.PiliGlyph
import erl.bilico.ui.components.PiliIcon
import erl.bilico.ui.components.PipIcon
import erl.bilico.ui.components.PlayerControlsPadding
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
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit = { if (it) onToggleFullscreen() },
    hapticEnabled: Boolean = true,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    inPipMode: Boolean = false,
    danmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit = {},
    subtitleLabel: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    onEnterPip: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onWatchLater: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    longPressSpeedActive: Boolean = false,
    longPressSpeedValue: Float = 2f,
    onLongPressSpeedChange: (Boolean) -> Unit = {},
    subtitleOverlay: @Composable (controlsVisible: Boolean) -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsVisible, isPlaying, isBuffering, errorMessage) {
        if (controlsVisible && isPlaying && !isBuffering && errorMessage == null) {
            delay(4500)
            controlsVisible = false
        }
    }

    // 只看「要不要显示控件」这一个开关。暂停、缓冲、网络抖动都不能把控件顶回来，
    // 否则点一下屏幕看到控件纹丝不动，像是点不动。
    val showChrome = !inPipMode && (controlsVisible || errorMessage != null)

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        overlay()

        subtitleOverlay(showChrome)

        if (!inPipMode) {
            PlayerGestureLayer(
                positionMs = positionMs,
                durationMs = durationMs,
                longPressSpeedActive = longPressSpeedActive,
                longPressSpeedValue = longPressSpeedValue,
                fullscreen = fullscreen,
                hapticEnabled = hapticEnabled,
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
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)),
                    )
                    .padding(PlayerControlsPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(COUIIcons.Back, "返回", onBack)
                if (fullscreen && onHome != null) {
                    PlayerIconButton(COUIIcons.Home, "首页", onHome)
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
                // 竖屏非全屏时，弹幕开关在简介那一行的胶囊右端，这里不再重复。
                if (fullscreen) {
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
                if (onWatchLater != null || onShare != null) {
                    PlayerMoreMenu(onWatchLater = onWatchLater, onShare = onShare)
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

        if (showChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                PlayerProgress(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerIconButton(
                        icon = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                        label = if (isPlaying) "暂停" else "播放",
                        onClick = onPlayPause,
                    )
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
    }
}

/**
 * 进度条照 PiliPlus：一条 3dp 的细线，走过的部分实白、剩下的半透明白，
 * 播放头只是一颗小圆点，拖动时才略微放大。
 */
@Composable
private fun PlayerProgress(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    val max = durationMs.coerceAtLeast(1L).toFloat()
    val value = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(0f, max)
    val fraction = if (durationMs > 0L) (value / max).coerceIn(0f, 1f) else 0f
    val enabled = durationMs > 0L

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled, max) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek(((offset.x / widthPx.coerceAtLeast(1)) * max).toLong())
                }
            }
            .pointerInput(enabled, max) {
                if (!enabled) return@pointerInput
                fun fractionAt(x: Float) = (x / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = fractionAt(offset.x) * max
                    },
                    onHorizontalDrag = { change, _ ->
                        dragValue = fractionAt(change.position.x) * max
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek(dragValue.toLong())
                    },
                    onDragCancel = { dragging = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White.copy(alpha = 0.24f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(if (dragging) 11.dp else 9.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        tint = tint,
    )
}

/** 字体图标按钮：字形占满 40dp 的触摸区，按下感与图标按钮一致。 */
@Composable
private fun PlayerGlyphButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PiliIcon(
            glyph = glyph,
            contentDescription = label,
            size = 20.dp,
            tint = tint,
        )
    }
}

/** 顶栏右端的三个点：稍后再看与分享，都是这一页本来就能做的事。 */
@Composable
private fun PlayerMoreMenu(
    onWatchLater: (() -> Unit)?,
    onShare: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        PlayerIconButton(COUIIcons.More, "更多", onClick = { expanded = true })
        CouiPopupMenu(
            show = expanded,
            onDismiss = { expanded = false },
            items = buildList {
                onWatchLater?.let { add(CouiMenuItem("稍后再看", it)) }
                onShare?.let { add(CouiMenuItem("分享", it)) }
            },
        )
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

@Composable
internal fun QualityDialog(
    qualities: List<QualityOption>,
    activeQuality: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = remember(qualities) { qualities.distinctBy { it.quality } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "清晰度",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    val active = option.quality == activeQuality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.quality) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label.ifBlank { "${option.quality}P" },
                            modifier = Modifier.weight(1f),
                            style = COUITheme.textStyles.body1,
                            color = if (active) {
                                COUITheme.colorScheme.primary
                            } else {
                                COUITheme.colorScheme.onSurface
                            },
                        )
                        if (option.needVip) {
                            Text(
                                text = "大会员",
                                style = COUITheme.textStyles.footnote2,
                                color = COUITheme.colorScheme.primary,
                            )
                        } else if (option.needLogin) {
                            Text(
                                text = "登录",
                                style = COUITheme.textStyles.footnote2,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun speedLabel(speed: Float): String = when (speed) {
    0.5f -> "0.5x"
    0.75f -> "0.75x"
    1f -> "1.0x"
    1.25f -> "1.25x"
    1.5f -> "1.5x"
    2f -> "2.0x"
    else -> "${speed}x"
}

@Composable
internal fun SpeedDialog(
    active: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "播放倍速",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            PLAYBACK_SPEEDS.forEach { option ->
                val selected = option == active
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (option == 1f) "正常" else speedLabel(option),
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.body1,
                        color = if (selected) {
                            COUITheme.colorScheme.primary
                        } else {
                            COUITheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
