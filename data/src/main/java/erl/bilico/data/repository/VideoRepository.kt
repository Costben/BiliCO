package erl.bilico.data.repository

import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoPart
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
) {
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
                )
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
            )
        }
    }

    /**
     * 取 DASH 流：先走 WBI 的 playurl，失败再退回不带签名的 MP4 接口。
     * 返回的实际清晰度以视频轨 id 为准，请求清晰度不可用时由 UI 提示。
     */
    suspend fun playback(bvid: String, cid: Long, qn: Int): Result<PlaybackSource> =
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
                    return@runCatching response.data.toPlaybackSource(qn)
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
                data.toPlaybackSource(qn)
            }
        }
}
