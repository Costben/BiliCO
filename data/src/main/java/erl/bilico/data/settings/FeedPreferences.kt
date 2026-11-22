package erl.bilico.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import erl.bilico.data.biliDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 推荐流封面的裁切比例。[ratio] 是宽高比，封面按它裁成固定高度。 */
enum class CoverAspect(val code: Int, val label: String, val ratio: Float) {
    WIDE(0, "16:9", 16f / 9f),
    CLASSIC(1, "4:3", 4f / 3f),
    ;

    companion object {
        fun fromCode(code: Int): CoverAspect = entries.firstOrNull { it.code == code } ?: WIDE
    }
}

/** 信息流的外观与刷新选项：封面比例、推荐列数、单次下拉刷新拉取数量。 */
class FeedPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _coverAspect = MutableStateFlow(CoverAspect.WIDE)
    val coverAspect: StateFlow<CoverAspect> = _coverAspect.asStateFlow()

    private val _columns = MutableStateFlow(DEFAULT_COLUMNS)
    val columns: StateFlow<Int> = _columns.asStateFlow()

    private val _refreshCount = MutableStateFlow(DEFAULT_REFRESH_COUNT)
    val refreshCount: StateFlow<Int> = _refreshCount.asStateFlow()

    suspend fun restore() {
        val prefs = context.biliDataStore.data.first()
        _coverAspect.value = CoverAspect.fromCode(prefs[KEY_COVER_ASPECT] ?: CoverAspect.WIDE.code)
        _columns.value = (prefs[KEY_COLUMNS] ?: DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        _refreshCount.value = (prefs[KEY_REFRESH_COUNT] ?: DEFAULT_REFRESH_COUNT)
            .coerceIn(MIN_REFRESH_COUNT, MAX_REFRESH_COUNT)
    }

    fun setCoverAspect(aspect: CoverAspect) {
        if (aspect == _coverAspect.value) return
        _coverAspect.value = aspect
        scope.launch { context.biliDataStore.edit { it[KEY_COVER_ASPECT] = aspect.code } }
    }

    fun setColumns(columns: Int) {
        val resolved = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        if (resolved == _columns.value) return
        _columns.value = resolved
        scope.launch { context.biliDataStore.edit { it[KEY_COLUMNS] = resolved } }
    }

    fun setRefreshCount(count: Int) {
        val resolved = count.coerceIn(MIN_REFRESH_COUNT, MAX_REFRESH_COUNT)
        if (resolved == _refreshCount.value) return
        _refreshCount.value = resolved
        scope.launch { context.biliDataStore.edit { it[KEY_REFRESH_COUNT] = resolved } }
    }

    companion object {
        const val MIN_COLUMNS = 1
        const val MAX_COLUMNS = 4
        const val DEFAULT_COLUMNS = 2
        const val MIN_REFRESH_COUNT = 6
        const val MAX_REFRESH_COUNT = 30
        const val DEFAULT_REFRESH_COUNT = 20
        val REFRESH_COUNT_OPTIONS = listOf(6, 8, 10, 12, 15, 16, 20, 24, 30)

        private val KEY_COVER_ASPECT = intPreferencesKey("feed_cover_aspect")
        private val KEY_COLUMNS = intPreferencesKey("feed_columns")
        private val KEY_REFRESH_COUNT = intPreferencesKey("feed_refresh_count")
    }
}
