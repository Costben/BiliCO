package erl.bilico.data.repository

import erl.bilico.data.model.SearchArticle
import erl.bilico.data.model.SearchItem
import erl.bilico.data.model.SearchUser
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun searchVideos(keyword: String, page: Int): Result<List<SearchVideo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.searchType(
                    signedParams(
                        keyword = keyword,
                        searchType = "video",
                        page = page,
                    ) + mapOf("order" to "totalrank", "platform" to "pc", "web_location" to "1430654"),
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
    suspend fun searchArticles(keyword: String, page: Int): Result<List<SearchArticle>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.searchType(
                    signedParams(
                        keyword = keyword,
                        searchType = "article",
                        page = page,
                    ) + mapOf("order" to "totalrank", "web_location" to "1430654"),
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
            durationText = Formatters.formatDuration(seconds.toLong()),
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
