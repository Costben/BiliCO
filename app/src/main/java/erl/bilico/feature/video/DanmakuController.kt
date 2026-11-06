package erl.bilico.feature.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.repository.DanmakuRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 弹幕时间轴：按播放进度拉当前段与下一段（6 分钟一段），拼成一条按时间排序的列表。
 * 切分 P 或换集时 [attach] 换 cid 并清空。
 */
internal class DanmakuController(
    private val repository: DanmakuRepository,
    private val scope: CoroutineScope,
) {
    var items: List<DanmakuItem> by mutableStateOf(emptyList())
        private set

    private var cid = 0L
    private val loaded = HashSet<Int>()
    private val loading = HashSet<Int>()
    private val failed = HashSet<Int>()

    fun attach(newCid: Long) {
        if (newCid == cid) return
        cid = newCid
        items = emptyList()
        loaded.clear()
        loading.clear()
        failed.clear()
    }

    fun onPosition(positionMs: Long) {
        if (cid <= 0L) return
        val index = (positionMs.coerceAtLeast(0L) / SEGMENT_MS).toInt() + 1
        load(index)
        load(index + 1)
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
                items = (items + segment).sortedBy { it.timeMs }
            }
        }
    }

    private companion object {
        const val SEGMENT_MS = 360_000L
    }
}
