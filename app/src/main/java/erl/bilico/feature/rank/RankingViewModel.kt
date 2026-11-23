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

/** B 站 2025 分区改版后旧版 rid（1/3/4/36/188/160/181/119/129）已停更于 2025-04，改用新版 tid_v2 实时榜。 */
val RANKING_SECTIONS = listOf(
    RankingTab(0, "全站"),
    RankingTab(1005, "动画"),
    RankingTab(1003, "音乐"),
    RankingTab(1008, "游戏"),
    RankingTab(1010, "知识"),
    RankingTab(1012, "科技数码"),
    RankingTab(1020, "美食"),
    RankingTab(1013, "汽车"),
    RankingTab(1014, "时尚美妆"),
    RankingTab(1018, "体育运动"),
    RankingTab(1024, "动物"),
    RankingTab(1002, "娱乐"),
    RankingTab(1001, "影视"),
    RankingTab(1007, "鬼畜"),
    RankingTab(1004, "舞蹈"),
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
