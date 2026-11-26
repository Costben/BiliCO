package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ViewInfo? = null,
)

/** 单个视频的标签；`x/tag/archive/tags` 只下发这些字段里用得上的两个。 */
@Serializable
data class ArchiveTagsResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<ArchiveTag>? = null,
)

@Serializable
data class ArchiveTag(
    @SerialName("tag_id") val id: Long = 0,
    @SerialName("tag_name") val name: String = "",
)

/** 单条视频的统计数据，比 `x/web-interface/view` 轻得多，列表逐条补数据时用它。 */
@Serializable
data class ArchiveStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: VideoStat? = null,
)

@Serializable
data class VideoDimension(
    val width: Int = 0,
    val height: Int = 0,
    val rotate: Int = 0,
) {
    fun aspectRatio(): Float {
        if (width <= 0 || height <= 0) return 0f
        return if (rotate == 1 || rotate == 90 || rotate == 270) {
            height.toFloat() / width.toFloat()
        } else {
            width.toFloat() / height.toFloat()
        }
    }
}

@Serializable
data class VideoDescV2(
    @SerialName("raw_text") val rawText: String = "",
    val type: Int = 1,
    @SerialName("biz_id") val bizId: Long = 0,
)

@Serializable
data class VideoStaff(
    val mid: Long = 0,
    val name: String = "",
    val title: String = "",
)

@Serializable
data class ViewInfo(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    @SerialName("desc_v2") val descV2: List<VideoDescV2>? = null,
    val pic: String = "",
    val pubdate: Long = 0,
    val duration: Int = 0,
    val dimension: VideoDimension? = null,
    val owner: VideoOwner = VideoOwner(),
    val staff: List<VideoStaff>? = null,
    val stat: VideoStat = VideoStat(),
    val pages: List<VideoPage> = emptyList(),
    @SerialName("ugc_season") val ugcSeason: UgcSeason? = null,
)

/** 合集：同一 UP 把多个视频串成一份播放列表，接口只在视频属于合集时下发。 */
@Serializable
data class UgcSeason(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    val sections: List<UgcSection> = emptyList(),
)

@Serializable
data class UgcSection(
    val id: Long = 0,
    val title: String = "",
    val episodes: List<UgcEpisode> = emptyList(),
)

@Serializable
data class UgcEpisode(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val arc: UgcArc = UgcArc(),
)

@Serializable
data class UgcArc(
    val aid: Long = 0,
    val bvid: String = "",
    val pic: String = "",
    val title: String = "",
    val duration: Long = 0,
    val pubdate: Long = 0,
    val stat: VideoStat = VideoStat(),
)

@Serializable
data class VideoOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class VideoStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val favorite: Long = 0,
    val coin: Long = 0,
    val share: Long = 0,
    val like: Long = 0,
)

@Serializable
data class VideoPage(
    val cid: Long = 0,
    val page: Int = 0,
    val part: String = "",
    val duration: Long = 0,
    val dimension: VideoDimension? = null,
)

@Serializable
data class RelatedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<RelatedItem>? = null,
)

@Serializable
data class RelatedItem(
    val aid: Long = 0,
    val bvid: String = "",
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: VideoOwner = VideoOwner(),
    val stat: VideoStat = VideoStat(),
)

@Serializable
data class VideoshotResponse(
    val code: Int = 0,
    val message: String = "",
    val data: VideoshotData? = null,
)

@Serializable
data class VideoshotData(
    val pvdata: String = "",
    @SerialName("img_x_len") val imgXLen: Int = 10,
    @SerialName("img_y_len") val imgYLen: Int = 10,
    @SerialName("img_x_size") val imgXSize: Int = 160,
    @SerialName("img_y_size") val imgYSize: Int = 90,
    val image: List<String>? = null,
    val index: List<Int>? = null,
)

data class VideoPreviewFrame(
    val sheetUrl: String,
    val col: Int,
    val row: Int,
    val xLen: Int,
    val yLen: Int,
    val cellWidth: Int,
    val cellHeight: Int,
)

data class VideoPreviewShot(
    val images: List<String>,
    val index: List<Int>,
    val imgXLen: Int,
    val imgYLen: Int,
    val imgXSize: Int,
    val imgYSize: Int,
) {
    fun resolveFrame(positionMs: Long, durationMs: Long): VideoPreviewFrame? {
        if (images.isEmpty() || imgXLen <= 0 || imgYLen <= 0) return null
        val perSheet = imgXLen * imgYLen
        val totalSlots = images.size * perSheet
        if (totalSlots <= 0) return null

        val targetSec = (positionMs.coerceAtLeast(0L) / 1000L).toInt()
        val rawFrame = if (index.isNotEmpty()) {
            var lo = 0
            var hi = index.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (index[mid] <= targetSec) lo = mid + 1 else hi = mid
            }
            val matched = (lo - 1).coerceAtLeast(0)
            if (index.size >= 2 && index[0] == 0 && index[1] == 0) {
                (matched - 1).coerceAtLeast(0)
            } else {
                matched
            }
        } else if (durationMs > 0L) {
            val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.9999f)
            (fraction * totalSlots).toInt()
        } else {
            0
        }

        val frameIndex = rawFrame.coerceIn(0, totalSlots - 1)
        val sheetIndex = (frameIndex / perSheet).coerceIn(0, images.lastIndex)
        val inSheet = frameIndex % perSheet
        return VideoPreviewFrame(
            sheetUrl = images[sheetIndex],
            col = inSheet % imgXLen,
            row = inSheet / imgXLen,
            xLen = imgXLen,
            yLen = imgYLen,
            cellWidth = imgXSize.coerceAtLeast(1),
            cellHeight = imgYSize.coerceAtLeast(1),
        )
    }
}
