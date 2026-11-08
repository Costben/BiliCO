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
    /** 已经补过正文的动态编号，重新加载同一页时不重复请求。 */
    private val enriched = mutableSetOf<String>()

    init {
        if (session.isLoggedIn()) initialLoad()
    }

    fun onLoginStateChanged(loggedIn: Boolean) {
        if (loggedIn && _state.value.items.isEmpty() && !inFlight) initialLoad()
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

    /** 首次进入或刚登录：整页显示加载中，不走下拉刷新。 */
    private fun initialLoad() {
        _state.update { it.copy(isInitialLoading = true, isRefreshing = false, error = null) }
        load(firstPage = true)
    }

    fun refresh() {
        // 下拉刷新只认 isRefreshing 的「升起 → 落下」：列表为空或已有请求在路上也得升起来，
        // 否则刷新球等不到落下的信号会一直转。空列表时保留原来的空态 / 错误态，不再换成整页加载。
        _state.update { it.copy(isInitialLoading = false, isRefreshing = true) }
        if (inFlight) return
        _state.update { it.copy(error = null) }
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
                    // 刷新会把整页换成没补过图的原始数据，「补过哪些」的记录要一起清掉，
                    // 否则同一批编号第二次就被当成已处理，图片又退回一张封面加占位。
                    if (firstPage) enriched.clear()
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
                    enrichOpus(page.items)
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

    /**
     * 长文型 opus 在 feed 里只有一张封面和「[图片]」占位，正文里真正的图要按动态编号再取一次。
     * 只补这类卡片，取到就原地换掉文字和图片；失败就保持原样，不影响整页。
     */
    private fun enrichOpus(items: List<DynamicItemUi>) {
        val requestGeneration = generation
        val targets = items.filter { item ->
            val id = item.dynamicId
            id.isNotBlank() &&
                item.video == null &&
                item.content.contains(IMAGE_PLACEHOLDER) &&
                enriched.add(id)
        }
        if (targets.isEmpty()) return
        // 先把这几张卡片标成「图还在取」，和列表赋值落在同一帧里：
        // 界面直接画占位块，不会先闪一帧「[图片]」再换成图片。
        val pendingIds = targets.mapTo(HashSet()) { it.dynamicId }
        _state.update { state ->
            state.copy(
                items = state.items.map { current ->
                    if (current.dynamicId in pendingIds) current.copy(pendingImages = true) else current
                },
            )
        }
        targets.forEach { item ->
            val id = item.dynamicId
            viewModelScope.launch {
                val filled = repository.withOpusContent(item)
                if (requestGeneration != generation) return@launch
                _state.update { state ->
                    state.copy(
                        items = state.items.map { current ->
                            when {
                                current.dynamicId != id -> current
                                filled == item -> current.copy(pendingImages = false)
                                else -> filled.copy(pendingImages = false)
                            }
                        },
                    )
                }
            }
        }
    }

    private companion object {
        /** feed 卡片里长文图片的占位写法。 */
        const val IMAGE_PLACEHOLDER = "[图片]"
    }
}
