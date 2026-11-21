package erl.bilico.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 进程级 Media3 边播边存磁盘缓存（200MB LRU）：
 * 回拖进度条、单曲循环或切回已看片段时直接读本地缓存，避免重复拉流耗电与流量浪费。
 */
@OptIn(UnstableApi::class)
object PlaybackMediaCache {
    private const val MAX_CACHE_BYTES = 200L * 1024L * 1024L
    private const val CACHE_DIR_NAME = "exo_media_cache"

    @Volatile
    private var cache: SimpleCache? = null

    private fun obtain(context: Context): SimpleCache {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: run {
                val appContext = context.applicationContext
                val dir = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
                val db = StandaloneDatabaseProvider(appContext)
                SimpleCache(dir, LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES), db).also {
                    cache = it
                }
            }
        }
    }

    /** 将网络数据源包装为带磁盘 LRU 缓存的数据源；读写缓存异常时自动透传上游，不阻断播放。 */
    fun wrap(context: Context, upstream: DataSource.Factory): DataSource.Factory =
        runCatching {
            CacheDataSource.Factory()
                .setCache(obtain(context))
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }.getOrDefault(upstream)

    /** 清理全部已缓存的播放分段。 */
    fun clear(context: Context) {
        synchronized(this) {
            val active = cache
            if (active != null) {
                runCatching {
                    active.keys.toList().forEach { key ->
                        runCatching { active.removeResource(key) }
                    }
                }
            } else {
                runCatching { File(context.applicationContext.cacheDir, CACHE_DIR_NAME).deleteRecursively() }
            }
        }
    }
}
