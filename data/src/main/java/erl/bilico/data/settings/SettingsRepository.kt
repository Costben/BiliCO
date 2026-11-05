package erl.bilico.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 默认清晰度档位，值就是 B 站的 quality 编号。 */
enum class DefaultQuality(val id: Int, val label: String) {
    P1080(80, "1080P 高清"),
    P720(64, "720P 高清"),
    P480(32, "480P 清晰"),
    P360(16, "360P 流畅"),
}

/**
 * 本地设置：深色模式、默认清晰度、搜索历史。
 * 值是 StateFlow，Compose 直接 collect；写入走 DataStore，进程重启后保留。
 */
class SettingsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _darkMode = MutableStateFlow(0)
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    private val _defaultQuality = MutableStateFlow(DefaultQuality.P1080.id)
    val defaultQuality: StateFlow<Int> = _defaultQuality.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    init {
        scope.launch {
            val prefs = context.biliDataStore.data.first()
            _darkMode.value = prefs[KEY_DARK_MODE] ?: 0
            _defaultQuality.value = prefs[KEY_QUALITY] ?: DefaultQuality.P1080.id
            _searchHistory.value = prefs[KEY_SEARCH_HISTORY]
                ?.split('\u0001')
                ?.filter { it.isNotBlank() }
                ?.take(HISTORY_LIMIT)
                .orEmpty()
        }
    }

    fun setDarkMode(mode: Int) {
        _darkMode.value = mode.coerceIn(0, 2)
        scope.launch { context.biliDataStore.edit { it[KEY_DARK_MODE] = _darkMode.value } }
    }

    fun setDefaultQuality(quality: Int) {
        _defaultQuality.value = quality
        scope.launch { context.biliDataStore.edit { it[KEY_QUALITY] = quality } }
    }

    fun addSearchHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        _searchHistory.value = (listOf(trimmed) + _searchHistory.value.filterNot { it == trimmed })
            .take(HISTORY_LIMIT)
        persistHistory()
    }

    fun removeSearchHistory(keyword: String) {
        _searchHistory.value = _searchHistory.value.filterNot { it == keyword }
        persistHistory()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        persistHistory()
    }

    private fun persistHistory() {
        val encoded = _searchHistory.value.joinToString("\u0001")
        scope.launch { context.biliDataStore.edit { it[KEY_SEARCH_HISTORY] = encoded } }
    }

    private companion object {
        const val HISTORY_LIMIT = 12
        val KEY_DARK_MODE = intPreferencesKey("dark_mode")
        val KEY_QUALITY = intPreferencesKey("default_quality")
        val KEY_SEARCH_HISTORY = stringPreferencesKey("search_history")
    }
}
