package erl.bilico.platform

import androidx.compose.runtime.Composable
import erl.bilico.data.model.PlaybackSource
import erl.bilico.player.BiliPlayer

@Composable
internal actual fun BindPlatformPlayerBandwidth(
    player: BiliPlayer,
    onBandwidthKbps: (Long) -> Unit,
) = Unit

internal actual fun readPlatformPlayerTelemetry(
    player: BiliPlayer,
    source: PlaybackSource?,
): PlatformPlayerTelemetry {
    val videoSize = player.videoSize
    val videoUrl = source?.videoUrl
    val type = when {
        videoUrl == null -> "未知"
        videoUrl.contains(".m3u8", ignoreCase = true) -> "HLS (AVPlayer)"
        videoUrl.contains(".mpd", ignoreCase = true) -> "DASH (AVPlayer)"
        else -> "MP4 / Progressive (AVPlayer)"
    }
    return PlatformPlayerTelemetry(
        width = videoSize.width.takeIf { it > 0 } ?: 0,
        height = videoSize.height.takeIf { it > 0 } ?: 0,
        frameRate = source?.frameRate?.takeIf { it > 0f },
        videoCodec = "AVPlayer 硬件解码",
        audioCodec = "AAC",
        videoBitrate = "未知",
        audioBitrate = "未知",
        renderedFrames = 0L,
        droppedDetail = "0",
        videoUrl = videoUrl,
        playerType = type,
        speed = player.speed,
    )
}
