package erl.bilico.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<FeedVideo> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class FeedViewModel(
    private val repository: FeedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var nextPage = 1
    private var inFlight = false

    init {
        refresh()
    }

    fun refresh() {
        if (inFlight) return
        inFlight = true
        _state.update {
            it.copy(
                isInitialLoading = it.items.isEmpty(),
                isRefreshing = it.items.isNotEmpty(),
                error = null,
            )
        }
        viewModelScope.launch {
            repository.recommend(1)
                .onSuccess { list ->
                    nextPage = 2
                    _state.update {
                        it.copy(
                            items = list,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isEnd = list.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "推荐加载失败",
                        )
                    }
                }
            inFlight = false
        }
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.items.isEmpty()) return
        inFlight = true
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val page = nextPage
            repository.recommend(page)
                .onSuccess { list ->
                    if (list.isEmpty()) {
                        _state.update { it.copy(isLoadingMore = false, isEnd = true) }
                    } else {
                        nextPage = page + 1
                        _state.update {
                            it.copy(
                                items = (it.items + list).distinctBy { video -> video.bvid },
                                isLoadingMore = false,
                            )
                        }
                    }
                }
                .onFailure {
                    // 翻页失败保留已有内容，下次滚动再试。
                    _state.update { it.copy(isLoadingMore = false) }
                }
            inFlight = false
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
