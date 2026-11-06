package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayerInfoData? = null,
)

@Serializable
data class PlayerInfoData(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    @SerialName("last_play_time") val lastPlayTime: Long = 0,
    @SerialName("last_play_cid") val lastPlayCid: Long = 0,
    val subtitle: SubtitleContainer? = null,
)

@Serializable
data class SubtitleContainer(
    val subtitles: List<SubtitleRawItem> = emptyList(),
)

@Serializable
data class SubtitleRawItem(
    val id: Long = 0,
    @SerialName("id_str") val idStr: String = "",
    val lan: String = "",
    @SerialName("lan_doc") val lanDoc: String = "",
    @SerialName("subtitle_url") val subtitleUrl: String = "",
    @SerialName("subtitle_url_v2") val subtitleUrlV2: String = "",
    @SerialName("ai_status") val aiStatus: Int = 0,
    @SerialName("ai_type") val aiType: Int = 0,
)

data class SubtitleTrack(
    val key: String,
    val lan: String,
    val label: String,
    val url: String,
    val isAi: Boolean,
)

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val content: String,
)

data class PlayerAuxiliaryInfo(
    val lastPlayTimeMs: Long = 0L,
    val lastPlayCid: Long = 0L,
    val subtitles: List<SubtitleTrack> = emptyList(),
)
