package erl.bilico.feature.pgc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.BangumiCard
import erl.bilico.data.repository.PgcRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BangumiFollowUiState(
    val items: List<BangumiCard> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class BangumiFollowViewModel(
    private val repository: PgcRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BangumiFollowUiState())
    val state: StateFlow<BangumiFollowUiState> = _state.asStateFlow()

    private var nextPage = 1
    private var inFlight = false

    init {
        refresh()
    }

    fun refresh() {
        val mid = session.account.value?.mid
        if (mid == null || mid <= 0L) {
            _state.value = BangumiFollowUiState(isLoading = false)
            return
        }
        nextPage = 1
        inFlight = false
        _state.update { it.copy(isLoading = true, error = null) }
        load(firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.items.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        load(firstPage = false)
    }

    private fun load(firstPage: Boolean) {
        val mid = session.account.value?.mid ?: return
        if (inFlight) return
        inFlight = true
        val page = if (firstPage) 1 else nextPage
        viewModelScope.launch {
            repository.follows(mid, page)
                .onSuccess { (list, hasMore) ->
                    nextPage = page + 1
                    _state.update {
                        it.copy(
                            items = if (firstPage) list else it.items + list,
                            isLoading = false,
                            isLoadingMore = false,
                            isEnd = !hasMore || list.isEmpty(),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = if (firstPage) error.message ?: "追番列表加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}
