package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LiveListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveListData? = null,
)

@Serializable
data class LiveListData(
    val list: List<LiveRoom> = emptyList(),
    val count: Int = 0,
    @SerialName("has_more") val hasMore: Int = 0,
)

@Serializable
data class LiveRoom(
    @SerialName("roomid") val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val uname: String = "",
    val face: String = "",
    val cover: String = "",
    @SerialName("user_cover") val userCover: String = "",
    @SerialName("system_cover") val systemCover: String = "",
    val keyframe: String = "",
    val online: Long = 0,
    @SerialName("area_name") val areaName: String = "",
    @SerialName("area_v2_name") val areaV2Name: String = "",
    @SerialName("parent_name") val parentName: String = "",
    @SerialName("area_v2_parent_name") val areaV2ParentName: String = "",
)

@Serializable
data class LivePlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LivePlayUrlData? = null,
)

@Serializable
data class LivePlayUrlData(
    val durl: List<LiveDurl> = emptyList(),
    @SerialName("quality_description") val qualityDescription: List<LiveQuality> = emptyList(),
    @SerialName("current_quality") val currentQuality: Int = 0,
    @SerialName("playurl_info") val playurlInfo: LivePlayurlInfo? = null,
    @SerialName("live_status") val liveStatus: Int? = null,
)

@Serializable
data class LivePlayurlInfo(
    val playurl: LivePlayurl? = null,
)

@Serializable
data class LivePlayurl(
    val stream: List<LiveStream> = emptyList(),
    @SerialName("g_qn_desc") val gQnDesc: List<LiveQuality> = emptyList(),
)

@Serializable
data class LiveStream(
    @SerialName("protocol_name") val protocolName: String = "",
    val format: List<LiveFormat> = emptyList(),
)

@Serializable
data class LiveFormat(
    @SerialName("format_name") val formatName: String = "",
    val codec: List<LiveCodec> = emptyList(),
)

@Serializable
data class LiveCodec(
    @SerialName("codec_name") val codecName: String = "",
    @SerialName("current_qn") val currentQn: Int = 0,
    @SerialName("accept_qn") val acceptQn: List<Int> = emptyList(),
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("url_info") val urlInfo: List<LiveUrlInfo> = emptyList(),
)

@Serializable
data class LiveUrlInfo(
    val host: String = "",
    val extra: String = "",
)

@Serializable
data class LiveDurl(
    val url: String = "",
    val order: Int = 0,
)

@Serializable
data class LiveQuality(
    val qn: Int = 0,
    val desc: String = "",
)

@Serializable
data class LiveRoomInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveRoomInfoData? = null,
)

@Serializable
data class LiveRoomInfoData(
    @SerialName("room_id") val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("user_cover") val userCover: String = "",
    val keyframe: String = "",
    val description: String = "",
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("area_name") val areaName: String = "",
    @SerialName("parent_area_name") val parentAreaName: String = "",
    val online: Long = 0,
)

@Serializable
data class LiveRoomDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveRoomDetailData? = null,
)

@Serializable
data class LiveRoomDetailData(
    @SerialName("room_info") val roomInfo: LiveRoomDetailInfo? = null,
    @SerialName("anchor_info") val anchorInfo: LiveAnchorInfo? = null,
)

@Serializable
data class LiveRoomDetailInfo(
    @SerialName("room_id") val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val description: String = "",
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("area_name") val areaName: String = "",
    @SerialName("parent_area_name") val parentAreaName: String = "",
    val online: Long = 0,
    val keyframe: String = "",
)

@Serializable
data class LiveAnchorInfo(
    @SerialName("base_info") val baseInfo: LiveAnchorBaseInfo? = null,
)

@Serializable
data class LiveAnchorBaseInfo(
    val uname: String = "",
    val face: String = "",
)

@Serializable
data class LiveDanmakuInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveDanmakuInfoData? = null,
)

@Serializable
data class LiveDanmakuInfoData(
    val token: String = "",
    @SerialName("host_list") val hosts: List<LiveDanmakuHost> = emptyList(),
)

@Serializable
data class LiveDanmakuHost(
    val host: String = "",
    val port: Int = 0,
    @SerialName("ws_port") val wsPort: Int = 0,
    @SerialName("wss_port") val wssPort: Int = 0,
)

/** 一条已经解好的直播弹幕，UI 只需要这些东西。 */
data class LiveDanmakuMessage(
    val name: String,
    val text: String,
    val mid: Long = 0,
    val medalName: String = "",
    val medalLevel: Int = 0,
)

/** 弹幕服务端：token 用在认证包里，urls 按顺序尝试。 */
data class LiveDanmakuServer(
    val token: String,
    val urls: List<String>,
)

