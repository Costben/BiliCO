package erl.bilico.feature.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.util.Formatters
import erl.bilico.di.LocalAppContainer
import erl.bilico.feature.video.PlayerModalHost
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState

@Composable
fun CastSheet(
    request: CastMediaRequest,
    inlineInStage: Boolean = false,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current ?: return
    val controller = container.dlna

    if (!controller.isSupported) {
        UnsupportedCastSheet(
            inlineInStage = inlineInStage,
            onDismiss = onDismiss,
        )
        return
    }

    val state by controller.state.collectAsState()

    LaunchedEffect(Unit) {
        if (state.devices.isEmpty() && !state.scanning) {
            controller.scanDevices()
        }
    }

    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
        maxWidth = 420.dp,
    ) {
        val scheme = COUITheme.colorScheme
        val dismiss = LocalDismissState.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "投屏到电视 (DLNA)",
                    style = COUITheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (state.scanning) "搜索中..." else "重新搜索",
                    fontSize = 13.sp,
                    color = if (state.scanning) scheme.onSurfaceSecondary else scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !state.scanning) { controller.scanDevices() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            if (request.title.isNotBlank()) {
                Text(
                    text = request.title,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (inlineInStage) 220.dp else 360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.activeDevice?.let { active ->
                    ActiveCastPanel(
                        device = active,
                        state = state,
                        isLive = request.liveStreamUrl.isNotBlank(),
                        onTogglePlayPause = controller::togglePlayPause,
                        onSeekBack = { controller.seekBy(-15_000L) },
                        onSeekForward = { controller.seekBy(15_000L) },
                        onStop = controller::stopCast,
                    )
                }

                if (!state.error.isNullOrBlank()) {
                    Text(
                        text = state.error.orEmpty(),
                        fontSize = 12.sp,
                        color = scheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                when {
                    state.scanning && state.devices.isEmpty() -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), size = 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "正在搜索局域网 DLNA 设备...",
                                style = COUITheme.textStyles.body2,
                                color = scheme.onSurfaceSecondary,
                            )
                        }
                    }

                    state.devices.isEmpty() -> {
                        Text(
                            text = "未发现可用投屏设备，请确认电视或盒子与手机在同一 Wi-Fi 下并已开启投屏接收。",
                            style = COUITheme.textStyles.body2,
                            color = scheme.onSurfaceSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    }

                    else -> {
                        state.devices.forEach { device ->
                            val isCurrent = state.activeDevice?.avTransportControlUrl == device.avTransportControlUrl
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isCurrent) scheme.primary.copy(alpha = 0.12f)
                                        else scheme.surfaceContainerHigh,
                                    )
                                    .clickable(enabled = !state.isBusy) {
                                        controller.startCast(device, request)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = device.friendlyName,
                                        style = COUITheme.textStyles.body1,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isCurrent) scheme.primary else scheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = when {
                                        isCurrent && state.isBusy -> "连接中..."
                                        isCurrent -> "投屏中"
                                        else -> "投屏"
                                    },
                                    fontSize = 12.sp,
                                    color = if (isCurrent) scheme.primary else scheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { controller.setUseProxy(!state.useProxy) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "本地中转代理",
                            style = COUITheme.textStyles.body2,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "开启可携带请求头避免电视播放 403",
                            fontSize = 11.sp,
                            color = scheme.onSurfaceSecondary,
                        )
                    }
                    Switch(
                        checked = state.useProxy,
                        onCheckedChange = controller::setUseProxy,
                    )
                }
            }

            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = if (state.activeDevice != null) "停止投屏" else "重新搜索",
                    onClick = {
                        if (state.activeDevice != null) controller.stopCast() else controller.scanDevices()
                    },
                ),
                positive = DialogButtonBarAction(text = "关闭", onClick = dismiss),
            )
        }
    }
}

@Composable
private fun UnsupportedCastSheet(
    inlineInStage: Boolean,
    onDismiss: () -> Unit,
) {
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
    ) {
        val dismiss = LocalDismissState.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "投屏",
                style = COUITheme.textStyles.title3,
                color = COUITheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "iOS 版暂不支持局域网 DLNA 投屏，请在控制中心使用系统「屏幕镜像」或隔空播放。",
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { dismiss?.invoke() ?: onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("知道了")
            }
        }
    }
}

@Composable
private fun ActiveCastPanel(
    device: DlnaDevice,
    state: CastUiState,
    isLive: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onStop: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "正在投屏：${device.friendlyName}",
            style = COUITheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!isLive && state.durationMs > 0L) {
            LinearProgressIndicator(
                progress = (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${Formatters.formatDuration(state.positionMs / 1000L)} / ${Formatters.formatDuration(state.durationMs / 1000L)}",
                fontSize = 11.sp,
                color = scheme.onSurfaceSecondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!isLive) {
                CastControlButton("-15s", onClick = onSeekBack, modifier = Modifier.weight(1f))
            }
            CastControlButton(
                text = if (state.isPlaying) "暂停" else "播放",
                onClick = onTogglePlayPause,
                modifier = Modifier.weight(1f),
            )
            if (!isLive) {
                CastControlButton("+15s", onClick = onSeekForward, modifier = Modifier.weight(1f))
            }
            CastControlButton("结束", onClick = onStop, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CastControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
        )
    }
}
