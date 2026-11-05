package erl.bilico.data.repository

import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoriteRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
) {
    suspend fun folders(mid: Long): Result<List<FavoriteFolder>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.favFolders(mid)
            if (response.code != 0) error(response.message.ifBlank { "收藏夹加载失败(${response.code})" })
            response.data?.list.orEmpty().mapNotNull { folder ->
                val mediaId = folder.id.takeIf { it > 0L } ?: folder.fid
                if (mediaId <= 0L) return@mapNotNull null
                FavoriteFolder(
                    mediaId = mediaId,
                    title = Formatters.decodeHtmlEntities(folder.title),
                    cover = Formatters.normalizeImageUrl(folder.cover),
                    countText = "${Formatters.formatStat(folder.media_count.toLong())}个内容",
                )
            }
        }
    }

    suspend fun videos(mediaId: Long, page: Int): Result<Pair<List<LibraryVideo>, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val response = api.favResources(
                    mapOf(
                        "media_id" to mediaId.toString(),
                        "pn" to page.coerceAtLeast(1).toString(),
                        "ps" to PAGE_SIZE.toString(),
                        "order" to "mtime",
                        "type" to "0",
                        "tid" to "0",
                        "platform" to "web",
                    )
                )
                if (response.code != 0) error(response.message.ifBlank { "收藏内容加载失败(${response.code})" })
                val list = response.data?.medias.orEmpty().mapNotNull { media ->
                    val bvid = media.bvid.ifBlank { media.bvId }
                    if (bvid.isBlank()) return@mapNotNull null
                    LibraryVideo(
                        bvid = bvid,
                        title = Formatters.decodeHtmlEntities(media.title),
                        cover = Formatters.normalizeImageUrl(media.cover),
                        upName = media.upper?.name.orEmpty(),
                        playText = Formatters.formatStat(media.cntInfo?.play ?: 0L),
                        durationText = Formatters.formatDuration(media.duration.toLong()),
                        durationSec = media.duration,
                    )
                }
                list to (response.data?.has_more == true)
            }
        }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
