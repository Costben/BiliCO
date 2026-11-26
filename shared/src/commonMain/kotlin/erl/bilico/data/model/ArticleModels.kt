package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
data class ArticleViewResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ArticleViewData? = null,
)

/**
 * `x/article/view`。正文有三种载体：`opus.content.paragraphs`（结构化，最优）、
 * `ops`（Quill 风格 JSON）、`content`（HTML 或 JSON 字符串，兜底），逐个尝试取最完整的。
 */
@Serializable
data class ArticleViewData(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val title: String = "",
    val summary: String = "",
    @SerialName("banner_url") val bannerUrl: String = "",
    val author: ArticleAuthor? = null,
    @SerialName("publish_time")
    @Serializable(with = FlexibleLongSerializer::class)
    val publishTime: Long = 0,
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    @SerialName("origin_image_urls") val originImageUrls: List<String> = emptyList(),
    @SerialName("dyn_id_str") val dynamicId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0,
    val content: String = "",
    val opus: ArticleOpus? = null,
    val ops: List<JsonObject> = emptyList(),
    val stats: ArticleStats? = null,
)

@Serializable
data class ArticleAuthor(
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class ArticleOpus(
    val title: String = "",
    val content: JsonObject? = null,
) {
    fun paragraphs(): List<JsonObject> {
        val paragraphs: JsonArray? = runCatching {
            content?.get("paragraphs")?.jsonArray
        }.getOrNull()
        return paragraphs.orEmpty().mapNotNull { runCatching { it.jsonObject }.getOrNull() }
    }
}

@Serializable
data class ArticleStats(
    val view: Long? = null,
    val like: Long? = null,
    val favorite: Long? = null,
    val reply: Long? = null,
    val share: Long? = null,
)

/** `x/polymer/web-dynamic/v1/detail` 与 `opus/detail` 共用的外壳。 */
@Serializable
data class DynamicDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicDetailData? = null,
)

@Serializable
data class DynamicDetailData(
    val item: DynamicItem? = null,
    val fallback: DynamicOpusFallback? = null,
)

@Serializable
data class DynamicOpusFallback(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val type: Int = 0,
)

/** 正文里的一小段文字，可以单独带字号/颜色/加粗/斜体/删除线/链接/表情。 */
data class ArticleSpan(
    val text: String,
    val fontSize: Int? = null,
    val colorArgb: Long? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val link: String? = null,
    val emote: EmoteImage? = null,
)

data class ArticleLinkCard(
    val title: String,
    val description: String = "",
    val cover: String = "",
    val url: String = "",
)

/** 阅读页的一段正文。图片块和卡片块不再参与行内样式。 */
sealed interface ArticleBlock {
    data class Heading(val spans: List<ArticleSpan>) : ArticleBlock
    data class Paragraph(val spans: List<ArticleSpan>) : ArticleBlock
    data class Quote(val spans: List<ArticleSpan>) : ArticleBlock
    data class ListBlock(
        val ordered: Boolean,
        val items: List<List<ArticleSpan>>,
    ) : ArticleBlock

    data class Code(val language: String, val content: String) : ArticleBlock
    data class Image(val url: String, val width: Int = 0, val height: Int = 0) : ArticleBlock
    data class LinkCard(val card: ArticleLinkCard) : ArticleBlock
    data object Divider : ArticleBlock
}

/** 阅读页要展示的全部内容，UI 不再直接消费接口模型。 */
data class ArticleDetail(
    val articleId: Long = 0,
    val dynamicId: String = "",
    val title: String,
    val summary: String = "",
    val banner: String = "",
    val authorMid: Long = 0,
    val authorName: String = "",
    val authorFace: String = "",
    val publishTimeSec: Long = 0,
    val blocks: List<ArticleBlock> = emptyList(),
    val stats: ArticleStats? = null,
    /** 评论区目标：专栏是 type=12，图文动态是 type=11 / 17。 */
    val commentOid: Long = 0,
    val commentType: Int = 0,
) {
    /** 有正文可看时才值得进阅读页；只有标题的卡片直接给个空态。 */
    val hasBody: Boolean get() = blocks.isNotEmpty()
}

/**
 * 把 opus/detail 的 `modules` 数组摊平成同一个对象，只取需要的三块。
 *
 * 每个 module 都带着全套 key、不适用的填 null，直接相加会让后面的 null
 * 覆盖掉前面真正的 `module_content`，所以只合并非 null 的字段。
 */
internal fun JsonElement?.flattenDynamicModules(): JsonObject? {
    val element = this ?: return null
    return when (element) {
        is JsonObject -> element
        is JsonArray -> element
            .mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .fold(JsonObject(emptyMap())) { acc, next ->
                JsonObject(acc + next.filterValues { it !is JsonNull })
            }

        else -> null
    }
}
