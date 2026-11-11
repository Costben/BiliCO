package erl.bilico.feature.rank

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.feature.feed.FeedCard
import erl.bilico.feature.feed.FeedColumns
import erl.bilico.feature.feed.FeedSegmentRowHeight
import erl.bilico.feature.feed.rememberWatchLaterAction
import erl.bilico.ui.LocalTopBarBounds
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.NavState
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.attachToRow
import erl.bilico.ui.Route
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

/** 排行榜分段：分区 chips + 整榜列表，接口一次返回全部。 */
@Composable
fun RankingSection(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    listState: LazyGridState,
    active: Boolean,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
) {
    val viewModel: RankingViewModel = viewModel { RankingViewModel(container.ranking) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val coverWidthPx = remember(screenWidthDp, density) {
        with(density) { ((screenWidthDp.dp - 34.dp) / 2f).roundToPx() }.coerceAtMost(600)
    }
    val coverHeightPx = coverWidthPx * 9 / 16
    val titleHeightPx = with(density) { TitleRowHeight.toPx() }

    // 只有换分区才回顶：重新进这一页时保留上次看到的位置。
    var lastSection by remember { mutableStateOf(state.section) }
    LaunchedEffect(state.section) {
        if (state.section != lastSection) {
            lastSection = state.section
            listState.scrollToItem(0)
        }
    }

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

    // 双击当前分段：直接停在被下拉位置刷新，回顶对齐该位置，刷新完再回顶。
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0 || !active) return@LaunchedEffect
        pullState.snapToRefreshing { viewModel.refresh() }
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            if (listState.firstVisibleItemIndex > 6) {
                listState.scrollToItem(6)
            }
            listState.animateScrollToItem(0)
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
            item(key = "sections", span = { GridItemSpan(maxLineSpan) }) {
                RankingTabs(
                    selected = state.section,
                    onSelect = viewModel::select,
                )
            }

            if (state.items.isEmpty()) {
                item(key = "state", span = { GridItemSpan(maxLineSpan) }) {
                    when {
                        state.isLoading -> LoadingState()
                        state.error != null -> ErrorState(state.error.orEmpty()) {
                            viewModel.refresh()
                        }

                        else -> EmptyState("这个分区暂时没有榜单")
                    }
                }
            }

            items(
                items = state.items,
                key = { it.bvid },
                contentType = { "rank-card" },
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
                    onWatchLater = rememberWatchLaterAction(container, video),
                )
            }
        }
    }
}

@Composable
private fun RankingTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RANKING_SECTIONS.forEachIndexed { index, section ->
            val active = index == selected
            Text(
                text = section.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) {
                            COUITheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            COUITheme.colorScheme.surfaceContainer
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                fontSize = 13.sp,
                color = if (active) {
                    COUITheme.colorScheme.primary
                } else {
                    COUITheme.colorScheme.onSurfaceSecondary
                },
            )
        }
    }
}
