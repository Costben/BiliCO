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

/** 信息流的外观选项。目前只有封面比例，以后信息流上可调的东西也放这里。 */
class FeedPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _coverAspect = MutableStateFlow(CoverAspect.WIDE)
    val coverAspect: StateFlow<CoverAspect> = _coverAspect.asStateFlow()

    suspend fun restore() {
        val prefs = context.biliDataStore.data.first()
        _coverAspect.value = CoverAspect.fromCode(prefs[KEY_COVER_ASPECT] ?: CoverAspect.WIDE.code)
    }

    fun setCoverAspect(aspect: CoverAspect) {
        if (aspect == _coverAspect.value) return
        _coverAspect.value = aspect
        scope.launch { context.biliDataStore.edit { it[KEY_COVER_ASPECT] = aspect.code } }
    }

    private companion object {
        val KEY_COVER_ASPECT = intPreferencesKey("feed_cover_aspect")
    }
}
