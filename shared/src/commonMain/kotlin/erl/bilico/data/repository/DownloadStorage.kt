package erl.bilico.data.repository

import erl.bilico.data.model.DownloadIndexPayload
import erl.bilico.data.model.DownloadStatus
import erl.bilico.data.model.DownloadTask
import erl.bilico.data.network.BiliClient
import erl.bilico.platform.DownloadLock
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.dlDeleteDirectory
import erl.bilico.platform.dlDirectoryDiskUsage
import erl.bilico.platform.dlEnsureDir
import erl.bilico.platform.dlFileExists
import erl.bilico.platform.dlFileLength
import erl.bilico.platform.dlReadText
import erl.bilico.platform.dlWriteTextAtomic
import erl.bilico.platform.downloadsRootPath

/**
 * 离线缓存目录与元数据索引管理（跨平台）。
 *
 * 目录结构：
 * `<root>/index.json`
 * `<root>/<taskId>/video.m4s|mp4`
 * `<root>/<taskId>/audio.m4s`
 * `<root>/<taskId>/cover.jpg`
 * `<root>/<taskId>/danmaku_<idx>.pb`
 * `<root>/<taskId>/sub_<idx>.json`
 */
internal class DownloadStorage(context: PlatformContext) {

    val rootDir: String = downloadsRootPath(context)

    private val indexPath = "$rootDir/index.json"
    private val indexLock = DownloadLock()

    fun loadIndex(): List<DownloadTask> = indexLock.withLock {
        if (!dlFileExists(indexPath)) return@withLock emptyList()
        val raw = runCatching { dlReadText(indexPath) }.getOrNull().orEmpty()
        if (raw.isBlank()) return@withLock emptyList()
        val tasks = runCatching {
            BiliClient.json.decodeFromString(DownloadIndexPayload.serializer(), raw).tasks
        }.getOrDefault(emptyList())
        // 进程重启后仍在运行/排队的任务置为暂停，保留 .part 断点，避免冷启动自动偷跑流量。
        tasks.map { task ->
            val sanitized = if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                task.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L)
            } else {
                task
            }
            if (sanitized.status == DownloadStatus.COMPLETED && !hasPlayableFiles(sanitized)) {
                sanitized.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = "缓存文件已丢失",
                    speedBytesPerSec = 0L,
                )
            } else {
                sanitized
            }
        }
    }

    fun saveIndex(tasks: List<DownloadTask>) = indexLock.withLock {
        dlEnsureDir(rootDir)
        val payload = DownloadIndexPayload(tasks = tasks)
        val encoded = BiliClient.json.encodeToString(DownloadIndexPayload.serializer(), payload)
        dlWriteTextAtomic(indexPath, encoded)
    }

    fun taskDir(taskId: String): String = "$rootDir/${safeDirName(taskId)}".also {
        dlEnsureDir(it)
    }

    fun videoPath(task: DownloadTask): String = "${taskDir(task.id)}/video.${task.videoExt}"

    fun videoPartPath(task: DownloadTask): String = "${taskDir(task.id)}/video.${task.videoExt}.part"

    fun audioPath(task: DownloadTask): String = "${taskDir(task.id)}/audio.m4s"

    fun audioPartPath(task: DownloadTask): String = "${taskDir(task.id)}/audio.m4s.part"

    fun coverPath(task: DownloadTask): String = "${taskDir(task.id)}/cover.jpg"

    fun danmakuPath(taskId: String, index: Int): String = "${taskDir(taskId)}/danmaku_$index.pb"

    fun subtitlePath(taskId: String, fileName: String): String = "${taskDir(taskId)}/$fileName"

    fun hasPlayableFiles(task: DownloadTask): Boolean {
        val video = videoPath(task)
        if (!dlFileExists(video) || dlFileLength(video) <= 0L) return false
        if (task.hasAudio) {
            val audio = audioPath(task)
            if (!dlFileExists(audio) || dlFileLength(audio) <= 0L) return false
        }
        return true
    }

    fun deleteTaskFiles(taskId: String) {
        val dir = "$rootDir/${safeDirName(taskId)}"
        runCatching { dlDeleteDirectory(dir) }
    }

    fun taskDiskUsage(task: DownloadTask): Long {
        val dir = "$rootDir/${safeDirName(task.id)}"
        return dlDirectoryDiskUsage(dir)
    }

    fun totalUsedBytes(): Long =
        dlDirectoryDiskUsage(rootDir, excludeNames = setOf("index.json", ".nomedia"))

    private fun safeDirName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
