package erl.bilico.data.session

import erl.bilico.platform.DownloadLock
import erl.bilico.platform.KeyValueStore
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.ioDispatcher
import erl.bilico.platform.logWarn
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val stateLock = DownloadLock()
    private val writeMutex = Mutex()
    private val values = LinkedHashMap<String, String>()

    @Volatile
    private var loaded = false

    @Volatile
    private var lastWrite: Long = 0L

    private var writeGeneration: Long = 0L
    private var persistedGeneration: Long = 0L
    private var pendingRemoval: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }

    private fun ensureLoadedLocked() {
        if (loaded) return
        val raw = store.getString(KEY_COOKIES)
        if (!raw.isNullOrBlank()) {
            runCatching {
                json.decodeFromString<Map<String, String>>(raw)
            }.getOrNull()?.let { values.putAll(it) }
        }
        loaded = true
    }

    fun get(name: String): String? = stateLock.withLock {
        ensureLoadedLocked()
        values[name]
    }

    fun snapshot(): Map<String, String> = stateLock.withLock {
        ensureLoadedLocked()
        values.toMap()
    }

    fun put(name: String, value: String) {
        if (name.isBlank()) return
        stateLock.withLock {
            ensureLoadedLocked()
            if (value.isEmpty()) values.remove(name) else values[name] = value
            markDirtyLocked(remove = false)
        }
        scheduleFlush()
    }

    fun putAll(cookies: Map<String, String>) {
        if (cookies.isEmpty()) return
        stateLock.withLock {
            ensureLoadedLocked()
            cookies.forEach { (name, value) ->
                if (name.isNotBlank()) {
                    if (value.isEmpty()) values.remove(name) else values[name] = value
                }
            }
            markDirtyLocked(remove = false)
        }
        scheduleFlush()
    }

    fun clear() {
        stateLock.withLock {
            ensureLoadedLocked()
            values.clear()
            markDirtyLocked(remove = true)
        }
        scheduleFlush()
    }

    /** 切账号用：整份 Cookie 换成另一个账号的，一次落盘。 */
    fun replaceAll(cookies: Map<String, String>) {
        stateLock.withLock {
            ensureLoadedLocked()
            values.clear()
            cookies.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotEmpty()) values[name] = value
            }
            markDirtyLocked(remove = false)
        }
        scheduleFlush()
    }

    fun cookieHeaderForHost(host: String): String {
        val lower = host.lowercase()
        if (lower != "bilibili.com" && !lower.endsWith(".bilibili.com")) return ""
        val snap = stateLock.withLock {
            ensureLoadedLocked()
            values.toMap()
        }
        if (snap.isEmpty()) return ""
        return snap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun saveSetCookieHeaders(headers: List<String>, sourceUrl: String = "") {
        if (headers.isEmpty()) return
        val changed = stateLock.withLock {
            ensureLoadedLocked()
            var dirty = false
            headers.forEach { raw ->
                val pair = raw.substringBefore(';').trim()
                val eq = pair.indexOf('=')
                if (eq <= 0) return@forEach
                val name = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (name.isBlank()) return@forEach
                val previous = values[name]
                if (value.isEmpty() || value.equals("deleted", ignoreCase = true)) {
                    if (name in PROTECTED_SESSION_COOKIES) {
                        // 仅记录来源与 Cookie 键名，不记录任何凭证值；避免个别子接口下发过期头静默清空登录态。
                        logWarn("BiliCookie", "Ignored server clear for $name from $sourceUrl")
                        return@forEach
                    }
                    if (previous != null) {
                        values.remove(name)
                        dirty = true
                    }
                } else if (previous != value) {
                    values[name] = value
                    dirty = true
                }
            }
            if (dirty) markDirtyLocked(remove = false)
            dirty
        }
        if (changed) scheduleFlush()
    }

    private fun markDirtyLocked(remove: Boolean) {
        lastWrite = currentTimeMillis()
        pendingRemoval = remove
        writeGeneration++
    }

    private fun scheduleFlush() {
        scope.launch(ioDispatcher) {
            writeMutex.withLock {
                while (true) {
                    val task = stateLock.withLock {
                        if (persistedGeneration >= writeGeneration) return@withLock null
                        FlushTask(
                            generation = writeGeneration,
                            remove = pendingRemoval,
                            snapshot = if (pendingRemoval) emptyMap() else values.toMap(),
                        )
                    } ?: break

                    if (task.remove) {
                        store.remove(KEY_COOKIES)
                    } else {
                        store.putString(KEY_COOKIES, json.encodeToString(task.snapshot))
                    }

                    stateLock.withLock {
                        if (task.generation > persistedGeneration) {
                            persistedGeneration = task.generation
                        }
                    }
                }
            }
        }
    }

    private data class FlushTask(
        val generation: Long,
        val remove: Boolean,
        val snapshot: Map<String, String>,
    )

    private companion object {
        const val KEY_COOKIES = "cookies"
        val PROTECTED_SESSION_COOKIES = setOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5")
    }
}
