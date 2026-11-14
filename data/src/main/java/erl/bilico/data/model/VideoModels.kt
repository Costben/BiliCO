package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ViewInfo? = null,
)

/** 单条视频的统计数据，比 `x/web-interface/view` 轻得多，列表逐条补数据时用它。 */
@Serializable
data class ArchiveStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: VideoStat? = null,
)

@Serializable
data class ViewInfo(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    val pic: String = "",
    val pubdate: Long = 0,
    val duration: Int = 0,
    val owner: VideoOwner = VideoOwner(),
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
