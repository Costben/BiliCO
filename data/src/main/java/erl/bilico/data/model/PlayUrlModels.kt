@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class PlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayUrlData? = null,
)

@Serializable
data class PlayUrlData(
    val quality: Int = 0,
    val timelength: Long = 0,
    @SerialName("accept_quality") val acceptQuality: List<Int> = emptyList(),
    @SerialName("accept_description") val acceptDescription: List<String> = emptyList(),
    @SerialName("support_formats") val supportFormats: List<FormatItem>? = null,
    @SerialName("last_play_time") val lastPlayTime: Long = 0,
    @SerialName("last_play_cid") val lastPlayCid: Long = 0,
    val dash: Dash? = null,
    val durl: List<Durl>? = null,
)

@Serializable
data class Durl(
    val order: Int = 0,
    val length: Long = 0,
    val size: Long = 0,
    val url: String = "",
    @JsonNames("backupUrl", "backup_url") val backupUrl: List<String>? = null,
)

@Serializable
data class Dash(
    val duration: Int = 0,
    val video: List<DashTrack> = emptyList(),
    val audio: List<DashTrack>? = null,
)

@Serializable
data class DashTrack(
    val id: Int = 0,
    @JsonNames("baseUrl", "base_url") val baseUrl: String = "",
    @JsonNames("backupUrl", "backup_url") val backupUrl: List<String>? = null,
    val bandwidth: Long = 0,
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("mime_type") val mimeType: String = "",
)

@Serializable
data class FormatItem(
    val quality: Int = 0,
    @SerialName("new_description") val newDescription: String = "",
    val description: String = "",
    @SerialName("need_login") val needLogin: Boolean = false,
    @SerialName("need_vip") val needVip: Boolean = false,
)
