package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpaceLiveRoom(
    val roomStatus: Int = 0,
    val liveStatus: Int = 0,
    val url: String = "",
    val title: String = "",
    val cover: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val roomid: Long = 0,
)

@Serializable
data class SpaceLiveRoomOldResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceLiveRoom? = null,
)

@Serializable
data class SpaceSeasonsSeriesResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceSeasonsSeriesData? = null,
)

@Serializable
data class SpaceSeasonsSeriesData(
    @SerialName("items_lists")
    val itemsLists: SpaceSeasonsItemsLists? = null,
)

@Serializable
data class SpaceSeasonsItemsLists(
    val page: SpaceSeasonPage? = null,
    @SerialName("seasons_list")
    val seasonsList: List<SpaceCollectionEntry> = emptyList(),
    @SerialName("series_list")
    val seriesList: List<SpaceCollectionEntry> = emptyList(),
)

@Serializable
data class SpaceSeasonPage(
    @SerialName("page_num")
    val pageNum: Int = 1,
    @SerialName("page_size")
    val pageSize: Int = 20,
    val total: Int = 0,
)

@Serializable
data class SpaceSeriesPage(
    val num: Int = 1,
    val size: Int = 30,
    val total: Int = 0,
)

@Serializable
data class SpaceCollectionEntry(
    val meta: SpaceCollectionMeta = SpaceCollectionMeta(),
    val archives: List<SpaceCollectionArchive> = emptyList(),
)

@Serializable
data class SpaceCollectionMeta(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("series_id")
    val seriesId: Long = 0,
    val name: String = "",
    val title: String = "",
    val cover: String = "",
    val description: String = "",
    val total: Int = 0,
)

@Serializable
data class SpaceCollectionArchive(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val pubdate: Long = 0,
    val stat: SpaceCollectionStat? = null,
)

@Serializable
data class SpaceCollectionStat(
    @Serializable(with = FlexibleLongSerializer::class)
    val view: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val danmaku: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val reply: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val like: Long = 0,
)

@Serializable
data class SpaceSeasonArchivesResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceSeasonArchivesData? = null,
)

@Serializable
data class SpaceSeasonArchivesData(
    val archives: List<SpaceCollectionArchive> = emptyList(),
    val page: SpaceSeasonPage = SpaceSeasonPage(),
)

@Serializable
data class SpaceSeriesArchivesResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceSeriesArchivesData? = null,
)

@Serializable
data class SpaceSeriesArchivesData(
    val archives: List<SpaceCollectionArchive> = emptyList(),
    val page: SpaceSeriesPage = SpaceSeriesPage(),
)

@Serializable
data class SpaceLikedVideosResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceLikedVideosData? = null,
)

@Serializable
data class SpaceLikedVideosData(
    val list: List<SpaceLikedVideoItem> = emptyList(),
)

@Serializable
data class SpaceLikedVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val pubdate: Long = 0,
    val owner: SpaceLikedOwner? = null,
    val stat: SpaceCollectionStat? = null,
)

@Serializable
data class SpaceLikedOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class SpaceOpusFeedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceOpusFeedData? = null,
)

@Serializable
data class SpaceOpusFeedData(
    @SerialName("has_more")
    val hasMore: Boolean = false,
    val offset: String = "",
    val items: List<SpaceOpusItem> = emptyList(),
)

@Serializable
data class SpaceOpusItem(
    @SerialName("opus_id")
    val opusId: String = "",
    @SerialName("jump_url")
    val jumpUrl: String = "",
    val content: String = "",
    val cover: SpaceOpusCover? = null,
    val stat: SpaceOpusStat? = null,
    @SerialName("pub_time")
    val pubTime: String = "",
)

@Serializable
data class SpaceOpusCover(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class SpaceOpusStat(
    val like: String = "",
    val view: String = "",
)
