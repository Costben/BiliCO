package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// ---------- 接口模型（pgc/view/web/season） ----------

@Serializable
data class PgcSeasonResponse(
    val code: Int = 0,
    val message: String = "",
    val result: PgcSeason? = null,
    val data: PgcSeason? = null,
)

@Serializable
data class PgcSeason(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("media_id") val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val evaluate: String = "",
    val rating: PgcRating? = null,
    val stat: PgcStat? = null,
    val episodes: List<PgcEpisode>? = null,
    val seasons: List<PgcSeasonRef>? = null,
    val areas: List<PgcArea>? = null,
    val styles: List<String>? = null,
    @SerialName("season_title") val seasonTitle: String = "",
    @SerialName("season_type_name") val seasonTypeName: String = "",
    @SerialName("user_status") val userStatus: PgcUserStatus? = null,
    val total: Int = 0,
)

@Serializable
data class PgcUserStatus(
    val follow: Int = 0,
)

@Serializable
data class PgcRating(
    val score: Float = 0f,
    val count: Int = 0,
)

@Serializable
data class PgcStat(
    val views: Long = 0,
    val danmakus: Long = 0,
    val favorites: Long = 0,
    val coins: Long = 0,
    val likes: Long = 0,
    val reply: Long = 0,
)

@Serializable
data class PgcArea(
    val id: Int = 0,
    val name: String = "",
)

@Serializable
data class PgcEpisode(
    val id: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    @SerialName("long_title") val longTitle: String = "",
    val cover: String = "",
    val duration: Long = 0,
    val badge: String = "",
)

@Serializable
data class PgcSeasonRef(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("season_title") val seasonTitle: String = "",
    val title: String = "",
    val cover: String = "",
)

// ---------- 番剧排行（pgc/web/rank/list、pgc/season/rank/web/list） ----------

@Serializable
data class PgcRankResponse(
    val code: Int = 0,
    val message: String = "",
    val result: PgcRankData? = null,
    val data: PgcRankData? = null,
)

@Serializable
data class PgcRankData(
    val list: List<PgcRankItem> = emptyList(),
)

@Serializable
data class PgcRankItem(
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val badge: String = "",
    val rating: String = "",
    @SerialName("new_ep") val newEp: PgcNewEp? = null,
    val stat: PgcRankStat? = null,
)

@Serializable
data class PgcNewEp(
    @SerialName("index_show") val indexShow: String = "",
)

@Serializable
data class PgcRankStat(
    val view: Long = 0,
    val follow: Long = 0,
    val danmaku: Long = 0,
)

// ---------- 追番列表（x/space/bangumi/follow/list） ----------

@Serializable
data class BangumiFollowResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BangumiFollowData? = null,
)

@Serializable
data class BangumiFollowData(
    val total: Int = 0,
    val pn: Int = 1,
    val ps: Int = 30,
    val list: List<BangumiFollowItem> = emptyList(),
)

@Serializable
data class BangumiFollowItem(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("media_id") val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("badge") val badge: String = "",
    @SerialName("new_ep") val newEp: PgcNewEp? = null,
    val progress: String = "",
    @SerialName("season_type_name") val seasonTypeName: String = "",
    @SerialName("total") val total: Int = 0,
)

// ---------- UI 模型 ----------

data class BangumiEpisode(
    val epId: Long,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val cover: String,
    val durationSec: Long,
    val badge: String,
)

data class BangumiSeasonRef(
    val seasonId: Long,
    val title: String,
)

data class BangumiDetail(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val evaluate: String,
    val score: String,
    val rankText: String,
    val viewsText: String,
    val followText: String,
    val seasonTypeName: String,
    val metaText: String,
    val episodes: List<BangumiEpisode>,
    val seasons: List<BangumiSeasonRef>,
    val followed: Boolean = false,
)

/** 番剧排行、追番列表共用的一张卡片。 */
data class BangumiCard(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val subtitle: String,
    val rating: String,
    val badge: String = "",
)

// ---------- 消息（x/msgfeed/*、message.bilibili.com） ----------

@Serializable
data class MessageCursor(
    @Serializable(with = FlexibleLongSerializer::class) val id: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class) val time: Long = 0,
    @SerialName("is_end") val isEnd: Boolean = false,
)

@Serializable
data class MessageUser(
    @Serializable(with = FlexibleLongSerializer::class) val mid: Long = 0,
    val nickname: String = "",
    val avatar: String = "",
)

@Serializable
data class MessageReplyResponse(
    val code: Int = 0,
    val message: String = "",
    val data: MessageReplyData? = null,
)

@Serializable
data class MessageReplyData(
    val cursor: MessageCursor? = null,
    val items: List<MessageReplyItem>? = null,
)

@Serializable
data class MessageReplyItem(
    @Serializable(with = FlexibleLongSerializer::class) val id: Long = 0,
    val user: MessageUser? = null,
    val item: MessageContent? = null,
    val counts: Int = 0,
    @SerialName("reply_time") val replyTime: Long = 0,
)

@Serializable
data class MessageContent(
    val business: String = "",
    val title: String = "",
    @SerialName("source_content") val sourceContent: String = "",
    @SerialName("target_reply_content") val targetReplyContent: String = "",
    @SerialName("root_reply_content") val rootReplyContent: String = "",
)

@Serializable
data class MessageAtResponse(
    val code: Int = 0,
    val message: String = "",
    val data: MessageAtData? = null,
)

@Serializable
data class MessageAtData(
    val cursor: MessageCursor? = null,
    val items: List<MessageAtItem>? = null,
)

@Serializable
data class MessageAtItem(
    @Serializable(with = FlexibleLongSerializer::class) val id: Long = 0,
    val user: MessageUser? = null,
    val item: MessageAtContent? = null,
    @SerialName("at_time") val atTime: Long = 0,
)

@Serializable
data class MessageAtContent(
    val title: String = "",
    @SerialName("source_content") val sourceContent: String = "",
    @SerialName("image") val image: String = "",
)

@Serializable
data class MessageLikeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: MessageLikeData? = null,
)

@Serializable
data class MessageLikeData(
    val latest: MessageLikeBucket? = null,
    val total: MessageLikeBucket? = null,
)

@Serializable
data class MessageLikeBucket(
    val cursor: MessageCursor? = null,
    val items: List<MessageLikeItem>? = null,
)

@Serializable
data class MessageLikeItem(
    @Serializable(with = FlexibleLongSerializer::class) val id: Long = 0,
    val users: List<MessageUser>? = null,
    val item: MessageContent? = null,
    val counts: Int = 0,
    @SerialName("like_time") val likeTime: Long = 0,
)

@Serializable
data class SystemNoticeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<SystemNoticeItem>? = null,
)

@Serializable
data class SystemNoticeItem(
    val id: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class) val cursor: Long = 0,
    val title: String = "",
    @Serializable(with = SystemNoticeContentSerializer::class) val content: String = "",
    @SerialName("time_at") val timeAt: String = "",
)

/** 系统通知的 content 是一层 JSON 字符串，取 web 字段的正文。 */
object SystemNoticeContentSerializer :
    kotlinx.serialization.KSerializer<String> {

    override val descriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "SystemNoticeContent",
            kotlinx.serialization.descriptors.PrimitiveKind.STRING,
        )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): String {
        val raw = decoder.decodeString()
        return runCatching {
            val element = Json.parseToJsonElement(raw)
            if (element is JsonObject) {
                element["web"]?.jsonPrimitive?.contentOrNull ?: raw
            } else {
                raw
            }
        }.getOrDefault(raw)
    }
}

enum class MessageKind {
    REPLY,
    AT,
    LIKE,
    SYSTEM,
}

data class MessageEntry(
    val key: String,
    val avatar: String,
    val name: String,
    val title: String,
    val content: String,
    val extra: String,
    val timeText: String,
)

data class MessagePage(
    val items: List<MessageEntry>,
    val cursor: MessageCursor?,
    val isEnd: Boolean,
)
