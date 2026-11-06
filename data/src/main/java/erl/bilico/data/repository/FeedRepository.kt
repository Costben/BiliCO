package erl.bilico.data.repository

import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.RecommendItem
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    /**
     * 首页推荐：`fresh_idx` 既是页号也是去重种子，翻页时递增。
     * 只保留普通稿件（goto=av），直播/番剧卡片这一轮不展示。
     */
    suspend fun recommend(page: Int): Result<List<FeedVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val index = page.coerceAtLeast(1).toString()
            val params = mapOf(
                "ps" to "20",
                "fresh_idx" to index,
                "fresh_idx_1h" to index,
                "feed_version" to "V8",
                "fresh_type" to "3",
                "homepage_ver" to "1",
                "web_location" to "1430650",
            )
            val response = api.recommend(WbiUtils.sign(params, keys.first, keys.second))
            if (response.code != 0) {
                error(response.message.ifBlank { "推荐加载失败(${response.code})" })
            }
            response.data?.item.orEmpty()
                .filter { it.goto == "av" || it.goto.isNullOrBlank() }
                .mapNotNull { it.toFeedVideo() }
        }
    }

    private fun RecommendItem.toFeedVideo(): FeedVideo? {
        val videoBvid = bvid?.takeIf { it.isNotBlank() } ?: return null
        val cover = pic?.takeIf { it.isNotBlank() } ?: return null
        val viewCount = stat?.view ?: 0L
        val seconds = (duration ?: 0).toLong().coerceAtLeast(0L)
        return FeedVideo(
            bvid = videoBvid,
            aid = id,
            title = title.orEmpty(),
            cover = Formatters.normalizeImageUrl(cover),
            upName = owner?.name.orEmpty(),
            upMid = owner?.mid ?: 0L,
            upFace = Formatters.normalizeImageUrl(owner?.face),
            playCount = viewCount,
            danmaku = stat?.danmaku ?: 0L,
            durationSec = duration ?: 0,
            playCountText = Formatters.formatStat(viewCount),
            danmakuText = Formatters.formatStat(stat?.danmaku ?: 0L),
            durationText = Formatters.formatDuration(seconds),
        )
    }
}
