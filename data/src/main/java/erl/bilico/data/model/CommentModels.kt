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
    val mid: Long = 0,
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
    val parent: Long = 0,
    val oid: Long = 0,
    val mid: Long = 0,
    val count: Int = 0,
    val rcount: Int = 0,
    val like: Int = 0,
    val action: Int = 0,
    val ctime: Long = 0,
    val member: ReplyMember = ReplyMember(),
    val content: ReplyContent = ReplyContent(),
    val pictures: List<ReplyPicture>? = null,
    val replies: List<ReplyItem>? = null,
    @SerialName("up_action") val upAction: ReplyUpAction? = null,
    @SerialName("reply_control") val replyControl: ReplyControl? = null,
)

@Serializable
data class ReplyMember(
    @Serializable(with = FlexibleStringSerializer::class) val mid: String = "0",
    val uname: String = "未知用户",
    val avatar: String = "",
    @SerialName("level_info") val levelInfo: ReplyLevel = ReplyLevel(),
    val vip: ReplyVip? = null,
    @SerialName("user_sailing") val userSailing: ReplyUserSailing? = null,
)

@Serializable
data class ReplyVip(
    val vipStatus: Int = 0,
    @SerialName("nickname_color") val nicknameColor: String = "",
)

@Serializable
data class ReplyUserSailing(
    val cardbg: ReplySailingCard? = null,
    @SerialName("cardbg_with_focus") val cardbgWithFocus: ReplySailingCard? = null,
)

@Serializable
data class ReplySailingCard(
    val id: Long = 0,
    val name: String = "",
    val image: String = "",
    val fan: ReplySailingFan? = null,
)

@Serializable
data class ReplySailingFan(
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("is_fan") val isFan: Int = 0,
    val number: Long = 0,
    val color: String = "",
    val name: String = "",
    @SerialName("num_desc") val numDesc: String = "",
    @SerialName("num_prefix") val numPrefix: String = "NO.",
)

@Serializable
data class ReplyControl(
    val location: String = "",
    @SerialName("time_desc") val timeDesc: String = "",
    @SerialName("sub_reply_entry_text") val subReplyEntryText: String = "",
    @SerialName("up_reply") val upReply: Boolean = false,
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
    val pictures: List<ReplyPicture>? = null,
)

@Serializable
data class ReplyPicture(
    @SerialName("img_src") val imgSrc: String = "",
    val src: String = "",
    val url: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("img_width") val imgWidth: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("img_height") val imgHeight: Int = 0,
) {
    val displayUrl: String get() = imgSrc.ifBlank { src }.ifBlank { url }
}

@Serializable
data class ReplyEmote(
    val url: String = "",
    val meta: ReplyEmoteMeta? = null,
)

@Serializable
data class ReplyEmoteMeta(
    val size: Int = 1,
)

@Serializable
data class ReplyUpAction(
    val like: Boolean = false,
    val reply: Boolean = false,
)

@Serializable
data class AddReplyResponse(
    val code: Int = 0,
    val message: String = "",
    val data: AddReplyData? = null,
)

@Serializable
data class AddReplyData(
    val rpid: Long = 0,
    val root: Long = 0,
    val parent: Long = 0,
    val reply: ReplyItem? = null,
)
