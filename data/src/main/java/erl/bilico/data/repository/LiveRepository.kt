package erl.bilico.data.repository

import erl.bilico.data.live.toLiveSuperChat
import erl.bilico.data.model.LiveCandidate
import erl.bilico.data.model.LiveCodecNode
import erl.bilico.data.model.LiveDanmakuServer
import erl.bilico.data.model.LiveFormatNode
import erl.bilico.data.model.LivePlayUrlData
import erl.bilico.data.model.LivePlayback
import erl.bilico.data.model.LiveQuality
import erl.bilico.data.model.LiveRoomDetail
import erl.bilico.data.model.LiveRoomItem
import erl.bilico.data.model.LiveStreamNode
import erl.bilico.data.model.LiveStreamSelection
import erl.bilico.data.model.LiveSuperChat
import erl.bilico.data.model.LiveUrlHostNode
import erl.bilico.data.model.QualityOption
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class LiveRoomPage(
    val items: List<LiveRoomItem>,
    val hasMore: Boolean,
)

class LiveRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    private val onlineRankCache = ConcurrentHashMap<Long, Long>()
    private val onlineRankSemaphore = Semaphore(4)

    fun cachedOnlineRankCount(roomId: Long): Long? = onlineRankCache[roomId]

    fun cacheOnlineRankCount(roomId: Long, count: Long) {
        if (roomId > 0L && count >= 0L) {
            onlineRankCache[roomId] = count
        }
    }

    /** 发送直播间弹幕。 */
    suspend fun sendDanmaku(
        roomId: Long,
        message: String,
        csrf: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(roomId > 0L) { "还没有加载到直播间信息" }
            require(csrf.isNotBlank()) { "登录后才能发弹幕" }
            val text = message.trim()
            require(text.isNotEmpty()) { "先写点什么" }
            buvid.ensure()
            val response = api.sendLiveDanmaku(
                mapOf(
                    "bubble" to "0",
                    "msg" to text,
                    "color" to "16777215",
                    "mode" to "1",
                    "room_type" to "0",
                    "fontsize" to "25",
                    "rnd" to (System.currentTimeMillis() / 1000L).toString(),
                    "roomid" to roomId.toString(),
                    "csrf" to csrf,
                    "csrf_token" to csrf,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "发送弹幕失败（${response.code}）" })
            }
        }
    }

    /**
     * PiliPlus `Api.liveContributionRank` 对应的高能观众数：
     * 用 `type=online_rank` 拉第 1 页第 1 条即可拿到 `data.count`。
     */
    suspend fun onlineRankCount(
        roomId: Long,
        ruid: Long,
        forceRefresh: Boolean = false,
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (roomId <= 0L || ruid <= 0L) {
            return@withContext Result.failure(IllegalArgumentException("缺少直播间或主播 ID"))
        }
        if (!forceRefresh) {
            onlineRankCache[roomId]?.let { return@withContext Result.success(it) }
        }
        runCatching {
            onlineRankSemaphore.withPermit {
                if (!forceRefresh) {
                    onlineRankCache[roomId]?.let { return@withPermit it }
                }
                buvid.ensure()
                val keys = wbi.getKeys()
                val params = mapOf(
                    "ruid" to ruid.toString(),
                    "room_id" to roomId.toString(),
                    "page" to "1",
                    "page_size" to "1",
                    "type" to "online_rank",
                    "switch" to "contribution_rank",
                    "platform" to "web",
                    "web_location" to "444.8",
                )
                val response = api.liveContributionRank(WbiUtils.sign(params, keys.first, keys.second))
                if (response.code != 0) {
                    error(response.message.ifBlank { "高能观众获取失败(${response.code})" })
                }
                val count = response.data?.count ?: error("高能观众为空")
                onlineRankCache[roomId] = count
                count
            }
        }
    }
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
                    uid = room.uid,
                    title = room.title,
                    cover = Formatters.normalizeImageUrl(
                        listOf(room.cover, room.userCover, room.systemCover, room.keyframe)
                            .firstOrNull { it.isNotBlank() },
                    ),
                    anchor = room.uname,
                    online = room.online,
                    areaName = room.areaV2Name.ifBlank { room.areaName },
                    onlineCount = onlineRankCache[room.roomId],
                )
            }
            LiveRoomPage(
                items = items,
                hasMore = items.isNotEmpty() && (data.hasMore == 1 || page * PAGE_SIZE < data.count),
            )
        }
    }

    /**
     * 关注的主播列表：接口会把所有已关注主播都返回，只有 `live_status == 1` 的是在播的。
     * 动态页的「直播」筛选只要在播的那批。
     */
    suspend fun following(page: Int): Result<LiveRoomPage> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val requestPage = page.coerceAtLeast(1)
            val response = api.liveFollowing(
                mapOf(
                    "page" to requestPage.toString(),
                    "page_size" to FOLLOW_PAGE_SIZE.toString(),
                    "ignoreRecord" to "1",
                    "hit_ab" to "true",
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "关注直播加载失败(${response.code})" })
            }
            val data = response.data ?: error("关注直播为空")
            val items = data.list
                .filter { it.liveStatus == 1 && it.roomId > 0L }
                .map { room ->
                    LiveRoomItem(
                        roomId = room.roomId,
                        uid = room.uid,
                        title = room.title.ifBlank { "未命名直播间" },
                        cover = Formatters.normalizeImageUrl(room.roomCover),
                        anchor = room.uname,
                        online = 0L,
                        areaName = room.areaName,
                        onlineCount = onlineRankCache[room.roomId],
                    )
                }
            val pageCount = data.pageCount
            LiveRoomPage(
                items = items,
                hasMore = data.list.isNotEmpty() && (pageCount <= 0 || requestPage < pageCount),
            )
        }
    }

    suspend fun detail(roomId: Long): Result<LiveRoomDetail> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.liveRoomDetail(roomId)
            val room = response.data?.roomInfo
            if (response.code == 0 && room != null) {
                val resolvedId = room.roomId.takeIf { it > 0L } ?: roomId
                return@runCatching LiveRoomDetail(
                    roomId = resolvedId,
                    uid = room.uid,
                    title = room.title,
                    cover = Formatters.normalizeImageUrl(
                        listOf(room.cover, room.keyframe).firstOrNull { it.isNotBlank() },
                    ),
                    anchorName = response.data?.anchorInfo?.baseInfo?.uname.orEmpty(),
                    anchorFace = Formatters.normalizeImageUrl(
                        response.data?.anchorInfo?.baseInfo?.face,
                    ),
                    anchorOfficialType = response.data?.anchorInfo?.officialInfo?.resolvedType() ?: -1,
                    anchorOfficialDesc = response.data?.anchorInfo?.officialInfo?.resolvedDesc().orEmpty(),
                    online = room.online,
                    description = room.description,
                    areaName = room.areaName,
                    isLive = room.liveStatus == 1,
                    onlineCount = onlineRankCache[resolvedId],
                )
            }
            fallbackDetail(roomId)
        }
    }

    /** getInfoByRoom 偶发 -352，用 room/v1 兜底拿标题、开播状态与主播 uid。 */
    private suspend fun fallbackDetail(roomId: Long): LiveRoomDetail {
        val response = api.liveRoomInfo(roomId)
        val data = response.data
            ?: error(response.message.ifBlank { "直播间信息获取失败(${response.code})" })
        val resolvedId = data.roomId.takeIf { it > 0L } ?: roomId
        return LiveRoomDetail(
            roomId = resolvedId,
            uid = data.uid,
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
            onlineCount = onlineRankCache[resolvedId],
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

    suspend fun superChats(roomId: Long): Result<List<LiveSuperChat>> = withContext(Dispatchers.IO) {
        runCatching {
            if (roomId <= 0L) return@runCatching emptyList()
            buvid.ensure()
            val response = api.liveSuperChats(roomId)
            if (response.code != 0) {
                error(response.message.ifBlank { "醒目留言加载失败(${response.code})" })
            }
            val list = response.data?.get("list") as? JsonArray ?: return@runCatching emptyList()
            val nowSec = System.currentTimeMillis() / 1000L
            list.mapNotNull { (it as? JsonObject)?.toLiveSuperChat() }
                .filter { it.endTimeSec > nowSec }
        }
    }

    suspend fun playback(
        roomId: Long,
        qn: Int = DEFAULT_QN,
        onlyAudio: Boolean = false,
        preferredLine: List<String>? = null,
    ): Result<LivePlayback> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val keys = wbi.getKeys()
                val params = linkedMapOf(
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
                if (onlyAudio) {
                    params["only_audio"] = "1"
                }
                val response = api.livePlayUrl(WbiUtils.sign(params, keys.first, keys.second))
                val data = response.data
                if (response.code != 0 || data == null) {
                    error(response.message.ifBlank { "直播流获取失败(${response.code})" })
                }
                if (data.liveStatus != null && data.liveStatus != 1) {
                    error("未开播")
                }
                data.toPlayback(qn, onlyAudio, preferredLine)
            }
        }

    /**
     * 照搬 PiliPlus 的 `stream -> format -> codec -> url_info` 层级结构保留完整线路树，
     * 同时依据偏好（或默认 HLS/fmp4/avc）定位初始线路索引，并展开有序候选列表供播放失败时顺延。
     */
    private fun LivePlayUrlData.toPlayback(
        requestedQn: Int,
        onlyAudio: Boolean,
        preferredLine: List<String>?,
    ): LivePlayback {
        val qualities = qualitiesOf()
        val qnDescMap = qualities.associate { it.quality to it.label }
        val rawStreams = playurlInfo?.playurl?.stream.orEmpty()
        val streamNodes = rawStreams.mapNotNull { stream ->
            val formatNodes = stream.format.mapNotNull { format ->
                val codecNodes = format.codec.mapNotNull { codec ->
                    val hosts = codec.urlInfo.filter { it.host.isNotBlank() }
                        .map { LiveUrlHostNode(host = it.host, extra = it.extra) }
                    if (codec.baseUrl.isBlank() || hosts.isEmpty()) return@mapNotNull null
                    val label = qnDescMap[codec.currentQn]
                        ?: codec.currentQn.takeIf { it > 0 }?.let { "${it}P" }
                        ?: ""
                    LiveCodecNode(
                        codecName = codec.codecName,
                        currentQn = codec.currentQn,
                        qualityDesc = label,
                        acceptQn = codec.acceptQn,
                        baseUrl = codec.baseUrl,
                        urlInfos = hosts,
                    )
                }
                if (codecNodes.isEmpty()) null else LiveFormatNode(format.formatName, codecNodes)
            }
            if (formatNodes.isEmpty()) null else LiveStreamNode(stream.protocolName, formatNodes)
        }

        val initialSelection = resolveInitialSelection(streamNodes, preferredLine)
        val candidates = ArrayList<LiveCandidate>()
        fun addSelection(sel: LiveStreamSelection) {
            val s = streamNodes.getOrNull(sel.streamIndex) ?: return
            val f = s.formats.getOrNull(sel.formatIndex) ?: return
            val c = f.codecs.getOrNull(sel.codecIndex) ?: return
            val u = c.urlInfos.getOrNull(sel.urlIndex) ?: return
            val fullUrl = u.host + c.baseUrl + u.extra
            if (candidates.none { it.url == fullUrl }) {
                candidates += LiveCandidate(
                    url = fullUrl,
                    isHls = s.protocolName == "http_hls" || fullUrl.contains(".m3u8", ignoreCase = true),
                    selection = sel,
                    currentQn = c.currentQn,
                    acceptQn = c.acceptQn,
                )
            }
        }

        addSelection(initialSelection)
        val sortedStreamIndices = streamNodes.indices.sortedBy {
            if (streamNodes[it].protocolName == "http_hls") 0 else 1
        }
        for (si in sortedStreamIndices) {
            val formats = streamNodes[si].formats
            val sortedFormatIndices = formats.indices.sortedBy {
                if (formats[it].formatName == "fmp4") 0 else 1
            }
            for (fi in sortedFormatIndices) {
                val codecs = formats[fi].codecs
                val sortedCodecIndices = codecs.indices.sortedBy {
                    if (codecs[it].codecName == "avc") 0 else 1
                }
                for (ci in sortedCodecIndices) {
                    for (ui in codecs[ci].urlInfos.indices) {
                        addSelection(LiveStreamSelection(si, fi, ci, ui))
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
        val activeQuality = first.currentQn.takeIf { it > 0 }
            ?: currentQuality.takeIf { it > 0 }
            ?: requestedQn
        return LivePlayback(
            candidates = candidates,
            qualities = qualities,
            activeQuality = activeQuality,
            streams = streamNodes,
            initialSelection = initialSelection,
            onlyAudio = onlyAudio,
        )
    }

    private fun resolveInitialSelection(
        streams: List<LiveStreamNode>,
        preferredLine: List<String>?,
    ): LiveStreamSelection {
        if (preferredLine != null && preferredLine.size >= 3) {
            val (pName, fName, cName) = preferredLine
            streams.forEachIndexed { si, s ->
                if (s.protocolName == pName) {
                    s.formats.forEachIndexed { fi, f ->
                        if (f.formatName == fName) {
                            f.codecs.forEachIndexed { ci, c ->
                                if (c.codecName == cName) {
                                    return LiveStreamSelection(si, fi, ci, 0)
                                }
                            }
                        }
                    }
                }
            }
        }
        // 没有保存偏好时默认优先 HLS + fmp4 + avc，保持原有首选画质与低首帧延迟策略。
        var bestScore = Int.MAX_VALUE
        var best = LiveStreamSelection()
        streams.forEachIndexed { si, s ->
            val pScore = if (s.protocolName == "http_hls") 0 else 100
            s.formats.forEachIndexed { fi, f ->
                val fScore = if (f.formatName == "fmp4") 0 else 10
                f.codecs.forEachIndexed { ci, c ->
                    val cScore = if (c.codecName == "avc") 0 else 1
                    val score = pScore + fScore + cScore
                    if (score < bestScore) {
                        bestScore = score
                        best = LiveStreamSelection(si, fi, ci, 0)
                    }
                }
            }
        }
        return best
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

    private companion object {
        const val DEFAULT_QN = 10000
        const val PAGE_SIZE = 30
        const val FOLLOW_PAGE_SIZE = 30
    }
}
