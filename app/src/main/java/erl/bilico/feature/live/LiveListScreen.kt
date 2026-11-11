package erl.bilico.feature.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.feature.feed.FeedSegmentRowHeight
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.FullPageRefreshHoldMillis
import erl.bilico.ui.components.FullPageRefreshTexts
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun LiveListScreen(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    refreshTick: Int,
    gridState: LazyGridState = rememberLazyGridState(),
    active: Boolean = true,
    onCollapse: ((Float) -> Unit)? = null,
) {
    val viewModel: LiveListViewModel = viewModel { LiveListViewModel(container.live) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }

    // 顶上这一条胶囊的高度要一起让出去，否则第一排卡会钻到胶囊底下。
    val contentTop = topInset + FeedSegmentRowHeight

    // 嵌在推荐页里时，滚动要把顶栏一起折起来。
    LaunchedEffect(gridState, onCollapse) {
        val report = onCollapse ?: return@LaunchedEffect
        snapshotFlow {
            val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || first.index > 0) titleHeightPx
            else (-first.offset.y).toFloat().coerceIn(0f, titleHeightPx)
        }
            .distinctUntilChanged()
            .collect(report)
    }

    // 双击顶栏：直接停在被下拉位置刷新，回顶对齐该位置，刷新完再回顶。
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0 || !active) return@LaunchedEffect
        pullState.snapToRefreshing { viewModel.refresh() }
        if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
            if (gridState.firstVisibleItemIndex > 6) {
                gridState.scrollToItem(6)
            }
            gridState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 4) {
                    viewModel.loadMore()
                }
            }
    }

    FullPagePullToRefresh(
        isRefreshing = state.isRefreshing,
        onRefresh = {
            scope.launch {
                if (gridState.firstVisibleItemIndex > 0) gridState.scrollToItem(0)
                viewModel.refresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        contentPadding = PaddingValues(top = contentTop),
        colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = contentTop + 12.dp,
                end = 12.dp,
                bottom = 66.dp + navBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.items.isEmpty()) {
                item(key = "state", span = { GridItemSpan(2) }) {
                    Box(Modifier.fillMaxWidth()) {
                        when {
                            state.isInitialLoading -> LoadingState()
                            state.error != null -> ErrorState(state.error.orEmpty()) {
                                scope.launch { viewModel.refresh() }
                            }

                            else -> EmptyState("暂时没有直播")
                        }
                    }
                }
            }
            items(
                items = state.items,
                key = { it.roomId },
                contentType = { "live-card" },
            ) { room ->
                LiveRoomCard(room = room, onClick = { nav.push(Route.LiveRoom(room.roomId)) })
            }
            if (state.items.isNotEmpty() && state.isLoadingMore) {
                item(key = "loading-more", span = { GridItemSpan(2) }) {
                    LoadingState()
                }
            }
        }
    }
}
