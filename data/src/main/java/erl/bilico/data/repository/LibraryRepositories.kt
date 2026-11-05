package erl.bilico.data.repository

import erl.bilico.data.model.FollowingUser
import erl.bilico.data.model.FollowingsUser
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 稍后再看。接口一次给全量分页，没有游标。 */
class WatchLaterRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
) {
    suspend fun list(page: Int): Result<Pair<List<LibraryVideo>, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val pn = page.coerceAtLeast(1)
                val response = api.watchLater(
                    mapOf("pn" to pn.toString(), "ps" to PAGE_SIZE.toString())
                )
                if (response.code != 0) {
                    error(response.message.ifBlank { "稍后再看加载失败(${response.code})" })
                }
                val data = response.data
                val items = data?.list.orEmpty().mapNotNull { item ->
                    if (item.bvid.isBlank()) return@mapNotNull null
                    LibraryVideo(
                        bvid = item.bvid,
                        title = Formatters.decodeHtmlEntities(item.title),
                        cover = Formatters.normalizeImageUrl(item.pic),
                        upName = item.owner?.name.orEmpty(),
                        durationText = Formatters.formatDuration(item.duration.toLong()),
                        durationSec = item.duration,
                        progressSec = item.progress,
                    )
                }
                items to (pn * PAGE_SIZE < (data?.count ?: 0))
            }
        }

    private companion object {
        const val PAGE_SIZE = 30
    }
}

/** 关注列表。只有登录后能读自己的关注。 */
class RelationRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
) {
    suspend fun followings(mid: Long, page: Int): Result<Pair<List<FollowingUser>, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val pn = page.coerceAtLeast(1)
                val response = api.followings(
                    mapOf(
                        "vmid" to mid.toString(),
                        "pn" to pn.toString(),
                        "ps" to PAGE_SIZE.toString(),
                        "order" to "desc",
                        "order_type" to "attention",
                    )
                )
                if (response.code != 0) {
                    error(response.message.ifBlank { "关注列表加载失败(${response.code})" })
                }
                val data = response.data
                val items = data?.list.orEmpty().mapNotNull { user ->
                    if (user.mid <= 0L) return@mapNotNull null
                    FollowingUser(
                        mid = user.mid,
                        name = user.uname,
                        face = Formatters.normalizeImageUrl(user.face),
                        sign = user.sign,
                    )
                }
                items to (pn * PAGE_SIZE < (data?.total ?: 0))
            }
        }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
