package erl.bilico.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import erl.bilico.data.biliDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 自动连播下一条推荐视频时的右下角弹窗开关与倒计时配置。 */
class AutoNextPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _recommendPopup = MutableStateFlow(true)
    val recommendPopup: StateFlow<Boolean> = _recommendPopup.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(DEFAULT_COUNTDOWN_SECONDS)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    suspend fun restore() {
        val prefs = context.biliDataStore.data.first()
        _recommendPopup.value = prefs[KEY_RECOMMEND_POPUP] ?: true
        _countdownSeconds.value = (prefs[KEY_COUNTDOWN_SECONDS] ?: DEFAULT_COUNTDOWN_SECONDS)
            .coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS)
    }

    fun setRecommendPopup(enabled: Boolean) {
        if (enabled == _recommendPopup.value) return
        _recommendPopup.value = enabled
        scope.launch { context.biliDataStore.edit { it[KEY_RECOMMEND_POPUP] = enabled } }
    }

    fun setCountdownSeconds(seconds: Int) {
        val resolved = seconds.coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS)
        if (resolved == _countdownSeconds.value) return
        _countdownSeconds.value = resolved
        scope.launch { context.biliDataStore.edit { it[KEY_COUNTDOWN_SECONDS] = resolved } }
    }

    companion object {
        const val MIN_COUNTDOWN_SECONDS = 3
        const val MAX_COUNTDOWN_SECONDS = 10
        const val DEFAULT_COUNTDOWN_SECONDS = 3

        private val KEY_RECOMMEND_POPUP = booleanPreferencesKey("auto_next_recommend_popup")
        private val KEY_COUNTDOWN_SECONDS = intPreferencesKey("auto_next_recommend_countdown_seconds")
    }
}
