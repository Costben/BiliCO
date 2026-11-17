package erl.bilico.data.repository

import erl.bilico.data.model.DashTrack
import erl.bilico.data.model.PlayUrlData
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.QualityOption

/**
 * playurl 的 dash / durl 转成播放器直接能用的两种流。
 * 视频与番剧两个取流入口共用。
 *
 * 清晰度菜单默认只保留真的拿到视频轨的那几档。[vipAccount] 为 true 时把这些限制放开：
 * 大会员能看到 4K / HDR / 杜比视界这些本来该有的完整档位，即使这一路没取到轨；
 * 选中后没有对应轨就按现有规则降到最近的一档并提示。非大会员保持只列能播的。
 *
 * [cdn] 决定首选线路；其余候选地址按同一套规则排好序塞进 backup 列表，
 * 播放失败时播放器可以就地换线，不用重新请求 playurl。
 */
internal fun PlayUrlData.toPlaybackSource(
    targetQn: Int,
    cdn: CdnOption = CdnOption.Auto,
    vipAccount: Boolean = false,
): PlaybackSource {
    val dashVideoIds = dash?.video.orEmpty()
        .filter { it.baseUrl.isNotBlank() }
        .map { it.id }
        .toSet()
    // 没取到视频轨的档位：非大会员直接不列，大会员留着（点了会降级并提示）。
    fun keepWithoutTrack(quality: Int): Boolean =
        vipAccount || dashVideoIds.isEmpty() || quality in dashVideoIds
    val qualities = buildList {
        supportFormats.orEmpty().forEach { format ->
            if (!keepWithoutTrack(format.quality)) return@forEach
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
                if (!keepWithoutTrack(quality)) return@forEachIndexed
                add(QualityOption(quality, acceptDescription.getOrNull(index).orEmpty()))
            }
        }
    }

    val dashData = dash
    if (dashData != null) {
        val video = pickDashVideo(dashData.video, targetQn) ?: error("没有可播放的视频流")
        val audio = pickDashAudio(dashData.audio.orEmpty())
        val videoUrls = PlaybackCdn.candidates(
            listOf(video.baseUrl) + video.backupUrl.orEmpty(),
            cdn,
        )
        val audioUrls = audio?.let {
            PlaybackCdn.candidates(listOf(it.baseUrl) + it.backupUrl.orEmpty(), cdn)
        }.orEmpty()
        return PlaybackSource(
            videoUrl = videoUrls.firstOrNull() ?: video.baseUrl,
            videoBackupUrls = videoUrls.drop(1),
            audioUrl = audioUrls.firstOrNull(),
            audioBackupUrls = audioUrls.drop(1),
            quality = video.id,
            qualities = qualities,
            durationMs = timelength,
            lastPlayTimeMs = lastPlayTime.coerceAtLeast(0L),
            lastPlayCid = lastPlayCid.coerceAtLeast(0L),
            aspect = if (video.width > 0 && video.height > 0) {
                video.width.toFloat() / video.height.toFloat()
            } else {
                0f
            },
            frameRate = video.frameRate,
        )
    }

    val segment = durl?.firstOrNull() ?: error("播放地址为空")
    val segmentUrls = PlaybackCdn.candidates(
        listOf(segment.url) + segment.backupUrl.orEmpty(),
        cdn,
    )
    return PlaybackSource(
        videoUrl = segmentUrls.firstOrNull() ?: segment.url,
        videoBackupUrls = segmentUrls.drop(1),
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
    // 同一档位优先 AVC，兼容性最好；没有 AVC 时再用 HEVC/AV1。
    // 没有不超过目标清晰度的轨时（例如低画质偏好遇到高起跳片源），取最低可用档位，行为对齐 PiliPlus。
    val comparator = if (within.isNotEmpty()) {
        compareByDescending<DashTrack> { it.id }
    } else {
        compareBy<DashTrack> { it.id }
    }.thenBy { if (it.codecs.startsWith("avc")) 0 else 1 }
    val pool = within.ifEmpty { playable }
    return pool.sortedWith(comparator).firstOrNull()
}

private fun pickDashAudio(audios: List<DashTrack>): DashTrack? {
    val playable = audios.filter { it.baseUrl.isNotBlank() }
    if (playable.isEmpty()) return null
    val standard = playable.filter { it.id in AUDIO_TRACK_IDS }
    return standard.maxByOrNull { it.id } ?: playable.maxByOrNull { it.bandwidth }
}

private val AUDIO_TRACK_IDS = setOf(30216, 30232, 30280)
