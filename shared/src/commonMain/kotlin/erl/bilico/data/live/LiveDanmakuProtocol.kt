package erl.bilico.data.live

import erl.bilico.platform.decompressBrotli
import erl.bilico.platform.decompressZlib

/**
 * 哔哩哔哩直播弹幕协议。包头 16 字节大端：
 * 总长 / 头长 / 协议版本 / 操作码 / 序号，后面跟正文。
 */
internal object LiveDanmakuProtocol {
    const val HEAD_LENGTH = 16

    const val PROTO_JSON = 0
    const val PROTO_HEARTBEAT = 1
    const val PROTO_ZLIB = 2
    const val PROTO_BROTLI = 3

    const val OP_HEARTBEAT = 2
    const val OP_HEARTBEAT_REPLY = 3
    const val OP_MESSAGE = 5
    const val OP_AUTH = 7
    const val OP_AUTH_REPLY = 8

    private const val MAX_FRAME_BYTES = 2 * 1024 * 1024
    private const val MAX_PACKETS_PER_FRAME = 2048
    private const val MAX_COMPRESSION_DEPTH = 2

    class Packet(val operation: Int, val body: ByteArray)

    fun encode(operation: Int, body: ByteArray): ByteArray {
        val total = HEAD_LENGTH + body.size
        val out = ByteArray(total)
        putIntBE(out, 0, total)
        putShortBE(out, 4, HEAD_LENGTH)
        putShortBE(out, 6, PROTO_HEARTBEAT)
        putIntBE(out, 8, operation)
        putIntBE(out, 12, 1)
        body.copyInto(out, HEAD_LENGTH)
        return out
    }

    fun decode(data: ByteArray): List<Packet> {
        if (data.size < HEAD_LENGTH || data.size > MAX_FRAME_BYTES) return emptyList()
        val out = ArrayList<Packet>()
        decodeInto(data, 0, out)
        return out
    }

    private fun decodeInto(data: ByteArray, depth: Int, out: MutableList<Packet>) {
        if (depth > MAX_COMPRESSION_DEPTH) return
        var pos = 0
        while (data.size - pos >= HEAD_LENGTH && out.size < MAX_PACKETS_PER_FRAME) {
            val start = pos
            val total = readIntBE(data, pos)
            if (total < HEAD_LENGTH || total > data.size - start) return
            val headLength = readShortBE(data, pos + 4)
            val version = readShortBE(data, pos + 6)
            val operation = readIntBE(data, pos + 8)
            pos += HEAD_LENGTH
            if (headLength < HEAD_LENGTH || headLength > total) {
                pos = start + total
                continue
            }
            val bodyLength = total - headLength
            val skip = headLength - HEAD_LENGTH
            if (bodyLength < 0 || pos + skip + bodyLength > data.size) return
            pos += skip
            val body = data.copyOfRange(pos, pos + bodyLength)
            pos += bodyLength

            when (version) {
                PROTO_ZLIB -> {
                    val raw = runCatching { decompressZlib(body) }.getOrNull()
                    if (raw != null && raw.isNotEmpty()) decodeInto(raw, depth + 1, out)
                }

                PROTO_BROTLI -> {
                    val raw = runCatching { decompressBrotli(body) }.getOrNull()
                    if (raw != null && raw.isNotEmpty()) decodeInto(raw, depth + 1, out)
                }

                else -> out += Packet(operation, body)
            }
        }
    }

    private fun readIntBE(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)

    private fun readShortBE(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or
            (buf[offset + 1].toInt() and 0xFF)

    private fun putIntBE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun putShortBE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }
}
