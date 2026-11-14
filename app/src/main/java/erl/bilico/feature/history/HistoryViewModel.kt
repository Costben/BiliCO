package erl.bilico.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.HistoryItem
import erl.bilico.data.model.HistoryStat
import erl.bilico.data.repository.HistoryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    /** 按条目 key 缓存已取到的播放量 / 弹幕数，列表接口本身不带统计。 */
    val stats: Map<String, HistoryStat> = emptyMap(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class HistoryViewModel(
    private val repository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var nextMax = 0L
    private var nextViewAt = 0L
    private var nextBusiness = ""
    private var inFlight = false

    // 统计一条一条排队取：只有一条请求在飞，滚多快都不会一次性打出一堆请求。
    private val statQueue = Channel<HistoryItem>(Channel.UNLIMITED)
    // 发过请求的 key 不再重复请求，失败的也记下，免得每次滚回来都重试。
    private val statRequested = mutableSetOf<String>()

    init {
        refresh()
        viewModelScope.launch {
            for (item in statQueue) {
                if (item.key in _state.value.stats) continue
                val stat = repository.stat(item) ?: continue
                _state.update { it.copy(stats = it.stats + (item.key to stat)) }
            }
        }
    }

    fun refresh() {
        if (inFlight) return
        _state.update {
            it.copy(
                isInitialLoading = it.items.isEmpty(),
                isRefreshing = it.items.isNotEmpty(),
                error = null,
            )
        }
        load(firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.items.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        load(firstPage = false)
    }

    /** 列表把当前可见的下标报上来，只给还没取过统计的条目排队。 */
    fun requestStats(indices: List<Int>) {
        val items = _state.value.items
        indices.mapNotNull { items.getOrNull(it) }
            .filter { it.key.isNotBlank() && statRequested.add(it.key) }
            .forEach { statQueue.trySend(it) }
    }

    private fun load(firstPage: Boolean) {
        inFlight = true
        viewModelScope.launch {
            repository.page(
                max = if (firstPage) 0L else nextMax,
                viewAt = if (firstPage) 0L else nextViewAt,
                business = if (firstPage) null else nextBusiness.ifBlank { null },
            )
                .onSuccess { page ->
                    nextMax = page.max
                    nextViewAt = page.viewAt
                    nextBusiness = page.business
                    _state.update {
                        it.copy(
                            items = if (firstPage) {
                                page.items.distinctBy { item -> item.key.ifBlank { item.bvid } }
                            } else {
                                (it.items + page.items).distinctBy { item -> item.key.ifBlank { item.bvid } }
                            },
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isEnd = !page.hasMore,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (firstPage) error.message ?: "历史记录加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}
