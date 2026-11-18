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
    /** 最近一次刷新插到列表最前面的新动态条数；0 表示这次没有拼接出新的边界。 */
    val refreshInsertedCount: Int = 0,
    /** 每完成一次刷新加一；列表据此把位置拉回新内容顶部。 */
    val refreshSerial: Int = 0,
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
    private val enricher = DynamicEnricher(repository, viewModelScope) { transform ->
        _state.update { it.copy(items = transform(it.items)) }
    }
    /** 这次会话里点过赞的动态。刷新回来的列表还是旧状态时，用这里盖回去。 */
    private val likedOverrides = mutableMapOf<String, Boolean>()

    init {
        viewModelScope.launch {
            repository.deletedIds.collect { deletedId ->
                _state.update { s ->
                    val next = s.items.filterNot { it.dynamicId == deletedId }
                    if (next.size == s.items.size) s else s.copy(items = next)
                }
            }
        }
        if (session.isLoggedIn()) initialLoad()
    }

    fun deleteDynamic(
        item: DynamicItemUi,
        onResult: (ok: Boolean, message: String) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            repository.deleteDynamic(item.dynamicId)
                .onSuccess { onResult(true, "已删除动态") }
                .onFailure { err -> onResult(false, err.message ?: "删除动态失败") }
        }
    }

    /**
     * 点赞或取消点赞。先把列表上的数字和状态改掉，失败再退回去。
     * 刷新时新数据会盖住旧的，这里不额外记一份，避免和接口状态对不上。
     */
    fun toggleLike(
        item: DynamicItemUi,
        onResult: (ok: Boolean, message: String) -> Unit = { _, _ -> },
    ) {
        val id = item.dynamicId
        if (id.isBlank()) {
            onResult(false, "这条动态暂不支持点赞")
            return
        }
        if (!session.isLoggedIn()) {
            onResult(false, "登录后可用")
            return
        }
        val liked = !item.liked
        likedOverrides[id] = liked
        _state.update { state ->
            state.copy(items = state.items.map { current -> current.withLike(id, liked) })
        }
        viewModelScope.launch {
            repository.thumbDynamic(id, liked)
                .onSuccess { onResult(true, if (liked) "已点赞" else "已取消点赞") }
                .onFailure { err ->
                    if (likedOverrides[id] == liked) likedOverrides.remove(id)
                    _state.update { state ->
                        state.copy(items = state.items.map { current -> current.withLike(id, !liked) })
                    }
                    onResult(false, err.message ?: "点赞失败")
                }
        }
    }

    fun onLoginStateChanged(loggedIn: Boolean) {
        if (loggedIn && _state.value.items.isEmpty() && !inFlight) initialLoad()
    }

    fun setFilter(filter: DynamicFeedType) {
        if (_state.value.filter == filter) return
        generation++
        enricher.reset()
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
                refreshInsertedCount = 0,
                refreshSerial = 0,
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
                    if (firstPage) {
                        enricher.clearHistory()
                    }
                    _state.update { current ->
                        val oldItems = current.items
                        val pageItems = applyLikeOverrides(page.items)
                        if (firstPage) {
                            if (oldItems.isEmpty()) {
                                offset = page.offset
                                baseline = page.baseline
                                current.copy(
                                    items = pageItems,
                                    refreshInsertedCount = 0,
                                    refreshSerial = current.refreshSerial + 1,
                                    isInitialLoading = false,
                                    isRefreshing = false,
                                    isLoadingMore = false,
                                    isEnd = !page.hasMore || page.items.isEmpty(),
                                )
                            } else {
                                // 刷新不清空列表：只把这次真正新增的动态叠到最前面，
                                // 拼接边界留给列表画「回到顶部」的占位。
                                val knownIds = oldItems.mapTo(HashSet()) { it.id }
                                val fresh = pageItems
                                    .filterNot { it.id in knownIds }
                                    .distinctBy { it.id }
                                if (offset.isEmpty()) {
                                    offset = page.offset
                                }
                                if (page.baseline.isNotEmpty()) {
                                    baseline = page.baseline
                                }
                                if (fresh.isNotEmpty()) {
                                    current.copy(
                                        items = fresh + oldItems,
                                        refreshInsertedCount = fresh.size,
                                        refreshSerial = current.refreshSerial + 1,
                                        isInitialLoading = false,
                                        isRefreshing = false,
                                        isLoadingMore = false,
                                        isEnd = false,
                                    )
                                } else {
                                    current.copy(
                                        refreshInsertedCount = 0,
                                        isInitialLoading = false,
                                        isRefreshing = false,
                                        isLoadingMore = false,
                                    )
                                }
                            }
                        } else {
                            offset = page.offset
                            baseline = page.baseline
                            current.copy(
                                items = (current.items + pageItems).distinctBy { item -> item.id },
                                isInitialLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                isEnd = !page.hasMore || page.items.isEmpty(),
                            )
                        }
                    }
                    enricher.enrich(page.items)
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

    /** 刷新回来的卡片还带着接口旧状态时，用这次会话里点过的结果盖回去。 */
    private fun applyLikeOverrides(items: List<DynamicItemUi>): List<DynamicItemUi> {
        if (likedOverrides.isEmpty()) return items
        return items.map { item ->
            val liked = likedOverrides[item.dynamicId] ?: return@map item
            if (item.liked == liked) item else item.withLike(item.dynamicId, liked)
        }
    }
}

/** 只改目标动态自己的点赞态，转发里嵌着的原动态不动。 */
private fun DynamicItemUi.withLike(dynamicId: String, liked: Boolean): DynamicItemUi {
    if (this.dynamicId != dynamicId || this.liked == liked) return this
    val delta = if (liked) 1 else -1
    return copy(liked = liked, likeCount = (likeCount + delta).coerceAtLeast(0))
}
