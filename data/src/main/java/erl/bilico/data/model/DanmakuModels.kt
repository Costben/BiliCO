package erl.bilico.data.model

enum class DanmakuType {
    SCROLL,
    TOP,
    BOTTOM,
}

/** 一条弹幕。timeMs 是相对视频开头的绝对毫秒，来自 seg.so 的 progress 字段。 */
data class DanmakuItem(
    val id: Long,
    val timeMs: Int,
    val type: DanmakuType,
    val content: String,
    val color: Int,
    val fontSize: Int,
)
