package erl.bilico.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSDataCompressionAlgorithmZlib
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.decompressedDataUsingAlgorithm
import platform.Foundation.timeIntervalSince1970
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import platform.zlib.Z_FINISH
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
    val startOfDay = NSCalendar.currentCalendar.startOfDayForDate(date)
    return (startOfDay.timeIntervalSince1970 / 86400.0).toLong()
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
    val items = comps.queryItems as? List<*>
    items?.forEach { raw ->
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
        val retInit = inflateInit_(stream.ptr, zlibVersion()?.toKString(), kotlinx.cinterop.sizeOf<z_stream>().toInt())
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

actual fun decompressBrotli(data: ByteArray): ByteArray = ByteArray(0)

actual fun clearWebCookies() {
    dispatch_async(dispatch_get_main_queue()) {
        val store = WKWebsiteDataStore.defaultDataStore()
        store.fetchDataRecordsOfTypes(WKWebsiteDataStore.allWebsiteDataTypes()) { records ->
            @Suppress("UNCHECKED_CAST")
            val typed = records as? List<Any> ?: return@fetchDataRecordsOfTypes
            if (typed.isNotEmpty()) {
                store.removeDataOfTypes(
                    WKWebsiteDataStore.allWebsiteDataTypes(),
                    forDataRecords = typed,
                    completionHandler = {},
                )
            }
        }
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
