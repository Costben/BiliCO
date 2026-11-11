package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DynamicFeedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicFeedData? = null,
)

@Serializable
data class DynamicFeedData(
    val items: List<DynamicItem> = emptyList(),
    val offset: String = "",
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("update_baseline") val updateBaseline: String = "",
)

/**
 * 动态卡片。`modules` 在 feed 接口里是对象，opus 详情里可能给数组，
 * 这里保留原始 JSON，由仓库逐条宽松解析，单条异常不会拖垮整页。
 */
@Serializable
data class DynamicItem(
    @SerialName("id_str") val idStr: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val type: String = "",
    val modules: JsonElement? = null,
    val orig: DynamicItem? = null,
    val basic: DynamicBasic? = null,
)

/** 评论与资源 ID：动态的评论区 oid 和专栏 cv 号都在这里。 */
@Serializable
data class DynamicBasic(
    @SerialName("comment_id_str") val commentIdStr: String = "",
    @SerialName("comment_type")
    @Serializable(with = FlexibleIntSerializer::class)
    val commentType: Int = 0,
    @SerialName("rid_str") val ridStr: String = "",
    @SerialName("jump_url") val jumpUrl: String = "",
    /** 充电专属：只有给 UP 主充电过才看得到正文。 */
    @SerialName("is_only_fans") val isOnlyFans: Boolean = false,
)

@Serializable
data class DynamicModules(
    @SerialName("module_author") val author: DynamicAuthor? = null,
    @SerialName("module_dynamic") val content: DynamicContent? = null,
    @SerialName("module_stat") val stat: DynamicStatModule? = null,
    /** 热度评论：动态卡片下面外露的那条「精选评论」。 */
    @SerialName("module_interaction") val interaction: DynamicInteraction? = null,
    /** 充电专属之类被拦下的正文：只在 opus/detail 的数组形式里出现。 */
    @SerialName("module_blocked") val blocked: DynamicBlocked? = null,
)

/** `module_author.icon_badge`：作者名旁边的灰底小角标。 */
@Serializable
data class DynamicIconBadge(
    val text: String = "",
    val icon: String = "",
    @SerialName("render_img") val renderImg: String = "",
)

/**
 * 充电专属动态的锁定卡片。`hint_message` 用换行分成两段：
 * 第一行是标题（如「剥壳员专属动态」），第二行才是说明，`title` 字段通常是空的。
 */
@Serializable
data class DynamicBlocked(
    @SerialName("blocked_type")
    @Serializable(with = FlexibleIntSerializer::class)
    val blockedType: Int = 0,
    val icon: DynamicBlockedImage? = null,
    @SerialName("bg_img") val bgImg: DynamicBlockedImage? = null,
    val title: String = "",
    @SerialName("hint_message") val hintMessage: String = "",
    val button: DynamicBlockedButton? = null,
)

@Serializable
data class DynamicBlockedImage(
    @SerialName("img_day") val imgDay: String = "",
    @SerialName("img_dark") val imgDark: String = "",
) {
    val url: String get() = imgDay.ifBlank { imgDark }
}

@Serializable
data class DynamicBlockedButton(
    val icon: String = "",
    val text: String = "",
    @SerialName("jump_url") val jumpUrl: String = "",
    @SerialName("handle_type")
    @Serializable(with = FlexibleIntSerializer::class)
    val handleType: Int = 0,
)

/** `module_interaction` 里混着点赞和评论，`type == 1` 才是评论。 */
@Serializable
data class DynamicInteraction(
    val items: List<DynamicInteractionItem> = emptyList(),
)

@Serializable
data class DynamicInteractionItem(
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0,
    val desc: DynamicDesc? = null,
)

@Serializable
data class DynamicAuthor(
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    @SerialName("pub_ts")
    @Serializable(with = FlexibleLongSerializer::class)
    val pubTs: Long = 0,
    @SerialName("pub_time") val pubTime: String = "",
    @SerialName("pub_action") val pubAction: String = "",
    /** 头像框。 */
    val pendant: DynamicPendant? = null,
    val vip: DynamicVip? = null,
    /** 装扮卡片：右侧的名牌图与粉丝编号。 */
    val decorate: DynamicDecorate? = null,
    /** opus 详情里装扮字段换了个名字，两种都收。 */
    @SerialName("decoration_card") val decorationCard: DynamicDecorate? = null,
    /** 动态名片的灰色小标签，充电专属之类的角标就在这里。 */
    @SerialName("icon_badge") val iconBadge: DynamicIconBadge? = null,
) {
    val decorateCard: DynamicDecorate? get() = decorate ?: decorationCard
}

@Serializable
data class DynamicPendant(
    val image: String = "",
)

@Serializable
data class DynamicVip(
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val status: Int = 0,
    @SerialName("nickname_color") val nicknameColor: String = "",
)

@Serializable
data class DynamicDecorate(
    @SerialName("card_url") val cardUrl: String = "",
    val fan: DynamicFan? = null,
)

@Serializable
data class DynamicFan(
    val color: String = "",
    @SerialName("num_str") val numStr: String = "",
    @SerialName("num_desc") val numDesc: String = "",
    @SerialName("num_prefix") val numPrefix: String = "",
) {
    val numberText: String get() = numStr.ifBlank { numDesc }
}

@Serializable
data class DynamicContent(
    val desc: DynamicDesc? = null,
    val major: DynamicMajor? = null,
)

@Serializable
data class DynamicDesc(
    val text: String = "",
    @SerialName("rich_text_nodes") val richTextNodes: List<DynamicRichTextNode> = emptyList(),
)

@Serializable
data class DynamicRichTextNode(
    val type: String = "",
    val text: String = "",
    @SerialName("orig_text") val origText: String = "",
    val emoji: DynamicEmoji? = null,
    val rich: DynamicRichTextNode? = null,
)

@Serializable
data class DynamicEmoji(
    @SerialName("icon_url") val iconUrl: String = "",
    val url: String = "",
    @SerialName("webp_url") val webpUrl: String = "",
    @SerialName("gif_url") val gifUrl: String = "",
    @SerialName("static_url") val staticUrl: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val size: Int = 1,
    val text: String = "",
) {
    val displayUrl: String
        get() = iconUrl.ifBlank { url }.ifBlank { webpUrl }.ifBlank { gifUrl }.ifBlank { staticUrl }
}

@Serializable
data class DynamicMajor(
    val type: String = "",
    val archive: ArchiveMajor? = null,
    val article: ArticleMajor? = null,
    val draw: DrawMajor? = null,
    val opus: OpusMajor? = null,
    val live: LiveMajor? = null,
    /** 开播动态的直播间信息塞在 content 里，是一段 JSON 文本。 */
    @SerialName("live_rcmd") val liveRcmd: LiveRcmdMajor? = null,
)

/** 开播动态：`live_rcmd.content` 解出来就是这一层。 */
@Serializable
data class LiveRcmdMajor(
    val content: String = "",
)

@Serializable
data class LiveRcmdContent(
    @SerialName("live_play_info") val playInfo: LiveMajor? = null,
)

/** 直播间信息，`major.live` 与 `live_rcmd.content.live_play_info` 是同一套字段。 */
@Serializable
data class LiveMajor(
    @SerialName("room_id")
    @Serializable(with = FlexibleLongSerializer::class)
    val roomId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("area_name") val areaName: String = "",
    @SerialName("parent_area_name") val parentAreaName: String = "",
    @SerialName("live_status")
    @Serializable(with = FlexibleIntSerializer::class)
    val liveStatus: Int = 0,
    @SerialName("watched_show") val watchedShow: LiveWatchedShow? = null,
)

@Serializable
data class LiveWatchedShow(
    @Serializable(with = FlexibleLongSerializer::class)
    val num: Long = 0,
    @SerialName("text_small") val textSmall: String = "",
    @SerialName("text_large") val textLarge: String = "",
)

/** 专栏卡片。`id` 就是 cv 号，正文走 `x/article/view`。 */
@Serializable
data class ArticleMajor(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val title: String = "",
    val desc: String = "",
    val covers: List<String> = emptyList(),
    @SerialName("jump_url") val jumpUrl: String = "",
    val label: String = "",
)

@Serializable
data class ArchiveMajor(
    @Serializable(with = FlexibleLongSerializer::class)
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    @SerialName("duration_text") val durationText: String = "",
)

@Serializable
data class DrawMajor(
    val items: List<DynamicImageData> = emptyList(),
)

/**
 * 图文动态。正文在 `summary.text`（最多 500 字），图片列表的字段名是 `url`，
 * 不是 `draw.items` 的 `src`，取图时两处都要看。
 */
@Serializable
data class OpusMajor(
    val pics: List<DynamicImageData> = emptyList(),
    val summary: OpusSummary? = null,
    val title: String? = null,
    @SerialName("jump_url") val jumpUrl: String = "",
)

@Serializable
data class OpusSummary(
    val text: String = "",
    @SerialName("rich_text_nodes") val richTextNodes: List<DynamicRichTextNode> = emptyList(),
)

@Serializable
data class DynamicImageData(
    // 图文用 url，旧版图片动态用 src，两边都收着。
    val src: String = "",
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
) {
    val displayUrl: String get() = src.ifBlank { url }
}

@Serializable
data class DynamicStatModule(
    val comment: DynamicStatItem = DynamicStatItem(),
    val forward: DynamicStatItem = DynamicStatItem(),
    val like: DynamicStatItem = DynamicStatItem(),
)

@Serializable
data class DynamicStatItem(
    @Serializable(with = FlexibleIntSerializer::class)
    val count: Int = 0,
)
