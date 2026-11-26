package erl.bilico.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.HistoryBusiness
import erl.bilico.data.model.HistoryItem
import erl.bilico.data.model.HistoryStat
import erl.bilico.data.repository.HistoryRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Job
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
    val inFlightFirstPage: Boolean = false,
    val inFlightLoadMore: Boolean = false,
    val initialized: Boolean = false,
    val stale: Boolean = false,
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
    private val tabJobs = mutableMapOf<HistoryTab, Job>()
    private val tabGenerations = HistoryTab.entries.associateWith { 0L }.toMutableMap()

    private val statQueue = Channel<HistoryItem>(Channel.UNLIMITED)
    private val statRequested = mutableSetOf<String>()

    private var accountSynced = false
    private var activeMid: Long? = null

    init {
        viewModelScope.launch {
            for (item in statQueue) {
                if (item.businessType != HistoryBusiness.VIDEO) continue
                if (item.key in _state.value.stats) continue
                val stat = repository.stat(item) ?: continue
                _state.update { it.copy(stats = it.stats + (item.key to stat)) }
            }
        }
    }

    /** 账号登录、退出或切换时重置缓存状态，避免沿用上一账号的历史记录。 */
    fun syncAccount(mid: Long?) {
        if (accountSynced && activeMid == mid) return
        val hadAccount = accountSynced
        accountSynced = true
        activeMid = mid
        if (!hadAccount && mid != null) return
        cancelAllTabLoads()
        statRequested.clear()
        HistoryTab.entries.forEach { tab ->
            cursors[tab] = HistoryTabCursor()
        }
        _state.value = HistoryUiState(
            tabs = HistoryTab.entries.associateWith {
                HistoryTabState(isInitialLoading = mid != null)
            },
        )
    }

    fun refreshPauseState() {
        viewModelScope.launch {
            repository.isPaused().onSuccess { paused ->
                _state.update { it.copy(isPaused = paused) }
            }
        }
    }

    /**
     * 重新进入历史页或从视频/直播/专栏返回栈顶时调用：
     * - 其他已加载页签标记为待刷新，切过去时静默补拉；
     * - 当前页签若已有数据则静默刷新（不闪全屏加载态），否则走首次加载。
     */
    fun onEnterOrReturn(activeTab: HistoryTab) {
        HistoryTab.entries.forEach { tab ->
            if (tab != activeTab) {
                val cur = cursors.getValue(tab)
                if (cur.initialized) cursors[tab] = cur.copy(stale = true)
            }
        }
        refreshPauseState()
        val cur = cursors.getValue(activeTab)
        if (cur.initialized) {
            silentRefresh(activeTab)
        } else {
            ensureTabLoaded(activeTab)
        }
    }

    fun ensureTabLoaded(tab: HistoryTab) {
        val cursor = cursors.getValue(tab)
        if (cursor.stale) {
            silentRefresh(tab)
            return
        }
        if (cursor.initialized || cursor.inFlightFirstPage) return
        cursors[tab] = cursor.copy(initialized = true)
        loadTab(tab, firstPage = true, silent = false)
    }

    /** 静默刷新第一页：已有列表时保留原画面与滚动位置，不弹出刷新指示器。 */
    fun silentRefresh(tab: HistoryTab) {
        val cursor = cursors.getValue(tab)
        if (cursor.inFlightFirstPage) return
        if (cursor.inFlightLoadMore) {
            tabJobs.remove(tab)?.cancel()
        }
        val hasItems = _state.value.tabs[tab]?.items?.isNotEmpty() == true
        cursors[tab] = cursor.copy(
            initialized = true,
            stale = false,
            inFlightLoadMore = false,
        )
        if (!hasItems) {
            updateTabState(tab) {
                it.copy(isInitialLoading = true, isLoadingMore = false, error = null)
            }
        }
        loadTab(tab, firstPage = true, silent = hasItems)
    }

    fun refresh(tab: HistoryTab) {
        tabJobs.remove(tab)?.cancel()
        val cursor = cursors.getValue(tab)
        cursors[tab] = cursor.copy(
            initialized = true,
            stale = false,
            inFlightFirstPage = false,
            inFlightLoadMore = false,
        )
        updateTabState(tab) {
            it.copy(
                isInitialLoading = it.items.isEmpty(),
                isRefreshing = it.items.isNotEmpty(),
                isLoadingMore = false,
                error = null,
            )
        }
        refreshPauseState()
        loadTab(tab, firstPage = true, silent = false)
    }

    fun loadMore(tab: HistoryTab) {
        val cursor = cursors.getValue(tab)
        val tabState = _state.value.tabs[tab] ?: return
        if (cursor.inFlightFirstPage || cursor.inFlightLoadMore || tabState.isEnd || tabState.items.isEmpty()) return
        updateTabState(tab) { it.copy(isLoadingMore = true) }
        loadTab(tab, firstPage = false, silent = false)
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
            cancelAllTabLoads()
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

    private fun cancelAllTabLoads() {
        HistoryTab.entries.forEach { tab ->
            tabJobs.remove(tab)?.cancel()
            tabGenerations[tab] = (tabGenerations[tab] ?: 0L) + 1L
        }
    }

    private fun loadTab(tab: HistoryTab, firstPage: Boolean, silent: Boolean) {
        val cur = cursors.getValue(tab)
        val generation = (tabGenerations[tab] ?: 0L) + 1L
        tabGenerations[tab] = generation
        cursors[tab] = cur.copy(
            inFlightFirstPage = firstPage,
            inFlightLoadMore = !firstPage,
            stale = false,
        )
        tabJobs[tab] = viewModelScope.launch {
            repository.page(
                max = if (firstPage) 0L else cur.nextMax,
                viewAt = if (firstPage) 0L else cur.nextViewAt,
                type = tab.apiType,
                business = if (firstPage) null else cur.nextBusiness.ifBlank { null },
            )
                .onSuccess { page ->
                    if (tabGenerations[tab] != generation) return@onSuccess
                    val latestCursor = cursors.getValue(tab)
                    val existingItems = _state.value.tabs[tab]?.items.orEmpty()
                    val dedupedFirstPage = if (firstPage) page.items.distinctBy { it.key } else emptyList()
                    val preservedTail = if (
                        firstPage &&
                        silent &&
                        page.hasMore &&
                        dedupedFirstPage.isNotEmpty() &&
                        existingItems.size > dedupedFirstPage.size
                    ) {
                        val freshKeys = dedupedFirstPage.mapTo(HashSet()) { it.key }
                        if (existingItems.any { it.key in freshKeys }) {
                            val cutoffViewAt = dedupedFirstPage.minOf { it.viewAtSec }
                            existingItems.filter { it.key !in freshKeys && it.viewAtSec <= cutoffViewAt }
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                    cursors[tab] = if (firstPage && preservedTail.isNotEmpty()) {
                        latestCursor.copy(
                            inFlightFirstPage = false,
                            inFlightLoadMore = false,
                            initialized = true,
                            stale = false,
                        )
                    } else {
                        latestCursor.copy(
                            nextMax = page.max,
                            nextViewAt = page.viewAt,
                            nextBusiness = page.business,
                            inFlightFirstPage = false,
                            inFlightLoadMore = false,
                            initialized = true,
                            stale = false,
                        )
                    }
                    updateTabState(tab) { state ->
                        val merged = when {
                            firstPage && preservedTail.isNotEmpty() -> dedupedFirstPage + preservedTail
                            firstPage -> dedupedFirstPage
                            else -> (state.items + page.items).distinctBy { it.key }
                        }
                        val isEnd = if (firstPage && preservedTail.isNotEmpty()) state.isEnd else !page.hasMore
                        state.copy(
                            items = merged,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isEnd = isEnd,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (tabGenerations[tab] != generation) return@onFailure
                    val latestCursor = cursors.getValue(tab)
                    val hasItems = _state.value.tabs[tab]?.items?.isNotEmpty() == true
                    cursors[tab] = latestCursor.copy(
                        inFlightFirstPage = false,
                        inFlightLoadMore = false,
                        initialized = hasItems,
                    )
                    updateTabState(tab) { state ->
                        state.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (firstPage && !silent && state.items.isEmpty()) {
                                error.message ?: "历史记录加载失败"
                            } else {
                                state.error
                            },
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
