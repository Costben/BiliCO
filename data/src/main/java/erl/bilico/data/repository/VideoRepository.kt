package erl.bilico.data.repository

import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.VideoCollection
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoEpisode
import erl.bilico.data.model.VideoPart
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.session.SessionRepository
import okhttp3.OkHttpClient
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

class VideoRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
    private val okHttp: OkHttpClient,
) {
    /**
     * 短链点开后先跟着跳转走，从最终地址里取出 BV 号。
     * 取不到就返回 null，调用方再交给浏览器。
     */
    suspend fun resolveShortLink(url: String): String? = withContext(Dispatchers.IO) {
        val target = url.trim()
        if (!target.startsWith("http", ignoreCase = true)) return@withContext null
        runCatching {
            buvid.ensure()
            val client = okHttp.newBuilder().followRedirects(true).build()
            val request = Request.Builder().url(target).get().build()
            client.newCall(request).execute().use { response ->
                val landed = response.request.url.toString()
                BvidInUrl.find(landed)?.value
            }
        }.getOrNull()
    }

    suspend fun detail(bvid: String): Result<VideoDetail> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.videoInfo(bvid)
            val data = response.data
                ?: error(response.message.ifBlank { "视频信息为空(${response.code})" })
            val pages = data.pages.map { page ->
                VideoPart(
                    cid = page.cid,
                    index = page.page,
                    title = page.part,
                    durationSec = page.duration,
                    aspect = page.dimension?.aspectRatio() ?: 0f,
                )
            }
            val collection = data.ugcSeason?.let { season ->
                val episodes = season.sections
                    .flatMap { it.episodes }
                    .mapNotNull { episode ->
                        val arc = episode.arc
                        val episodeBvid = episode.bvid.ifBlank { arc.bvid }
                        if (episodeBvid.isBlank()) return@mapNotNull null
                        VideoEpisode(
                            bvid = episodeBvid,
                            cid = episode.cid,
                            title = episode.title.ifBlank { arc.title },
                            durationSec = arc.duration,
                        )
                    }
                season.id.takeIf { episodes.isNotEmpty() }?.let {
                    VideoCollection(id = it, title = season.title, episodes = episodes)
                }
            }
            VideoDetail(
                bvid = data.bvid,
                aid = data.aid,
                cid = data.cid.takeIf { it > 0 } ?: pages.firstOrNull()?.cid ?: 0L,
                title = data.title,
                desc = data.desc,
                cover = data.pic,
                pubdate = data.pubdate,
                durationSec = data.duration,
                upMid = data.owner.mid,
                upName = data.owner.name,
                upFace = data.owner.face,
                view = data.stat.view,
                danmaku = data.stat.danmaku,
                reply = data.stat.reply,
                like = data.stat.like,
                coin = data.stat.coin,
                favorite = data.stat.favorite,
                pages = pages,
                collection = collection,
                aspect = data.dimension?.aspectRatio()
                    ?.takeIf { it > 0f }
                    ?: pages.firstOrNull { it.aspect > 0f }?.aspect
                    ?: 0f,
            )
        }
    }

    /**
     * 取 DASH 流：先走 WBI 的 playurl，失败再退回不带签名的 MP4 接口。
     * 返回的实际清晰度以视频轨 id 为准，请求清晰度不可用时由 UI 提示。
     */
    suspend fun playback(
        bvid: String,
        cid: Long,
        qn: Int,
        cdn: CdnOption = CdnOption.Auto,
    ): Result<PlaybackSource> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val keys = wbi.getKeys()
                val params = mapOf(
                    "bvid" to bvid,
                    "cid" to cid.toString(),
                    "qn" to qn.coerceAtLeast(16).toString(),
                    "fnval" to "4048",
                    "fnver" to "0",
                    "fourk" to "1",
                    "voice_balance" to "1",
                    "gaia_source" to "pre-load",
                    "isGaiaAvoided" to "true",
                    "web_location" to "1315873",
                )
                val signed = WbiUtils.sign(params, keys.first, keys.second)
                val response = runCatching { api.playUrl(signed) }.getOrNull()
                if (response != null && response.code == 0 && response.data != null) {
                    return@runCatching response.data.toPlaybackSource(qn, cdn, vipAccount = session.isVip())
                }

                val legacyParams = mapOf(
                    "bvid" to bvid,
                    "cid" to cid.toString(),
                    "qn" to qn.coerceAtLeast(16).toString(),
                    "fnval" to "16",
                    "fnver" to "0",
                    "fourk" to "1",
                    "platform" to "html5",
                    "high_quality" to "1",
                )
                val legacy = api.playUrlLegacy(legacyParams)
                val data = legacy.data
                if (legacy.code != 0 || data == null) {
                    error(legacy.message.ifBlank { "播放地址获取失败(${legacy.code})" })
                }
                data.toPlaybackSource(qn, cdn, vipAccount = session.isVip())
            }
        }

    /** 简介下面那一栏「其他推荐视频」：接口按相关度给一串，字段和推荐流卡片对得上。 */
    suspend fun related(bvid: String): Result<List<FeedVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.related(bvid)
            if (response.code != 0) {
                error(response.message.ifBlank { "相关推荐加载失败(${response.code})" })
            }
            response.data.orEmpty()
                .filter { it.bvid.isNotBlank() && it.title.isNotBlank() }
                .map { item ->
                    val seconds = item.duration.toLong().coerceAtLeast(0L)
                    FeedVideo(
                        bvid = item.bvid,
                        aid = item.aid,
                        title = item.title,
                        cover = Formatters.normalizeImageUrl(item.pic),
                        upName = item.owner.name,
                        upMid = item.owner.mid,
                        upFace = Formatters.normalizeImageUrl(item.owner.face),
                        playCount = item.stat.view,
                        danmaku = item.stat.danmaku,
                        durationSec = item.duration,
                        playCountText = Formatters.formatStat(item.stat.view),
                        danmakuText = Formatters.formatStat(item.stat.danmaku),
                        durationText = Formatters.formatDuration(seconds),
                    )
                }
        }
    }

    private companion object {
        val BvidInUrl = Regex("BV[0-9A-Za-z]{10}")
    }
}
