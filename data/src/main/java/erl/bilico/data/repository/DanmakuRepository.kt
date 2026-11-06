package erl.bilico.data.repository

import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.DanmakuProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分段弹幕：一段 6 分钟，index 从 1 开始。
 * 只做读取与 LRU 缓存，解析交给 [DanmakuProto]；失败返回空列表，不影响播放。
 */
class DanmakuRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
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
            DanmakuProto.parse(bytes)
        }.getOrNull() ?: return@withContext emptyList()

        synchronized(cache) { cache[key] = items }
        items
    }

    private companion object {
        const val MAX_SEGMENTS = 12
    }
}
