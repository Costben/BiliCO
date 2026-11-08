package erl.bilico.feature.dynamic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import erl.bilico.feature.live.LiveListViewModel
import erl.bilico.feature.live.LiveRoomCard
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

/**
 * 动态页的「直播」筛选：只看关注的主播里当前在播的那批。
 *
 * 数据来自直播关注列表接口，和动态流没有关系，所以走独立的分页 ViewModel，
 * 但列表外壳、下拉刷新与顶栏折叠规则跟动态流保持一致。
 */
@Composable
internal fun DynamicLiveTab(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    active: Boolean,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
) {
    val viewModel: LiveListViewModel = viewModel(key = "dynamic-live") {
        LiveListViewModel(container.live, following = true)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }

    LaunchedEffect(gridState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || first.index > 0) titleHeightPx
            else (-first.offset.y).toFloat().coerceIn(0f, titleHeightPx)
        }
            .distinctUntilChanged()
            .collect(onCollapse)
    }
    LaunchedEffect(gridState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 4) {
                    viewModel.loadMore()
                }
            }
    }

    val segmentTop = topInset + 8.dp

    // 双击「直播」筛选或双击顶栏：回到第一条并重新拉一遍。
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0 || !active) return@LaunchedEffect
        gridState.scrollToItem(0)
        viewModel.refresh()
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
        // 和网格一样让出分段胶囊高度，刷新球才不会被胶囊压住。
        contentPadding = PaddingValues(top = segmentTop + FeedSegmentRowHeight),
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
                top = segmentTop + FeedSegmentRowHeight,
                end = 12.dp,
                bottom = 66.dp + navBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.items.isEmpty()) {
                item(key = "state", span = { GridItemSpan(2) }) {
                    Box(Modifier.fillMaxWidth()) {
                        when {
                            state.isInitialLoading -> LoadingState()
                            state.error != null -> ErrorState(state.error.orEmpty()) {
                                scope.launch { viewModel.refresh() }
                            }

                            else -> EmptyState("关注的主播暂时都没在播")
                        }
                    }
                }
            }
            items(
                items = state.items,
                key = { it.roomId },
                contentType = { "dynamic-live-card" },
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
