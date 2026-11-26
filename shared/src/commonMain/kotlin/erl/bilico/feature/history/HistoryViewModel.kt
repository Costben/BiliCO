package erl.bilico.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.HistoryBusiness
import erl.bilico.data.model.HistoryItem
import erl.bilico.data.model.HistoryStat
import erl.bilico.data.repository.HistoryRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryTab(val label: String, val apiType: String) {
    VIDEO("视频", "archive"),
    LIVE("直播", "live"),
    ARTICLE("专栏", "article"),
}

data class HistoryTabState(
    val items: List<HistoryItem> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

private data class HistoryTabCursor(
    val nextMax: Long = 0L,
    val nextViewAt: Long = 0L,
    val nextBusiness: String = "",
    val inFlight: Boolean = false,
    val initialized: Boolean = false,
)

data class HistoryUiState(
    val tabs: Map<HistoryTab, HistoryTabState> = HistoryTab.entries.associateWith { HistoryTabState() },
    /** 按条目 key 缓存已取到的播放量 / 弹幕数，列表接口本身不带统计。 */
    val stats: Map<String, HistoryStat> = emptyMap(),
    val isPaused: Boolean = false,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
) {
    /** 对当前页签内已加载的记录做实时过滤。 */
    fun filteredItems(tab: HistoryTab): List<HistoryItem> {
        val source = tabs[tab]?.items.orEmpty()
        val kw = searchQuery.trim().lowercase()
        if (kw.isEmpty()) return source
        return source.filter { item ->
            item.title.lowercase().contains(kw) ||
                item.upName.lowercase().contains(kw) ||
                item.badge.lowercase().contains(kw) ||
                item.bvid.lowercase().contains(kw) ||
                Formatters.formatViewDate(item.viewAtSec).lowercase().contains(kw)
        }
    }
}

class HistoryViewModel(
    private val repository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val cursors = HistoryTab.entries.associateWith { HistoryTabCursor() }.toMutableMap()

    private val statQueue = Channel<HistoryItem>(Channel.UNLIMITED)
    private val statRequested = mutableSetOf<String>()

    init {
        ensureTabLoaded(HistoryTab.VIDEO)
        refreshPauseState()
        viewModelScope.launch {
            for (item in statQueue) {
                if (item.businessType != HistoryBusiness.VIDEO) continue
                if (item.key in _state.value.stats) continue
                val stat = repository.stat(item) ?: continue
                _state.update { it.copy(stats = it.stats + (item.key to stat)) }
            }
        }
    }

    fun refreshPauseState() {
        viewModelScope.launch {
            repository.isPaused().onSuccess { paused ->
                _state.update { it.copy(isPaused = paused) }
            }
        }
    }

    fun ensureTabLoaded(tab: HistoryTab) {
        val cursor = cursors.getValue(tab)
        if (cursor.initialized || cursor.inFlight) return
        cursors[tab] = cursor.copy(initialized = true)
        loadTab(tab, firstPage = true)
    }

    fun refresh(tab: HistoryTab) {
        val cursor = cursors.getValue(tab)
        if (cursor.inFlight) return
        cursors[tab] = cursor.copy(initialized = true)
        updateTabState(tab) {
            it.copy(
                isInitialLoading = it.items.isEmpty(),
                isRefreshing = it.items.isNotEmpty(),
                error = null,
            )
        }
        loadTab(tab, firstPage = true)
    }

    fun loadMore(tab: HistoryTab) {
        val cursor = cursors.getValue(tab)
        val tabState = _state.value.tabs[tab] ?: return
        if (cursor.inFlight || tabState.isEnd || tabState.items.isEmpty()) return
        updateTabState(tab) { it.copy(isLoadingMore = true) }
        loadTab(tab, firstPage = false)
    }

    fun requestStats(items: List<HistoryItem>, visibleIndices: List<Int>) {
        visibleIndices.mapNotNull { items.getOrNull(it) }
            .filter { it.businessType == HistoryBusiness.VIDEO && it.key.isNotBlank() && statRequested.add(it.key) }
            .forEach { statQueue.trySend(it) }
    }

    fun openSearch() {
        _state.update { it.copy(searchActive = true) }
    }

    fun closeSearch() {
        _state.update { it.copy(searchActive = false, searchQuery = "") }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    suspend fun clearAllHistory(): Result<Unit> {
        val result = repository.clearAll()
        if (result.isSuccess) {
            HistoryTab.entries.forEach { tab ->
                cursors[tab] = HistoryTabCursor(initialized = true)
                updateTabState(tab) {
                    HistoryTabState(
                        items = emptyList(),
                        isInitialLoading = false,
                        isEnd = true,
                    )
                }
            }
        }
        return result
    }

    suspend fun togglePaused(): Result<Boolean> {
        val target = !_state.value.isPaused
        val result = repository.setPaused(target)
        result.onSuccess { paused ->
            _state.update { it.copy(isPaused = paused) }
        }
        return result
    }

    private fun loadTab(tab: HistoryTab, firstPage: Boolean) {
        val cur = cursors.getValue(tab)
        cursors[tab] = cur.copy(inFlight = true)
        viewModelScope.launch {
            repository.page(
                max = if (firstPage) 0L else cur.nextMax,
                viewAt = if (firstPage) 0L else cur.nextViewAt,
                type = tab.apiType,
                business = if (firstPage) null else cur.nextBusiness.ifBlank { null },
            )
                .onSuccess { page ->
                    cursors[tab] = cur.copy(
                        nextMax = page.max,
                        nextViewAt = page.viewAt,
                        nextBusiness = page.business,
                        inFlight = false,
                        initialized = true,
                    )
                    updateTabState(tab) { state ->
                        val merged = if (firstPage) {
                            page.items.distinctBy { it.key }
                        } else {
                            (state.items + page.items).distinctBy { it.key }
                        }
                        state.copy(
                            items = merged,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isEnd = !page.hasMore,
                        )
                    }
                }
                .onFailure { error ->
                    cursors[tab] = cur.copy(inFlight = false, initialized = true)
                    updateTabState(tab) { state ->
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (firstPage) error.message ?: "历史记录加载失败" else state.error,
                        )
                    }
                }
        }
    }

    private inline fun updateTabState(
        tab: HistoryTab,
        crossinline transform: (HistoryTabState) -> HistoryTabState,
    ) {
        _state.update { cur ->
            val current = cur.tabs[tab] ?: HistoryTabState()
            cur.copy(tabs = cur.tabs + (tab to transform(current)))
        }
    }
}
