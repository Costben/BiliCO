package erl.bilico.data.repository

import erl.bilico.data.model.VideoRelationData
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 视频页互动：点赞、投币、收藏、稍后再看、关注 UP。
 * 全部依赖登录 Cookie 与 bili_jct，未登录时由界面直接拦下，不走到这里。
 */
class InteractionRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
) {
    suspend fun relation(aid: Long): Result<VideoRelationData> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.videoRelation(aid)
            if (response.code != 0) error(response.message.ifBlank { "互动状态获取失败(${response.code})" })
            response.data ?: VideoRelationData()
        }
    }

    suspend fun like(aid: Long, liked: Boolean): Result<Unit> = post {
        api.likeVideo(
            mapOf(
                "aid" to aid.toString(),
                "like" to if (liked) "1" else "2",
                "csrf" to csrf(),
            )
        )
    }

    /** 点踩走同一个接口，3 点踩、4 取消点踩；点赞与点踩互斥由服务端处理。 */
    suspend fun dislike(aid: Long, disliked: Boolean): Result<Unit> = post {
        api.likeVideo(
            mapOf(
                "aid" to aid.toString(),
                "like" to if (disliked) "3" else "4",
                "csrf" to csrf(),
            )
        )
    }

    /** UP 主粉丝数；拿不到就返回 null，界面上直接不显示这一段。 */
    suspend fun upFollowers(mid: Long): Long? = withContext(Dispatchers.IO) {
        if (mid <= 0L) return@withContext null
        runCatching {
            buvid.ensure()
            api.relationStat(mid).data?.follower
        }.getOrNull()
    }

    suspend fun coin(aid: Long, count: Int): Result<Unit> = post {
        api.coinVideo(
            mapOf(
                "aid" to aid.toString(),
                "multiply" to count.coerceIn(1, 2).toString(),
                "select_like" to "0",
                "csrf" to csrf(),
            )
        )
    }

    suspend fun favorite(aid: Long, mediaId: Long): Result<Unit> = post {
        api.favoriteResource(
            mapOf(
                "rid" to aid.toString(),
                "type" to "2",
                "add_media_ids" to mediaId.toString(),
                "del_media_ids" to "",
                "csrf" to csrf(),
            )
        )
    }

    suspend fun addToWatchLater(aid: Long): Result<Unit> = post {
        api.addWatchLater(
            mapOf(
                "aid" to aid.toString(),
                "csrf" to csrf(),
            )
        )
    }

    suspend fun follow(mid: Long, following: Boolean): Result<Unit> = post {
        api.modifyRelation(
            mapOf(
                "fid" to mid.toString(),
                "act" to if (following) "1" else "2",
                "csrf" to csrf(),
                "re_src" to "11",
            )
        )
    }

    suspend fun isFollowing(mid: Long): Boolean = withContext(Dispatchers.IO) {
        if (mid <= 0L) return@withContext false
        runCatching {
            buvid.ensure()
            api.userRelation(mid).data?.attribute?.let { it == 2 || it == 6 } ?: false
        }.getOrDefault(false)
    }

    private suspend fun post(block: suspend () -> erl.bilico.data.model.BaseResponse): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                require(csrf().isNotBlank()) { "登录后可用" }
                val response = block()
                if (response.code != 0) {
                    error(response.message.ifBlank { "操作失败(${response.code})" })
                }
            }
        }

    private fun csrf(): String = session.csrf()
}
