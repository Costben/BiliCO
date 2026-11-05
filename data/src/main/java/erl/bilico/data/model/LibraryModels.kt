package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- 收藏夹 ----------

@Serializable
data class FavFolderResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FavFolderList? = null,
)

@Serializable
data class FavFolderList(
    val count: Int = 0,
    val list: List<FavFolder> = emptyList(),
)

@Serializable
data class FavFolder(
    val id: Long = 0,
    val fid: Long = 0,
    val title: String = "",
    val cover: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val media_count: Int = 0,
)

@Serializable
data class FavResourceResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FavResourceData? = null,
)

@Serializable
data class FavResourceData(
    val info: FavResourceInfo? = null,
    val medias: List<FavMedia> = emptyList(),
    val has_more: Boolean = false,
)

@Serializable
data class FavResourceInfo(
    val id: Long = 0,
    val title: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val media_count: Int = 0,
)

@Serializable
data class FavMedia(
    val id: Long = 0,
    val type: Int = 0,
    val title: String = "",
    val cover: String = "",
    val bvid: String = "",
    @SerialName("bv_id") val bvId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val duration: Int = 0,
    val upper: LibraryUpper? = null,
    @SerialName("cnt_info") val cntInfo: FavMediaCount? = null,
)

@Serializable
data class FavMediaCount(
    @Serializable(with = FlexibleLongSerializer::class)
    val play: Long = 0,
)

// ---------- 稍后再看 ----------

@Serializable
data class WatchLaterResponse(
    val code: Int = 0,
    val message: String = "",
    val data: WatchLaterData? = null,
)

@Serializable
data class WatchLaterData(
    val count: Int = 0,
    val list: List<WatchLaterItem> = emptyList(),
)

@Serializable
data class WatchLaterItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val duration: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val progress: Int = 0,
    val owner: LibraryUpper? = null,
)

// ---------- 关注列表 ----------

@Serializable
data class FollowingsResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FollowingsData? = null,
)

@Serializable
data class FollowingsData(
    val list: List<FollowingsUser> = emptyList(),
    @Serializable(with = FlexibleIntSerializer::class)
    val total: Int = 0,
)

@Serializable
data class FollowingsUser(
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = "",
)

@Serializable
data class LibraryUpper(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)
