package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RankingResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RankingData? = null,
)

@Serializable
data class RankingData(
    val note: String = "",
    val list: List<RankingItem>? = null,
)

@Serializable
data class RankingItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: RankingOwner? = null,
    val stat: RankingStat? = null,
)

@Serializable
data class RankingOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class RankingStat(
    val view: Long = 0,
    val like: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val favorite: Long = 0,
    val coin: Long = 0,
    val share: Long = 0,
)

/** 视频页互动所需的接口模型。 */
@Serializable
data class VideoRelationResponse(
    val code: Int = 0,
    val message: String = "",
    val data: VideoRelationData? = null,
)

@Serializable
data class VideoRelationData(
    val attention: Boolean = false,
    val favorite: Boolean = false,
    val like: Boolean = false,
    val coin: Int = 0,
)

@Serializable
data class HasLikedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Int = 0,
)

@Serializable
data class CoinedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: CoinedData? = null,
)

@Serializable
data class CoinedData(
    val multiply: Int = 0,
)

@Serializable
data class UserRelationResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UserRelationData? = null,
)

@Serializable
data class UserRelationData(
    @SerialName("attribute") val attribute: Int = 0,
)
