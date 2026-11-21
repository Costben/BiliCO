package erl.bilico.feature.video

import android.net.Uri
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import erl.bilico.data.model.PlaybackSource
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 播放器的实时计数：网络带宽估计。
 * ExoPlayer 只在 AnalyticsListener 里增量上报带宽，取不到「当前值」，所以自己记住最新一次；
 * 帧数与丢帧直接从解码器计数里读，见 [readSnapshot]。
 */
@Stable
internal class PlayerStats {
    var bandwidthKbps by mutableLongStateOf(0L)
}

/** 监听当前播放器并持续收集 [PlayerStats]；换播放器时重新挂监听。 */
@Composable
internal fun rememberPlayerStats(player: Player): PlayerStats {
    val stats = remember(player) { PlayerStats() }
    DisposableEffect(player) {
        val listener = object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                stats.bandwidthKbps = bitrateEstimate / 1000
            }
        }
        val exo = player as? ExoPlayer
        exo?.addAnalyticsListener(listener)
        onDispose { exo?.removeAnalyticsListener(listener) }
    }
    return stats
}

/** 一次采样得到的全部文案。面板每 500ms 重采一次，只在数据落定时重组，不逐帧刷新。 */
private data class StatsSnapshot(
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
)

/**
 * 「视频信息统计」面板：清晰度、分辨率与帧率、编解码、码率、已渲染帧与丢帧、缓冲、带宽估计和线路。
 * 竖屏走标准弹窗，全屏横屏时内嵌在播放器舞台里，避免 Dialog 另开窗口抢方向。
 */
@Composable
internal fun PlayerStatsDialog(
    player: Player,
    source: PlaybackSource?,
    inlineInStage: Boolean,
    onDismiss: () -> Unit,
) {
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
                val rows = buildList {
                    snapshot.quality?.let { add("清晰度" to it) }
                    add("分辨率" to snapshot.resolution)
                    add("帧率" to snapshot.frameRate)
                    add("播放器" to snapshot.playerType)
                    add("视频编码" to snapshot.videoCodec)
                    add("音频编码" to snapshot.audioCodec)
                    add("视频码率" to snapshot.videoBitrate)
                    add("音频码率" to snapshot.audioBitrate)
                    add("已渲染帧" to snapshot.renderedFrames.toString())
                    add("丢帧" to snapshot.droppedDetail)
                    add("缓冲" to snapshot.buffer)
                    add("带宽估计" to snapshot.bandwidth)
                    add("视频线路" to snapshot.videoHost)
                    add("音频线路" to snapshot.audioHost)
                    add("播放速度" to snapshot.speed)
                }
                rows.forEach { (label, value) -> StatRow(label, value) }
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

private fun readSnapshot(
    player: Player,
    source: PlaybackSource?,
    stats: PlayerStats,
    previous: StatsSnapshot?,
): StatsSnapshot {
    val video = player.currentTracks.formatOf(C.TRACK_TYPE_VIDEO)
    val audio = player.currentTracks.formatOf(C.TRACK_TYPE_AUDIO)
    val videoSize = player.videoSize
    // 播放器还没解码出画面时 videoSize 是 0，此时退回轨道声明值；两处都没有就沿用上一次。
    val width = videoSize.width.takeIf { it > 0 } ?: video?.width ?: 0
    val height = videoSize.height.takeIf { it > 0 } ?: video?.height ?: 0
    val frameRate = video?.frameRate?.takeIf { it > 0f } ?: source?.frameRate?.takeIf { it > 0f }
    val counters = (player as? ExoPlayer)?.videoDecoderCounters
    counters?.ensureUpdated()
    val rendered = counters?.renderedOutputBufferCount?.toLong() ?: 0L
    val dropped = counters?.droppedBufferCount ?: 0
    val total = rendered + dropped
    val videoUrl = source?.videoUrl ?: player.currentMediaItem?.localConfiguration?.uri?.toString()
    return StatsSnapshot(
        quality = source?.qualities?.firstOrNull { it.quality == source.quality }?.label,
        resolution = if (width > 0 && height > 0) {
            "$width x $height"
        } else {
            previous?.resolution ?: "未知"
        },
        frameRate = if (frameRate != null) {
            String.format(Locale.US, "%.3f fps", frameRate)
        } else {
            previous?.frameRate ?: "未知"
        },
        videoCodec = codecText(video),
        audioCodec = codecText(audio),
        videoBitrate = bitrateText(video?.bitrate ?: 0),
        audioBitrate = bitrateText(audio?.bitrate ?: 0),
        renderedFrames = rendered,
        droppedDetail = if (total > 0) {
            String.format(Locale.US, "%d (%.1f%%)", dropped, dropped * 100f / total)
        } else {
            dropped.toString()
        },
        buffer = formatBuffer(player.bufferedPosition - player.currentPosition),
        bandwidth = bandwidthText(stats.bandwidthKbps),
        videoHost = hostOf(videoUrl),
        audioHost = hostOf(source?.audioUrl ?: videoUrl),
        playerType = playerType(videoUrl),
        speed = String.format(Locale.US, "%.2fx", player.playbackParameters.speed),
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

private fun bandwidthText(kbps: Long): String = when {
    kbps <= 0 -> "未知"
    kbps < 1000 -> "$kbps Kbps"
    else -> String.format(Locale.US, "%.1f Mbps", kbps / 1000f)
}

private fun formatBuffer(ms: Long): String =
    String.format(Locale.US, "%.1f 秒", ms.coerceAtLeast(0L) / 1000f)

private fun hostOf(url: String?): String {
    val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return "未知"
    val host = uri.host ?: return "未知"
    return if (uri.port > 0) "$host:${uri.port}" else host
}

private fun playerType(url: String?): String = when {
    url == null -> "未知"
    url.contains(".m3u8", ignoreCase = true) -> "HLS"
    url.contains(".mpd", ignoreCase = true) -> "DASH"
    else -> "MP4 / Progressive"
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
