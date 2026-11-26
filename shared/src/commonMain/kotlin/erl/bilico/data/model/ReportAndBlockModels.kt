package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** UP 主 / 用户官方认证信息（兼容 `official` 与 `official_verify` 两种字段格式）。 */
@Serializable
data class OfficialVerifyData(
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = -1,
    @Serializable(with = FlexibleIntSerializer::class)
    val role: Int = 0,
    val title: String = "",
    val desc: String = "",
) {
    /** 0 = 个人认证（橙黄闪电），1 = 机构认证（蓝闪电），-1 = 未认证。 */
    fun resolvedType(): Int = when {
        type == 0 || type == 1 -> type
        role == 1 || role == 2 || role == 7 -> 0
        role in 3..6 -> 1
        else -> -1
    }

    fun resolvedDesc(): String = desc.ifBlank { title }
}

/** 用户名片接口 `x/web-interface/card` 返回结构。 */
@Serializable
data class MemberCardResponse(
    val code: Int = 0,
    val message: String = "",
    val data: MemberCardData? = null,
)

@Serializable
data class MemberCardData(
    val card: MemberCardInfo? = null,
    val following: Boolean = false,
    @Serializable(with = FlexibleLongSerializer::class)
    val follower: Long = 0,
)

@Serializable
data class MemberCardInfo(
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val fans: Long = 0,
    @SerialName("Official") val official: OfficialVerifyData? = null,
    @SerialName("official_verify") val officialVerify: OfficialVerifyData? = null,
) {
    fun resolvedOfficial(): OfficialVerifyData? =
        official?.takeIf { it.resolvedType() >= 0 }
            ?: officialVerify?.takeIf { it.resolvedType() >= 0 }
}

/** 服务端黑名单 `x/relation/blacks`。 */
@Serializable
data class BlackListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BlackListData? = null,
)

@Serializable
data class BlackListData(
    val list: List<BlackListUser> = emptyList(),
    @Serializable(with = FlexibleIntSerializer::class)
    val total: Int = 0,
)

@Serializable
data class BlackListUser(
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = "",
    @SerialName("official_verify") val officialVerify: OfficialVerifyData? = null,
)

/** 视频举报标签 `x/web-interface/archive/appeal/tags`。 */
@Serializable
data class AppealTagsResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<AppealTag> = emptyList(),
)

@Serializable
data class AppealTag(
    @Serializable(with = FlexibleIntSerializer::class)
    val tid: Int = 0,
    val name: String = "",
    val controls: List<AppealTagControl> = emptyList(),
)

@Serializable
data class AppealTagControl(
    val name: String = "",
    val placeholder: String = "",
)

/** UP 主举报 `space.bilibili.com/ajax/report/add` 返回结构（可能返回 status 或 code）。 */
@Serializable
data class MemberReportResponse(
    val code: Int = 0,
    val status: Boolean? = null,
    val message: String = "",
    val data: JsonElement? = null,
)

/** UP 主卡片摘要（视频页关注区等场景使用）。 */
data class UpCardSummary(
    val followers: Long = 0L,
    val officialType: Int = -1,
    val officialDesc: String = "",
)

/** 本地屏蔽的视频条目。 */
@Serializable
data class BlockedVideo(
    val bvid: String,
    val title: String = "",
    val upName: String = "",
    val upMid: Long = 0L,
    val addedAtMs: Long = 0L,
)

/** 本地屏蔽或服务端拉黑的用户条目。 */
@Serializable
data class BlockedUser(
    val mid: Long,
    val name: String = "",
    val face: String = "",
    val addedAtMs: Long = 0L,
)

/** 屏蔽与推荐过滤持久化配置（参考 PiliPlus RecommendFilter + 全局屏蔽）。 */
@Serializable
data class BlockConfig(
    val blockedVideos: List<BlockedVideo> = emptyList(),
    val blockedUsers: List<BlockedUser> = emptyList(),
    val videoKeywords: List<String> = emptyList(),
    val upKeywords: List<String> = emptyList(),
    val commentKeywords: List<String> = emptyList(),
    val dynamicKeywords: List<String> = emptyList(),
    val danmakuKeywords: List<String> = emptyList(),
    val minDurationSec: Int = 0,
    val minPlayCount: Int = 0,
    val minLikeRatioPercent: Int = 0,
    val exemptFollowed: Boolean = true,
    val applyToRelatedVideos: Boolean = true,
)
