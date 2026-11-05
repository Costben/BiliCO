package erl.bilico.data.repository

import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.model.SpaceProfile
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SpaceRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    /** 空间头卡：资料走 acc/info，粉丝/关注与获赞分别来自两个统计接口，取不到就留空。 */
    suspend fun profile(mid: Long): Result<SpaceProfile> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val response = api.spaceInfo(
                WbiUtils.sign(
                    mapOf(
                        "mid" to mid.toString(),
                        "platform" to "web",
                        "web_location" to "1550101",
                    ),
                    keys.first,
                    keys.second,
                    includeRiskFingerprint = true,
                )
            )
            if (response.code != 0) error(response.message.ifBlank { "空间加载失败(${response.code})" })
            val data = response.data ?: error("没有拿到用户资料")
            val stat = runCatching { api.relationStat(mid) }.getOrNull()?.data
            val likes = runCatching { api.upStat(mid) }.getOrNull()?.data?.likes
            SpaceProfile(
                mid = data.mid.takeIf { it > 0L } ?: mid,
                name = data.name,
                face = Formatters.normalizeImageUrl(data.face),
                sign = Formatters.decodeHtmlEntities(data.sign),
                followersText = stat?.let { Formatters.formatStat(it.follower) } ?: "-",
                followingText = stat?.let { Formatters.formatStat(it.following) } ?: "-",
                likesText = likes?.let { Formatters.formatStat(it) } ?: "-",
            )
        }
    }

    suspend fun videos(mid: Long, page: Int): Result<Pair<List<LibraryVideo>, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val keys = wbi.getKeys()
                val pn = page.coerceAtLeast(1)
                val response = api.spaceVideos(
                    WbiUtils.sign(
                        mapOf(
                            "mid" to mid.toString(),
                            "pn" to pn.toString(),
                            "ps" to PAGE_SIZE.toString(),
                            "order" to "pubdate",
                            "platform" to "web",
                            "web_location" to "1550101",
                        ),
                        keys.first,
                        keys.second,
                        includeRiskFingerprint = true,
                    )
                )
                if (response.code != 0) error(response.message.ifBlank { "投稿加载失败(${response.code})" })
                val list = response.data?.list?.vlist.orEmpty().mapNotNull { item ->
                    if (item.bvid.isBlank()) return@mapNotNull null
                    val seconds = Formatters.parseDurationText(item.length)
                    LibraryVideo(
                        bvid = item.bvid,
                        title = Formatters.decodeHtmlEntities(item.title),
                        cover = Formatters.normalizeImageUrl(item.pic),
                        upName = "",
                        playText = Formatters.formatStat(item.play),
                        durationText = Formatters.formatDuration(seconds.toLong()),
                        durationSec = seconds,
                    )
                }
                val total = response.data?.page?.count ?: 0
                list to (pn * PAGE_SIZE < total)
            }
        }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
