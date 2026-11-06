package erl.bilico.feature.pgc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.BangumiCard
import erl.bilico.data.repository.PgcRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BangumiRankUiState(
    val items: List<BangumiCard> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class BangumiRankViewModel(
    private val repository: PgcRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BangumiRankUiState())
    val state: StateFlow<BangumiRankUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    private fun load(refreshing: Boolean) {
        job?.cancel()
        _state.update {
            it.copy(isLoading = it.items.isEmpty(), isRefreshing = refreshing, error = null)
        }
        job = viewModelScope.launch {
            repository.rank()
                .onSuccess { items ->
                    _state.update {
                        it.copy(items = items, isLoading = false, isRefreshing = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "番剧排行加载失败",
                        )
                    }
                }
        }
    }
}
