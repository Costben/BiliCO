package erl.bilico.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import erl.bilico.platform.LocalScreenConfig
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.repository.RankingRepository
import erl.bilico.di.AppContainer
import erl.bilico.ui.LocalTopBarBounds
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.attachToRow
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.FullPageRefreshHoldMillis
import erl.bilico.ui.components.FullPageRefreshTexts
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.expandOrigin
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HotFeedUiState(
    val items: List<FeedVideo> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class HotFeedViewModel(
    private val repository: RankingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HotFeedUiState())
    val state: StateFlow<HotFeedUiState> = _state.asStateFlow()

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
            repository.popular(page = 1)
                .onSuccess { (list, noMore) ->
                    nextPage = 2
                    _state.update {
                        it.copy(
                            items = list,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isEnd = noMore || list.isEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "热门列表加载失败",
                        )
                    }
                }
            inFlight = false
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (inFlight || snapshot.isEnd || snapshot.items.isEmpty()) return
        inFlight = true
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            repository.popular(page = nextPage)
                .onSuccess { (list, noMore) ->
                    nextPage++
                    _state.update { current ->
                        current.copy(
                            items = (current.items + list).distinctBy { it.bvid },
                            isLoadingMore = false,
                            isEnd = noMore || list.isEmpty(),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingMore = false) }
                }
            inFlight = false
        }
    }
}

/** 热门分段：全站综合热门分页视频流。 */
@Composable
internal fun HotFeedSection(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    listState: LazyGridState,
    active: Boolean,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
) {
    BindScrollToTop(listState, enabled = active)
    val viewModel: HotFeedViewModel = viewModel { HotFeedViewModel(container.ranking) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val blocks by container.blocks.snapshot.collectAsStateWithLifecycle()
    val visibleItems = remember(state.items, blocks) {
        state.items.filter { blocks.shouldShowFeedVideo(it) }
    }
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val density = LocalDensity.current
    val screenWidthDp = LocalScreenConfig.current.screenWidthDp
    val coverAspect by container.settings.feed.coverAspect.collectAsStateWithLifecycle()
    val coverWidthPx = remember(screenWidthDp, density) {
        with(density) { ((screenWidthDp.dp - 34.dp) / 2f).roundToPx() }.coerceAtMost(600)
    }
    val coverHeightPx = remember(coverWidthPx, coverAspect) {
        (coverWidthPx / coverAspect.ratio).toInt()
    }
    val titleHeightPx = with(density) { TitleRowHeight.toPx() }

    LaunchedEffect(listState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || first.index > 0) titleHeightPx
            else (-first.offset.y).toFloat().coerceIn(0f, titleHeightPx)
        }
            .distinctUntilChanged()
            .collect(onCollapse)
    }

    val refreshBaseline = remember { mutableIntStateOf(refreshTick) }
    LaunchedEffect(refreshTick) {
        if (refreshTick == refreshBaseline.intValue || !active) return@LaunchedEffect
        pullState.snapToRefreshing { viewModel.refresh() }
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            if (listState.firstVisibleItemIndex > 6) listState.scrollToItem(6)
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = visibleItems.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 6) {
                    viewModel.loadMore()
                }
            }
    }

    FullPagePullToRefresh(
        isRefreshing = state.isRefreshing,
        onRefresh = { scope.launch { viewModel.refresh() } },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        contentPadding = PaddingValues(top = topInset + FeedSegmentRowHeight),
        colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Fixed(FeedColumns),
            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = topInset + FeedSegmentRowHeight,
                end = 12.dp,
                bottom = 66.dp + navBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visibleItems.isEmpty()) {
                item(key = "state", span = { GridItemSpan(maxLineSpan) }) {
                    when {
                        state.isInitialLoading -> LoadingState()
                        state.error != null -> ErrorState(state.error.orEmpty()) { viewModel.refresh() }
                        else -> EmptyState("暂时没有热门视频")
                    }
                }
            }

            items(
                items = visibleItems,
                key = { it.bvid },
                contentType = { "hot-card" },
            ) { video ->
                val origin = rememberExpandOrigin()
                val topBar = LocalTopBarBounds.current
                SideEffect {
                    origin.attachToRow(listState.asRowScrollState(), video.bvid, topBar)
                }
                FeedCard(
                    video = video,
                    aspect = coverAspect,
                    coverWidthPx = coverWidthPx,
                    coverHeightPx = coverHeightPx,
                    modifier = Modifier.expandOrigin(origin),
                    onOpen = { nav.push(Route.Video(video.bvid), origin) },
                    onWatchLater = rememberWatchLaterAction(container, video),
                )
            }

            if (visibleItems.isNotEmpty()) {
                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            state.isLoadingMore -> LoadingState()
                            state.isEnd -> Text(
                                text = "没有更多了",
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
