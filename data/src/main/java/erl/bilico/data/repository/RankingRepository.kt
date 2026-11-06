package erl.bilico.data.repository

import erl.bilico.data.model.FeedVideo
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 全站与分区排行榜：一次返回整榜，没有分页。 */
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
            response.data?.list.orEmpty().mapNotNull { item ->
                if (item.bvid.isBlank()) return@mapNotNull null
                val view = item.stat?.view ?: 0L
                val danmaku = item.stat?.danmaku ?: 0L
                FeedVideo(
                    bvid = item.bvid,
                    aid = item.aid,
                    title = Formatters.decodeHtmlEntities(item.title),
                    cover = Formatters.normalizeImageUrl(item.pic),
                    upName = item.owner?.name.orEmpty(),
                    upMid = item.owner?.mid ?: 0L,
                    upFace = Formatters.normalizeImageUrl(item.owner?.face),
                    playCount = view,
                    danmaku = danmaku,
                    durationSec = item.duration,
                    playCountText = Formatters.formatStat(view),
                    danmakuText = Formatters.formatStat(danmaku),
                    durationText = Formatters.formatDuration(item.duration.toLong()),
                )
            }
        }
    }
}
