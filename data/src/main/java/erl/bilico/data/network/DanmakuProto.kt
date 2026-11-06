package erl.bilico.data.network

import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import java.nio.charset.StandardCharsets

/**
 * `x/v2/dm/web/seg.so` 的最小 protobuf 读取器。
 *
 * 只解 `DmSegMobileReply.elems` 里渲染需要的字段，未知字段按 wire type 跳过；
 * 单条弹幕解析失败就丢掉，不影响整段。不引 protobuf 运行时。
 */
internal object DanmakuProto {

    fun parse(data: ByteArray): List<DanmakuItem> {
        if (data.isEmpty()) return emptyList()
        val items = ArrayList<DanmakuItem>(256)
        val reader = Reader(data, 0, data.size)
        while (reader.pos < reader.end) {
            val tag = reader.readTag() ?: break
            val field = tag ushr 3
            val wire = tag and 0x07
            if (field == 1 && wire == 2) {
                val length = reader.readVarint()?.toInt() ?: break
                if (length <= 0 || reader.pos + length > reader.end) break
                parseElem(data, reader.pos, reader.pos + length)?.let(items::add)
                reader.pos += length
            } else if (!reader.skip(wire)) {
                break
            }
        }
        return items
    }

    private fun parseElem(data: ByteArray, start: Int, end: Int): DanmakuItem? {
        var id = 0L
        var timeMs = 0
        var mode = 1
        var fontsize = 25
        var color = 0xFFFFFF
        var content: String? = null
        val reader = Reader(data, start, end)
        while (reader.pos < reader.end) {
            val tag = reader.readTag() ?: return null
            val field = tag ushr 3
            val wire = tag and 0x07
            when (field) {
                1 -> id = reader.readVarint() ?: return null
                2 -> timeMs = (reader.readVarint() ?: return null).toInt()
                3 -> mode = (reader.readVarint() ?: return null).toInt()
                4 -> fontsize = (reader.readVarint() ?: return null).toInt()
                5 -> color = (reader.readVarint() ?: return null).toInt()
                7 -> content = reader.readString() ?: return null
                else -> if (!reader.skip(wire)) return null
            }
        }
        val text = content?.takeIf { it.isNotBlank() } ?: return null
        // 1-3 滚动、4 底部、5 顶部；7/8/9 是高级 / 代码 / BAS 弹幕，本轮不渲染。
        if (mode !in 1..5) return null
        val type = when (mode) {
            4 -> DanmakuType.BOTTOM
            5 -> DanmakuType.TOP
            else -> DanmakuType.SCROLL
        }
        return DanmakuItem(
            id = id,
            timeMs = timeMs.coerceAtLeast(0),
            type = type,
            content = text,
            color = color and 0xFFFFFF,
            fontSize = fontsize,
        )
    }

    private class Reader(
        private val data: ByteArray,
        var pos: Int,
        val end: Int,
    ) {
        fun readVarint(): Long? {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (pos >= end) return null
                val byte = data[pos++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            return null
        }

        fun readTag(): Int? {
            val value = readVarint() ?: return null
            val tag = value.toInt()
            if (tag ushr 3 <= 0) return null
            return tag
        }

        fun readString(): String? {
            val length = (readVarint() ?: return null).toInt()
            if (length < 0 || pos + length > end) return null
            val value = String(data, pos, length, StandardCharsets.UTF_8)
            pos += length
            return value
        }

        fun skip(wireType: Int): Boolean = when (wireType) {
            0 -> readVarint() != null
            1 -> advance(8)
            2 -> {
                val length = (readVarint() ?: return false).toInt()
                length >= 0 && advance(length)
            }

            5 -> advance(4)
            else -> false
        }

        private fun advance(count: Int): Boolean {
            if (count < 0 || pos + count > end) return false
            pos += count
            return true
        }
    }
}
