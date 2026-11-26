package erl.bilico.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.LiveRoomItem
import erl.bilico.data.repository.LiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveListUiState(
    val items: List<LiveRoomItem> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class LiveListViewModel(
    private val repository: LiveRepository,
    /** true 时拉「关注的主播里在播的」，供动态页的直播筛选复用。 */
    private val following: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveListUiState())
    val state: StateFlow<LiveListUiState> = _state.asStateFlow()

    private var nextPage = 1
    private var inFlight = false

    init {
        load(page = 1, firstPage = true)
    }

    fun refresh() {
        // 下拉刷新只认 isRefreshing 的「升起 → 落下」：列表为空或已有请求在路上也得升起来，
        // 否则刷新球等不到落下的信号会一直转。空列表时保留原来的空态 / 错误态，不再换成整页加载。
        _state.update { it.copy(isInitialLoading = false, isRefreshing = true) }
        if (inFlight) return
        _state.update { it.copy(error = null) }
        load(page = 1, firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.items.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        load(page = nextPage, firstPage = false)
    }

    private fun load(page: Int, firstPage: Boolean) {
        inFlight = true
        viewModelScope.launch {
            val request = if (following) repository.following(page) else repository.rooms(page)
            request
                .onSuccess { result ->
                    nextPage = page + 1
                    _state.update {
                        it.copy(
                            items = if (firstPage) {
                                result.items
                            } else {
                                (it.items + result.items).distinctBy { room -> room.roomId }
                            },
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isEnd = !result.hasMore,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (firstPage) error.message ?: "直播列表加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}
