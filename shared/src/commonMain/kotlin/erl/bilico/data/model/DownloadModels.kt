package erl.bilico.data.model

import erl.bilico.platform.currentTimeMillis
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

@Serializable
data class OfflineSubtitleEntry(
    val key: String,
    val lan: String,
    val label: String,
    val isAi: Boolean = false,
    val fileName: String,
)

@Serializable
data class OfflineChapterEntry(
    val title: String,
    val fromMs: Long,
    val toMs: Long,
)

@Serializable
data class DownloadTask(
    val id: String,
    val isPgc: Boolean = false,
    val bvid: String,
    val aid: Long = 0L,
    val cid: Long,
    val seasonId: Long = 0L,
    val epId: Long = 0L,
    val title: String,
    val partTitle: String = "",
    val partIndex: Int = 1,
    val totalParts: Int = 1,
    val coverUrl: String = "",
    val upMid: Long = 0L,
    val upName: String = "",
    val upFace: String = "",
    val desc: String = "",
    val pubdate: Long = 0L,
    val durationSec: Long = 0L,
    val aspect: Float = 0f,
    val quality: Int = 80,
    val qualityLabel: String = "1080P",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null,
    val createdAtMs: Long = currentTimeMillis(),
    val completedAtMs: Long = 0L,
    val hasAudio: Boolean = true,
    val videoExt: String = "m4s",
    val subtitles: List<OfflineSubtitleEntry> = emptyList(),
    val chapters: List<OfflineChapterEntry> = emptyList(),
) {
    val isActive: Boolean
        get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING

    val isCompleted: Boolean
        get() = status == DownloadStatus.COMPLETED

    val progress: Float
        get() = when {
            status == DownloadStatus.COMPLETED -> 1f
            totalBytes > 0L -> (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val displayTitle: String
        get() = when {
            isPgc && partTitle.isNotBlank() -> "$title · $partTitle"
            totalParts > 1 && partTitle.isNotBlank() -> "$title (P$partIndex $partTitle)"
            totalParts > 1 -> "$title (P$partIndex)"
            else -> title
        }

    companion object {
        fun videoTaskId(bvid: String, cid: Long): String = "av_${bvid}_$cid"
        fun pgcTaskId(seasonId: Long, epId: Long, cid: Long): String = "pgc_${seasonId}_${epId}_$cid"
    }
}

@Serializable
internal data class DownloadIndexPayload(
    val version: Int = 1,
    val tasks: List<DownloadTask> = emptyList(),
)
