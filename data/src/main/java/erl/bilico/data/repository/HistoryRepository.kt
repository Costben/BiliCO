package erl.bilico.data.repository

import erl.bilico.data.model.HistoryItem
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
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

class HistoryRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
) {
    suspend fun page(
        max: Long = 0L,
        viewAt: Long = 0L,
        business: String? = null,
    ): Result<HistoryPage> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val params = buildMap {
                put("ps", "30")
                if (max > 0L) put("max", max.toString())
                if (viewAt > 0L) put("view_at", viewAt.toString())
                if (!business.isNullOrBlank()) put("business", business)
            }
            val response = api.history(params)
            if (response.code != 0) {
                error(response.message.ifBlank { "历史记录加载失败(${response.code})" })
            }
            val data = response.data ?: error("历史记录为空")
            val items = data.list.mapNotNull { entry ->
                val page = entry.history ?: return@mapNotNull null
                val isArchive = page.business.equals("archive", ignoreCase = true)
                val isPgc = page.business.equals("pgc", ignoreCase = true)
                if (!isArchive && !isPgc) return@mapNotNull null
                if (isArchive && page.bvid.isBlank()) return@mapNotNull null
                if (isPgc && page.oid <= 0L && page.epid <= 0L) return@mapNotNull null
                val cover = listOf(entry.cover, entry.pic, entry.covers.firstOrNull().orEmpty())
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                val itemKey = if (isPgc) "pgc-${page.oid}-${page.epid}" else "archive-${page.bvid}"
                HistoryItem(
                    key = itemKey,
                    bvid = page.bvid,
                    cid = page.cid,
                    seasonId = if (isPgc) page.oid else 0L,
                    epId = if (isPgc) page.epid else 0L,
                    isPgc = isPgc,
                    title = Formatters.decodeHtmlEntities(entry.title),
                    cover = Formatters.normalizeImageUrl(cover),
                    upName = entry.authorName.ifBlank { if (isPgc) "番剧" else "" },
                    viewAtSec = entry.viewAt,
                    progressSec = if (entry.progress == -1) entry.duration.coerceAtLeast(0) else entry.progress.coerceAtLeast(0),
                    durationSec = entry.duration.coerceAtLeast(0),
                )
            }
            val cursor = data.cursor
            HistoryPage(
                items = items,
                max = cursor?.max ?: 0L,
                viewAt = cursor?.viewAt ?: 0L,
                business = cursor?.business.orEmpty(),
                hasMore = cursor != null && cursor.max > 0L && items.isNotEmpty(),
            )
        }
    }
}
