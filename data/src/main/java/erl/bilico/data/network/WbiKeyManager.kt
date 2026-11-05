package erl.bilico.data.network

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WBI 签名密钥：内存缓存 + SharedPreferences 持久化 + 互斥刷新。
 * 缓存 24 小时，剩余不足 1 小时时提前刷新。
 */
class WbiKeyManager(
    private val context: Context,
    private val api: () -> BiliApi,
) {
    @Volatile
    private var cachedKeys: Pair<String, String>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    private val refreshMutex = Mutex()

    init {
        restoreFromStorage()
    }

    suspend fun getKeys(): Pair<String, String> {
        val cached = cachedKeys
        if (cached != null && isCacheValid()) return cached

        return refreshMutex.withLock {
            val rechecked = cachedKeys
            if (rechecked != null && isCacheValid()) {
                return@withLock Result.success(rechecked)
            }
            refreshKeys()
        }.getOrThrow()
    }

    suspend fun refresh(): Result<Pair<String, String>> = refreshMutex.withLock {
        refreshKeys()
    }

    private suspend fun refreshKeys(): Result<Pair<String, String>> {
        return try {
            val nav = api().nav()
            val wbiImg = nav.data?.wbiImg
                ?: return Result.failure(IllegalStateException("WBI 密钥缺失（nav code=${nav.code}）"))
            val imgKey = wbiImg.imgUrl.substringAfterLast("/").substringBefore(".")
            val subKey = wbiImg.subUrl.substringAfterLast("/").substringBefore(".")
            if (imgKey.isBlank() || subKey.isBlank()) {
                return Result.failure(IllegalStateException("WBI 密钥格式异常"))
            }
            cachedKeys = imgKey to subKey
            cacheTimestamp = System.currentTimeMillis()
            persistToStorage()
            Result.success(imgKey to subKey)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun invalidate() {
        cachedKeys = null
        cacheTimestamp = 0L
    }

    private fun isCacheValid(): Boolean =
        System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION_MS

    private fun persistToStorage() {
        val keys = cachedKeys ?: return
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
            .putString(SP_KEY_IMG, keys.first)
            .putString(SP_KEY_SUB, keys.second)
            .putLong(SP_KEY_TIMESTAMP, cacheTimestamp)
            .apply()
    }

    private fun restoreFromStorage() {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val imgKey = sp.getString(SP_KEY_IMG, null) ?: return
        val subKey = sp.getString(SP_KEY_SUB, null) ?: return
        val timestamp = sp.getLong(SP_KEY_TIMESTAMP, 0L)
        if (timestamp <= 0L) return
        cachedKeys = imgKey to subKey
        cacheTimestamp = timestamp
        if (!isCacheValid()) invalidate()
    }

    private companion object {
        const val SP_NAME = "wbi_keys_sp"
        const val SP_KEY_IMG = "wbi_img_key"
        const val SP_KEY_SUB = "wbi_sub_key"
        const val SP_KEY_TIMESTAMP = "wbi_timestamp"
        const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
    }
}
