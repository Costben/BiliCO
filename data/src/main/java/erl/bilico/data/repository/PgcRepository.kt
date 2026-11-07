package erl.bilico.data.repository

import erl.bilico.data.model.BangumiCard
import erl.bilico.data.model.BangumiDetail
import erl.bilico.data.model.BangumiEpisode
import erl.bilico.data.model.BangumiSeasonRef
import erl.bilico.data.model.PlayUrlData
import erl.bilico.data.model.PlaybackSource
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 番剧/影视：详情、取流、排行榜、追番列表。
 * 详情与榜单接口不需要签名；取流走 WBI，失败再回落旧端点。
 */
class PgcRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
    private val session: erl.bilico.data.session.SessionRepository,
) {
    suspend fun detail(seasonId: Long, epId: Long = 0L): Result<BangumiDetail> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val params = if (epId > 0L) {
                    mapOf("ep_id" to epId.toString())
                } else {
                    mapOf("season_id" to seasonId.toString())
                }
                val response = api.pgcSeason(params)
                if (response.code != 0) {
                    error(response.message.ifBlank { "番剧详情加载失败(${response.code})" })
                }
                val season = response.result ?: response.data ?: error("番剧详情为空")
                val episodes = season.episodes.orEmpty().map { episode ->
                    BangumiEpisode(
                        epId = episode.id,
                        aid = episode.aid,
                        bvid = episode.bvid,
                        cid = episode.cid,
                        title = episode.title.ifBlank { episode.longTitle },
                        cover = Formatters.normalizeImageUrl(episode.cover),
                        durationSec = episode.duration / 1000,
                        badge = episode.badge,
                    )
                }
                val seasons = season.seasons.orEmpty()
                    .filter { it.seasonId > 0L }
                    .map { ref ->
                        BangumiSeasonRef(
                            seasonId = ref.seasonId,
                            title = ref.seasonTitle.ifBlank { ref.title },
                        )
                    }
                val meta = buildList {
                    season.seasonTypeName.takeIf { it.isNotBlank() }?.let(::add)
                    season.areas.orEmpty().map { it.name }.filter { it.isNotBlank() }
                        .joinToString(" ") { it }
                        .takeIf { it.isNotBlank() }?.let(::add)
                    season.styles.orEmpty().take(3).joinToString("/")
                        .takeIf { it.isNotBlank() }?.let(::add)
                    if (season.total > 0) add("全 ${season.total} 话")
                }.joinToString(" · ")
                val rating = season.rating
                BangumiDetail(
                    seasonId = season.seasonId.takeIf { it > 0L } ?: seasonId,
                    title = season.title,
                    cover = Formatters.normalizeImageUrl(season.cover),
                    evaluate = season.evaluate.trim(),
                    score = rating?.score?.takeIf { it > 0f }?.let { "%.1f".format(it) }.orEmpty(),
                    rankText = (rating?.count ?: 0).takeIf { it > 0 }
                        ?.let { "${Formatters.formatStat(it.toLong())}人评分" }
                        .orEmpty(),
                    viewsText = Formatters.formatStat(season.stat?.views ?: 0L),
                    followText = Formatters.formatStat(season.stat?.favorites ?: 0L),
                    seasonTypeName = season.seasonTypeName,
                    metaText = meta,
                    episodes = episodes,
                    seasons = seasons,
                    followed = (season.userStatus?.follow ?: 0) == 1,
                    lastEpId = season.userStatus?.progress?.lastEpId ?: 0L,
                    lastTimeSec = (season.userStatus?.progress?.lastTime ?: 0L).coerceAtLeast(0L),
                )
            }
        }

    suspend fun setFollow(seasonId: Long, follow: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val csrf = session.csrf()
                require(csrf.isNotBlank()) { "登录后可用" }
                val fields = mapOf("season_id" to seasonId.toString(), "csrf" to csrf)
                val response = if (follow) api.followBangumi(fields) else api.unfollowBangumi(fields)
                if (response.code != 0) {
                    error(response.message.ifBlank { "追番操作失败(${response.code})" })
                }
            }
        }

    /**
     * PGC 取流。未登录时接口只给试看或低清晰度，错误码需要翻成人话给界面。
     */
    suspend fun playback(
        epId: Long,
        cid: Long,
        qn: Int,
        aid: Long,
        seasonId: Long,
        bvid: String,
        cdn: CdnOption = CdnOption.Auto,
    ): Result<PlaybackSource> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val keys = runCatching { wbi.getKeys() }.getOrNull()
            val params = buildMap {
                if (epId > 0L) put("ep_id", epId.toString())
                if (cid > 0L) put("cid", cid.toString())
                put("qn", qn.coerceAtLeast(16).toString())
                put("fnval", "12240")
                put("fnver", "0")
                put("fourk", "1")
                put("voice_balance", "1")
                put("gaia_source", "pre-load")
                put("isGaiaAvoided", "true")
                put("web_location", "1315873")
                if (seasonId > 0L) put("season_id", seasonId.toString())
                put("try_look", "1")
                if (aid > 0L) put("avid", aid.toString())
                if (bvid.isNotBlank()) put("bvid", bvid)
            }
            val signed = if (keys != null) {
                WbiUtils.sign(params, keys.first, keys.second)
            } else {
                params
            }
            val body = runCatching { api.pgcPlayUrl(signed).string() }.getOrNull()
            if (body != null) {
                val data = runCatching { parsePlayUrl(body) }.getOrNull()
                if (data != null) return@runCatching data.toPlaybackSource(qn, cdn)
            }
            val legacy = api.pgcPlayUrlLegacy(signed).string()
            parsePlayUrl(legacy).toPlaybackSource(qn, cdn)
        }
    }

    suspend fun rank(seasonType: Int = 1): Result<List<BangumiCard>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val params = mapOf("day" to "3", "season_type" to seasonType.toString())
            val signed = WbiUtils.sign(params, keys.first, keys.second)
            val response = if (seasonType == 1) {
                api.pgcRank(signed)
            } else {
                api.pgcSeasonRank(signed)
            }
            if (response.code != 0) {
                error(response.message.ifBlank { "番剧排行加载失败(${response.code})" })
            }
            val list = response.result?.list ?: response.data?.list.orEmpty()
            list.mapNotNull { item ->
                if (item.seasonId <= 0L) return@mapNotNull null
                BangumiCard(
                    seasonId = item.seasonId,
                    title = item.title,
                    cover = Formatters.normalizeImageUrl(item.cover),
                    subtitle = item.newEp?.indexShow.orEmpty(),
                    rating = item.rating,
                    badge = item.badge,
                )
            }
        }
    }

    suspend fun follows(mid: Long, page: Int): Result<Pair<List<BangumiCard>, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val pn = page.coerceAtLeast(1)
                val response = api.bangumiFollow(
                    mapOf(
                        "vmid" to mid.toString(),
                        "type" to "1",
                        "follow_status" to "0",
                        "pn" to pn.toString(),
                        "ps" to PAGE_SIZE.toString(),
                    )
                )
                if (response.code != 0) {
                    error(response.message.ifBlank { "追番列表加载失败(${response.code})" })
                }
                val data = response.data
                val items = data?.list.orEmpty().mapNotNull { item ->
                    if (item.seasonId <= 0L) return@mapNotNull null
                    BangumiCard(
                        seasonId = item.seasonId,
                        title = item.title,
                        cover = Formatters.normalizeImageUrl(item.cover),
                        subtitle = item.newEp?.indexShow.orEmpty().ifBlank { item.progress },
                        rating = item.total.takeIf { it > 0 }?.let { "全 $it 话" }.orEmpty(),
                        badge = item.badge,
                    )
                }
                items to (pn * PAGE_SIZE < (data?.total ?: 0))
            }
        }

    private fun parsePlayUrl(body: String): PlayUrlData {
        val root = BiliClient.json.parseToJsonElement(body).jsonObject
        val code = root["code"]?.jsonPrimitive?.intOrNull ?: -1
        if (code != 0) {
            val message = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            error(playUrlError(code, message))
        }
        val result = (root["result"] as? JsonObject)
            ?: (root["data"] as? JsonObject)
            ?: error("番剧播放数据为空")
        val info = (result["video_info"] as? JsonObject) ?: result
        return BiliClient.json.decodeFromString(PlayUrlData.serializer(), info.toString())
    }

    private fun playUrlError(code: Int, message: String): String = when (code) {
        -10403 -> "该集需要大会员"
        -101 -> "请先登录后观看"
        -403 -> "没有观看权限"
        else -> message.ifBlank { "播放地址获取失败($code)" }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
