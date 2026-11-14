package erl.bilico.data.repository

import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoriteRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
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
        fetch(mediaId, page, PAGE_SIZE)

    /** 收藏夹列表左边的四宫格：只取前几个视频的封面。 */
    suspend fun covers(mediaId: Long, limit: Int = COVER_LIMIT): Result<List<String>> =
        fetch(mediaId, 1, limit).map { (list, _) ->
            list.mapNotNull { it.cover.takeIf(String::isNotBlank) }.take(limit)
        }

    private suspend fun fetch(
        mediaId: Long,
        page: Int,
        pageSize: Int,
    ): Result<Pair<List<LibraryVideo>, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val response = api.favResources(
                    mapOf(
                        "media_id" to mediaId.toString(),
                        "pn" to page.coerceAtLeast(1).toString(),
                        "ps" to pageSize.toString(),
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
                        aid = media.id,
                        favType = media.type.takeIf { it > 0 } ?: 2,
                    )
                }
                list to (response.data?.has_more == true)
            }
        }

    /** 从指定收藏夹移除一条或多条资源；走 B 站批量移除接口。 */
    suspend fun removeVideos(mediaId: Long, videos: List<LibraryVideo>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(mediaId > 0L) { "无效的收藏夹" }
                val valid = videos.filter { it.aid > 0L }
                require(valid.isNotEmpty()) { "缺少可删除的视频标识" }
                buvid.ensure()
                val csrf = session.csrf()
                check(csrf.isNotBlank()) { "登录后可用" }
                for (chunk in valid.chunked(PAGE_SIZE)) {
                    val resources = chunk.joinToString(",") { "${it.aid}:${it.favType}" }
                    val response = api.batchDelFavorite(
                        mapOf(
                            "media_id" to mediaId.toString(),
                            "resources" to resources,
                            "platform" to "web",
                            "csrf" to csrf,
                        )
                    )
                    if (response.code != 0) {
                        error(response.message.ifBlank { "删除收藏失败(${response.code})" })
                    }
                }
            }
        }

    private companion object {
        const val PAGE_SIZE = 20
        const val COVER_LIMIT = 4
    }
}
