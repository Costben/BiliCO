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
    @SerialName("is_followed") val isFollowed: Int = 0,
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

// ---------- App 端首页推荐（支持 three_point_v2「我不想看」理由下发） ----------

@Serializable
data class AppFeedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: AppFeedData? = null,
)

@Serializable
data class AppFeedData(
    val items: List<AppFeedItem> = emptyList(),
)

@Serializable
data class AppFeedItem(
    @SerialName("card_type") val cardType: String = "",
    @SerialName("card_goto") val cardGoto: String = "",
    val goto: String = "",
    val param: String = "",
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val uri: String = "",
    @SerialName("rcmd_reason") val rcmdReason: String = "",
    @SerialName("cover_left_text_1") val coverLeftText1: String = "",
    @SerialName("cover_left_text_2") val coverLeftText2: String = "",
    @SerialName("cover_right_text") val coverRightText: String = "",
    val args: AppFeedArgs? = null,
    @SerialName("player_args") val playerArgs: AppFeedPlayerArgs? = null,
    @SerialName("desc_button") val descButton: AppFeedDescButton? = null,
    @SerialName("three_point") val threePoint: AppFeedThreePoint? = null,
    @SerialName("three_point_v2") val threePointV2: List<AppFeedThreePointV2> = emptyList(),
    @SerialName("ad_info") val adInfo: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class AppFeedArgs(
    @Serializable(with = FlexibleLongSerializer::class) val aid: Long = 0,
    @SerialName("up_id") @Serializable(with = FlexibleLongSerializer::class) val upId: Long = 0,
    @SerialName("up_name") val upName: String = "",
)

@Serializable
data class AppFeedPlayerArgs(
    @Serializable(with = FlexibleLongSerializer::class) val aid: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class) val cid: Long = 0,
    @Serializable(with = FlexibleIntSerializer::class) val duration: Int = 0,
)

@Serializable
data class AppFeedDescButton(
    val text: String = "",
)

@Serializable
data class AppFeedThreePoint(
    @SerialName("dislike_reasons") val dislikeReasons: List<AppFeedReason> = emptyList(),
    val feedbacks: List<AppFeedReason> = emptyList(),
)

@Serializable
data class AppFeedThreePointV2(
    val type: String = "",
    val title: String = "",
    val reasons: List<AppFeedReason> = emptyList(),
)

@Serializable
data class AppFeedReason(
    @Serializable(with = FlexibleIntSerializer::class) val id: Int = 0,
    val name: String = "",
    val toast: String = "",
)

@Serializable
data class LiveMultipleUsersResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Map<String, LiveMultipleUserEntry>? = null,
)

@Serializable
data class LiveMultipleUserEntry(
    val info: LiveMultipleUserInfo? = null,
)

@Serializable
data class LiveMultipleUserInfo(
    val uid: Long = 0,
    val uname: String = "",
    val face: String = "",
    @SerialName("official_verify") val officialVerify: OfficialVerifyData? = null,
)

