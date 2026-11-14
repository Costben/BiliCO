package erl.bilico.data.repository

import erl.bilico.data.model.FollowingUser
import erl.bilico.data.model.FollowingsUser
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 稍后再看。接口一次给全量分页，没有游标。 */
class WatchLaterRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
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
                        playText = item.stat?.let { Formatters.formatStat(it.view) }.orEmpty(),
                        danmakuText = item.stat?.takeIf { it.danmaku > 0 }?.let {
                            Formatters.formatStat(it.danmaku)
                        }.orEmpty(),
                        durationText = Formatters.formatDuration(item.duration.toLong()),
                        durationSec = item.duration,
                        progressSec = item.progress,
                        aid = item.aid,
                    )
                }
                items to (pn * PAGE_SIZE < (data?.count ?: 0))
            }
        }

    /** 从稍后再看里移除一条；列表本身也依赖登录 Cookie 与 bili_jct。 */
    suspend fun remove(aid: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val csrf = session.csrf()
            check(csrf.isNotBlank()) { "登录后可用" }
            val response = api.delWatchLater(
                mapOf("aid" to aid.toString(), "csrf" to csrf)
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "移除失败(${response.code})" })
            }
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
    private val session: SessionRepository,
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

    suspend fun unfollow(mid: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(mid > 0L) { "无效的用户" }
            buvid.ensure()
            val csrf = session.csrf()
            check(csrf.isNotBlank()) { "登录后可用" }
            val response = api.modifyRelation(
                mapOf(
                    "fid" to mid.toString(),
                    "act" to "2",
                    "csrf" to csrf,
                    "re_src" to "11",
                )
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "取消关注失败(${response.code})" })
            }
        }
    }

    /** B 站批量关系接口不支持取消关注，这里限并发逐条取消，返回成功取关的 mid 集合。 */
    suspend fun unfollowBatch(mids: List<Long>): Result<Set<Long>> = withContext(Dispatchers.IO) {
        runCatching {
            val targets = mids.filter { it > 0L }.distinct()
            require(targets.isNotEmpty()) { "未选择关注对象" }
            buvid.ensure()
            val csrf = session.csrf()
            check(csrf.isNotBlank()) { "登录后可用" }
            val gate = Semaphore(3)
            var firstError: Throwable? = null
            val succeeded = coroutineScope {
                targets.map { mid ->
                    async {
                        gate.withPermit {
                            runCatching {
                                val response = api.modifyRelation(
                                    mapOf(
                                        "fid" to mid.toString(),
                                        "act" to "2",
                                        "csrf" to csrf,
                                        "re_src" to "11",
                                    )
                                )
                                if (response.code != 0) {
                                    error(response.message.ifBlank { "取消关注失败(${response.code})" })
                                }
                                mid
                            }.onFailure { if (firstError == null) firstError = it }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull().toSet()
            }
            if (succeeded.isEmpty()) {
                throw firstError ?: IllegalStateException("取消关注失败")
            }
            succeeded
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
