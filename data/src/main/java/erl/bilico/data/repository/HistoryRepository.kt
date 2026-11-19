package erl.bilico.data.repository

import erl.bilico.data.model.HistoryBusiness
import erl.bilico.data.model.HistoryData
import erl.bilico.data.model.HistoryItem
import erl.bilico.data.model.HistoryStat
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HistoryPage(
    val items: List<HistoryItem>,
    val max: Long,
    val viewAt: Long,
    val business: String,
    val hasMore: Boolean,
)

data class HistorySearchPage(
    val items: List<HistoryItem>,
    val hasMore: Boolean,
)

class HistoryRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
) {
    suspend fun page(
        max: Long = 0L,
        viewAt: Long = 0L,
        type: String = "archive",
        business: String? = null,
    ): Result<HistoryPage> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val params = buildMap {
                put("ps", "30")
                if (type.isNotBlank()) put("type", type)
                if (max > 0L) put("max", max.toString())
                if (viewAt > 0L) put("view_at", viewAt.toString())
                if (!business.isNullOrBlank()) put("business", business)
            }
            val response = api.history(params)
            if (response.code != 0) {
                error(response.message.ifBlank { "历史记录加载失败(${response.code})" })
            }
            val data = response.data ?: error("历史记录为空")
            val items = data.list.mapNotNull { it.toHistoryItem() }
            val cursor = data.cursor
            HistoryPage(
                items = items,
                max = cursor?.max ?: 0L,
                viewAt = cursor?.viewAt ?: 0L,
                business = cursor?.business.orEmpty(),
                hasMore = cursor != null && cursor.max > 0L && data.list.isNotEmpty(),
            )
        }
    }

    suspend fun search(
        keyword: String,
        page: Int = 1,
        business: String = "all",
    ): Result<HistorySearchPage> = withContext(Dispatchers.IO) {
        runCatching {
            val clean = keyword.trim()
            require(clean.isNotEmpty()) { "请输入搜索关键词" }
            buvid.ensure()
            val response = api.historySearch(
                mapOf(
                    "pn" to page.coerceAtLeast(1).toString(),
                    "ps" to "30",
                    "keyword" to clean,
                    "business" to business,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "搜索历史失败(${response.code})" })
            }
            val data = response.data ?: error("搜索结果为空")
            val items = data.list.mapNotNull { it.toHistoryItem() }
            HistorySearchPage(
                items = items,
                hasMore = data.hasMore && items.isNotEmpty(),
            )
        }
    }

    suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            buvid.ensure()
            val response = api.clearHistory(mapOf("csrf" to csrf))
            if (response.code != 0) {
                error(response.message.ifBlank { "清空失败(${response.code})" })
            }
        }
    }

    suspend fun isPaused(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.historyShadowStatus()
            if (response.code != 0) {
                error(response.message.ifBlank { "获取历史状态失败(${response.code})" })
            }
            response.data
        }
    }

    suspend fun setPaused(paused: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            buvid.ensure()
            val response = api.setHistoryShadow(
                mapOf(
                    "switch" to paused.toString(),
                    "csrf" to csrf,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "设置失败(${response.code})" })
            }
            paused
        }
    }

    /**
     * 单个条目的播放量 / 弹幕数。历史列表接口不带统计，只能按条补；
     * 番剧没有 bvid，改从番剧季信息里取，翻页拿不到就返回 null。
     */
    suspend fun stat(item: HistoryItem): HistoryStat? = withContext(Dispatchers.IO) {
        runCatching {
            if (item.businessType != HistoryBusiness.VIDEO) return@runCatching null
            if (item.isPgc) {
                if (item.seasonId <= 0L) return@runCatching null
                val response = api.pgcSeason(mapOf("season_id" to item.seasonId.toString()))
                if (response.code != 0) return@runCatching null
                val stat = (response.result ?: response.data)?.stat ?: return@runCatching null
                HistoryStat(view = stat.views, danmaku = stat.danmakus)
            } else {
                if (item.bvid.isBlank()) return@runCatching null
                val response = api.archiveStat(item.bvid)
                if (response.code != 0) return@runCatching null
                val stat = response.data ?: return@runCatching null
                HistoryStat(view = stat.view, danmaku = stat.danmaku)
            }
        }.getOrNull()
    }

    private fun HistoryData.toHistoryItem(): HistoryItem? {
        val entry = history ?: return null
        val biz = entry.business.lowercase()
        val coverUrl = listOf(cover, pic, covers.firstOrNull().orEmpty())
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val normalizedCover = Formatters.normalizeImageUrl(coverUrl)
        val cleanTitle = Formatters.decodeHtmlEntities(title)
        return when (biz) {
            "archive" -> {
                if (entry.bvid.isBlank()) return null
                HistoryItem(
                    key = "archive-${entry.bvid}",
                    bvid = entry.bvid,
                    cid = entry.cid,
                    businessType = HistoryBusiness.VIDEO,
                    title = cleanTitle,
                    cover = normalizedCover,
                    upName = authorName,
                    viewAtSec = viewAt,
                    progressSec = if (progress == -1) duration.coerceAtLeast(0) else progress.coerceAtLeast(0),
                    durationSec = duration.coerceAtLeast(0),
                )
            }

            "pgc" -> {
                if (entry.oid <= 0L && entry.epid <= 0L) return null
                HistoryItem(
                    key = "pgc-${entry.oid}-${entry.epid}",
                    bvid = entry.bvid,
                    cid = entry.cid,
                    seasonId = entry.oid,
                    epId = entry.epid,
                    isPgc = true,
                    businessType = HistoryBusiness.VIDEO,
                    badge = badge,
                    title = cleanTitle,
                    cover = normalizedCover,
                    upName = authorName.ifBlank { "番剧" },
                    viewAtSec = viewAt,
                    progressSec = if (progress == -1) duration.coerceAtLeast(0) else progress.coerceAtLeast(0),
                    durationSec = duration.coerceAtLeast(0),
                )
            }

            "live" -> {
                val roomId = entry.oid
                if (roomId <= 0L) return null
                HistoryItem(
                    key = "live-$roomId",
                    businessType = HistoryBusiness.LIVE,
                    targetId = roomId,
                    liveStatus = liveStatus,
                    badge = if (liveStatus == 1) "直播中" else badge.ifBlank { "未开播" },
                    title = cleanTitle,
                    cover = normalizedCover,
                    upName = authorName,
                    viewAtSec = viewAt,
                )
            }

            "article", "article-list" -> {
                val cvId = entry.oid
                if (cvId <= 0L) return null
                HistoryItem(
                    key = "$biz-$cvId",
                    businessType = HistoryBusiness.ARTICLE,
                    targetId = cvId,
                    badge = badge.ifBlank { tagName.ifBlank { "专栏" } },
                    title = cleanTitle,
                    cover = normalizedCover,
                    upName = authorName,
                    viewAtSec = viewAt,
                )
            }

            else -> null
        }
    }
}
