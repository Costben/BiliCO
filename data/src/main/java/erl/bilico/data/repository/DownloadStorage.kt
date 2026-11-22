package erl.bilico.data.repository

import android.content.Context
import erl.bilico.data.model.DownloadIndexPayload
import erl.bilico.data.model.DownloadStatus
import erl.bilico.data.model.DownloadTask
import erl.bilico.data.network.BiliClient
import java.io.File

/**
 * 离线缓存目录与元数据索引管理。
 *
 * 目录结构：
 * `<externalFilesDir or filesDir>/downloads/.nomedia`
 * `<root>/index.json`
 * `<root>/<taskId>/video.m4s|mp4`
 * `<root>/<taskId>/audio.m4s`
 * `<root>/<taskId>/cover.jpg`
 * `<root>/<taskId>/danmaku_<idx>.pb`
 * `<root>/<taskId>/sub_<idx>.json`
 */
internal class DownloadStorage(context: Context) {

    val rootDir: File = run {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "downloads").apply {
            if (!exists()) mkdirs()
            val nomedia = File(this, ".nomedia")
            if (!nomedia.exists()) runCatching { nomedia.createNewFile() }
        }
    }

    private val indexFile = File(rootDir, "index.json")
    private val indexLock = Any()

    fun loadIndex(): List<DownloadTask> = synchronized(indexLock) {
        if (!indexFile.exists()) return emptyList()
        val raw = runCatching { indexFile.readText() }.getOrNull().orEmpty()
        if (raw.isBlank()) return emptyList()
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

    fun saveIndex(tasks: List<DownloadTask>) = synchronized(indexLock) {
        if (!rootDir.exists()) rootDir.mkdirs()
        val payload = DownloadIndexPayload(tasks = tasks)
        val encoded = BiliClient.json.encodeToString(DownloadIndexPayload.serializer(), payload)
        val tmp = File(rootDir, "index.json.tmp")
        tmp.writeText(encoded)
        if (!tmp.renameTo(indexFile)) {
            indexFile.writeText(encoded)
            tmp.delete()
        }
    }

    fun taskDir(taskId: String): File = File(rootDir, safeDirName(taskId)).apply {
        if (!exists()) mkdirs()
    }

    fun videoFile(task: DownloadTask): File = File(taskDir(task.id), "video.${task.videoExt}")

    fun videoPartFile(task: DownloadTask): File = File(taskDir(task.id), "video.${task.videoExt}.part")

    fun audioFile(task: DownloadTask): File = File(taskDir(task.id), "audio.m4s")

    fun audioPartFile(task: DownloadTask): File = File(taskDir(task.id), "audio.m4s.part")

    fun coverFile(task: DownloadTask): File = File(taskDir(task.id), "cover.jpg")

    fun danmakuFile(taskId: String, index: Int): File = File(taskDir(taskId), "danmaku_$index.pb")

    fun subtitleFile(taskId: String, fileName: String): File = File(taskDir(taskId), fileName)

    fun hasPlayableFiles(task: DownloadTask): Boolean {
        val video = videoFile(task)
        if (!video.exists() || video.length() <= 0L) return false
        if (task.hasAudio) {
            val audio = audioFile(task)
            if (!audio.exists() || audio.length() <= 0L) return false
        }
        return true
    }

    fun deleteTaskFiles(taskId: String) {
        val dir = File(rootDir, safeDirName(taskId))
        if (dir.exists()) {
            runCatching { dir.deleteRecursively() }
        }
    }

    fun taskDiskUsage(task: DownloadTask): Long {
        val dir = File(rootDir, safeDirName(task.id))
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun totalUsedBytes(): Long {
        if (!rootDir.exists()) return 0L
        return rootDir.walkTopDown()
            .filter { it.isFile && it.name != "index.json" && it.name != ".nomedia" }
            .sumOf { it.length() }
    }

    private fun safeDirName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
