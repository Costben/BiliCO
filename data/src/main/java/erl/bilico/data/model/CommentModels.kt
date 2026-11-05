package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReplyResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ReplyData? = null,
)

@Serializable
data class ReplyData(
    val cursor: ReplyCursor = ReplyCursor(),
    val page: ReplyPage = ReplyPage(),
    val replies: List<ReplyItem>? = null,
    val hots: List<ReplyItem>? = null,
    @SerialName("top_replies") val topReplies: List<ReplyItem>? = null,
    val top: ReplyTop? = null,
    val upper: ReplyUpper? = null,
)

@Serializable
data class ReplyPage(
    val num: Int = 0,
    val size: Int = 0,
    val count: Int = 0,
    val acount: Int = 0,
)

@Serializable
data class ReplyTop(
    val upper: ReplyItem? = null,
    val admin: ReplyItem? = null,
)

@Serializable
data class ReplyUpper(
    val top: ReplyItem? = null,
)

@Serializable
data class ReplyCursor(
    @SerialName("all_count") val allCount: Int = 0,
    @SerialName("is_end") val isEnd: Boolean = false,
    @SerialName("pagination_reply") val paginationReply: ReplyPagination? = null,
)

@Serializable
data class ReplyPagination(
    @SerialName("next_offset") val nextOffset: String = "",
)

@Serializable
data class ReplyItem(
    val rpid: Long = 0,
    val root: Long = 0,
    val oid: Long = 0,
    val mid: Long = 0,
    val count: Int = 0,
    val rcount: Int = 0,
    val like: Int = 0,
    val ctime: Long = 0,
    val member: ReplyMember = ReplyMember(),
    val content: ReplyContent = ReplyContent(),
    val replies: List<ReplyItem>? = null,
    @SerialName("up_action") val upAction: ReplyUpAction? = null,
)

@Serializable
data class ReplyMember(
    @Serializable(with = FlexibleStringSerializer::class) val mid: String = "0",
    val uname: String = "未知用户",
    val avatar: String = "",
    @SerialName("level_info") val levelInfo: ReplyLevel = ReplyLevel(),
)

@Serializable
data class ReplyLevel(
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("current_level") val currentLevel: Int = 0,
)

@Serializable
data class ReplyContent(
    @Serializable(with = UnescapedStringSerializer::class) val message: String = "",
    val emote: Map<String, ReplyEmote>? = null,
)

@Serializable
data class ReplyEmote(
    val url: String = "",
)

@Serializable
data class ReplyUpAction(
    val like: Boolean = false,
    val reply: Boolean = false,
)
