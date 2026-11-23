package erl.bilico.data.repository

import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.RankingItem
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 全站与分区排行榜、综合热门：共用同一套稿件映射逻辑。 */
class RankingRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    suspend fun ranking(rid: Int): Result<List<FeedVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val params = mapOf("rid" to rid.toString(), "type" to "all")
            val response = api.ranking(WbiUtils.sign(params, keys.first, keys.second))
            if (response.code != 0) {
                error(response.message.ifBlank { "排行榜加载失败(${response.code})" })
            }
            response.data?.list.orEmpty().mapNotNull { it.toFeedVideo() }
        }
    }

    suspend fun popular(page: Int, pageSize: Int = 20): Result<Pair<List<FeedVideo>, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val pn = page.coerceAtLeast(1)
                val response = api.popular(mapOf("pn" to pn.toString(), "ps" to pageSize.toString()))
                if (response.code != 0) {
                    error(response.message.ifBlank { "热门加载失败(${response.code})" })
                }
                val data = response.data
                val list = data?.list.orEmpty().mapNotNull { it.toFeedVideo() }
                val hasMore = data != null && !data.noMore && list.isNotEmpty()
                list to hasMore
            }
        }

    private fun RankingItem.toFeedVideo(): FeedVideo? {
        if (bvid.isBlank()) return null
        val view = stat?.view ?: 0L
        val danmaku = stat?.danmaku ?: 0L
        return FeedVideo(
            bvid = bvid,
            aid = aid,
            title = Formatters.decodeHtmlEntities(title),
            cover = Formatters.normalizeImageUrl(pic),
            upName = owner?.name.orEmpty(),
            upMid = owner?.mid ?: 0L,
            upFace = Formatters.normalizeImageUrl(owner?.face),
            playCount = view,
            likeCount = stat?.like ?: 0L,
            danmaku = danmaku,
            durationSec = duration,
            playCountText = Formatters.formatStat(view),
            danmakuText = Formatters.formatStat(danmaku),
            durationText = Formatters.formatDuration(duration.toLong()),
        )
    }
}
