package erl.bilico.data.repository

import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.DanmakuProto
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分段弹幕：一段 6 分钟，index 从 1 开始。
 * 读取带 LRU 缓存，解析交给 [DanmakuProto]；失败返回空列表，不影响播放。
 */
class DanmakuRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
) {
    private val cache = object : LinkedHashMap<Long, List<DanmakuItem>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<DanmakuItem>>) =
            size > MAX_SEGMENTS
    }

    suspend fun segment(cid: Long, index: Int): List<DanmakuItem> = withContext(Dispatchers.IO) {
        if (cid <= 0L || index < 1) return@withContext emptyList()
        val key = cid * 1000L + index
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val items = runCatching {
            buvid.ensure()
            val body = api.danmakuSegment(oid = cid, segmentIndex = index)
            val bytes = body.use { it.bytes() }
            withContext(Dispatchers.Default) { DanmakuProto.parse(bytes) }
        }.getOrNull() ?: return@withContext emptyList()

        synchronized(cache) { cache[key] = items }
        items
    }

    /** 发一条滚动弹幕。成功后丢掉当前段的缓存，并返回服务端生成的 dmid（若未下发则返回 0）。 */
    suspend fun send(
        aid: Long,
        cid: Long,
        message: String,
        progressMs: Long,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            require(aid > 0L && cid > 0L) { "还没有加载到视频信息" }
            buvid.ensure()
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后才能发弹幕" }
            val text = message.trim()
            require(text.isNotEmpty()) { "先写点什么" }
            require(text.length <= 100) { "弹幕最多 100 字" }

            val response = api.sendDanmaku(
                mapOf(
                    "oid" to cid.toString(),
                    "aid" to aid.toString(),
                    "type" to "1",
                    "msg" to text,
                    "progress" to progressMs.coerceAtLeast(0L).toString(),
                    "color" to "16777215",
                    "fontsize" to "25",
                    "mode" to "1",
                    "pool" to "0",
                    "plat" to "1",
                    "csrf" to csrf,
                ),
            )
            if (response.code != 0) error(sendErrorText(response.code, response.message))
            dropSegment(cid, progressMs)
            response.data?.dmid?.takeIf { it > 0L }
                ?: response.data?.dmidStr?.toLongOrNull()?.takeIf { it > 0L }
                ?: 0L
        }
    }

    /** 举报单条弹幕。 */
    suspend fun report(
        cid: Long,
        dmid: Long,
        reason: Int,
        content: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(cid > 0L && dmid > 0L) { "该弹幕暂不支持举报" }
            buvid.ensure()
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后才能举报弹幕" }
            val fields = buildMap {
                put("cid", cid.toString())
                put("dmid", dmid.toString())
                put("reason", reason.toString())
                if (content.isNotBlank()) put("content", content.trim())
                put("csrf", csrf)
            }
            val response = api.reportDanmaku(fields)
            if (response.code != 0) error(reportErrorText(response.code, response.message))
        }
    }

    private fun dropSegment(cid: Long, progressMs: Long) {
        val index = (progressMs.coerceAtLeast(0L) / SEGMENT_MS).toInt() + 1
        synchronized(cache) { cache.remove(cid * 1000L + index) }
    }

    private fun sendErrorText(code: Int, fallback: String): String = when (code) {
        -101 -> "请先登录"
        -102 -> "账号被封禁"
        -111 -> "鉴权失败，请重新登录"
        -400 -> "请求参数错误"
        -509 -> "请求过于频繁，请稍后再试"
        36700 -> "系统升级中，请稍后再试"
        36701 -> "弹幕包含被禁止的内容"
        36702 -> "弹幕长度超出限制"
        36703 -> "发送频率过快，请稍后再试"
        36704 -> "当前视频暂不允许发送弹幕"
        36705 -> "当前账号等级不足，无法发送该弹幕"
        36711 -> "该视频禁止发送弹幕"
        36712 -> "当前账号等级限制，弹幕长度上限更低"
        else -> fallback.ifBlank { "发送弹幕失败（$code）" }
    }

    private fun reportErrorText(code: Int, fallback: String): String = when (code) {
        -101 -> "请先登录"
        -111 -> "鉴权失败，请重新登录"
        36201 -> "该弹幕不存在"
        36203 -> "举报类型错误"
        36204 -> "已经举报过该弹幕"
        else -> fallback.ifBlank { "举报弹幕失败（$code）" }
    }

    private companion object {
        const val MAX_SEGMENTS = 12
        const val SEGMENT_MS = 360_000L
    }
}
