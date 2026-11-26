package erl.bilico.data.repository

import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.model.VideoCollection
import erl.bilico.data.model.VideoDescV2
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoEpisode
import erl.bilico.data.model.VideoOwner
import erl.bilico.data.model.VideoPart
import erl.bilico.data.model.VideoPreviewShot
import erl.bilico.data.model.VideoStaff
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import erl.bilico.platform.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.request
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class VideoRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
    private val redirectHttp: HttpClient,
) {
    suspend fun resolveShortLink(url: String): String? = withContext(ioDispatcher) {
        val target = url.trim()
        if (!target.startsWith("http", ignoreCase = true)) return@withContext null
        runCatching {
            buvid.ensure()
            val response = redirectHttp.get(target)
            val landed = response.request.url.toString()
            BvidInUrl.find(landed)?.value
        }.getOrNull()
    }

    suspend fun detail(bvid: String): Result<VideoDetail> = withContext(ioDispatcher) {
        runCatching {
            buvid.ensure()
            val (response, tags) = coroutineScope {
                val tags = async {
                    runCatching { api.archiveTags(bvid) }.getOrNull()?.data.orEmpty()
                        .map { it.name.trim() }
                        .filter { it.isNotEmpty() }
                }
                api.videoInfo(bvid) to tags.await()
            }
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
            val (resolvedDesc, descMentions) = parseVideoDesc(
                rawDesc = data.desc,
                descV2 = data.descV2,
                owner = data.owner,
                staff = data.staff,
            )
            VideoDetail(
                bvid = data.bvid,
                aid = data.aid,
                cid = data.cid.takeIf { it > 0 } ?: pages.firstOrNull()?.cid ?: 0L,
                title = data.title,
                desc = resolvedDesc,
                descMentions = descMentions,
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
                tags = tags,
                aspect = data.dimension?.aspectRatio()
                    ?.takeIf { it > 0f }
                    ?: pages.firstOrNull { it.aspect > 0f }?.aspect
                    ?: 0f,
            )
        }
    }

    private fun parseVideoDesc(
        rawDesc: String,
        descV2: List<VideoDescV2>?,
        owner: VideoOwner,
        staff: List<VideoStaff>?,
    ): Pair<String, Map<String, Long>> {
        val mentions = LinkedHashMap<String, Long>()
        descV2.orEmpty().forEach { item ->
            if (item.type == 2 && item.bizId > 0L) {
                val name = item.rawText.trim().removePrefix("@")
                if (name.isNotEmpty()) mentions[name] = item.bizId
            }
        }
        staff.orEmpty().forEach { member ->
            val name = member.name.trim()
            if (member.mid > 0L && name.isNotEmpty() && !mentions.containsKey(name)) {
                mentions[name] = member.mid
            }
        }
        val ownerName = owner.name.trim()
        if (owner.mid > 0L && ownerName.isNotEmpty() && !mentions.containsKey(ownerName)) {
            mentions[ownerName] = owner.mid
        }

        val trimmedRaw = rawDesc.trim()
        val structured = descV2.orEmpty()
        val hasStructuredMentions = structured.any { it.type == 2 && it.rawText.isNotBlank() }
        val rawHasAllMentions = !hasStructuredMentions || structured
            .filter { it.type == 2 && it.rawText.isNotBlank() }
            .all { item ->
                val name = item.rawText.trim().removePrefix("@")
                trimmedRaw.contains("@$name")
            }
        val resolved = if (structured.isNotEmpty() && (trimmedRaw.isEmpty() || trimmedRaw == "-" || !rawHasAllMentions)) {
            buildString {
                structured.forEach { item ->
                    if (item.type == 2) {
                        val name = item.rawText.trim().removePrefix("@")
                        if (name.isNotEmpty()) {
                            append('@')
                            append(name)
                            append(' ')
                        }
                    } else {
                        append(item.rawText)
                    }
                }
            }.trim()
        } else {
            rawDesc
        }
        return resolved to mentions
    }

    suspend fun playback(
        bvid: String,
        cid: Long,
        qn: Int,
        cdn: CdnOption = CdnOption.Auto,
    ): Result<PlaybackSource> = withContext(ioDispatcher) {
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
                "try_look" to "1",
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
                "try_look" to "1",
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

    suspend fun castUrl(
        bvid: String,
        cid: Long,
        qn: Int = 80,
        cdn: CdnOption = CdnOption.Auto,
    ): Result<String> = withContext(ioDispatcher) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val params = mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to qn.coerceAtLeast(16).toString(),
                "fnval" to "1",
                "fnver" to "0",
                "fourk" to "1",
                "platform" to "pc",
            )
            val signed = WbiUtils.sign(params, keys.first, keys.second)
            val data = runCatching { api.playUrl(signed) }.getOrNull()
                ?.takeIf { it.code == 0 }?.data
                ?: api.playUrlLegacy(
                    mapOf(
                        "bvid" to bvid,
                        "cid" to cid.toString(),
                        "qn" to qn.coerceAtLeast(16).toString(),
                        "fnval" to "1",
                        "fnver" to "0",
                        "platform" to "html5",
                        "high_quality" to "1",
                    ),
                ).data
                ?: error("获取投屏流地址失败")
            val segment = data.durl?.firstOrNull() ?: error("未拿到可投屏的视频流")
            PlaybackCdn.candidates(listOf(segment.url) + segment.backupUrl.orEmpty(), cdn)
                .firstOrNull()
                ?: segment.url
        }
    }

    suspend fun videoshot(bvid: String, cid: Long): VideoPreviewShot? = withContext(ioDispatcher) {
        if (bvid.isBlank() || cid <= 0L) return@withContext null
        runCatching {
            buvid.ensure()
            val response = api.videoshot(bvid = bvid, cid = cid, index = 1)
            val data = response.data ?: return@runCatching null
            if (response.code != 0) return@runCatching null
            val urls = data.image.orEmpty()
                .map { Formatters.normalizeImageUrl(it) }
                .filter { it.isNotBlank() }
            if (urls.isEmpty()) return@runCatching null
            VideoPreviewShot(
                images = urls,
                index = data.index.orEmpty(),
                imgXLen = data.imgXLen.coerceAtLeast(1),
                imgYLen = data.imgYLen.coerceAtLeast(1),
                imgXSize = data.imgXSize.coerceAtLeast(1),
                imgYSize = data.imgYSize.coerceAtLeast(1),
            )
        }.getOrNull()
    }

    suspend fun related(bvid: String): Result<List<FeedVideo>> = withContext(ioDispatcher) {
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
                        likeCount = item.stat.like,
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
