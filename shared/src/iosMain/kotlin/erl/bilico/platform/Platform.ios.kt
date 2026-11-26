package erl.bilico.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSLocale
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.COMPRESSION_BROTLI
import platform.darwin.COMPRESSION_STATUS_END
import platform.darwin.COMPRESSION_STATUS_OK
import platform.darwin.COMPRESSION_STREAM_DECODE
import platform.darwin.COMPRESSION_STREAM_FINALIZE
import platform.darwin.compression_stream
import platform.darwin.compression_stream_destroy
import platform.darwin.compression_stream_init
import platform.darwin.compression_stream_process
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual class PlatformContext actual constructor()

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun randomUuid(): String = NSUUID().UUIDString()

actual fun formatEpochSeconds(epochSeconds: Long, pattern: String): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    val formatter = NSDateFormatter().apply {
        dateFormat = pattern
        locale = NSLocale("en_US_POSIX")
    }
    return formatter.stringFromDate(date)
}

actual fun epochYear(epochSeconds: Long): Int {
    val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    return NSCalendar.currentCalendar.component(NSCalendarUnitYear, fromDate = date).toInt()
}

actual fun epochDayIndex(epochSeconds: Long): Long {
    val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    val cal = NSCalendar.currentCalendar
    val year = cal.component(NSCalendarUnitYear, fromDate = date).toInt()
    val month = cal.component(NSCalendarUnitMonth, fromDate = date).toInt()
    val day = cal.component(NSCalendarUnitDay, fromDate = date).toInt()
    return daysFromCivil(year, month, day)
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0L) y else y - 399L) / 400L
    val yoe = y - era * 400L
    val doy = (153L * (month + if (month > 2) -3 else 9) + 2L) / 5L + day - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    return era * 146097L + doe - 719468L
}

actual fun todayDate(): SimpleDate {
    val cal = NSCalendar.currentCalendar
    val now = NSDate()
    val y = cal.component(NSCalendarUnitYear, fromDate = now).toInt()
    val m = cal.component(NSCalendarUnitMonth, fromDate = now).toInt()
    val d = cal.component(NSCalendarUnitDay, fromDate = now).toInt()
    return SimpleDate(y, m, d)
}

actual fun dateToEpochSeconds(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
    val cal = NSCalendar.currentCalendar
    val comps = NSDateComponents().apply {
        this.year = year.toLong()
        this.month = month.toLong()
        this.day = day.toLong()
        this.hour = hour.toLong()
        this.minute = minute.toLong()
        this.second = second.toLong()
    }
    val date = cal.dateFromComponents(comps) ?: return 0L
    return date.timeIntervalSince1970.toLong()
}

actual fun addDaysToDate(year: Int, month: Int, day: Int, deltaDays: Int): SimpleDate {
    val cal = NSCalendar.currentCalendar
    val comps = NSDateComponents().apply {
        this.year = year.toLong()
        this.month = month.toLong()
        this.day = day.toLong()
        this.hour = 12L
    }
    val base = cal.dateFromComponents(comps) ?: return SimpleDate(year, month, day)
    val delta = NSDateComponents().apply { this.day = deltaDays.toLong() }
    val shifted = cal.dateByAddingComponents(delta, toDate = base, options = 0u) ?: base
    return SimpleDate(
        year = cal.component(NSCalendarUnitYear, fromDate = shifted).toInt(),
        month = cal.component(NSCalendarUnitMonth, fromDate = shifted).toInt(),
        day = cal.component(NSCalendarUnitDay, fromDate = shifted).toInt(),
    )
}

actual fun parseUrlComponents(url: String): ParsedUrl? {
    val comps = NSURLComponents.componentsWithString(url) ?: return null
    val queryMap = LinkedHashMap<String, String>()
    comps.queryItems?.forEach { raw ->
        val item = raw as? NSURLQueryItem ?: return@forEach
        val name = item.name
        val value = item.value ?: ""
        queryMap[name] = value
    }
    return ParsedUrl(
        scheme = comps.scheme.orEmpty(),
        host = comps.host.orEmpty(),
        encodedPath = comps.percentEncodedPath.orEmpty().ifEmpty { comps.path.orEmpty() },
        queryParams = queryMap,
    )
}

actual fun rewriteHttpsHost(url: String, newHost: String): String? {
    val comps = NSURLComponents.componentsWithString(url) ?: return null
    comps.scheme = "https"
    comps.host = newHost
    comps.port = NSNumber(int = 443)
    return comps.URL?.absoluteString
}

@OptIn(ExperimentalForeignApi::class)
actual fun decompressZlib(data: ByteArray): ByteArray {
    if (data.isEmpty()) return ByteArray(0)
    memScoped {
        val stream = alloc<z_stream>()
        val retInit = inflateInit_(stream.ptr, zlibVersion()?.toKString(), sizeOf<z_stream>().toInt())
        if (retInit != Z_OK) return ByteArray(0)
        try {
            val outChunks = ArrayList<ByteArray>()
            val chunkSize = 8192
            val outBuf = ByteArray(chunkSize)
            data.usePinned { inPinned ->
                stream.next_in = inPinned.addressOf(0).reinterpret()
                stream.avail_in = data.size.toUInt()
                var status = Z_OK
                do {
                    outBuf.usePinned { outPinned ->
                        stream.next_out = outPinned.addressOf(0).reinterpret()
                        stream.avail_out = chunkSize.toUInt()
                        status = inflate(stream.ptr, 0)
                    }
                    val have = chunkSize - stream.avail_out.toInt()
                    if (have > 0) {
                        outChunks += outBuf.copyOf(have)
                    }
                } while (status == Z_OK)
                if (status != Z_STREAM_END && status != Z_OK) {
                    return ByteArray(0)
                }
            }
            val total = outChunks.sumOf { it.size }
            val result = ByteArray(total)
            var offset = 0
            for (chunk in outChunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            return result
        } finally {
            inflateEnd(stream.ptr)
        }
    }
}

private const val MAX_BROTLI_DECOMPRESSED_BYTES = 16 * 1024 * 1024

@OptIn(ExperimentalForeignApi::class)
actual fun decompressBrotli(data: ByteArray): ByteArray {
    if (data.isEmpty()) return ByteArray(0)
    memScoped {
        val stream = alloc<compression_stream>()
        val initStatus = compression_stream_init(
            stream.ptr,
            COMPRESSION_STREAM_DECODE,
            COMPRESSION_BROTLI,
        )
        if (initStatus != COMPRESSION_STATUS_OK) return ByteArray(0)
        try {
            val outChunks = ArrayList<ByteArray>()
            val chunkSize = 8192
            val outBuf = ByteArray(chunkSize)
            var total = 0
            var status = COMPRESSION_STATUS_OK
            data.usePinned { inPinned ->
                stream.src_ptr = inPinned.addressOf(0).reinterpret()
                stream.src_size = data.size.toULong()
                do {
                    val prevSrcSize = stream.src_size
                    outBuf.usePinned { outPinned ->
                        stream.dst_ptr = outPinned.addressOf(0).reinterpret()
                        stream.dst_size = chunkSize.toULong()
                        status = compression_stream_process(stream.ptr, COMPRESSION_STREAM_FINALIZE.toInt())
                    }
                    val have = chunkSize - stream.dst_size.toInt()
                    if (have > 0) {
                        total += have
                        if (total > MAX_BROTLI_DECOMPRESSED_BYTES) return ByteArray(0)
                        outChunks += outBuf.copyOf(have)
                    }
                    if (status == COMPRESSION_STATUS_OK && have == 0 && stream.src_size == prevSrcSize) {
                        return ByteArray(0)
                    }
                } while (status == COMPRESSION_STATUS_OK)
            }
            if (status != COMPRESSION_STATUS_END) return ByteArray(0)
            val result = ByteArray(total)
            var offset = 0
            for (chunk in outChunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            return result
        } finally {
            compression_stream_destroy(stream.ptr)
        }
    }
}

actual fun clearWebCookies() {
    dispatch_async(dispatch_get_main_queue()) {
        val store = WKWebsiteDataStore.defaultDataStore()
        val cookieStore = store.httpCookieStore
        cookieStore.getAllCookies { list ->
            list?.forEach { raw ->
                val cookie = raw as? NSHTTPCookie ?: return@forEach
                cookieStore.deleteCookie(cookie, completionHandler = null)
            }
        }
        store.removeDataOfTypes(
            WKWebsiteDataStore.allWebsiteDataTypes(),
            modifiedSince = NSDate.dateWithTimeIntervalSince1970(0.0),
            completionHandler = {},
        )
    }
}

actual fun imageCacheDirectoryPath(): String {
    val base = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
    return "${base ?: "/tmp"}/bilico_image_cache"
}

actual fun logDebug(tag: String, message: String) {
    println("[$tag] $message")
}

actual fun logWarn(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] WARN: $message ${throwable?.message.orEmpty()}")
}

actual class KeyValueStore actual constructor(
    context: PlatformContext,
    private val name: String,
) {
    private val defaults = NSUserDefaults.standardUserDefaults
    private fun fullKey(key: String) = "$name.$key"

    actual fun contains(key: String): Boolean =
        defaults.objectForKey(fullKey(key)) != null

    actual fun getString(key: String): String? =
        defaults.stringForKey(fullKey(key))

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = fullKey(key))
    }

    actual fun getInt(key: String, default: Int): Int {
        val k = fullKey(key)
        return if (defaults.objectForKey(k) == null) default else defaults.integerForKey(k).toInt()
    }

    actual fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = fullKey(key))
    }

    actual fun getLong(key: String, default: Long): Long {
        val k = fullKey(key)
        return if (defaults.objectForKey(k) == null) default else defaults.integerForKey(k)
    }

    actual fun putLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = fullKey(key))
    }

    actual fun getFloat(key: String, default: Float): Float {
        val k = fullKey(key)
        return if (defaults.objectForKey(k) == null) default else defaults.floatForKey(k)
    }

    actual fun putFloat(key: String, value: Float) {
        defaults.setFloat(value, forKey = fullKey(key))
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val k = fullKey(key)
        return if (defaults.objectForKey(k) == null) default else defaults.boolForKey(k)
    }

    actual fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = fullKey(key))
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(fullKey(key))
    }
}

actual class SynchronizedMap<K, V> actual constructor() {
    private val lock = NSLock()
    private val map = LinkedHashMap<K, V>()

    actual operator fun get(key: K): V? {
        lock.lock()
        return try {
            map[key]
        } finally {
            lock.unlock()
        }
    }

    actual operator fun set(key: K, value: V) {
        lock.lock()
        try {
            map[key] = value
        } finally {
            lock.unlock()
        }
    }

    actual fun containsKey(key: K): Boolean {
        lock.lock()
        return try {
            map.containsKey(key)
        } finally {
            lock.unlock()
        }
    }

    actual fun size(): Int {
        lock.lock()
        return try {
            map.size
        } finally {
            lock.unlock()
        }
    }

    actual fun remove(key: K): V? {
        lock.lock()
        return try {
            map.remove(key)
        } finally {
            lock.unlock()
        }
    }

    actual fun clear() {
        lock.lock()
        try {
            map.clear()
        } finally {
            lock.unlock()
        }
    }

    actual fun toMap(): Map<K, V> {
        lock.lock()
        return try {
            map.toMap()
        } finally {
            lock.unlock()
        }
    }

    actual fun putAll(from: Map<K, V>) {
        lock.lock()
        try {
            map.putAll(from)
        } finally {
            lock.unlock()
        }
    }
}
