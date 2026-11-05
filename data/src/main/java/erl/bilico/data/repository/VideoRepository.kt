package erl.bilico.data.repository

import erl.bilico.data.model.DashTrack
import erl.bilico.data.model.PlayUrlData
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.QualityOption
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
                    return@runCatching response.data.toSource(qn)
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
                data.toSource(qn)
            }
        }

    private fun PlayUrlData.toSource(targetQn: Int): PlaybackSource {
        val dashVideoIds = dash?.video.orEmpty()
            .filter { it.baseUrl.isNotBlank() }
            .map { it.id }
            .toSet()
        val qualities = buildList {
            supportFormats.orEmpty().forEach { format ->
                // 未登录时接口仍会列出全部清晰度，只保留真的拿到流的那几档
                if (dashVideoIds.isNotEmpty() && format.quality !in dashVideoIds) return@forEach
                add(
                    QualityOption(
                        quality = format.quality,
                        label = format.newDescription.ifBlank { format.description },
                        needLogin = format.needLogin,
                        needVip = format.needVip,
                    ),
                )
            }
            if (isEmpty()) {
                acceptQuality.forEachIndexed { index, quality ->
                    if (dashVideoIds.isNotEmpty() && quality !in dashVideoIds) return@forEachIndexed
                    add(QualityOption(quality, acceptDescription.getOrNull(index).orEmpty()))
                }
            }
        }

        val dashData = dash
        if (dashData != null) {
            val video = pickVideo(dashData.video, targetQn) ?: error("没有可播放的视频流")
            val audio = pickAudio(dashData.audio.orEmpty())
            return PlaybackSource(
                videoUrl = video.baseUrl,
                videoBackupUrls = video.backupUrl.orEmpty().filter { it.isNotBlank() },
                audioUrl = audio?.baseUrl,
                audioBackupUrls = audio?.backupUrl.orEmpty().filter { it.isNotBlank() },
                quality = video.id,
                qualities = qualities,
                durationMs = timelength,
            )
        }

        val segment = durl?.firstOrNull() ?: error("播放地址为空")
        return PlaybackSource(
            videoUrl = segment.url,
            videoBackupUrls = segment.backupUrl.orEmpty().filter { it.isNotBlank() },
            audioUrl = null,
            audioBackupUrls = emptyList(),
            quality = quality,
            qualities = qualities,
            durationMs = segment.length,
        )
    }

    private fun pickVideo(videos: List<DashTrack>, targetQn: Int): DashTrack? {
        val playable = videos.filter { it.baseUrl.isNotBlank() }
        if (playable.isEmpty()) return null
        val within = playable.filter { it.id <= targetQn }
        val pool = within.ifEmpty { playable }
        // 同一档位优先 AVC，兼容性最好；没有 AVC 时再用 HEVC/AV1。
        return pool.sortedWith(
            compareByDescending<DashTrack> { it.id }
                .thenBy { if (it.codecs.startsWith("avc")) 0 else 1 },
        ).firstOrNull()
    }

    private fun pickAudio(audios: List<DashTrack>): DashTrack? {
        val playable = audios.filter { it.baseUrl.isNotBlank() }
        if (playable.isEmpty()) return null
        val standard = playable.filter { it.id in AUDIO_TRACK_IDS }
        return standard.maxByOrNull { it.id } ?: playable.maxByOrNull { it.bandwidth }
    }

    private companion object {
        val AUDIO_TRACK_IDS = setOf(30216, 30232, 30280)
    }
}
