package erl.bilico.data.repository

import erl.bilico.data.model.DashTrack
import erl.bilico.data.model.PlayUrlData
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.QualityOption

/**
 * playurl 的 dash / durl 转成播放器直接能用的两种流。
 * 视频与番剧两个取流入口共用，清晰度菜单只保留真的拿到视频轨的那几档。
 */
internal fun PlayUrlData.toPlaybackSource(targetQn: Int): PlaybackSource {
    val dashVideoIds = dash?.video.orEmpty()
        .filter { it.baseUrl.isNotBlank() }
        .map { it.id }
        .toSet()
    val qualities = buildList {
        supportFormats.orEmpty().forEach { format ->
            if (dashVideoIds.isNotEmpty() && format.quality !in dashVideoIds) return@forEach
            add(
                QualityOption(
                    quality = format.quality,
                    label = format.newDescription.ifBlank { format.description },
                    needLogin = format.needLogin,
                    needVip = format.needVip,
                ),
            )
        }
        if (isEmpty()) {
            acceptQuality.forEachIndexed { index, quality ->
                if (dashVideoIds.isNotEmpty() && quality !in dashVideoIds) return@forEachIndexed
                add(QualityOption(quality, acceptDescription.getOrNull(index).orEmpty()))
            }
        }
    }

    val dashData = dash
    if (dashData != null) {
        val video = pickDashVideo(dashData.video, targetQn) ?: error("没有可播放的视频流")
        val audio = pickDashAudio(dashData.audio.orEmpty())
        return PlaybackSource(
            videoUrl = video.baseUrl,
            videoBackupUrls = video.backupUrl.orEmpty().filter { it.isNotBlank() },
            audioUrl = audio?.baseUrl,
            audioBackupUrls = audio?.backupUrl.orEmpty().filter { it.isNotBlank() },
            quality = video.id,
            qualities = qualities,
            durationMs = timelength,
            lastPlayTimeMs = lastPlayTime.coerceAtLeast(0L),
            lastPlayCid = lastPlayCid.coerceAtLeast(0L),
        )
    }

    val segment = durl?.firstOrNull() ?: error("播放地址为空")
    return PlaybackSource(
        videoUrl = segment.url,
        videoBackupUrls = segment.backupUrl.orEmpty().filter { it.isNotBlank() },
        audioUrl = null,
        audioBackupUrls = emptyList(),
        quality = quality,
        qualities = qualities,
        durationMs = segment.length,
        lastPlayTimeMs = lastPlayTime.coerceAtLeast(0L),
        lastPlayCid = lastPlayCid.coerceAtLeast(0L),
    )
}

private fun pickDashVideo(videos: List<DashTrack>, targetQn: Int): DashTrack? {
    val playable = videos.filter { it.baseUrl.isNotBlank() }
    if (playable.isEmpty()) return null
    val within = playable.filter { it.id <= targetQn }
    val pool = within.ifEmpty { playable }
    // 同一档位优先 AVC，兼容性最好；没有 AVC 时再用 HEVC/AV1。
    return pool.sortedWith(
        compareByDescending<DashTrack> { it.id }
            .thenBy { if (it.codecs.startsWith("avc")) 0 else 1 },
    ).firstOrNull()
}

private fun pickDashAudio(audios: List<DashTrack>): DashTrack? {
    val playable = audios.filter { it.baseUrl.isNotBlank() }
    if (playable.isEmpty()) return null
    val standard = playable.filter { it.id in AUDIO_TRACK_IDS }
    return standard.maxByOrNull { it.id } ?: playable.maxByOrNull { it.bandwidth }
}

private val AUDIO_TRACK_IDS = setOf(30216, 30232, 30280)
