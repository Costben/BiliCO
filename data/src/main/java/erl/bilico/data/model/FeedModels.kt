package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecommendResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RecommendData? = null,
)

@Serializable
data class RecommendData(
    val item: List<RecommendItem>? = null,
)

@Serializable
data class RecommendItem(
    val id: Long = 0,
    val bvid: String? = null,
    val cid: Long? = null,
    val goto: String? = null,
    val pic: String? = null,
    val title: String? = null,
    val duration: Int? = null,
    val pubdate: Long? = null,
    val owner: RecommendOwner? = null,
    val stat: RecommendStat? = null,
)

@Serializable
data class RecommendOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class RecommendStat(
    val view: Long = 0,
    val like: Long = 0,
    val danmaku: Long = 0,
)
