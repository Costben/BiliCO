package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class SearchAllResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchAllData? = null,
)

@Serializable
data class SearchAllData(
    val page: Int = 1,
    val pagesize: Int = 20,
    @SerialName("numResults") val numResults: Int = 0,
    @SerialName("numPages") val numPages: Int = 0,
    @SerialName("top_tlist") val topTlist: Map<String, Int?> = emptyMap(),
    @SerialName("v_voucher") val vVoucher: String? = null,
    val result: List<SearchAllSection>? = null,
)

@Serializable
data class SearchAllSection(
    @SerialName("result_type") val resultType: String = "",
    val data: List<JsonObject>? = null,
)

@Serializable
data class SearchTypeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchTypeData? = null,
)

/**
 * 分类搜索统一数据模型：`search_type` 决定哪组字段有值。
 * 标题里的 `<em class="keyword">` 由仓库层清掉。
 */
@Serializable
data class SearchTypeData(
    val page: Int = 1,
    val pagesize: Int = 20,
    @SerialName("numResults") val numResults: Int = 0,
    @SerialName("numPages") val numPages: Int = 0,
    @SerialName("v_voucher") val vVoucher: String? = null,
    val result: List<SearchItem>? = null,
)

@Serializable
data class SearchPgcScore(
    @Serializable(with = FlexibleFloatSerializer::class)
    val score: Float = 0f,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("user_count") val userCount: Int = 0,
)

@Serializable
data class SearchOfficialVerify(
    val type: Int = -1,
    val desc: String = "",
)

@Serializable
data class SearchItem(
    val type: String = "",
    val id: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val aid: Long = 0,
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
    @Serializable(with = FlexibleIntSerializer::class)
    val level: Int = 0,
    @SerialName("official_verify") val officialVerify: SearchOfficialVerify? = null,
    val res: List<SearchItem> = emptyList(),
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
    // 番剧 / 影视结果的字段
    @SerialName("media_id") val mediaId: Long = 0,
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("season_type") val seasonType: Int = 0,
    @SerialName("season_type_name") val seasonTypeName: String = "",
    val cover: String = "",
    val areas: String = "",
    val styles: String = "",
    val pubtime: Long = 0,
    @SerialName("index_show") val indexShow: String = "",
    @SerialName("media_score") val mediaScore: SearchPgcScore? = null,
    // 直播间结果的字段
    @Serializable(with = FlexibleLongSerializer::class)
    val roomid: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val uid: Long = 0,
    val uface: String = "",
    @SerialName("user_cover") val userCover: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val online: Long = 0,
    @SerialName("cate_name") val cateName: String = "",
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

