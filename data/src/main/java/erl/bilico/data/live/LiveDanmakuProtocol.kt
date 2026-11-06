package erl.bilico.data.live

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

/**
 * 哔哩哔哩直播弹幕协议。包头 16 字节大端：
 * 总长 / 头长 / 协议版本 / 操作码 / 序号，后面跟正文。
 *
 * 服务端会把一批弹幕压成 zlib（2）或 brotli（3）塞进一个包，解压出来又是一串包，
 * 所以解码要递归一层。
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

    /** 一帧原始数据的上限，防止畸形包把内存吃光。 */
    private const val MAX_FRAME_BYTES = 2 * 1024 * 1024
    private const val MAX_PACKETS_PER_FRAME = 2048
    private const val MAX_COMPRESSION_DEPTH = 2

    class Packet(val operation: Int, val body: ByteArray)

    fun encode(operation: Int, body: ByteArray): ByteArray {
        val total = HEAD_LENGTH + body.size
        val buffer = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(total)
        buffer.putShort(HEAD_LENGTH.toShort())
        buffer.putShort(PROTO_HEARTBEAT.toShort())
        buffer.putInt(operation)
        buffer.putInt(1)
        buffer.put(body)
        return buffer.array()
    }

    fun decode(data: ByteArray): List<Packet> {
        if (data.size < HEAD_LENGTH || data.size > MAX_FRAME_BYTES) return emptyList()
        val out = ArrayList<Packet>()
        decodeInto(data, 0, out)
        return out
    }

    private fun decodeInto(data: ByteArray, depth: Int, out: MutableList<Packet>) {
        if (depth > MAX_COMPRESSION_DEPTH) return
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        while (buffer.remaining() >= HEAD_LENGTH && out.size < MAX_PACKETS_PER_FRAME) {
            val start = buffer.position()
            val total = buffer.int
            if (total < HEAD_LENGTH || total > buffer.capacity() - start) return
            val headLength = buffer.short.toInt() and 0xffff
            val version = buffer.short.toInt() and 0xffff
            val operation = buffer.int
            buffer.int
            if (headLength < HEAD_LENGTH || headLength > total) {
                buffer.position(start + total)
                continue
            }
            val bodyLength = total - headLength
            val skip = headLength - HEAD_LENGTH
            if (bodyLength < 0 || skip + bodyLength > buffer.remaining()) return
            buffer.position(buffer.position() + skip)
            val body = ByteArray(bodyLength)
            buffer.get(body)

            when (version) {
                PROTO_ZLIB, PROTO_BROTLI -> {
                    val raw = runCatching {
                        if (version == PROTO_ZLIB) inflate(body) else brotli(body)
                    }.getOrNull()
                    if (raw != null) decodeInto(raw, depth + 1, out)
                }

                else -> out += Packet(operation, body)
            }
        }
    }

    private fun inflate(body: ByteArray): ByteArray =
        InflaterInputStream(ByteArrayInputStream(body)).use { it.readBytes() }

    private fun brotli(body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(body.size * 4)
        BrotliInputStream(ByteArrayInputStream(body)).use { input ->
            input.copyTo(out, DEFAULT_BUFFER_SIZE)
        }
        return out.toByteArray()
    }
}
