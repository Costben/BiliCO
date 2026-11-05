package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HistoryResponse(
    val code: Int = 0,
    val message: String = "",
    val data: HistoryPageData? = null,
)

@Serializable
data class HistoryPageData(
    val list: List<HistoryData> = emptyList(),
    val cursor: HistoryCursor? = null,
)

@Serializable
data class HistoryCursor(
    val max: Long = 0,
    @SerialName("view_at") val viewAt: Long = 0,
    val business: String = "",
    val ps: Int = 30,
)

@Serializable
data class HistoryData(
    val title: String = "",
    val pic: String = "",
    val cover: String = "",
    val covers: List<String> = emptyList(),
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_face") val authorFace: String = "",
    @SerialName("author_mid") val authorMid: Long = 0,
    val duration: Int = 0,
    val history: HistoryEntry? = null,
    val progress: Int = -1,
    @SerialName("view_at") val viewAt: Long = 0,
)

@Serializable
data class HistoryEntry(
    val bvid: String = "",
    val cid: Long = 0,
    val epid: Long = 0,
    val oid: Long = 0,
    val business: String = "",
    val page: Int = 1,
)
