package erl.bilico.platform

import androidx.compose.runtime.Composable
import erl.bilico.data.model.PlaybackSource
import erl.bilico.player.BiliPlayer

internal data class PlatformPlayerTelemetry(
    val width: Int,
    val height: Int,
    val frameRate: Float?,
    val videoCodec: String,
    val audioCodec: String,
    val videoBitrate: String,
    val audioBitrate: String,
    val renderedFrames: Long,
    val droppedDetail: String,
    val videoUrl: String?,
    val playerType: String,
    val speed: Float,
)

@Composable
internal expect fun BindPlatformPlayerBandwidth(
    player: BiliPlayer,
    onBandwidthKbps: (Long) -> Unit,
)

internal expect fun readPlatformPlayerTelemetry(
    player: BiliPlayer,
    source: PlaybackSource?,
): PlatformPlayerTelemetry
