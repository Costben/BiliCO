package erl.bilico.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 播放器顶栏的显示配置。每种模式各存一份顺序和一份隐藏集合。
 *
 * 隐藏集合存「被隐藏的」而不是「显示的」，以后新增的按钮默认就是显示的。
 */
class PlayerTopBarPreferences(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _fullscreen = MutableStateFlow(PlayerTopBarLayout())
    val fullscreen: StateFlow<PlayerTopBarLayout> = _fullscreen.asStateFlow()

    private val _portrait = MutableStateFlow(PlayerTopBarLayout())
    val portrait: StateFlow<PlayerTopBarLayout> = _portrait.asStateFlow()

    suspend fun restore() {
        val prefs = context.biliDataStore.data.first()
        _fullscreen.value = PlayerTopBarLayout(
            order = parseIds(prefs[KEY_FULLSCREEN_ORDER], PlayerTopBarMode.FULLSCREEN),
            hidden = parseIds(prefs[KEY_FULLSCREEN_HIDDEN], PlayerTopBarMode.FULLSCREEN).toSet(),
        )
        _portrait.value = PlayerTopBarLayout(
            order = parseIds(prefs[KEY_PORTRAIT_ORDER], PlayerTopBarMode.PORTRAIT),
            hidden = parseIds(prefs[KEY_PORTRAIT_HIDDEN], PlayerTopBarMode.PORTRAIT).toSet(),
        )
    }

    fun setVisible(mode: PlayerTopBarMode, itemId: String, visible: Boolean) {
        val item = PlayerTopBarItem.fromId(itemId) ?: return
        if (mode !in item.modes) return
        val flow = flowFor(mode)
        val current = flow.value
        val hidden = if (visible) current.hidden - itemId else current.hidden + itemId
        if (hidden == current.hidden) return
        flow.value = current.copy(hidden = hidden)
        scope.launch {
            context.biliDataStore.edit { it[hiddenKey(mode)] = hidden.sorted().joinToString(",") }
        }
    }

    /** 把 [mode] 里第 [from] 个按钮挪到第 [to] 个位置，顺序按当前列表算。 */
    fun moveItem(mode: PlayerTopBarMode, from: Int, to: Int) {
        val flow = flowFor(mode)
        val current = flow.value
        val ordered = current.orderedItems(mode).toMutableList()
        if (from !in ordered.indices || to !in ordered.indices || from == to) return
        ordered.add(to, ordered.removeAt(from))
        val order = ordered.map { it.id }
        if (order == current.order) return
        flow.value = current.copy(order = order)
        scope.launch { context.biliDataStore.edit { it[orderKey(mode)] = order.joinToString(",") } }
    }

    private fun flowFor(mode: PlayerTopBarMode): MutableStateFlow<PlayerTopBarLayout> =
        when (mode) {
            PlayerTopBarMode.FULLSCREEN -> _fullscreen
            PlayerTopBarMode.PORTRAIT -> _portrait
        }

    private fun hiddenKey(mode: PlayerTopBarMode) = when (mode) {
        PlayerTopBarMode.FULLSCREEN -> KEY_FULLSCREEN_HIDDEN
        PlayerTopBarMode.PORTRAIT -> KEY_PORTRAIT_HIDDEN
    }

    private fun orderKey(mode: PlayerTopBarMode) = when (mode) {
        PlayerTopBarMode.FULLSCREEN -> KEY_FULLSCREEN_ORDER
        PlayerTopBarMode.PORTRAIT -> KEY_PORTRAIT_ORDER
    }

    private fun parseIds(raw: String?, mode: PlayerTopBarMode): List<String> =
        raw?.split(',')
            ?.mapNotNull { PlayerTopBarItem.fromId(it.trim()) }
            ?.filter { mode in it.modes }
            ?.map { it.id }
            ?.distinct()
            .orEmpty()

    private companion object {
        val KEY_FULLSCREEN_ORDER = stringPreferencesKey("player_top_bar_fullscreen_order")
        val KEY_PORTRAIT_ORDER = stringPreferencesKey("player_top_bar_portrait_order")
        val KEY_FULLSCREEN_HIDDEN = stringPreferencesKey("player_top_bar_fullscreen_hidden")
        val KEY_PORTRAIT_HIDDEN = stringPreferencesKey("player_top_bar_portrait_hidden")
    }
}
