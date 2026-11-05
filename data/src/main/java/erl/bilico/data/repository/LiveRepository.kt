package erl.bilico.data.repository

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

    private fun LivePlayUrlData.toPlayback(requestedQn: Int): LivePlayback {
        val qualities = qualitiesOf()
        val streams = playurlInfo?.playurl?.stream.orEmpty()
        val hls = streams.firstOrNull { it.protocolName == "http_hls" }
        val stream = hls ?: streams.firstOrNull { it.protocolName == "http_stream" }
        if (stream != null) {
            val url = stream.pickUrl()
            if (url != null) {
                return LivePlayback(
                    url = url,
                    isHls = stream === hls,
                    qualities = qualities,
                    activeQuality = stream.pickQuality() ?: currentQuality.takeIf { it > 0 } ?: requestedQn,
                )
            }
        }
        val flv = durl.firstOrNull { it.url.isNotBlank() }?.url
            ?: error("没有可播放的直播流")
        return LivePlayback(
            url = flv,
            isHls = false,
            qualities = qualities,
            activeQuality = currentQuality.takeIf { it > 0 } ?: requestedQn,
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

    /** HLS 优先 fmp4，回落 ts；编码优先 AVC，兼容性最好。 */
    private fun LiveStream.pickUrl(): String? {
        val formats = format.filter { it.codec.isNotEmpty() }
        val preferred = formats.firstOrNull { it.formatName == "fmp4" } ?: formats.firstOrNull()
            ?: return null
        val codecs = preferred.codec.filter { it.baseUrl.isNotBlank() }
        val codec = codecs.firstOrNull { it.codecName == "avc" } ?: codecs.firstOrNull() ?: return null
        val urlInfo = codec.urlInfo.firstOrNull { it.host.isNotBlank() } ?: return null
        return urlInfo.host + codec.baseUrl + urlInfo.extra
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
