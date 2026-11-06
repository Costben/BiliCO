package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import erl.bilico.data.model.QualityOption
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.PlayerControlsPadding
import erl.bilico.ui.components.PlayerScrim
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 只用 PlayerView 做渲染，控件全部由 Compose 画，保证跟 COUI 主题一致。
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
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    danmakuEnabled: Boolean = false,
    onToggleDanmaku: () -> Unit = {},
    overlay: @Composable () -> Unit = {},
) {
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
            Icon(
                imageVector = COUIIcons.ChevronBackward,
                contentDescription = "返回",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
                tint = Color.White,
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                style = COUITheme.textStyles.body2,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (errorMessage != null) {
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
        } else if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

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
                Icon(
                    imageVector = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPlayPause)
                        .padding(6.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.weight(1f))
                ControlLabel(
                    text = if (danmakuEnabled) "弹幕开" else "弹幕关",
                    onClick = onToggleDanmaku,
                )
                ControlLabel(
                    text = speedLabel(speed),
                    onClick = onSpeedClick,
                    modifier = Modifier.padding(start = 8.dp),
                )
                ControlLabel(
                    text = qualityLabel ?: "清晰度",
                    onClick = onQualityClick,
                    modifier = Modifier.padding(start = 8.dp),
                )
                ControlLabel(
                    text = if (fullscreen) "退出" else "全屏",
                    onClick = onToggleFullscreen,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerProgress(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val max = durationMs.coerceAtLeast(1L).toFloat()
    val value = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(0f, max)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = Formatters.formatDuration(positionMs / 1000),
            style = COUITheme.textStyles.footnote2,
            color = Color.White.copy(alpha = 0.9f),
        )
        Slider(
            value = value,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue.toLong())
            },
            valueRange = 0f..max,
            enabled = durationMs > 0L,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = COUITheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Text(
            text = Formatters.formatDuration(durationMs / 1000),
            style = COUITheme.textStyles.footnote2,
            color = Color.White.copy(alpha = 0.9f),
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
    1f -> "倍速"
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
                        text = speedLabel(option).replace("倍速", "正常"),
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
