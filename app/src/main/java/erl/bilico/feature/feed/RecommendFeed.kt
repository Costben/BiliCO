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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 推荐分段：原来的首页推荐列表，外面那层分段胶囊归 [FeedScreen]。 */
@Composable
internal fun RecommendFeed(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    listState: LazyGridState,
    active: Boolean,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
) {
    val viewModel: FeedViewModel = viewModel { FeedViewModel(container.feed) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val titleHeightPx = with(density) { TitleRowHeight.toPx() }

    val coverWidthPx = remember(screenWidthDp, density) {
        with(density) { ((screenWidthDp.dp - 34.dp) / 2f - 24.dp).roundToPx() }.coerceAtMost(600)
    }
    val coverHeightPx = coverWidthPx * 9 / 16

    // 只有自己是当前页时才报折叠量：翻走时收起来，翻回来按列表实际位置重新报一次。
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

    // 双击当前分段：回到第一条并重新拉一遍。
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0 || !active) return@LaunchedEffect
        listState.scrollToItem(0)
        viewModel.refresh()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 3) {
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = topInset + FeedSegmentRowHeight,
                end = 12.dp,
                bottom = 66.dp + navBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.items.isEmpty()) {
                item(key = "state", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth()) {
                        when {
                            state.isInitialLoading -> LoadingState()
                            state.error != null -> ErrorState(state.error.orEmpty()) {
                                scope.launch { viewModel.refresh() }
                            }

                            else -> EmptyState("暂时没有推荐内容")
                        }
                    }
                }
            }
            items(
                items = state.items,
                key = { it.bvid },
                contentType = { "feed-card" },
            ) { video ->
                val origin = rememberExpandOrigin()
                val topBar = LocalTopBarBounds.current
                SideEffect {
                    origin.attachToRow(listState.asRowScrollState(), video.bvid, topBar)
                }
                FeedCard(
                    video = video,
                    coverWidthPx = coverWidthPx,
                    coverHeightPx = coverHeightPx,
                    modifier = Modifier.expandOrigin(origin),
                    onOpen = { nav.push(Route.Video(video.bvid), origin) },
                )
            }
            if (state.items.isNotEmpty()) {
                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (state.isLoadingMore) {
                            LoadingState()
                        } else {
                            Text(
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
