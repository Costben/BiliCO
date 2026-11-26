package erl.bilico.platform

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import erl.bilico.data.model.PlaybackSource
import erl.bilico.player.BiliPlayer
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
internal actual fun BindPlatformPlayerBandwidth(
    player: BiliPlayer,
    onBandwidthKbps: (Long) -> Unit,
) {
    val currentOnBandwidth by rememberUpdatedState(onBandwidthKbps)
    DisposableEffect(player) {
        val listener = object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                currentOnBandwidth(bitrateEstimate / 1000L)
            }
        }
        player.exoPlayer.addAnalyticsListener(listener)
        onDispose { player.exoPlayer.removeAnalyticsListener(listener) }
    }
}

@OptIn(UnstableApi::class)
internal actual fun readPlatformPlayerTelemetry(
    player: BiliPlayer,
    source: PlaybackSource?,
): PlatformPlayerTelemetry {
    val exo = player.exoPlayer
    val video = exo.currentTracks.formatOf(C.TRACK_TYPE_VIDEO)
    val audio = exo.currentTracks.formatOf(C.TRACK_TYPE_AUDIO)
    val videoSize = player.videoSize
    val width = videoSize.width.takeIf { it > 0 } ?: video?.width ?: 0
    val height = videoSize.height.takeIf { it > 0 } ?: video?.height ?: 0
    val frameRate = video?.frameRate?.takeIf { it > 0f } ?: source?.frameRate?.takeIf { it > 0f }
    val counters = exo.videoDecoderCounters
    counters?.ensureUpdated()
    val rendered = counters?.renderedOutputBufferCount?.toLong() ?: 0L
    val dropped = counters?.droppedBufferCount ?: 0
    val total = rendered + dropped
    val droppedDetail = if (total > 0) {
        String.format(Locale.US, "%d (%.1f%%)", dropped, dropped * 100f / total)
    } else {
        dropped.toString()
    }
    val videoUrl = source?.videoUrl ?: exo.currentMediaItem?.localConfiguration?.uri?.toString()
    val type = when {
        videoUrl == null -> "未知"
        videoUrl.contains(".m3u8", ignoreCase = true) -> "HLS"
        videoUrl.contains(".mpd", ignoreCase = true) -> "DASH"
        else -> "MP4 / Progressive"
    }
    return PlatformPlayerTelemetry(
        width = width,
        height = height,
        frameRate = frameRate,
        videoCodec = codecText(video),
        audioCodec = codecText(audio),
        videoBitrate = bitrateText(video?.bitrate ?: 0),
        audioBitrate = bitrateText(audio?.bitrate ?: 0),
        renderedFrames = rendered,
        droppedDetail = droppedDetail,
        videoUrl = videoUrl,
        playerType = type,
        speed = exo.playbackParameters.speed,
    )
}

/** 把 `hev1.1.6.L150.90` 这类编码串归到人认得的标准名，后面附原始串方便核对。 */
private fun codecText(format: Format?): String {
    if (format == null) return "未知"
    val codecs = format.codecs.orEmpty()
    val mime = format.sampleMimeType.orEmpty()
    val name = when {
        codecs.startsWith("hev1") || codecs.startsWith("hvc1") -> "H.265"
        codecs.startsWith("avc1") || mime == "video/avc" -> "H.264"
        codecs.startsWith("av01") -> "AV1"
        codecs.startsWith("vp09") -> "VP9"
        codecs.startsWith("vp8") -> "VP8"
        codecs.startsWith("mp4a") -> "AAC"
        codecs.startsWith("ec-3") -> "E-AC-3"
        codecs.startsWith("ac-3") -> "AC-3"
        codecs.startsWith("flac") -> "FLAC"
        codecs.startsWith("opus") -> "Opus"
        else -> mime.substringAfter('/').ifEmpty { "未知" }
    }
    return if (codecs.isEmpty()) name else "$name · $codecs"
}

private fun bitrateText(bps: Int): String = when {
    bps <= 0 -> "未知"
    bps < 1_000_000 -> "${(bps / 1000f).roundToInt()} Kbps"
    else -> String.format(Locale.US, "%.1f Mbps", bps / 1_000_000f)
}

/** 取当前实际在播的那条轨的 [Format]（优先选中的轨，没有选中信息就退回首条）。 */
private fun Tracks.formatOf(type: Int): Format? {
    for (group in groups) {
        if (group.type != type || !group.isSupported) continue
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) return group.getTrackFormat(index)
        }
        return group.getTrackFormat(0)
    }
    return null
}
