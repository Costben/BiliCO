package erl.bilico.feature.rank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.repository.RankingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RankingTab(
    val rid: Int,
    val label: String,
)

val RANKING_SECTIONS = listOf(
    RankingTab(0, "全站"),
    RankingTab(1, "动画"),
    RankingTab(3, "音乐"),
    RankingTab(4, "游戏"),
    RankingTab(36, "知识"),
    RankingTab(188, "科技"),
    RankingTab(160, "生活"),
    RankingTab(181, "影视"),
    RankingTab(119, "鬼畜"),
    RankingTab(129, "舞蹈"),
)

data class RankingUiState(
    val section: Int = 0,
    val items: List<FeedVideo> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class RankingViewModel(
    private val repository: RankingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RankingUiState())
    val state: StateFlow<RankingUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load(refreshing = false)
    }

    fun select(index: Int) {
        if (index == _state.value.section || index !in RANKING_SECTIONS.indices) return
        _state.update { it.copy(section = index, items = emptyList(), error = null) }
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    private fun load(refreshing: Boolean) {
        job?.cancel()
        _state.update {
            it.copy(
                isLoading = it.items.isEmpty(),
                isRefreshing = refreshing,
                error = null,
            )
        }
        val sectionIndex = _state.value.section
        val rid = RANKING_SECTIONS[sectionIndex].rid
        job = viewModelScope.launch {
            repository.ranking(rid)
                .onSuccess { items ->
                    _state.update {
                        if (it.section != sectionIndex) {
                            it
                        } else {
                            it.copy(items = items, isLoading = false, isRefreshing = false)
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "排行榜加载失败",
                        )
                    }
                }
        }
    }
}
