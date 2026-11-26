package erl.bilico.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.settings.StatsOverlayField
import erl.bilico.di.LocalAppContainer
import erl.bilico.platform.BindPlatformPlayerBandwidth
import erl.bilico.platform.formatFixedDecimal
import erl.bilico.platform.readPlatformPlayerTelemetry
import erl.bilico.player.BiliPlayer
import erl.bilico.ui.components.FloatingWindowActiveIcon
import erl.bilico.ui.components.FloatingWindowOutlineIcon
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.delay

@Stable
internal class PlayerStats {
    var bandwidthKbps by mutableLongStateOf(0L)
}

@Composable
internal fun rememberPlayerStats(player: BiliPlayer): PlayerStats {
    val stats = remember(player) { PlayerStats() }
    BindPlatformPlayerBandwidth(player) { kbps -> stats.bandwidthKbps = kbps }
    return stats
}

internal data class StatsSnapshot(
    val quality: String?,
    val resolution: String,
    val frameRate: String,
    val videoCodec: String,
    val audioCodec: String,
    val videoBitrate: String,
    val audioBitrate: String,
    val renderedFrames: Long,
    val droppedDetail: String,
    val buffer: String,
    val bandwidth: String,
    val videoHost: String,
    val audioHost: String,
    val playerType: String,
    val speed: String,
) {
    fun toRows(): List<Pair<StatsOverlayField, String>> = buildList {
        quality?.let { add(StatsOverlayField.QUALITY to it) }
        add(StatsOverlayField.RESOLUTION to resolution)
        add(StatsOverlayField.FRAME_RATE to frameRate)
        add(StatsOverlayField.PLAYER_TYPE to playerType)
        add(StatsOverlayField.VIDEO_CODEC to videoCodec)
        add(StatsOverlayField.AUDIO_CODEC to audioCodec)
        add(StatsOverlayField.VIDEO_BITRATE to videoBitrate)
        add(StatsOverlayField.AUDIO_BITRATE to audioBitrate)
        add(StatsOverlayField.RENDERED_FRAMES to renderedFrames.toString())
        add(StatsOverlayField.DROPPED_FRAMES to droppedDetail)
        add(StatsOverlayField.BUFFER to buffer)
        add(StatsOverlayField.BANDWIDTH to bandwidth)
        add(StatsOverlayField.VIDEO_HOST to videoHost)
        add(StatsOverlayField.AUDIO_HOST to audioHost)
        add(StatsOverlayField.SPEED to speed)
    }
}

@Composable
internal fun PlayerStatsDialog(
    player: BiliPlayer,
    source: PlaybackSource?,
    inlineInStage: Boolean,
    onDismiss: () -> Unit,
) {
    val statsOverlay = LocalAppContainer.current?.settings?.statsOverlay
    val floatingEnabled = statsOverlay?.enabled?.collectAsStateWithLifecycle()?.value == true
    val stats = rememberPlayerStats(player)
    var snapshot by remember(player, source) { mutableStateOf(readSnapshot(player, source, stats, null)) }

    LaunchedEffect(player, source) {
        while (true) {
            snapshot = readSnapshot(player, source, stats, snapshot)
            delay(500)
        }
    }

    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
        horizontalPadding = if (inlineInStage) 12.dp else 24.dp,
        cornerRadius = 24.dp,
    ) {
        val scheme = COUITheme.colorScheme
        val dismiss = LocalDismissState.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                if (statsOverlay != null) {
                    HighlightedIconButton(
                        onClick = {
                            statsOverlay.setEnabled(!floatingEnabled)
                            dismiss()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .testTag("stats_floating_toggle"),
                    ) {
                        Icon(
                            imageVector = if (floatingEnabled) {
                                FloatingWindowActiveIcon
                            } else {
                                FloatingWindowOutlineIcon
                            },
                            contentDescription = if (floatingEnabled) "关闭浮窗" else "开启浮窗",
                            modifier = Modifier.size(22.dp),
                            tint = if (floatingEnabled) scheme.primary else scheme.onSurface,
                        )
                    }
                }
                Text(
                    text = "统计信息",
                    modifier = Modifier.align(Alignment.Center),
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = scheme.onSurface,
                )
                HighlightedIconButton(
                    onClick = dismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(COUIIcons.Close, "关闭", Modifier.size(24.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (inlineInStage) 300.dp else 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                snapshot.toRows().forEach { (field, value) -> StatRow(field.label, value) }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val scheme = COUITheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(84.dp),
            textAlign = TextAlign.End,
            style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurfaceSecondary,
        )
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = COUITheme.textStyles.footnote1,
            color = scheme.onSurface,
        )
    }
}

internal fun readSnapshot(
    player: BiliPlayer,
    source: PlaybackSource?,
    stats: PlayerStats,
    previous: StatsSnapshot?,
): StatsSnapshot {
    val telemetry = readPlatformPlayerTelemetry(player, source)
    val width = telemetry.width
    val height = telemetry.height
    val frameRate = telemetry.frameRate
    val videoUrl = telemetry.videoUrl
    return StatsSnapshot(
        quality = source?.qualities?.firstOrNull { it.quality == source.quality }?.label,
        resolution = if (width > 0 && height > 0) {
            "$width x $height"
        } else {
            previous?.resolution ?: "未知"
        },
        frameRate = if (frameRate != null) {
            "${formatFixedDecimal(frameRate.toDouble(), 3)} fps"
        } else {
            previous?.frameRate ?: "未知"
        },
        videoCodec = telemetry.videoCodec,
        audioCodec = telemetry.audioCodec,
        videoBitrate = telemetry.videoBitrate,
        audioBitrate = telemetry.audioBitrate,
        renderedFrames = telemetry.renderedFrames,
        droppedDetail = telemetry.droppedDetail,
        buffer = formatBuffer(player.bufferedPosition - player.currentPosition),
        bandwidth = bandwidthText(stats.bandwidthKbps),
        videoHost = hostOf(videoUrl),
        audioHost = hostOf(source?.audioUrl ?: videoUrl),
        playerType = telemetry.playerType,
        speed = "${formatFixedDecimal(telemetry.speed.toDouble(), 2)}x",
    )
}

private fun bandwidthText(kbps: Long): String = when {
    kbps <= 0 -> "未知"
    kbps < 1000 -> "$kbps Kbps"
    else -> "${formatFixedDecimal(kbps / 1000.0, 1)} Mbps"
}

private fun formatBuffer(ms: Long): String =
    "${formatFixedDecimal(ms.coerceAtLeast(0L) / 1000.0, 1)} 秒"

private fun hostOf(url: String?): String {
    if (url.isNullOrBlank()) return "未知"
    if (url.startsWith("file://")) return "本地缓存"
    val afterScheme = url.substringAfter("://", "")
    if (afterScheme.isEmpty()) return "未知"
    val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.ifBlank { "未知" }
}
