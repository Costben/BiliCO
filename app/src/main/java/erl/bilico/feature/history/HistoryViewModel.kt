package erl.bilico.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.HistoryItem
import erl.bilico.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
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

    init {
        refresh()
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
                                page.items
                            } else {
                                (it.items + page.items).distinctBy { item -> item.bvid }
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
