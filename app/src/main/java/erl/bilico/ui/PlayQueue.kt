package erl.bilico.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import erl.bilico.data.model.LibraryVideo

/** 队列里的一条视频：卡片要显示的字段都在 [video] 里，[startSec] 是点开后从哪里播。 */
data class QueuedVideo(
    val video: LibraryVideo,
    val startSec: Long = 0L,
)

/**
 * 连续播放队列。只在「播放全部」发起时存在；用户从别处打开任何视频都会把它作废，
 * 免得一条播完后莫名跳到另一个列表的下一条。
 *
 * 队列内容是可观察的，播放页的队列面板直接读它；面板里增删、跳播都改这一份。
 */
class PlayQueue {
    private val _items = mutableStateListOf<QueuedVideo>()
    val items: List<QueuedVideo> get() = _items

    /** 正在播的那条在队列里的位置；被移出队列后停在原位，下一条就是新挪上来的这条。 */
    private var index = -1

    /** 正在播的那条是谁。移出队列后仍然记着它，[sync] 才不会误清空剩下的队列。 */
    var playingBvid: String? = null
        private set

    /** 从第一条开始播，返回要打开的那条。 */
    fun start(list: List<QueuedVideo>): QueuedVideo? {
        _items.clear()
        _items.addAll(list)
        if (_items.isEmpty()) {
            index = -1
            playingBvid = null
            return null
        }
        index = 0
        playingBvid = _items[0].video.bvid
        return _items[0]
    }

    /** 播放页确认自己放的就是队列里那条；对不上说明是用户另开的视频，队列作废。 */
    fun sync(bvid: String) {
        if (playingBvid != bvid) clear()
    }

    fun next(loop: Boolean = false): QueuedVideo? {
        if (_items.isEmpty()) return null
        val nextIndex = index + 1
        val resolvedIndex = when {
            nextIndex in _items.indices -> nextIndex
            loop && _items.size > 1 -> 0
            else -> {
                if (!loop) clear()
                return null
            }
        }
        index = resolvedIndex
        val next = _items[resolvedIndex]
        playingBvid = next.video.bvid
        return next
    }

    /** 面板里点某一条：直接跳到它，返回要打开的那条。 */
    fun jumpTo(position: Int): QueuedVideo? {
        val target = _items.getOrNull(position) ?: return null
        index = position
        playingBvid = target.video.bvid
        return target
    }

    /** 面板里点叉：把这一条移出队列。正在播的那条被移出时继续播，指针停在原位。 */
    fun removeAt(position: Int) {
        if (position !in _items.indices) return
        _items.removeAt(position)
        if (_items.isEmpty()) {
            index = -1
            playingBvid = null
            return
        }
        if (position < index) {
            index--
        } else if (position == index) {
            // 正在播的这条被移出：它不占位了，下一条就是挪上来的这一位。
            index = position - 1
        }
    }

    fun clear() {
        _items.clear()
        index = -1
        playingBvid = null
    }
}
