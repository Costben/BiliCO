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

/** 推荐流封面的裁切比例。[ratio] 是宽高比，封面按它裁成固定高度。 */
enum class CoverAspect(val code: Int, val label: String, val ratio: Float) {
    WIDE(0, "16:9", 16f / 9f),
    CLASSIC(1, "4:3", 4f / 3f),
    ;

    companion object {
        fun fromCode(code: Int): CoverAspect = entries.firstOrNull { it.code == code } ?: WIDE
    }
}

/** 首页顶部分段分类（对齐 PiliPlus：推荐、热门、排行榜、番剧、影视、直播）。 */
enum class HomeTabType(
    val id: String,
    val label: String,
    val summary: String,
) {
    RECOMMEND("recommend", "推荐", "个性化推荐视频流"),
    HOT("hot", "热门", "全站综合热门视频"),
    RANKING("ranking", "排行榜", "全站与各分区实时榜单"),
    BANGUMI("bangumi", "番剧", "番剧热播排行榜"),
    CINEMA("cinema", "影视", "电影、电视剧、纪录片与综艺"),
    LIVE("live", "直播", "正在直播的推荐房间"),
    ;

    companion object {
        val DEFAULT_ORDER: List<HomeTabType> = listOf(
            RECOMMEND,
            HOT,
            RANKING,
            BANGUMI,
            CINEMA,
            LIVE,
        )

        fun fromId(id: String): HomeTabType? = entries.firstOrNull { it.id == id }
    }
}

/** 首页顶部分段的排列顺序与隐藏集合；新增分类默认可见并排在末尾。 */
data class HomeTabsLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    fun orderedTabs(): List<HomeTabType> {
        val valid = order.mapNotNull(HomeTabType::fromId).distinct()
        val missing = HomeTabType.DEFAULT_ORDER.filterNot { it in valid }
        return valid + missing
    }

    fun visibleTabs(): List<HomeTabType> {
        val visible = orderedTabs().filterNot { it.id in hidden }
        return visible.ifEmpty { listOf(HomeTabType.RECOMMEND) }
    }
}

/** 信息流的外观与刷新选项：封面比例、推荐列数、单次下拉刷新拉取数量、首页分类顺序。 */
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

    private val _homeTabs = MutableStateFlow(HomeTabsLayout())
    val homeTabs: StateFlow<HomeTabsLayout> = _homeTabs.asStateFlow()

    suspend fun restore() {
        val prefs = context.biliDataStore.data.first()
        _coverAspect.value = CoverAspect.fromCode(prefs[KEY_COVER_ASPECT] ?: CoverAspect.WIDE.code)
        _columns.value = (prefs[KEY_COLUMNS] ?: DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        _refreshCount.value = (prefs[KEY_REFRESH_COUNT] ?: DEFAULT_REFRESH_COUNT)
            .coerceIn(MIN_REFRESH_COUNT, MAX_REFRESH_COUNT)
        _homeTabs.value = HomeTabsLayout(
            order = parseHomeTabIds(prefs[KEY_HOME_TABS_ORDER]),
            hidden = parseHomeTabIds(prefs[KEY_HOME_TABS_HIDDEN]).toSet(),
        )
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

    fun setHomeTabVisible(tabId: String, visible: Boolean) {
        if (HomeTabType.fromId(tabId) == null) return
        val current = _homeTabs.value
        if (!visible && current.visibleTabs().size <= 1) return
        val hidden = if (visible) current.hidden - tabId else current.hidden + tabId
        if (hidden == current.hidden) return
        _homeTabs.value = current.copy(hidden = hidden)
        scope.launch {
            context.biliDataStore.edit { it[KEY_HOME_TABS_HIDDEN] = hidden.sorted().joinToString(",") }
        }
    }

    fun moveHomeTab(from: Int, to: Int) {
        val current = _homeTabs.value
        val ordered = current.orderedTabs().toMutableList()
        if (from !in ordered.indices || to !in ordered.indices || from == to) return
        ordered.add(to, ordered.removeAt(from))
        val order = ordered.map { it.id }
        if (order == current.order) return
        _homeTabs.value = current.copy(order = order)
        scope.launch { context.biliDataStore.edit { it[KEY_HOME_TABS_ORDER] = order.joinToString(",") } }
    }

    fun resetHomeTabs() {
        val defaults = HomeTabsLayout()
        if (_homeTabs.value == defaults) return
        _homeTabs.value = defaults
        scope.launch {
            context.biliDataStore.edit {
                it.remove(KEY_HOME_TABS_ORDER)
                it.remove(KEY_HOME_TABS_HIDDEN)
            }
        }
    }

    private fun parseHomeTabIds(raw: String?): List<String> =
        raw?.split(',')
            ?.mapNotNull { HomeTabType.fromId(it.trim())?.id }
            ?.distinct()
            .orEmpty()

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
        private val KEY_HOME_TABS_ORDER = stringPreferencesKey("feed_home_tabs_order")
        private val KEY_HOME_TABS_HIDDEN = stringPreferencesKey("feed_home_tabs_hidden")
    }
}
