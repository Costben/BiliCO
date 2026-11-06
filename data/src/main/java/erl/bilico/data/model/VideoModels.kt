package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ViewInfo? = null,
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
