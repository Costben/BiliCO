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
    val draw: DrawMajor? = null,
    val opus: OpusMajor? = null,
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

@Serializable
data class OpusMajor(
    val pics: List<DynamicImageData> = emptyList(),
    val summary: OpusSummary? = null,
    val title: String? = null,
)

@Serializable
data class OpusSummary(
    val text: String = "",
)

@Serializable
data class DynamicImageData(
    val src: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

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
