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
)

@Serializable
data class DynamicModules(
    @SerialName("module_author") val author: DynamicAuthor? = null,
    @SerialName("module_dynamic") val content: DynamicContent? = null,
    @SerialName("module_stat") val stat: DynamicStatModule? = null,
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
)

@Serializable
data class DynamicContent(
    val desc: DynamicDesc? = null,
    val major: DynamicMajor? = null,
)

@Serializable
data class DynamicDesc(
    val text: String = "",
)

@Serializable
data class DynamicMajor(
    val type: String = "",
    val archive: ArchiveMajor? = null,
    val article: ArticleMajor? = null,
    val draw: DrawMajor? = null,
    val opus: OpusMajor? = null,
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
