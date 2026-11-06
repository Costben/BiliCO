package erl.bilico.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Bilibili Cookie 的单一真源。
 *
 * 只保存 name/value，不区分 domain：B 站的鉴权 Cookie 都挂在 .bilibili.com 上，
 * 请求时按目标 URL 现场重建 Cookie，因此 passport 与 api 两个子域可以共用同一份会话。
 */
class CookieStore(
    private val context: Context,
    private val scope: CoroutineScope,
) : CookieJar {

    private val values = ConcurrentHashMap<String, String>()
    private val loaded = AtomicBoolean(false)
    private val loadLock = Any()
    private val lastWrite = AtomicLong(0L)
    private val json = Json { ignoreUnknownKeys = true }

    private fun ensureLoaded() {
        if (loaded.get()) return
        synchronized(loadLock) {
            if (loaded.get()) return
            val raw = runBlocking { context.biliDataStore.data.first()[KEY_COOKIES] }
            if (!raw.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString<Map<String, String>>(raw)
                }.getOrNull()?.let(values::putAll)
            }
            loaded.set(true)
        }
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
        scope.launch(Dispatchers.IO) {
            context.biliDataStore.edit { it.remove(KEY_COOKIES) }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host.lowercase()
        if (host != "bilibili.com" && !host.endsWith(".bilibili.com")) return emptyList()
        ensureLoaded()
        if (values.isEmpty()) return emptyList()
        return values.mapNotNull { (name, value) ->
            runCatching { Cookie.parse(url, "$name=$value") }.getOrNull()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        ensureLoaded()
        var changed = false
        cookies.forEach { cookie ->
            val previous = values[cookie.name]
            if (cookie.value.isEmpty()) {
                if (previous != null) {
                    values.remove(cookie.name)
                    changed = true
                }
            } else if (previous != cookie.value) {
                values[cookie.name] = cookie.value
                changed = true
            }
        }
        if (changed) persist()
    }

    private fun persist() {
        lastWrite.set(System.currentTimeMillis())
        val snapshot = values.toMap()
        scope.launch(Dispatchers.IO) {
            val encoded = json.encodeToString(snapshot)
            context.biliDataStore.edit { it[KEY_COOKIES] = encoded }
        }
    }

    private companion object {
        val KEY_COOKIES = stringPreferencesKey("cookies")
    }
}
