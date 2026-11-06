package erl.bilico.data.repository

import erl.bilico.data.model.LiveCandidate
import erl.bilico.data.model.LiveDanmakuServer
import erl.bilico.data.model.LivePlayUrlData
import erl.bilico.data.model.LivePlayback
import erl.bilico.data.model.LiveQuality
import erl.bilico.data.model.LiveRoomDetail
import erl.bilico.data.model.LiveRoomItem
import erl.bilico.data.model.LiveStream
import erl.bilico.data.model.QualityOption
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LiveRoomPage(
    val items: List<LiveRoomItem>,
    val hasMore: Boolean,
)

class LiveRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    suspend fun rooms(page: Int): Result<LiveRoomPage> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val params = mapOf(
                "parent_area_id" to "0",
                "area_id" to "0",
                "page" to page.coerceAtLeast(1).toString(),
                "page_size" to PAGE_SIZE.toString(),
                "sort_type" to "online",
            )
            val response = api.liveRooms(params)
            if (response.code != 0) {
                error(response.message.ifBlank { "直播列表加载失败(${response.code})" })
            }
            val data = response.data ?: error("直播列表为空")
            val items = data.list.mapNotNull { room ->
                if (room.roomId <= 0L) return@mapNotNull null
                LiveRoomItem(
                    roomId = room.roomId,
                    title = room.title,
                    cover = Formatters.normalizeImageUrl(
                        listOf(room.cover, room.userCover, room.systemCover, room.keyframe)
                            .firstOrNull { it.isNotBlank() },
                    ),
                    anchor = room.uname,
                    online = room.online,
                    areaName = room.areaV2Name.ifBlank { room.areaName },
                )
            }
            LiveRoomPage(
                items = items,
                hasMore = items.isNotEmpty() && (data.hasMore == 1 || page * PAGE_SIZE < data.count),
            )
        }
    }

    suspend fun detail(roomId: Long): Result<LiveRoomDetail> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.liveRoomDetail(roomId)
            val room = response.data?.roomInfo
            if (response.code == 0 && room != null) {
                return@runCatching LiveRoomDetail(
                    roomId = room.roomId.takeIf { it > 0L } ?: roomId,
                    title = room.title,
                    cover = Formatters.normalizeImageUrl(
                        listOf(room.cover, room.keyframe).firstOrNull { it.isNotBlank() },
                    ),
                    anchorName = response.data?.anchorInfo?.baseInfo?.uname.orEmpty(),
                    anchorFace = Formatters.normalizeImageUrl(
                        response.data?.anchorInfo?.baseInfo?.face,
                    ),
                    online = room.online,
                    description = room.description,
                    areaName = room.areaName,
                    isLive = room.liveStatus == 1,
                )
            }
            fallbackDetail(roomId)
        }
    }

    /** getInfoByRoom 偶发 -352，用 room/v1 兜底拿标题、开播状态与人气。 */
    private suspend fun fallbackDetail(roomId: Long): LiveRoomDetail {
        val response = api.liveRoomInfo(roomId)
        val data = response.data
            ?: error(response.message.ifBlank { "直播间信息获取失败(${response.code})" })
        return LiveRoomDetail(
            roomId = data.roomId.takeIf { it > 0L } ?: roomId,
            title = data.title,
            cover = Formatters.normalizeImageUrl(
                listOf(data.userCover, data.keyframe, data.cover).firstOrNull { it.isNotBlank() },
            ),
            anchorName = "",
            anchorFace = "",
            online = data.online,
            description = data.description,
            areaName = data.areaName,
            isLive = data.liveStatus == 1,
        )
    }

    /** 弹幕服务器的 token 与地址。房间号要用详情里的真实房号，短号拿不到 token。 */
    suspend fun danmakuServer(roomId: Long): Result<LiveDanmakuServer> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val keys = wbi.getKeys()
                val params = mapOf(
                    "id" to roomId.toString(),
                    "type" to "0",
                    "web_location" to "444.8",
                )
                val response = api.liveDanmakuInfo(
                    WbiUtils.sign(params, keys.first, keys.second),
                )
                if (response.code != 0) {
                    error(response.message.ifBlank { "弹幕服务器获取失败(${response.code})" })
                }
                val data = response.data ?: error("弹幕服务器为空")
                val urls = data.hosts.mapNotNull { host ->
                    // 优先 wss，没有加密端口时才退回明文 ws。
                    val port = when {
                        host.wssPort > 0 -> host.wssPort
                        host.port > 0 -> host.port
                        else -> 0
                    }
                    val scheme = if (host.wssPort > 0) "wss" else "ws"
                    if (host.host.isBlank() || port == 0) null else "$scheme://${host.host}:$port/sub"
                }
                if (data.token.isBlank() || urls.isEmpty()) error("弹幕服务器不可用")
                LiveDanmakuServer(token = data.token, urls = urls)
            }
        }

    suspend fun playback(roomId: Long, qn: Int = DEFAULT_QN): Result<LivePlayback> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val keys = wbi.getKeys()
                val params = mapOf(
                    "room_id" to roomId.toString(),
                    "protocol" to "0,1",
                    "format" to "0,1,2",
                    "codec" to "0,1,2",
                    "qn" to qn.coerceAtLeast(80).toString(),
                    "platform" to "web",
                    "ptype" to "8",
                    "dolby" to "5",
                    "panorama" to "1",
                    "web_location" to "444.8",
                )
                val response = api.livePlayUrl(WbiUtils.sign(params, keys.first, keys.second))
                val data = response.data
                if (response.code != 0 || data == null) {
                    error(response.message.ifBlank { "直播流获取失败(${response.code})" })
                }
                if (data.liveStatus != null && data.liveStatus != 1) {
                    error("未开播")
                }
                data.toPlayback(qn)
            }
        }

    /**
     * 旧项目 LivePlaybackPolicy.resolveLivePlayback 的做法：把接口给的所有线路摊平成一个有序候选表，
     * 播放失败时按顺序换下一条。这里同样先排 HLS（fmp4 优先、avc 优先），再排 FLV。
     */
    private fun LivePlayUrlData.toPlayback(requestedQn: Int): LivePlayback {
        val qualities = qualitiesOf()
        val streams = playurlInfo?.playurl?.stream.orEmpty()
        val ordered = streams.sortedBy { if (it.protocolName == "http_hls") 0 else 1 }
        val candidates = ArrayList<LiveCandidate>()
        for (stream in ordered) {
            val isHls = stream.protocolName == "http_hls"
            val formats = stream.format.filter { it.codec.isNotEmpty() }
                .sortedBy { if (it.formatName == "fmp4") 0 else 1 }
            for (format in formats) {
                val codecs = format.codec.filter { it.baseUrl.isNotBlank() }
                    .sortedBy { if (it.codecName == "avc") 0 else 1 }
                for (codec in codecs) {
                    for (urlInfo in codec.urlInfo) {
                        if (urlInfo.host.isBlank()) continue
                        val url = urlInfo.host + codec.baseUrl + urlInfo.extra
                        if (candidates.none { it.url == url }) {
                            candidates += LiveCandidate(url, isHls)
                        }
                    }
                }
            }
        }
        durl.forEach { part ->
            if (part.url.isNotBlank() && candidates.none { it.url == part.url }) {
                candidates += LiveCandidate(part.url, isHls = false)
            }
        }
        if (candidates.isEmpty()) error("没有可播放的直播流")
        val first = candidates.first()
        val activeQuality = if (first.isHls) {
            streams.firstOrNull { it.protocolName == "http_hls" }?.pickQuality()
        } else {
            null
        } ?: currentQuality.takeIf { it > 0 } ?: requestedQn
        return LivePlayback(
            candidates = candidates,
            qualities = qualities,
            activeQuality = activeQuality,
        )
    }

    private fun LivePlayUrlData.qualitiesOf(): List<QualityOption> {
        val source: List<LiveQuality> =
            playurlInfo?.playurl?.gQnDesc?.takeIf { it.isNotEmpty() } ?: qualityDescription
        // 只保留接口声明可接受的档位，避免列出服务端不会给的画质。
        val accepted = playurlInfo?.playurl?.stream.orEmpty()
            .flatMap { it.format }
            .flatMap { it.codec }
            .flatMap { it.acceptQn }
            .toSet()
        return source
            .filter { it.qn > 0 && it.desc.isNotBlank() }
            .filter { accepted.isEmpty() || it.qn in accepted }
            .distinctBy { it.qn }
            .map { QualityOption(quality = it.qn, label = it.desc) }
    }

    private fun LiveStream.pickQuality(): Int? {
        val formats = format.filter { it.codec.isNotEmpty() }
        val preferred = formats.firstOrNull { it.formatName == "fmp4" } ?: formats.firstOrNull()
        val codec = preferred?.codec?.firstOrNull { it.baseUrl.isNotBlank() }
        return codec?.currentQn?.takeIf { it > 0 }
    }

    private companion object {
        const val DEFAULT_QN = 10000
        const val PAGE_SIZE = 30
    }
}
