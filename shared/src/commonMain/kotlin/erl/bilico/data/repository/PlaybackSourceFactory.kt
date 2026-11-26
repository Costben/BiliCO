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
        val primaryVideoUrls = PlaybackCdn.candidates(
            listOf(video.baseUrl) + video.backupUrl.orEmpty(),
            cdn,
        )
        // 同清晰度的次选兼容编码轨（例如 AVC 节点卡住时回退 HVC1）一并加入备用列表，避免死等单条 CDN。
        val siblingCodecUrls = dashData.video
            .asSequence()
            .filter { it.id == video.id && it !== video && it.baseUrl.isNotBlank() && !it.codecs.startsWith("av01", ignoreCase = true) }
            .sortedBy(::videoCodecPriority)
            .flatMap { sibling ->
                PlaybackCdn.candidates(listOf(sibling.baseUrl) + sibling.backupUrl.orEmpty(), cdn).asSequence()
            }
            .toList()
        val videoUrls = (primaryVideoUrls + siblingCodecUrls).distinct()
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

private fun videoCodecPriority(track: DashTrack): Int {
    val c = track.codecs.lowercase()
    return when {
        c.startsWith("avc") -> 0
        c.startsWith("hvc1") -> 1
        c.startsWith("hev1") -> 2
        c.startsWith("av01") -> 4
        else -> 3
    }
}

private fun pickDashVideo(videos: List<DashTrack>, targetQn: Int): DashTrack? {
    val playable = videos.filter { it.baseUrl.isNotBlank() }
    if (playable.isEmpty()) return null
    // Apple AVPlayer 解码 av01 DASH 流会出现 0x0 黑屏：只要存在非 av01 轨道就优先排除 av01。
    val preferredPool = playable.filterNot { it.codecs.startsWith("av01", ignoreCase = true) }
        .ifEmpty { playable }
    val within = preferredPool.filter { it.id <= targetQn }
    val comparator = if (within.isNotEmpty()) {
        compareByDescending<DashTrack> { it.id }
    } else {
        compareBy<DashTrack> { it.id }
    }.thenBy(::videoCodecPriority)
    val pool = within.ifEmpty { preferredPool }
    return pool.sortedWith(comparator).firstOrNull()
}

private fun pickDashAudio(audios: List<DashTrack>): DashTrack? {
    val playable = audios.filter { it.baseUrl.isNotBlank() }
    if (playable.isEmpty()) return null
    val standard = playable.filter { it.id in AUDIO_TRACK_IDS }
    return standard.maxByOrNull { it.id } ?: playable.maxByOrNull { it.bandwidth }
}

private val AUDIO_TRACK_IDS = setOf(30216, 30232, 30280)
