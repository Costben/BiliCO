package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ImSessionListResponse(
    val code: Int = 0,
    val msg: String = "",
    val message: String = "",
    val data: ImSessionListData? = null,
)

@Serializable
data class ImSessionListData(
    @SerialName("session_list")
    val sessionList: List<ImSessionItem> = emptyList(),
    @SerialName("has_more")
    val hasMore: Int = 0,
)

@Serializable
data class ImSessionItem(
    @SerialName("talker_id")
    val talkerId: Long = 0,
    @SerialName("session_type")
    val sessionType: Int = 1,
    @SerialName("unread_count")
    val unreadCount: Int = 0,
    @SerialName("max_seqno")
    val maxSeqno: Long = 0,
    @SerialName("last_msg")
    val lastMsg: ImLastMsg? = null,
)

@Serializable
data class ImLastMsg(
    @SerialName("sender_uid")
    val senderUid: Long = 0,
    @SerialName("receiver_id")
    val receiverId: Long = 0,
    @SerialName("msg_type")
    val msgType: Int = 1,
    val content: String = "",
    val timestamp: Long = 0,
    @SerialName("msg_seqno")
    val msgSeqno: Long = 0,
)

@Serializable
data class ImFetchMsgsResponse(
    val code: Int = 0,
    val msg: String = "",
    val message: String = "",
    val data: ImFetchMsgsData? = null,
)

@Serializable
data class ImFetchMsgsData(
    val messages: List<ImMessageItem> = emptyList(),
    @SerialName("has_more")
    val hasMore: Int = 0,
    @SerialName("min_seqno")
    val minSeqno: Long = 0,
    @SerialName("max_seqno")
    val maxSeqno: Long = 0,
)

@Serializable
data class ImMessageItem(
    @SerialName("sender_uid")
    val senderUid: Long = 0,
    @SerialName("receiver_id")
    val receiverId: Long = 0,
    @SerialName("msg_type")
    val msgType: Int = 1,
    val content: String = "",
    val timestamp: Long = 0,
    @SerialName("msg_seqno")
    val msgSeqno: Long = 0,
    @SerialName("msg_key")
    val msgKey: Long = 0,
)

@Serializable
data class ImSendMsgResponse(
    val code: Int = 0,
    val msg: String = "",
    val message: String = "",
    val data: ImSendMsgData? = null,
)

@Serializable
data class ImSendMsgData(
    @SerialName("msg_key")
    val msgKey: Long = 0,
)

/** UI 展示层会话项 */
data class ImSessionUi(
    val talkerId: Long,
    val name: String,
    val face: String,
    val lastMessage: String,
    val timeText: String,
    val unreadCount: Int,
    val timestamp: Long,
)

/** UI 展示层单条私信消息 */
data class ImMessageUi(
    val msgKey: Long,
    val senderUid: Long,
    val isMe: Boolean,
    val content: String,
    val timeText: String,
    val timestamp: Long,
)

object ImContentParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(content: String, msgType: Int): String {
        if (content.isBlank()) return ""
        return runCatching {
            when (msgType) {
                1 -> {
                    val element = json.parseToJsonElement(content)
                    element.jsonObject["content"]?.jsonPrimitive?.content ?: content
                }
                2 -> "[图片]"
                5 -> "[撤回了一条消息]"
                7 -> "[分享了内容]"
                10 -> "[系统通知]"
                else -> {
                    val element = json.parseToJsonElement(content)
                    element.jsonObject["content"]?.jsonPrimitive?.content ?: content
                }
            }
        }.getOrDefault(content)
    }
}
