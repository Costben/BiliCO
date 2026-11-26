package erl.bilico.platform

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Android 进程级上下文桥：由 Application.onCreate 初始化，MainActivity 更新当前前台 Activity 引用。
 */
object AndroidPlatformBridge {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requireAppContext(): Context =
        appContext ?: error("AndroidPlatformBridge.init(context) has not been called")

    fun setCurrentActivity(activity: Activity?) {
        activityRef = activity?.let(::WeakReference)
    }

    fun currentActivity(): Activity? = activityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual class PlatformContext actual constructor() {
    private var explicitContext: Context? = null

    constructor(context: Context) : this() {
        explicitContext = context.applicationContext
        AndroidPlatformBridge.init(context)
    }

    val androidContext: Context
        get() = explicitContext ?: AndroidPlatformBridge.requireAppContext()
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun formatEpochSeconds(epochSeconds: Long, pattern: String): String {
    val instant = Instant.ofEpochSecond(epochSeconds)
    val dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US)
    return dt.format(formatter)
}

actual fun epochYear(epochSeconds: Long): Int =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).year

actual fun epochDayIndex(epochSeconds: Long): Long =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

actual fun todayDate(): SimpleDate {
    val today = LocalDate.now()
    return SimpleDate(today.year, today.monthValue, today.dayOfMonth)
}

actual fun dateToEpochSeconds(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
    runCatching {
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(ZoneId.systemDefault())
            .toEpochSecond()
    }.getOrDefault(0L)

actual fun addDaysToDate(year: Int, month: Int, day: Int, deltaDays: Int): SimpleDate =
    runCatching {
        val shifted = LocalDate.of(year, month, day).plusDays(deltaDays.toLong())
        SimpleDate(shifted.year, shifted.monthValue, shifted.dayOfMonth)
    }.getOrElse { SimpleDate(year, month, day) }

actual fun parseUrlComponents(url: String): ParsedUrl? {
    val httpUrl = url.toHttpUrlOrNull() ?: return null
    val queryMap = LinkedHashMap<String, String>()
    for (i in 0 until httpUrl.querySize) {
        val name = httpUrl.queryParameterName(i)
        val value = httpUrl.queryParameterValue(i).orEmpty()
        queryMap[name] = value
    }
    return ParsedUrl(
        scheme = httpUrl.scheme,
        host = httpUrl.host,
        encodedPath = httpUrl.encodedPath,
        queryParams = queryMap,
    )
}

actual fun rewriteHttpsHost(url: String, newHost: String): String? {
    val httpUrl = url.toHttpUrlOrNull() ?: return null
    return runCatching {
        httpUrl.newBuilder()
            .scheme("https")
            .host(newHost)
            .port(443)
            .build()
            .toString()
    }.getOrNull()
}

actual fun decompressZlib(data: ByteArray): ByteArray {
    if (data.isEmpty()) return ByteArray(0)
    return runCatching {
        InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }.getOrElse {
        runCatching {
            val inflater = Inflater(true)
            try {
                InflaterInputStream(ByteArrayInputStream(data), inflater).use { it.readBytes() }
            } finally {
                inflater.end()
            }
        }.getOrDefault(ByteArray(0))
    }
}

actual fun decompressBrotli(data: ByteArray): ByteArray {
    if (data.isEmpty()) return ByteArray(0)
    return runCatching {
        BrotliInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
    }.getOrDefault(ByteArray(0))
}

actual fun clearWebCookies() {
    Handler(Looper.getMainLooper()).post {
        runCatching {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies(null)
            manager.flush()
        }
    }
}

actual fun imageCacheDirectoryPath(): String {
    val dir = File(AndroidPlatformBridge.requireAppContext().cacheDir, "bilico_image_cache")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
}

actual fun logWarn(tag: String, message: String, throwable: Throwable?) {
    if (throwable != null) {
        Log.w(tag, message, throwable)
    } else {
        Log.w(tag, message)
    }
}

actual class KeyValueStore actual constructor(
    context: PlatformContext,
    name: String,
) {
    private val prefs = context.androidContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    actual fun contains(key: String): Boolean = prefs.contains(key)

    actual fun getString(key: String): String? = prefs.getString(key, null)

    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    actual fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    actual fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)

    actual fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    actual fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)

    actual fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    actual fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

actual class SynchronizedMap<K, V> actual constructor() {
    private val lock = Any()
    private val map = LinkedHashMap<K, V>()

    actual operator fun get(key: K): V? = synchronized(lock) { map[key] }

    actual operator fun set(key: K, value: V) {
        synchronized(lock) { map[key] = value }
    }

    actual fun containsKey(key: K): Boolean = synchronized(lock) { map.containsKey(key) }

    actual fun size(): Int = synchronized(lock) { map.size }

    actual fun remove(key: K): V? = synchronized(lock) { map.remove(key) }

    actual fun clear() {
        synchronized(lock) { map.clear() }
    }

    actual fun toMap(): Map<K, V> = synchronized(lock) { map.toMap() }

    actual fun putAll(from: Map<K, V>) {
        synchronized(lock) { map.putAll(from) }
    }
}
