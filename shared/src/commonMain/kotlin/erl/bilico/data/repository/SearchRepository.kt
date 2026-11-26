package erl.bilico.data.repository

import erl.bilico.data.model.SearchAllBundle
import erl.bilico.data.model.SearchArticle
import erl.bilico.data.model.SearchItem
import erl.bilico.data.model.SearchLive
import erl.bilico.data.model.SearchPageResult
import erl.bilico.data.model.SearchPgc
import erl.bilico.data.model.SearchSuggestResult
import erl.bilico.data.model.SearchSuggestion
import erl.bilico.data.model.SearchUser
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import erl.bilico.platform.formatFixedDecimal
import erl.bilico.platform.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class SearchRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    /** 热搜榜。wbi 版要签名，拿不到就退回不需要签名的老接口。 */
    suspend fun hotWords(): Result<List<String>> = withContext(ioDispatcher) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val signed = runCatching {
                api.searchSquare(
                    WbiUtils.sign(
                        mapOf("limit" to "10", "web_location" to "333.1387"),
                        keys.first,
                        keys.second,
                    )
                )
            }.getOrNull()
            val list = signed?.data?.trending?.list
                ?.map { it.showName.ifBlank { it.keyword } }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            if (list.isNotEmpty()) return@runCatching list
            api.searchSquareLegacy(10).data?.trending?.list
                ?.map { it.showName.ifBlank { it.keyword } }
                ?.filter { it.isNotBlank() }
                .orEmpty()
        }
    }

    /** 根据当前输入词获取搜索建议词列表。 */
    suspend fun suggest(term: String): Result<List<SearchSuggestion>> = withContext(ioDispatcher) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
        runCatching {
            val response = api.searchSuggest(trimmed)
            if (response.code != 0) return@runCatching emptyList()
            val resultElement = response.data?.result as? JsonObject ?: return@runCatching emptyList()
            val parsed = BiliClient.json.decodeFromJsonElement<SearchSuggestResult>(resultElement)
            parsed.tag.mapNotNull { tag ->
                val rawKeyword = tag.value.ifBlank { tag.term }
                val keyword = Formatters.decodeHtmlEntities(rawKeyword).trim()
                if (keyword.isNotEmpty()) {
                    SearchSuggestion(text = keyword, rawHtml = tag.name)
                } else {
                    null
                }
            }.distinctBy { it.text }
        }
    }

    /** 综合搜索第一页（无筛选时）：同时返回匹配的用户、番剧/影视与视频。 */
    suspend fun searchAll(keyword: String): Result<SearchAllBundle> = withContext(ioDispatcher) {
        runCatching {
            val response = api.searchAll(
                signedParams(
                    keyword = keyword,
                    searchType = "all",
                    page = 1,
                    extra = mapOf(
                        "platform" to "pc",
                        "web_location" to "1430654",
                    ),
                ),
            )
            if (response.code != 0) error(response.message.ifBlank { "搜索失败(${response.code})" })
            val data = response.data
            if (data?.vVoucher != null) error("触发风控验证，请稍后再试")
            val users = mutableListOf<SearchUser>()
            val pgc = mutableListOf<SearchPgc>()
            val videos = mutableListOf<SearchVideo>()
            data?.result.orEmpty().forEach { section ->
                val items = section.data.orEmpty().mapNotNull { obj ->
                    runCatching { BiliClient.json.decodeFromJsonElement<SearchItem>(obj) }.getOrNull()
                }
                when (section.resultType) {
                    "bili_user" -> users += items.filter { it.mid > 0L }.map { it.toUser() }
                    "media_bangumi", "media_ft" -> pgc += items.mapNotNull { it.toPgc() }
                    "video" -> videos += items.mapNotNull { it.toVideo() }
                }
            }
            SearchAllBundle(
                users = users.distinctBy { it.mid },
                pgc = pgc.distinctBy { it.seasonId },
                videos = videos.distinctBy { it.bvid },
                numResults = data?.numResults ?: 0,
                typeCounts = data?.topTlist.orEmpty().mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
            )
        }
    }

    suspend fun searchVideos(
        keyword: String,
        page: Int,
        order: String,
        tid: Int,
        duration: Int = 0,
        pubBeginSec: Long? = null,
        pubEndSec: Long? = null,
    ): Result<SearchPageResult<SearchVideo>> = fetchType(
        keyword = keyword,
        searchType = "video",
        page = page,
        extra = buildMap {
            if (order.isNotBlank()) put("order", order)
            if (duration > 0) put("duration", duration.toString())
            if (tid > 0) put("tids", tid.toString())
            if (pubBeginSec != null && pubEndSec != null) {
                put("pubtime_begin_s", pubBeginSec.toString())
                put("pubtime_end_s", pubEndSec.toString())
            }
            put("platform", "pc")
            put("web_location", "1430654")
        },
    ) { items -> items.mapNotNull { it.toVideo() } }

    suspend fun searchPgc(
        keyword: String,
        page: Int,
        searchType: String,
    ): Result<SearchPageResult<SearchPgc>> = fetchType(
        keyword = keyword,
        searchType = searchType,
        page = page,
        extra = mapOf(
            "platform" to "pc",
            "web_location" to "1430654",
        ),
    ) { items -> items.mapNotNull { it.toPgc() } }

    suspend fun searchLiveRooms(
        keyword: String,
        page: Int,
    ): Result<SearchPageResult<SearchLive>> = fetchType(
        keyword = keyword,
        searchType = "live_room",
        page = page,
        extra = mapOf(
            "platform" to "pc",
            "web_location" to "1430654",
        ),
    ) { items -> items.mapNotNull { it.toLive() } }

    suspend fun searchUsers(
        keyword: String,
        page: Int,
        order: String = "",
        orderSort: Int = 0,
        userType: Int = 0,
    ): Result<SearchPageResult<SearchUser>> = fetchType(
        keyword = keyword,
        searchType = "bili_user",
        page = page,
        extra = buildMap {
            if (order.isNotBlank()) put("order", order)
            put("order_sort", orderSort.toString())
            put("user_type", userType.toString())
            put("from_source", "webtop")
            put("platform", "pc")
            put("web_location", "1430654")
        },
    ) { items -> items.filter { it.mid > 0L }.map { it.toUser() } }

    /** 专栏搜索：`search_type=article`，结果是 cv 号，点开走阅读页。 */
    suspend fun searchArticles(
        keyword: String,
        page: Int,
        order: String,
        categoryId: Int = 0,
    ): Result<SearchPageResult<SearchArticle>> = fetchType(
        keyword = keyword,
        searchType = "article",
        page = page,
        extra = buildMap {
            put("order", order)
            if (categoryId > 0) put("category_id", categoryId.toString())
            put("platform", "pc")
            put("web_location", "1430654")
        },
    ) { items -> items.filter { it.id > 0L }.map { it.toArticle() } }

    private suspend fun <T> fetchType(
        keyword: String,
        searchType: String,
        page: Int,
        extra: Map<String, String>,
        mapper: (List<SearchItem>) -> List<T>,
    ): Result<SearchPageResult<T>> = withContext(ioDispatcher) {
        runCatching {
            val response = api.searchType(
                signedParams(
                    keyword = keyword,
                    searchType = searchType,
                    page = page,
                    extra = extra,
                ),
            )
            if (response.code != 0) error(response.message.ifBlank { "搜索失败(${response.code})" })
            if (response.data?.vVoucher != null) error("触发风控验证，请稍后再试")
            val mapped = mapper(response.data?.result.orEmpty())
            SearchPageResult(
                items = mapped,
                numResults = response.data?.numResults ?: mapped.size,
            )
        }
    }

    private suspend fun signedParams(
        keyword: String,
        searchType: String,
        page: Int,
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        buvid.ensure()
        val keys = wbi.getKeys()
        return WbiUtils.sign(
            buildMap {
                put("keyword", keyword)
                put("search_type", searchType)
                put("page", page.coerceAtLeast(1).toString())
                put("page_size", "30")
                putAll(extra)
            },
            keys.first,
            keys.second,
        )
    }

    private fun SearchItem.toVideo(
        fallbackAuthor: String = "",
        fallbackMid: Long = 0L,
    ): SearchVideo? {
        if (bvid.isBlank()) return null
        val seconds = Formatters.parseDurationText(duration)
        return SearchVideo(
            bvid = bvid,
            title = cleanTitle(title),
            cover = Formatters.normalizeImageUrl(pic),
            author = author.ifBlank { fallbackAuthor },
            upMid = if (mid > 0L) mid else fallbackMid,
            playText = Formatters.formatStat(play.toLong()),
            durationText = if (seconds > 0) Formatters.formatDuration(seconds.toLong()) else "",
            pubdateText = Formatters.formatTimeAgo(pubdate),
            aid = aid.takeIf { it > 0L } ?: id.takeIf { it > 0L } ?: 0L,
        )
    }

    private fun SearchItem.toUser(): SearchUser {
        val upName = cleanTitle(uname)
        return SearchUser(
            mid = mid,
            name = upName,
            face = Formatters.normalizeImageUrl(upic),
            sign = Formatters.decodeHtmlEntities(usign),
            fansText = Formatters.formatStat(fans.toLong()) + "粉丝",
            videoCountText = "${Formatters.formatStat(videos.toLong())}视频",
            level = level,
            officialType = officialVerify?.resolvedType() ?: -1,
            officialDesc = officialVerify?.desc.orEmpty(),
            recentVideos = res.mapNotNull { it.toVideo(fallbackAuthor = upName, fallbackMid = mid) }.take(2),
        )
    }

    private fun SearchItem.toArticle(): SearchArticle = SearchArticle(
        id = id,
        title = cleanTitle(title),
        cover = Formatters.normalizeImageUrl(imageUrls.firstOrNull()),
        author = author,
        upMid = mid,
        categoryName = categoryName,
        viewText = Formatters.formatStat(view.toLong()),
        likeText = Formatters.formatStat(like.toLong()),
        desc = cleanTitle(desc),
    )

    private fun SearchItem.toPgc(): SearchPgc? {
        if (seasonId <= 0L) return null
        val score = mediaScore?.score ?: 0f
        return SearchPgc(
            seasonId = seasonId,
            mediaId = mediaId,
            title = cleanTitle(title),
            cover = Formatters.normalizeImageUrl(cover),
            seasonTypeName = seasonTypeName,
            scoreText = if (score > 0f) "${formatFixedDecimal(score.toDouble(), 1)}分" else "",
            areas = areas,
            styles = styles,
            pubdateText = Formatters.formatDate(pubtime),
            indexShow = indexShow,
            desc = cleanTitle(desc),
        )
    }

    private fun SearchItem.toLive(): SearchLive? {
        if (roomid <= 0L) return null
        return SearchLive(
            roomId = roomid,
            uid = uid,
            title = cleanTitle(title),
            cover = Formatters.normalizeImageUrl(cover.ifBlank { userCover }),
            anchor = uname,
            face = Formatters.normalizeImageUrl(uface),
            cateName = cleanTitle(cateName),
            onlineText = if (online > 0L) "${Formatters.formatStat(online)}围观" else "",
        )
    }

    private fun cleanTitle(raw: String): String =
        Formatters.decodeHtmlEntities(raw.replace(HTML_TAG, "")).trim()

    private companion object {
        val HTML_TAG = Regex("<[^>]+>")
    }
}
