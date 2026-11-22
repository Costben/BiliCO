package erl.bilico.data.repository

import android.content.Context
import erl.bilico.data.model.BangumiDetail
import erl.bilico.data.model.BangumiEpisode
import erl.bilico.data.model.DownloadStatus
import erl.bilico.data.model.DownloadTask
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.PlayerAuxiliaryInfo
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoPart
import erl.bilico.data.network.BiliClient
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 离线缓存仓库：任务队列调度、音视频断点下载、封面/弹幕/字幕落盘与离线播放数据源拼装。
 */
class DownloadRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val client: BiliClient,
    private val videoRepository: VideoRepository,
    private val pgcRepository: PgcRepository,
    private val danmakuRepository: DanmakuRepository,
    private val subtitleRepository: SubtitleRepository,
    private val settings: SettingsRepository,
) {
    private val storage = DownloadStorage(context.applicationContext)
    private val stateLock = Any()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _totalCachedBytes = MutableStateFlow(0L)
    val totalCachedBytes: StateFlow<Long> = _totalCachedBytes.asStateFlow()

    @Volatile
    var onActiveDownloadsChanged: ((hasActive: Boolean) -> Unit)? = null

    private var workerJob: Job? = null
    private val taskJobs = ConcurrentHashMap<String, Job>()

    init {
        val loaded = storage.loadIndex()
        _tasks.value = loaded
        _totalCachedBytes.value = storage.totalUsedBytes()
        danmakuRepository.localSegmentLoader = ::loadLocalDanmakuBytes
        subtitleRepository.localInfoLoader = ::loadLocalPlayerInfo
    }

    // ---------- 入队与控制 ----------

    fun enqueueVideo(
        detail: VideoDetail,
        parts: List<VideoPart>,
        quality: Int,
        qualityLabel: String,
    ): Int {
        val targetParts = parts.ifEmpty {
            listOf(
                VideoPart(
                    cid = detail.cid,
                    index = 1,
                    title = "",
                    durationSec = detail.durationSec.toLong(),
                    aspect = detail.aspect,
                ),
            )
        }
        val now = System.currentTimeMillis()
        var added = 0
        synchronized(stateLock) {
            val current = _tasks.value.toMutableList()
            targetParts.forEachIndexed { idx, part ->
                if (part.cid <= 0L) return@forEachIndexed
                val id = DownloadTask.videoTaskId(detail.bvid, part.cid)
                val existingIdx = current.indexOfFirst { it.id == id }
                val existing = current.getOrNull(existingIdx)
                if (existing != null && (existing.isActive || existing.isCompleted)) return@forEachIndexed
                if (existing != null) {
                    storage.deleteTaskFiles(id)
                    current.removeAt(existingIdx)
                }
                val task = DownloadTask(
                    id = id,
                    isPgc = false,
                    bvid = detail.bvid,
                    aid = detail.aid,
                    cid = part.cid,
                    title = detail.title,
                    partTitle = part.title,
                    partIndex = part.index.coerceAtLeast(1),
                    totalParts = detail.pages.size.coerceAtLeast(targetParts.size),
                    coverUrl = detail.cover,
                    upMid = detail.upMid,
                    upName = detail.upName,
                    upFace = detail.upFace,
                    desc = detail.desc,
                    pubdate = detail.pubdate,
                    durationSec = part.durationSec.takeIf { it > 0L } ?: detail.durationSec.toLong(),
                    aspect = part.aspect.takeIf { it > 0f } ?: detail.aspect,
                    quality = quality,
                    qualityLabel = qualityLabel.ifBlank { DefaultQuality.fromId(quality).label },
                    status = DownloadStatus.QUEUED,
                    createdAtMs = now + idx,
                )
                current.add(0, task)
                added++
            }
            if (added > 0) commitTasksLocked(current)
        }
        if (added > 0) ensureWorker()
        return added
    }

    fun enqueueBangumi(
        detail: BangumiDetail,
        episodes: List<BangumiEpisode>,
        quality: Int,
        qualityLabel: String,
    ): Int {
        val now = System.currentTimeMillis()
        var added = 0
        synchronized(stateLock) {
            val current = _tasks.value.toMutableList()
            episodes.forEachIndexed { idx, ep ->
                if (ep.cid <= 0L) return@forEachIndexed
                val id = DownloadTask.pgcTaskId(detail.seasonId, ep.epId, ep.cid)
                val existingIdx = current.indexOfFirst { it.id == id }
                val existing = current.getOrNull(existingIdx)
                if (existing != null && (existing.isActive || existing.isCompleted)) return@forEachIndexed
                if (existing != null) {
                    storage.deleteTaskFiles(id)
                    current.removeAt(existingIdx)
                }
                val epIndex = detail.episodes.indexOfFirst { it.epId == ep.epId && it.cid == ep.cid }
                    .let { if (it >= 0) it + 1 else idx + 1 }
                val task = DownloadTask(
                    id = id,
                    isPgc = true,
                    bvid = ep.bvid,
                    aid = ep.aid,
                    cid = ep.cid,
                    seasonId = detail.seasonId,
                    epId = ep.epId,
                    title = detail.title,
                    partTitle = ep.title,
                    partIndex = epIndex,
                    totalParts = detail.episodes.size.coerceAtLeast(episodes.size),
                    coverUrl = ep.cover.ifBlank { detail.cover },
                    desc = detail.evaluate,
                    durationSec = ep.durationSec,
                    quality = quality,
                    qualityLabel = qualityLabel.ifBlank { DefaultQuality.fromId(quality).label },
                    status = DownloadStatus.QUEUED,
                    createdAtMs = now + idx,
                )
                current.add(0, task)
                added++
            }
            if (added > 0) commitTasksLocked(current)
        }
        if (added > 0) ensureWorker()
        return added
    }

    fun pause(taskId: String) {
        taskJobs.remove(taskId)?.cancel()
        synchronized(stateLock) {
            updateTaskLocked(taskId) { task ->
                if (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.DOWNLOADING) {
                    task.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
                } else {
                    task
                }
            }
        }
    }

    fun resume(taskId: String) {
        var resumed = false
        synchronized(stateLock) {
            updateTaskLocked(taskId) { task ->
                if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                    resumed = true
                    task.copy(
                        status = DownloadStatus.QUEUED,
                        errorMessage = null,
                        speedBytesPerSec = 0L,
                    )
                } else {
                    task
                }
            }
        }
        if (resumed) ensureWorker()
    }

    fun pauseAll() {
        taskJobs.values.forEach { it.cancel() }
        taskJobs.clear()
        synchronized(stateLock) {
            val updated = _tasks.value.map { task ->
                if (task.isActive) {
                    task.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
                } else {
                    task
                }
            }
            commitTasksLocked(updated)
        }
    }

    fun resumeAll() {
        var anyResumed = false
        synchronized(stateLock) {
            val updated = _tasks.value.map { task ->
                if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                    anyResumed = true
                    task.copy(status = DownloadStatus.QUEUED, errorMessage = null, speedBytesPerSec = 0L)
                } else {
                    task
                }
            }
            if (anyResumed) commitTasksLocked(updated)
        }
        if (anyResumed) ensureWorker()
    }

    fun delete(taskIds: Collection<String>) {
        if (taskIds.isEmpty()) return
        val targets = taskIds.toSet()
        targets.forEach { taskJobs.remove(it)?.cancel() }
        synchronized(stateLock) {
            val remaining = _tasks.value.filterNot { it.id in targets }
            commitTasksLocked(remaining)
        }
        scope.launch(Dispatchers.IO) {
            targets.forEach(storage::deleteTaskFiles)
            _totalCachedBytes.value = storage.totalUsedBytes()
        }
    }

    // ---------- 查询与离线播放数据源 ----------

    fun statusForVideo(bvid: String, cid: Long): DownloadStatus? =
        _tasks.value.firstOrNull { !it.isPgc && it.bvid == bvid && it.cid == cid }?.status

    fun statusForBangumi(seasonId: Long, epId: Long, cid: Long): DownloadStatus? =
        _tasks.value.firstOrNull {
            it.isPgc && it.seasonId == seasonId && it.epId == epId && it.cid == cid
        }?.status

    fun hasAnyCompletedForVideo(bvid: String): Boolean =
        _tasks.value.any { !it.isPgc && it.bvid == bvid && it.isCompleted && storage.hasPlayableFiles(it) }

    fun hasCompletedVideo(bvid: String, cid: Long): Boolean =
        findCompletedVideo(bvid, cid) != null

    fun findCompletedVideo(bvid: String, cid: Long): DownloadTask? {
        val list = _tasks.value.filter { !it.isPgc && it.bvid == bvid && it.isCompleted }
        val chosen = if (cid > 0L) list.firstOrNull { it.cid == cid } else list.firstOrNull()
        return chosen?.takeIf { storage.hasPlayableFiles(it) }
    }

    fun completedPartsForVideo(bvid: String): List<DownloadTask> =
        _tasks.value
            .filter { !it.isPgc && it.bvid == bvid && it.isCompleted && storage.hasPlayableFiles(it) }
            .sortedBy { it.partIndex }

    fun findCompletedBangumi(seasonId: Long, epId: Long): DownloadTask? {
        val list = _tasks.value.filter { it.isPgc && it.seasonId == seasonId && it.isCompleted }
        val chosen = if (epId > 0L) list.firstOrNull { it.epId == epId } else list.firstOrNull()
        return chosen?.takeIf { storage.hasPlayableFiles(it) }
    }

    fun completedEpisodesForSeason(seasonId: Long): List<DownloadTask> =
        _tasks.value
            .filter { it.isPgc && it.seasonId == seasonId && it.isCompleted && storage.hasPlayableFiles(it) }
            .sortedBy { it.partIndex }

    fun localCoverUri(task: DownloadTask): String? = storage.localCoverUriFor(task)

    fun buildOfflineVideoDetail(bvid: String): VideoDetail? =
        storage.toOfflineVideoDetail(completedPartsForVideo(bvid))

    fun buildOfflineBangumiDetail(seasonId: Long): BangumiDetail? =
        storage.toOfflineBangumiDetail(completedEpisodesForSeason(seasonId))

    fun buildOfflinePlaybackSource(task: DownloadTask): PlaybackSource? =
        storage.toOfflinePlaybackSource(task)

    fun taskDiskUsage(task: DownloadTask): Long = storage.taskDiskUsage(task)

    // ---------- 队列调度与下载执行 ----------

    private fun ensureWorker() {
        synchronized(stateLock) {
            if (workerJob?.isActive == true) return
            workerJob = scope.launch(Dispatchers.IO) {
                while (true) {
                    val next = synchronized(stateLock) {
                        _tasks.value.lastOrNull { it.status == DownloadStatus.QUEUED }
                    } ?: break
                    val job = launch { executeTask(next.id) }
                    taskJobs[next.id] = job
                    job.join()
                    taskJobs.remove(next.id)
                }
                notifyActiveChanged()
            }
        }
        notifyActiveChanged()
    }

    private suspend fun executeTask(taskId: String) {
        val initial = _tasks.value.firstOrNull { it.id == taskId } ?: return
        if (initial.status != DownloadStatus.QUEUED) return

        updateTask(taskId) { it.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null, speedBytesPerSec = 0L) }
        try {
            val cdn = CdnOption.fromId(settings.cdnOption.value)
            val source = fetchPlaybackSource(initial, cdn)
            val hasAudio = !source.audioUrl.isNullOrBlank()
            val videoExt = if (!hasAudio && source.videoUrl.contains(".mp4", ignoreCase = true)) "mp4" else "m4s"
            val actualQuality = source.quality
            val actualLabel = source.qualities.firstOrNull { it.quality == actualQuality }?.label
                ?.takeIf { it.isNotBlank() }
                ?: DefaultQuality.fromId(actualQuality).label

            val prepared = synchronized(stateLock) {
                updateTaskLocked(taskId) {
                    it.copy(
                        hasAudio = hasAudio,
                        videoExt = videoExt,
                        quality = actualQuality,
                        qualityLabel = actualLabel,
                    )
                }
                _tasks.value.firstOrNull { it.id == taskId }
            } ?: return

            val headers = buildMediaHeaders(prepared)
            val videoCandidates = listOf(source.videoUrl) + source.videoBackupUrls
            val audioCandidates = if (hasAudio) {
                listOfNotNull(source.audioUrl) + source.audioBackupUrls
            } else {
                emptyList()
            }
            val videoFinal = storage.videoFile(prepared)
            val videoPart = storage.videoPartFile(prepared)
            val audioFinal = storage.audioFile(prepared)
            val audioPart = storage.audioPartFile(prepared)

            val probedVideoSize = if (videoFinal.exists()) {
                videoFinal.length()
            } else {
                DownloadTransfer.probeContentLength(client.mediaClient(), videoCandidates, headers)
            }
            val probedAudioSize = if (!hasAudio) {
                0L
            } else if (audioFinal.exists()) {
                audioFinal.length()
            } else {
                DownloadTransfer.probeContentLength(client.mediaClient(), audioCandidates, headers)
            }
            val knownTotal = if (probedVideoSize > 0L && (!hasAudio || probedAudioSize > 0L)) {
                probedVideoSize + probedAudioSize
            } else {
                0L
            }
            if (knownTotal > 0L) {
                updateTask(taskId) { it.copy(totalBytes = knownTotal) }
            }

            val videoLength = DownloadTransfer.downloadWithFallback(
                client = client.mediaClient(),
                candidates = videoCandidates,
                headers = headers,
                partFile = videoPart,
                finalFile = videoFinal,
            ) { vDownloaded, vTotal, speed ->
                val audioExisting = if (audioFinal.exists()) audioFinal.length() else if (audioPart.exists()) audioPart.length() else 0L
                val total = when {
                    knownTotal > 0L -> knownTotal
                    vTotal > 0L && probedAudioSize > 0L -> vTotal + probedAudioSize
                    vTotal > 0L -> vTotal
                    else -> 0L
                }
                updateTaskProgress(taskId, vDownloaded + audioExisting, total, speed)
            }

            val audioLength = if (hasAudio) {
                DownloadTransfer.downloadWithFallback(
                    client = client.mediaClient(),
                    candidates = audioCandidates,
                    headers = headers,
                    partFile = audioPart,
                    finalFile = audioFinal,
                ) { aDownloaded, aTotal, speed ->
                    val total = when {
                        aTotal > 0L -> videoLength + aTotal
                        knownTotal > 0L -> knownTotal
                        else -> videoLength + aDownloaded
                    }
                    updateTaskProgress(taskId, videoLength + aDownloaded, total, speed)
                }
            } else {
                0L
            }

            // 封面、弹幕与字幕为增强体验的附属资源，失败不拖垮主视频缓存完成态。
            val (subs, chapters) = storage.downloadAssetsBestEffort(
                task = prepared,
                client = client,
                danmakuRepository = danmakuRepository,
                subtitleRepository = subtitleRepository,
            )

            val totalFinalBytes = videoLength + audioLength
            synchronized(stateLock) {
                updateTaskLocked(taskId) { task ->
                    task.copy(
                        status = DownloadStatus.COMPLETED,
                        downloadedBytes = totalFinalBytes,
                        totalBytes = totalFinalBytes,
                        speedBytesPerSec = 0L,
                        completedAtMs = System.currentTimeMillis(),
                        errorMessage = null,
                        subtitles = subs,
                        chapters = chapters,
                    )
                }
            }
            _totalCachedBytes.value = storage.totalUsedBytes()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            updateTask(taskId) { task ->
                if (task.status == DownloadStatus.PAUSED) {
                    task
                } else {
                    task.copy(
                        status = DownloadStatus.FAILED,
                        speedBytesPerSec = 0L,
                        errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "下载失败",
                    )
                }
            }
        }
    }

    private suspend fun fetchPlaybackSource(task: DownloadTask, cdn: CdnOption): PlaybackSource =
        if (task.isPgc) {
            pgcRepository.playback(
                epId = task.epId,
                cid = task.cid,
                qn = task.quality,
                aid = task.aid,
                seasonId = task.seasonId,
                bvid = task.bvid,
                cdn = cdn,
            ).getOrThrow()
        } else {
            videoRepository.playback(
                bvid = task.bvid,
                cid = task.cid,
                qn = task.quality,
                cdn = cdn,
            ).getOrThrow()
        }

    private fun buildMediaHeaders(task: DownloadTask): Map<String, String> =
        if (task.isPgc) {
            val referer = if (task.epId > 0L) {
                "https://www.bilibili.com/bangumi/play/ep${task.epId}"
            } else {
                "https://www.bilibili.com/bangumi/play/ss${task.seasonId}"
            }
            client.mediaRequestHeaders().toMutableMap().apply { put("Referer", referer) }
        } else {
            client.mediaRequestHeaders(task.bvid)
        }

    private fun loadLocalDanmakuBytes(cid: Long, index: Int): ByteArray? {
        val task = _tasks.value.firstOrNull { it.cid == cid && it.isCompleted } ?: return null
        return storage.readLocalDanmakuBytes(task, index)
    }

    private fun loadLocalPlayerInfo(cid: Long): PlayerAuxiliaryInfo? {
        val task = _tasks.value.firstOrNull { it.cid == cid && it.isCompleted } ?: return null
        return storage.readLocalPlayerInfo(task)
    }

    private fun updateTaskProgress(
        taskId: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ) {
        synchronized(stateLock) {
            val current = _tasks.value.toMutableList()
            val index = current.indexOfFirst { it.id == taskId }
            if (index < 0) return
            val existing = current[index]
            if (existing.status != DownloadStatus.DOWNLOADING) return
            current[index] = existing.copy(
                downloadedBytes = downloadedBytes.coerceAtLeast(0L),
                totalBytes = maxOf(existing.totalBytes, totalBytes),
                speedBytesPerSec = speedBytesPerSec.coerceAtLeast(0L),
            )
            _tasks.value = current
        }
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        synchronized(stateLock) {
            updateTaskLocked(taskId, transform)
        }
    }

    private fun updateTaskLocked(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        val current = _tasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == taskId }
        if (index < 0) return
        current[index] = transform(current[index])
        commitTasksLocked(current)
    }

    private fun commitTasksLocked(next: List<DownloadTask>) {
        _tasks.value = next
        storage.saveIndex(next)
        notifyActiveChanged()
    }

    private fun notifyActiveChanged() {
        val hasActive = _tasks.value.any { it.isActive }
        onActiveDownloadsChanged?.invoke(hasActive)
    }
}
