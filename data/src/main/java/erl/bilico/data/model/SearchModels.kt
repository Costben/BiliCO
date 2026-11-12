package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SearchTypeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchTypeData? = null,
)

/**
 * 视频与用户两种搜索共用一个模型：`search_type` 决定哪组字段有值。
 * 标题里的 `<em class="keyword">` 由仓库层清掉。
 */
@Serializable
data class SearchTypeData(
    val page: Int = 1,
    val pagesize: Int = 20,
    @SerialName("numResults") val numResults: Int = 0,
    @SerialName("numPages") val numPages: Int = 0,
    val result: List<SearchItem>? = null,
)

@Serializable
data class SearchItem(
    val type: String = "",
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val author: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val play: Int = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    val duration: String = "",
    val pubdate: Long = 0,
    // 用户结果的字段
    val uname: String = "",
    val usign: String = "",
    val upic: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val fans: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val videos: Int = 0,
    // 专栏结果的字段
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
    val desc: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val view: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val like: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val reply: Int = 0,
)

@Serializable
data class SearchSquareResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchSquareData? = null,
)

@Serializable
data class SearchSquareData(
    val trending: SearchTrending? = null,
)

@Serializable
data class SearchTrending(
    val title: String = "",
    val list: List<SearchTrendingItem> = emptyList(),
)

@Serializable
data class SearchTrendingItem(
    val keyword: String = "",
    @SerialName("show_name") val showName: String = "",
)

@Serializable
data class SearchSuggestResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchSuggestData? = null,
)

@Serializable
data class SearchSuggestData(
    val code: Int = 0,
    val result: JsonElement? = null,
)

@Serializable
data class SearchSuggestResult(
    val tag: List<SearchSuggestTag> = emptyList(),
)

@Serializable
data class SearchSuggestTag(
    val value: String = "",
    val term: String = "",
    val name: String = "",
)

data class SearchSuggestion(
    val text: String,
    val rawHtml: String = "",
)

