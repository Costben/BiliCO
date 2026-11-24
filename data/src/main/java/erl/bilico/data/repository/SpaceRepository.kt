package erl.bilico.data.repository

import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.model.SpaceCollection
import erl.bilico.data.model.SpaceCollectionArchive
import erl.bilico.data.model.SpaceOpusUi
import erl.bilico.data.model.SpaceProfile
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SpaceLikedVideosResult(
    val videos: List<LibraryVideo>,
    val isPrivate: Boolean = false,
)

data class SpaceOpusPage(
    val items: List<SpaceOpusUi>,
    val offset: String,
    val hasMore: Boolean,
)

class SpaceRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    /** 空间头卡：资料走 acc/info，粉丝/关注与获赞分别来自两个统计接口，取不到就留空。 */
    suspend fun profile(mid: Long): Result<SpaceProfile> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val response = api.spaceInfo(
                WbiUtils.sign(
                    mapOf(
                        "mid" to mid.toString(),
                        "platform" to "web",
                        "web_location" to "1550101",
                    ),
                    keys.first,
                    keys.second,
                    includeRiskFingerprint = true,
                )
            )
            if (response.code != 0) error(response.message.ifBlank { "空间加载失败(${response.code})" })
            val data = response.data ?: error("没有拿到用户资料")
            val stat = runCatching { api.relationStat(mid) }.getOrNull()?.data
            val likes = runCatching { api.upStat(mid) }.getOrNull()?.data?.likes
            val officialType = data.official?.resolvedType() ?: -1
            val officialDesc = data.official?.resolvedDesc().orEmpty()
            val resolvedMid = data.mid.takeIf { it > 0L } ?: mid
            val liveRoom = data.liveRoom?.takeIf { it.roomid > 0L }
                ?: runCatching { api.spaceLiveRoom(resolvedMid).data }.getOrNull()?.takeIf { it.roomid > 0L }
            val hasLiveRoom = liveRoom != null && (liveRoom.roomStatus != 0 || liveRoom.roomid > 0L)
            SpaceProfile(
                mid = resolvedMid,
                name = data.name,
                face = Formatters.normalizeImageUrl(data.face),
                sign = Formatters.decodeHtmlEntities(data.sign),
                followersText = stat?.let { Formatters.formatStat(it.follower) } ?: "-",
                followingText = stat?.let { Formatters.formatStat(it.following) } ?: "-",
                likesText = likes?.let { Formatters.formatStat(it) } ?: "-",
                officialType = officialType,
                officialDesc = officialDesc,
                liveRoomId = if (hasLiveRoom) liveRoom.roomid else 0L,
                isLive = hasLiveRoom && liveRoom.liveStatus == 1,
                liveTitle = if (hasLiveRoom) Formatters.decodeHtmlEntities(liveRoom.title) else "",
            )
        }
    }

    suspend fun videos(
        mid: Long,
        page: Int,
        keyword: String = "",
        order: String = "pubdate",
    ): Result<Pair<List<LibraryVideo>, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val keys = wbi.getKeys()
                val pn = page.coerceAtLeast(1)
                val params = mutableMapOf(
                    "mid" to mid.toString(),
                    "pn" to pn.toString(),
                    "ps" to PAGE_SIZE.toString(),
                    "order" to order.ifBlank { "pubdate" },
                    "platform" to "web",
                    "web_location" to "1550101",
                )
                if (keyword.isNotBlank()) {
                    params["keyword"] = keyword.trim()
                }
                val response = api.spaceVideos(
                    WbiUtils.sign(
                        params,
                        keys.first,
                        keys.second,
                        includeRiskFingerprint = true,
                    )
                )
                if (response.code != 0) error(response.message.ifBlank { "投稿加载失败(${response.code})" })
                val list = response.data?.list?.vlist.orEmpty().mapNotNull { item ->
                    if (item.bvid.isBlank()) return@mapNotNull null
                    val seconds = Formatters.parseDurationText(item.length)
                    LibraryVideo(
                        bvid = item.bvid,
                        title = Formatters.decodeHtmlEntities(item.title),
                        cover = Formatters.normalizeImageUrl(item.pic),
                        upName = "",
                        playText = Formatters.formatStat(item.play),
                        danmakuText = Formatters.formatStat(item.videoReview),
                        pubTimeText = Formatters.formatTimeAgo(item.created),
                        durationText = Formatters.formatDuration(seconds.toLong()),
                        durationSec = seconds,
                        aid = item.aid,
                        playCount = item.play,
                    )
                }
                val total = response.data?.page?.count ?: 0
                list to (pn * PAGE_SIZE < total)
            }
        }

    suspend fun collections(
        mid: Long,
        page: Int,
    ): Result<Pair<List<SpaceCollection>, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val pn = page.coerceAtLeast(1)
            val response = api.spaceSeasonsSeries(
                mapOf(
                    "mid" to mid.toString(),
                    "page_num" to pn.toString(),
                    "page_size" to COLLECTION_PAGE_SIZE.toString(),
                )
            )
            if (response.code != 0) error(response.message.ifBlank { "合集加载失败(${response.code})" })
            val lists = response.data?.itemsLists ?: return@runCatching emptyList<SpaceCollection>() to false
            val seasons = lists.seasonsList.mapNotNull { entry ->
                val meta = entry.meta
                if (meta.seasonId <= 0L) return@mapNotNull null
                SpaceCollection(
                    id = meta.seasonId,
                    isSeason = true,
                    name = Formatters.decodeHtmlEntities(meta.title.ifBlank { meta.name }),
                    cover = Formatters.normalizeImageUrl(meta.cover),
                    description = Formatters.decodeHtmlEntities(meta.description),
                    total = meta.total.takeIf { it > 0 } ?: entry.archives.size,
                )
            }
            val series = lists.seriesList.mapNotNull { entry ->
                val meta = entry.meta
                if (meta.seriesId <= 0L) return@mapNotNull null
                SpaceCollection(
                    id = meta.seriesId,
                    isSeason = false,
                    name = Formatters.decodeHtmlEntities(meta.name.ifBlank { meta.title }),
                    cover = Formatters.normalizeImageUrl(meta.cover),
                    description = Formatters.decodeHtmlEntities(meta.description),
                    total = meta.total.takeIf { it > 0 } ?: entry.archives.size,
                )
            }
            val pageInfo = lists.page
            val hasMore = pageInfo != null && pn * pageInfo.pageSize < pageInfo.total
            (seasons + series) to hasMore
        }
    }

    suspend fun collectionVideos(
        mid: Long,
        collection: SpaceCollection,
        page: Int,
    ): Result<Pair<List<LibraryVideo>, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val pn = page.coerceAtLeast(1)
            if (collection.isSeason) {
                val response = api.spaceSeasonArchives(
                    mapOf(
                        "mid" to mid.toString(),
                        "season_id" to collection.id.toString(),
                        "sort_reverse" to "false",
                        "page_num" to pn.toString(),
                        "page_size" to PAGE_SIZE.toString(),
                    )
                )
                if (response.code != 0) error(response.message.ifBlank { "合集内容加载失败(${response.code})" })
                val data = response.data ?: return@runCatching emptyList<LibraryVideo>() to false
                val items = data.archives.mapNotNull { it.toLibraryVideo() }
                val hasMore = pn * data.page.pageSize < data.page.total
                items to hasMore
            } else {
                val response = api.spaceSeriesArchives(
                    mapOf(
                        "mid" to mid.toString(),
                        "series_id" to collection.id.toString(),
                        "only_normal" to "true",
                        "sort" to "desc",
                        "pn" to pn.toString(),
                        "ps" to PAGE_SIZE.toString(),
                    )
                )
                if (response.code != 0) error(response.message.ifBlank { "系列内容加载失败(${response.code})" })
                val data = response.data ?: return@runCatching emptyList<LibraryVideo>() to false
                val items = data.archives.mapNotNull { it.toLibraryVideo() }
                val hasMore = pn * data.page.size < data.page.total
                items to hasMore
            }
        }
    }

    suspend fun likedVideos(mid: Long): Result<SpaceLikedVideosResult> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.spaceLikedVideos(mid)
            if (response.code == 53013) {
                return@runCatching SpaceLikedVideosResult(videos = emptyList(), isPrivate = true)
            }
            if (response.code != 0) error(response.message.ifBlank { "最近点赞加载失败(${response.code})" })
            val items = response.data?.list.orEmpty().mapNotNull { item ->
                if (item.bvid.isBlank()) return@mapNotNull null
                val stat = item.stat
                LibraryVideo(
                    bvid = item.bvid,
                    title = Formatters.decodeHtmlEntities(item.title),
                    cover = Formatters.normalizeImageUrl(item.pic),
                    upName = Formatters.decodeHtmlEntities(item.owner?.name.orEmpty()),
                    playText = Formatters.formatStat(stat?.view ?: 0L),
                    danmakuText = Formatters.formatStat(stat?.danmaku ?: 0L),
                    pubTimeText = if (item.pubdate > 0L) Formatters.formatTimeAgo(item.pubdate) else "",
                    durationText = Formatters.formatDuration(item.duration.toLong()),
                    durationSec = item.duration,
                    aid = item.aid,
                    playCount = stat?.view ?: 0L,
                )
            }
            SpaceLikedVideosResult(videos = items, isPrivate = false)
        }
    }

    suspend fun opusFeed(
        mid: Long,
        offset: String = "",
        page: Int = 1,
    ): Result<SpaceOpusPage> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val params = mutableMapOf(
                "host_mid" to mid.toString(),
                "page" to page.coerceAtLeast(1).toString(),
                "type" to "all",
            )
            if (offset.isNotBlank()) {
                params["offset"] = offset
            }
            val response = api.spaceOpusFeed(
                WbiUtils.sign(
                    params,
                    keys.first,
                    keys.second,
                    includeRiskFingerprint = true,
                )
            )
            if (response.code != 0) error(response.message.ifBlank { "图文加载失败(${response.code})" })
            val data = response.data ?: return@runCatching SpaceOpusPage(emptyList(), "", false)
            val items = data.items.mapNotNull { item ->
                val id = item.opusId.ifBlank {
                    item.jumpUrl.substringAfterLast("/").substringBefore("?")
                }
                if (id.isBlank()) return@mapNotNull null
                SpaceOpusUi(
                    opusId = id,
                    content = Formatters.decodeHtmlEntities(item.content),
                    coverUrl = Formatters.normalizeImageUrl(item.cover?.url.orEmpty()),
                    coverWidth = item.cover?.width ?: 0,
                    coverHeight = item.cover?.height ?: 0,
                    likeText = item.stat?.like.orEmpty(),
                    pubTime = item.pubTime,
                )
            }
            SpaceOpusPage(items = items, offset = data.offset, hasMore = data.hasMore)
        }
    }

    private fun SpaceCollectionArchive.toLibraryVideo(): LibraryVideo? {
        if (bvid.isBlank()) return null
        val views = stat?.view ?: 0L
        val danmaku = stat?.danmaku ?: 0L
        return LibraryVideo(
            bvid = bvid,
            title = Formatters.decodeHtmlEntities(title),
            cover = Formatters.normalizeImageUrl(pic),
            upName = "",
            playText = Formatters.formatStat(views),
            danmakuText = Formatters.formatStat(danmaku),
            pubTimeText = if (pubdate > 0L) Formatters.formatTimeAgo(pubdate) else "",
            durationText = Formatters.formatDuration(duration.toLong()),
            durationSec = duration,
            aid = aid,
            playCount = views,
        )
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val COLLECTION_PAGE_SIZE = 20
    }
}
