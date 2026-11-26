package erl.bilico.data.repository

import erl.bilico.data.model.BangumiDetail
import erl.bilico.data.model.BangumiEpisode
import erl.bilico.data.model.DownloadTask
import erl.bilico.data.model.OfflineChapterEntry
import erl.bilico.data.model.OfflineSubtitleEntry
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.PlayerAuxiliaryInfo
import erl.bilico.data.model.QualityOption
import erl.bilico.data.model.SubtitleTrack
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoPart
import erl.bilico.data.network.BiliClient
import erl.bilico.data.util.Formatters
import erl.bilico.platform.dlFileExists
import erl.bilico.platform.dlFileLength
import erl.bilico.platform.dlPathToFileUri
import erl.bilico.platform.dlReadBytes
import erl.bilico.platform.dlWriteBytes
import erl.bilico.platform.dlWriteTextAtomic
import erl.bilico.platform.ioDispatcher
import kotlinx.coroutines.withContext

private const val MAX_DANMAKU_SEGMENTS = 50

internal fun DownloadStorage.localCoverUriFor(task: DownloadTask): String? {
    val path = coverPath(task)
    return if (dlFileExists(path) && dlFileLength(path) > 0L) dlPathToFileUri(path) else null
}

internal fun DownloadStorage.toOfflineVideoDetail(parts: List<DownloadTask>): VideoDetail? {
    val first = parts.firstOrNull() ?: return null
    val cover = localCoverUriFor(first) ?: first.coverUrl
    return VideoDetail(
        bvid = first.bvid,
        aid = first.aid,
        cid = first.cid,
        title = first.title,
        desc = first.desc,
        cover = cover,
        pubdate = first.pubdate,
        durationSec = first.durationSec.toInt(),
        upMid = first.upMid,
        upName = first.upName,
        upFace = first.upFace,
        view = 0L,
        danmaku = 0L,
        reply = 0L,
        like = 0L,
        coin = 0L,
        favorite = 0L,
        pages = parts.map { task ->
            VideoPart(
                cid = task.cid,
                index = task.partIndex,
                title = task.partTitle,
                durationSec = task.durationSec,
                aspect = task.aspect,
            )
        },
        aspect = first.aspect,
    )
}

internal fun DownloadStorage.toOfflineBangumiDetail(episodes: List<DownloadTask>): BangumiDetail? {
    val first = episodes.firstOrNull() ?: return null
    val cover = localCoverUriFor(first) ?: first.coverUrl
    return BangumiDetail(
        seasonId = first.seasonId,
        title = first.title,
        cover = cover,
        evaluate = first.desc,
        score = "",
        rankText = "",
        viewsText = "",
        followText = "",
        seasonTypeName = "本地缓存",
        metaText = "已缓存 ${episodes.size} 话",
        episodes = episodes.map { task ->
            BangumiEpisode(
                epId = task.epId,
                aid = task.aid,
                bvid = task.bvid,
                cid = task.cid,
                title = task.partTitle.ifBlank { "第 ${task.partIndex} 话" },
                cover = localCoverUriFor(task) ?: task.coverUrl,
                durationSec = task.durationSec,
                badge = "",
            )
        },
        seasons = emptyList(),
    )
}

internal fun DownloadStorage.toOfflinePlaybackSource(task: DownloadTask): PlaybackSource? {
    if (!hasPlayableFiles(task)) return null
    val videoUri = dlPathToFileUri(videoPath(task))
    val audioUri = if (task.hasAudio) dlPathToFileUri(audioPath(task)) else null
    return PlaybackSource(
        videoUrl = videoUri,
        videoBackupUrls = emptyList(),
        audioUrl = audioUri,
        audioBackupUrls = emptyList(),
        quality = task.quality,
        qualities = listOf(QualityOption(quality = task.quality, label = task.qualityLabel)),
        durationMs = task.durationSec * 1000L,
        aspect = task.aspect,
    )
}

internal fun DownloadStorage.readLocalDanmakuBytes(task: DownloadTask, index: Int): ByteArray? {
    val path = danmakuPath(task.id, index)
    if (!dlFileExists(path) || dlFileLength(path) <= 0L) return null
    return runCatching { dlReadBytes(path) }.getOrNull()
}

internal fun DownloadStorage.readLocalPlayerInfo(task: DownloadTask): PlayerAuxiliaryInfo? {
    if (task.subtitles.isEmpty() && task.chapters.isEmpty()) return null
    val tracks = task.subtitles.mapNotNull { entry ->
        val path = subtitlePath(task.id, entry.fileName)
        if (!dlFileExists(path) || dlFileLength(path) <= 0L) return@mapNotNull null
        SubtitleTrack(
            key = entry.key,
            lan = entry.lan,
            label = entry.label,
            url = dlPathToFileUri(path),
            isAi = entry.isAi,
        )
    }
    val chapters = task.chapters.map {
        VideoChapter(title = it.title, fromMs = it.fromMs, toMs = it.toMs)
    }
    return PlayerAuxiliaryInfo(subtitles = tracks, chapters = chapters)
}

internal suspend fun DownloadStorage.downloadAssetsBestEffort(
    task: DownloadTask,
    client: BiliClient,
    danmakuRepository: DanmakuRepository,
    subtitleRepository: SubtitleRepository,
): Pair<List<OfflineSubtitleEntry>, List<OfflineChapterEntry>> {
    val coverUrl = Formatters.sizedImageUrl(task.coverUrl, 640, 360)
    if (coverUrl.startsWith("http")) {
        val coverOut = coverPath(task)
        if (!dlFileExists(coverOut) || dlFileLength(coverOut) <= 0L) {
            runCatching {
                val bytes = withContext(ioDispatcher) { client.fetchBytes(coverUrl) }
                if (bytes.isNotEmpty()) dlWriteBytes(coverOut, bytes)
            }
        }
    }

    if (task.cid > 0L) {
        val durationSec = task.durationSec.coerceAtLeast(1L)
        val segmentCount = ((durationSec - 1L) / 360L + 1L).toInt().coerceIn(1, MAX_DANMAKU_SEGMENTS)
        for (idx in 1..segmentCount) {
            val path = danmakuPath(task.id, idx)
            if (dlFileExists(path) && dlFileLength(path) > 0L) continue
            val bytes = danmakuRepository.fetchRawSegment(task.cid, idx) ?: continue
            if (bytes.isNotEmpty()) runCatching { dlWriteBytes(path, bytes) }
        }
    }

    if (task.cid <= 0L) return emptyList<OfflineSubtitleEntry>() to emptyList()
    val info = subtitleRepository.fetchPlayerInfo(
        bvid = task.bvid,
        aid = task.aid,
        cid = task.cid,
    ).getOrNull() ?: return emptyList<OfflineSubtitleEntry>() to emptyList()

    val savedSubs = mutableListOf<OfflineSubtitleEntry>()
    info.subtitles.take(4).forEachIndexed { index, track ->
        val raw = subtitleRepository.fetchRawSubtitle(track.url) ?: return@forEachIndexed
        val fileName = "sub_$index.json"
        val path = subtitlePath(task.id, fileName)
        if (runCatching { dlWriteTextAtomic(path, raw) }.getOrDefault(false)) {
            savedSubs += OfflineSubtitleEntry(
                key = track.key,
                lan = track.lan,
                label = track.label,
                isAi = track.isAi,
                fileName = fileName,
            )
        }
    }
    val chapters = info.chapters.map {
        OfflineChapterEntry(title = it.title, fromMs = it.fromMs, toMs = it.toMs)
    }
    return savedSubs to chapters
}
