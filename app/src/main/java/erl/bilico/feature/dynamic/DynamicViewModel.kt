package erl.bilico.feature.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.repository.DynamicFeedType
import erl.bilico.data.repository.DynamicRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DynamicUiState(
    val items: List<DynamicItemUi> = emptyList(),
    val filter: DynamicFeedType = DynamicFeedType.ALL,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class DynamicViewModel(
    private val repository: DynamicRepository,
    private val session: SessionRepository,
    filter: DynamicFeedType = DynamicFeedType.ALL,
) : ViewModel() {

    private val _state = MutableStateFlow(DynamicUiState(filter = filter))
    val state: StateFlow<DynamicUiState> = _state.asStateFlow()

    private var offset = ""
    private var baseline = ""
    private var inFlight = false
    private var generation = 0

    init {
        if (session.isLoggedIn()) refresh()
    }

    fun onLoginStateChanged(loggedIn: Boolean) {
        if (loggedIn && _state.value.items.isEmpty() && !inFlight) refresh()
    }

    fun setFilter(filter: DynamicFeedType) {
        if (_state.value.filter == filter) return
        generation++
        inFlight = false
        offset = ""
        baseline = ""
        _state.update {
            it.copy(
                filter = filter,
                items = emptyList(),
                isInitialLoading = true,
                isRefreshing = false,
                isLoadingMore = false,
                isEnd = false,
                error = null,
            )
        }
        load(firstPage = true)
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
        val requestGeneration = generation
        val requestType = _state.value.filter
        val requestOffset = if (firstPage) "" else offset
        val requestBaseline = if (firstPage) "" else baseline
        viewModelScope.launch {
            repository.feed(requestType, requestOffset, requestBaseline)
                .onSuccess { page ->
                    if (requestGeneration != generation) return@onSuccess
                    offset = page.offset
                    baseline = page.baseline
                    _state.update {
                        it.copy(
                            items = if (firstPage) {
                                page.items
                            } else {
                                (it.items + page.items).distinctBy { item -> item.id }
                            },
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isEnd = !page.hasMore || page.items.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    if (requestGeneration != generation) return@onFailure
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (firstPage) error.message ?: "动态加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}
