package erl.bilico.feature.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import erl.bilico.data.repository.DanmakuRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 弹幕时间轴：按播放进度拉当前段与下一段（6 分钟一段），拼成一条按时间排序的列表。
 * 切分 P 或换集时 [attach] 换 cid 并清空（包括当前视频的临时屏蔽）。
 */
internal class DanmakuController(
    private val repository: DanmakuRepository,
    private val scope: CoroutineScope,
) {
    var items: List<DanmakuItem> by mutableStateOf(emptyList())
        private set

    private var cid = 0L
    private var rawItems: List<DanmakuItem> = emptyList()
    private val loaded = HashSet<Int>()
    private val loading = HashSet<Int>()
    private val failed = HashSet<Int>()
    private val selfIds = HashSet<Long>()
    private val selfItems = ArrayList<DanmakuItem>()
    private val blockedMidHashes = HashSet<String>()
    private val blockedTexts = HashSet<String>()
    private var nextLocalId = -1L

    fun attach(newCid: Long) {
        if (newCid == cid) return
        cid = newCid
        rawItems = emptyList()
        items = emptyList()
        loaded.clear()
        loading.clear()
        failed.clear()
        selfIds.clear()
        selfItems.clear()
        blockedMidHashes.clear()
        blockedTexts.clear()
    }

    fun onPosition(positionMs: Long) {
        if (cid <= 0L) return
        val index = (positionMs.coerceAtLeast(0L) / SEGMENT_MS).toInt() + 1
        load(index)
        load(index + 1)
    }

    /** 自己刚发送成功后立即插入时间轴，不依赖分段缓存刷新，并保留蓝色自发标识。 */
    fun appendSelf(content: String, timeMs: Long, serverId: Long = 0L) {
        if (cid <= 0L) return
        val id = if (serverId > 0L) serverId else nextLocalId--
        if (serverId > 0L) selfIds.add(serverId)
        selfItems.add(
            DanmakuItem(
                id = id,
                timeMs = timeMs.coerceAtLeast(0L).toInt(),
                type = DanmakuType.SCROLL,
                content = content,
                color = 0xFFFFFF,
                fontSize = 25,
                isSelf = true,
            ),
        )
        publish(emptyList())
    }

    /** 仅在当前视频内临时屏蔽该弹幕发送者及全文。 */
    fun blockTemporary(item: DanmakuItem) {
        if (item.midHash.isNotBlank()) blockedMidHashes.add(item.midHash)
        val trimmed = item.content.trim()
        if (trimmed.isNotEmpty()) blockedTexts.add(trimmed)
        items = rawItems.filterNot(::isBlocked)
    }

    /** 发完弹幕后把当前段重新拉一遍，免得要等进度滚过分段边界才看得到。 */
    fun reload(positionMs: Long) {
        if (cid <= 0L) return
        val index = (positionMs.coerceAtLeast(0L) / SEGMENT_MS).toInt() + 1
        loaded.remove(index)
        failed.remove(index)
        load(index)
    }

    private fun load(index: Int) {
        if (index < 1 || index in loaded || index in loading || index in failed) return
        loading.add(index)
        val target = cid
        scope.launch {
            val segment = repository.segment(target, index)
            if (target != cid) return@launch
            loading.remove(index)
            if (segment.isEmpty()) {
                failed.add(index)
            } else {
                loaded.add(index)
                publish(segment)
            }
        }
    }

    private fun publish(segment: List<DanmakuItem>) {
        rawItems = (selfItems + rawItems + segment)
            .map { item ->
                if (!item.isSelf && item.id in selfIds) item.copy(isSelf = true) else item
            }
            .distinctBy { it.id }
            .sortedBy { it.timeMs }
        items = rawItems.filterNot(::isBlocked)
    }

    private fun isBlocked(item: DanmakuItem): Boolean {
        if (item.midHash.isNotBlank() && item.midHash in blockedMidHashes) return true
        return item.content.trim() in blockedTexts
    }

    private companion object {
        const val SEGMENT_MS = 360_000L
    }
}
