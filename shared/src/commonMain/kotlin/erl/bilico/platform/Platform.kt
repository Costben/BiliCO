package erl.bilico.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

expect val ioDispatcher: CoroutineDispatcher

expect class PlatformContext()

expect fun currentTimeMillis(): Long

expect fun randomUuid(): String

expect fun formatEpochSeconds(epochSeconds: Long, pattern: String): String

expect fun epochYear(epochSeconds: Long): Int

expect fun epochDayIndex(epochSeconds: Long): Long

data class SimpleDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<SimpleDate> {
    override fun compareTo(other: SimpleDate): Int = when {
        year != other.year -> year - other.year
        month != other.month -> month - other.month
        else -> day - other.day
    }

    fun isAfter(other: SimpleDate): Boolean = compareTo(other) > 0
    fun isBefore(other: SimpleDate): Boolean = compareTo(other) < 0
    fun withDayOfMonth(dayOfMonth: Int): SimpleDate = copy(day = dayOfMonth.coerceIn(1, 28))
    fun minusDays(days: Long): SimpleDate = addDaysToDate(year, month, day, -days.toInt())
    fun plusDays(days: Long): SimpleDate = addDaysToDate(year, month, day, days.toInt())
    fun formatIso(): String = "$year-${padTwoDigits(month.toLong())}-${padTwoDigits(day.toLong())}"
    fun startOfDayEpochSeconds(): Long = dateToEpochSeconds(year, month, day, 0, 0, 0)
    fun endOfDayEpochSeconds(): Long = dateToEpochSeconds(year, month, day, 23, 59, 59)

    companion object {
        fun now(): SimpleDate = todayDate()
        fun of(year: Int, month: Int, day: Int): SimpleDate = SimpleDate(year, month, day)
    }
}

expect fun todayDate(): SimpleDate

expect fun dateToEpochSeconds(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long

expect fun addDaysToDate(year: Int, month: Int, day: Int, deltaDays: Int): SimpleDate

inline fun <K, V> SynchronizedMap<K, V>.getOrPut(key: K, defaultValue: () -> V): V {
    this[key]?.let { return it }
    val created = defaultValue()
    this[key] = created
    return created
}

fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val value = v.coerceIn(0f, 1f)
    val c = value * sat
    val x = c * (1f - abs(((hue / 60f) % 2f) - 1f))
    val m = value - c
    val (r1, g1, b1) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val r = ((r1 + m) * 255f).roundToLong().toInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255f).roundToLong().toInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255f).roundToLong().toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

fun argbToHsv(argb: Int, out: FloatArray) {
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    out[0] = ((h % 360f) + 360f) % 360f
    out[1] = if (max == 0f) 0f else delta / max
    out[2] = max
}

fun formatHexColor6(argb: Int): String {
    val rgb = argb and 0xFFFFFF
    val chars = CharArray(7)
    chars[0] = '#'
    for (i in 0 until 6) {
        chars[6 - i] = HEX_UPPER[(rgb ushr (i * 4)) and 0xF]
    }
    return chars.concatToString()
}

expect fun parseUrlComponents(url: String): ParsedUrl?

expect fun rewriteHttpsHost(url: String, newHost: String): String?

expect fun decompressZlib(data: ByteArray): ByteArray

expect fun decompressBrotli(data: ByteArray): ByteArray

expect fun clearWebCookies()

expect fun imageCacheDirectoryPath(): String

expect fun logDebug(tag: String, message: String)

expect fun logWarn(tag: String, message: String, throwable: Throwable? = null)

expect class KeyValueStore(context: PlatformContext, name: String) {
    fun contains(key: String): Boolean
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getFloat(key: String, default: Float): Float
    fun putFloat(key: String, value: Float)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

expect class SynchronizedMap<K, V>() {
    operator fun get(key: K): V?
    operator fun set(key: K, value: V)
    fun containsKey(key: K): Boolean
    fun size(): Int
    fun remove(key: K): V?
    fun clear()
    fun toMap(): Map<K, V>
    fun putAll(from: Map<K, V>)
}

data class ParsedUrl(
    val scheme: String,
    val host: String,
    val encodedPath: String,
    val queryParams: Map<String, String>,
)

fun codePointToString(value: Int?): String? {
    if (value == null || value <= 0 || value > 0x10FFFF) return null
    if (value < 0x10000) {
        if (value in 0xD800..0xDFFF) return null
        return value.toChar().toString()
    }
    val offset = value - 0x10000
    val high = (0xD800 + (offset ushr 10)).toChar()
    val low = (0xDC00 + (offset and 0x3FF)).toChar()
    return "$high$low"
}

fun padTwoDigits(value: Long): String {
    val safe = abs(value)
    return if (safe < 10) "0$safe" else safe.toString()
}

fun formatFixedDecimal(value: Double, decimals: Int): String {
    if (!value.isFinite()) return "0"
    val factor = 10.0.pow(decimals).roundToLong().coerceAtLeast(1L)
    val scaled = (value * factor).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val absScaled = abs(scaled)
    val intPart = absScaled / factor
    val fracPart = (absScaled % factor).toString().padStart(decimals, '0')
    return if (decimals <= 0) "$sign$intPart" else "$sign$intPart.$fracPart"
}

@OptIn(ExperimentalEncodingApi::class)
fun encodeBase64(bytes: ByteArray): String = Base64.encode(bytes)

fun urlEncode(value: String): String {
    val bytes = value.encodeToByteArray()
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val c = b.toInt() and 0xFF
        val isUnreserved = (c in 'A'.code..'Z'.code) ||
            (c in 'a'.code..'z'.code) ||
            (c in '0'.code..'9'.code) ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
        if (isUnreserved) {
            sb.append(c.toChar())
        } else {
            sb.append('%')
            sb.append(HEX_UPPER[(c ushr 4) and 0xF])
            sb.append(HEX_UPPER[c and 0xF])
        }
    }
    return sb.toString()
}

fun md5Hex(input: String): String = md5Hex(input.encodeToByteArray())

fun md5Hex(bytes: ByteArray): String = Md5Digest.hash(bytes)

private const val HEX_UPPER = "0123456789ABCDEF"

private object Md5Digest {
    private val shifts = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    private val constants = intArrayOf(
        0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db, 0xc1bdceee.toInt(),
        0xf57c0faf.toInt(), 0x4787c62a, 0xa8304613.toInt(), 0xfd469501.toInt(),
        0x698098d8, 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
        0x6b901122, 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821,
        0xf61e2562.toInt(), 0xc040b340.toInt(), 0x265e5a51, 0xe9b6c7aa.toInt(),
        0xd62f105d.toInt(), 0x02441453, 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
        0x21e1cde6, 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed,
        0xa9e3e905.toInt(), 0xfcefa3f8.toInt(), 0x676f02d9, 0x8d2a4c8a.toInt(),
        0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122, 0xfde5380c.toInt(),
        0xa4beea44.toInt(), 0x4bdecfa9, 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(),
        0x289b7ec6, 0xeaa127fa.toInt(), 0xd4ef3085.toInt(), 0x04881d05,
        0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8, 0xc4ac5665.toInt(),
        0xf4292244.toInt(), 0x432aff97, 0xab9423a7.toInt(), 0xfc93a039.toInt(),
        0x655b59c3, 0x8f0ccc92.toInt(), 0xffeff47d.toInt(), 0x85845dd1.toInt(),
        0x6fa87e4f, 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1,
        0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb, 0xeb86d391.toInt(),
    )

    fun hash(message: ByteArray): String {
        val messageLenBytes = message.size
        val numBlocks = ((messageLenBytes + 8) ushr 6) + 1
        val totalLen = numBlocks shl 6
        val padding = ByteArray(totalLen)
        message.copyInto(padding)
        padding[messageLenBytes] = 0x80.toByte()
        val messageLenBits = messageLenBytes.toLong() shl 3
        for (i in 0 until 8) {
            padding[totalLen - 8 + i] = ((messageLenBits ushr (8 * i)) and 0xFF).toByte()
        }

        var a = 0x67452301
        var b = 0xefcdab89.toInt()
        var c = 0x98badcfe.toInt()
        var d = 0x10325476
        val buffer = IntArray(16)

        for (block in 0 until numBlocks) {
            val base = block shl 6
            for (i in 0 until 16) {
                val idx = base + (i shl 2)
                buffer[i] = (padding[idx].toInt() and 0xFF) or
                    ((padding[idx + 1].toInt() and 0xFF) shl 8) or
                    ((padding[idx + 2].toInt() and 0xFF) shl 16) or
                    ((padding[idx + 3].toInt() and 0xFF) shl 24)
            }
            val origA = a
            val origB = b
            val origC = c
            val origD = d
            for (i in 0 until 64) {
                val div16 = i ushr 4
                val f: Int
                val bufferIndex: Int
                when (div16) {
                    0 -> {
                        f = (b and c) or (b.inv() and d)
                        bufferIndex = i
                    }
                    1 -> {
                        f = (b and d) or (c and d.inv())
                        bufferIndex = (i * 5 + 1) and 0x0F
                    }
                    2 -> {
                        f = b xor c xor d
                        bufferIndex = (i * 3 + 5) and 0x0F
                    }
                    else -> {
                        f = c xor (b or d.inv())
                        bufferIndex = (i * 7) and 0x0F
                    }
                }
                val temp = b + rotateLeft(a + f + buffer[bufferIndex] + constants[i], shifts[i])
                a = d
                d = c
                c = b
                b = temp
            }
            a += origA
            b += origB
            c += origC
            d += origD
        }

        val out = ByteArray(16)
        var count = 0
        for (word in intArrayOf(a, b, c, d)) {
            for (j in 0 until 4) {
                out[count++] = ((word ushr (j * 8)) and 0xFF).toByte()
            }
        }
        val hex = "0123456789abcdef"
        val sb = StringBuilder(32)
        for (byte in out) {
            val v = byte.toInt() and 0xFF
            sb.append(hex[v ushr 4])
            sb.append(hex[v and 0x0F])
        }
        return sb.toString()
    }

    private fun rotateLeft(value: Int, bits: Int): Int = (value shl bits) or (value ushr (32 - bits))
}
