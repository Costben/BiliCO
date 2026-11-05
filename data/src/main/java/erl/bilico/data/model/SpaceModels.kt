package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpaceInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceUserInfo? = null,
)

@Serializable
data class SpaceUserInfo(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int = 0,
)

@Serializable
data class RelationStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RelationStatData? = null,
)

@Serializable
data class RelationStatData(
    @Serializable(with = FlexibleLongSerializer::class)
    val follower: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val following: Long = 0,
)

@Serializable
data class UpStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UpStatData? = null,
)

@Serializable
data class UpStatData(
    @Serializable(with = FlexibleLongSerializer::class)
    val likes: Long = 0,
    val archive: UpStatArchive = UpStatArchive(),
)

@Serializable
data class UpStatArchive(
    @Serializable(with = FlexibleLongSerializer::class)
    val view: Long = 0,
)

@Serializable
data class SpaceVideoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceVideoData? = null,
)

@Serializable
data class SpaceVideoData(
    val list: SpaceVideoList = SpaceVideoList(),
    val page: SpacePage = SpacePage(),
)

@Serializable
data class SpaceVideoList(
    val vlist: List<SpaceVideoItem> = emptyList(),
)

@Serializable
data class SpacePage(
    val pn: Int = 1,
    val ps: Int = 30,
    val count: Int = 0,
)

@Serializable
data class SpaceVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val play: Long = 0,
    val comment: Long = 0,
    val length: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val created: Long = 0,
)
