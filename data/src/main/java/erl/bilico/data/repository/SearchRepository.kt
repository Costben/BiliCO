package erl.bilico.data.repository

import erl.bilico.data.model.SearchArticle
import erl.bilico.data.model.SearchItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class SearchRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    /** 热搜榜。wbi 版要签名，拿不到就退回不需要签名的老接口。 */
    suspend fun hotWords(): Result<List<String>> = withContext(Dispatchers.IO) {
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
    suspend fun suggest(term: String): Result<List<SearchSuggestion>> = withContext(Dispatchers.IO) {
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

    suspend fun searchVideos(
        keyword: String,
        page: Int,
        order: String,
        tid: Int,
    ): Result<List<SearchVideo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.searchType(
                    signedParams(
                        keyword = keyword,
                        searchType = "video",
                        page = page,
                    ) + buildMap {
                        put("order", order)
                        put("platform", "pc")
                        put("web_location", "1430654")
                        // tids 是分区 id，0 表示不限分区，不带这个参数。
                        if (tid > 0) put("tids", tid.toString())
                    },
                )
                if (response.code != 0) error(response.message.ifBlank { "搜索失败(${response.code})" })
                response.data?.result.orEmpty().mapNotNull { it.toVideo() }
            }
        }

    suspend fun searchUsers(keyword: String, page: Int): Result<List<SearchUser>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.searchType(
                    signedParams(
                        keyword = keyword,
                        searchType = "bili_user",
                        page = page,
                    ) + mapOf("from_source" to "webtop", "web_location" to "1430654"),
                )
                if (response.code != 0) error(response.message.ifBlank { "搜索失败(${response.code})" })
                response.data?.result.orEmpty()
                    .filter { it.mid > 0L }
                    .map { it.toUser() }
            }
        }

    /** 专栏搜索：`search_type=article`，结果是 cv 号，点开走阅读页。 */
    suspend fun searchArticles(
        keyword: String,
        page: Int,
        order: String,
    ): Result<List<SearchArticle>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.searchType(
                    signedParams(
                        keyword = keyword,
                        searchType = "article",
                        page = page,
                    ) + mapOf("order" to order, "web_location" to "1430654"),
                )
                if (response.code != 0) error(response.message.ifBlank { "搜索失败(${response.code})" })
                response.data?.result.orEmpty()
                    .filter { it.id > 0L }
                    .map { it.toArticle() }
            }
        }

    private suspend fun signedParams(
        keyword: String,
        searchType: String,
        page: Int,
    ): Map<String, String> {
        buvid.ensure()
        val keys = wbi.getKeys()
        return WbiUtils.sign(
            mapOf(
                "keyword" to keyword,
                "search_type" to searchType,
                "page" to page.coerceAtLeast(1).toString(),
                "page_size" to "30",
            ),
            keys.first,
            keys.second,
        )
    }

    private fun SearchItem.toVideo(): SearchVideo? {
        if (bvid.isBlank()) return null
        val seconds = Formatters.parseDurationText(duration)
        return SearchVideo(
            bvid = bvid,
            title = cleanTitle(title),
            cover = Formatters.normalizeImageUrl(pic),
            author = author,
            upMid = mid,
            playText = Formatters.formatStat(play.toLong()),
            durationText = if (seconds > 0) Formatters.formatDuration(seconds.toLong()) else "",
            pubdateText = Formatters.formatTimeAgo(pubdate),
        )
    }

    private fun SearchItem.toUser(): SearchUser = SearchUser(
        mid = mid,
        name = cleanTitle(uname),
        face = Formatters.normalizeImageUrl(upic),
        sign = Formatters.decodeHtmlEntities(usign),
        fansText = Formatters.formatStat(fans.toLong()) + "粉丝",
        videoCountText = "${Formatters.formatStat(videos.toLong())}视频",
    )

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

    private fun cleanTitle(raw: String): String =
        Formatters.decodeHtmlEntities(raw.replace(HTML_TAG, "")).trim()

    private companion object {
        val HTML_TAG = Regex("<[^>]+>")
    }
}
