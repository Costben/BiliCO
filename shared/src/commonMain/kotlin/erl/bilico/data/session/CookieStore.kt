package erl.bilico.data.session

import erl.bilico.platform.KeyValueStore
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.SynchronizedMap
import erl.bilico.platform.currentTimeMillis
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Bilibili Cookie 的单一真源。
 *
 * 只保存 name/value，不区分 domain：B 站的鉴权 Cookie 都挂在 .bilibili.com 上，
 * 因此 passport 与 api 两个子域可以共用同一份会话。
 */
class CookieStore(
    context: PlatformContext,
    private val scope: CoroutineScope,
) {
    private val store = KeyValueStore(context, "bilico")
    private val values = SynchronizedMap<String, String>()

    @Volatile
    private var loaded = false

    @Volatile
    private var lastWrite: Long = 0L

    private val json = Json { ignoreUnknownKeys = true }

    private fun ensureLoaded() {
        if (loaded) return
        val raw = store.getString(KEY_COOKIES)
        if (!raw.isNullOrBlank()) {
            runCatching {
                json.decodeFromString<Map<String, String>>(raw)
            }.getOrNull()?.let { values.putAll(it) }
        }
        loaded = true
    }

    fun get(name: String): String? {
        ensureLoaded()
        return values[name]
    }

    fun snapshot(): Map<String, String> {
        ensureLoaded()
        return values.toMap()
    }

    fun put(name: String, value: String) {
        ensureLoaded()
        if (name.isBlank()) return
        if (value.isEmpty()) values.remove(name) else values[name] = value
        persist()
    }

    fun putAll(cookies: Map<String, String>) {
        ensureLoaded()
        if (cookies.isEmpty()) return
        cookies.forEach { (name, value) ->
            if (name.isNotBlank()) {
                if (value.isEmpty()) values.remove(name) else values[name] = value
            }
        }
        persist()
    }

    fun clear() {
        ensureLoaded()
        values.clear()
        scope.launch(erl.bilico.platform.ioDispatcher) {
            store.remove(KEY_COOKIES)
        }
    }

    /** 切账号用：整份 Cookie 换成另一个账号的，一次落盘。 */
    fun replaceAll(cookies: Map<String, String>) {
        ensureLoaded()
        values.clear()
        cookies.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotEmpty()) values[name] = value
        }
        persist()
    }

    fun cookieHeaderForHost(host: String): String {
        val lower = host.lowercase()
        if (lower != "bilibili.com" && !lower.endsWith(".bilibili.com")) return ""
        ensureLoaded()
        val snap = values.toMap()
        if (snap.isEmpty()) return ""
        return snap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun saveSetCookieHeaders(headers: List<String>) {
        if (headers.isEmpty()) return
        ensureLoaded()
        var changed = false
        headers.forEach { raw ->
            val pair = raw.substringBefore(';').trim()
            val eq = pair.indexOf('=')
            if (eq <= 0) return@forEach
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (name.isBlank()) return@forEach
            val previous = values[name]
            if (value.isEmpty() || value == "deleted") {
                if (previous != null) {
                    values.remove(name)
                    changed = true
                }
            } else if (previous != value) {
                values[name] = value
                changed = true
            }
        }
        if (changed) persist()
    }

    private fun persist() {
        lastWrite = currentTimeMillis()
        val snapshot = values.toMap()
        scope.launch(erl.bilico.platform.ioDispatcher) {
            store.putString(KEY_COOKIES, json.encodeToString(snapshot))
        }
    }

    private companion object {
        const val KEY_COOKIES = "cookies"
    }
}
