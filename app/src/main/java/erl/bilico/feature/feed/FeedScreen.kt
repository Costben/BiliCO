package erl.bilico.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.feature.live.LiveListScreen
import erl.bilico.feature.pgc.BangumiRankSection
import erl.bilico.feature.rank.RankingSection
import erl.bilico.ui.NavState
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefreshState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 推荐 tab 的四段：推荐流、排行榜、番剧、直播。左右滑动翻页，胶囊点击也一样翻。
 *
 * 分段胶囊不在这里画：它要跟着标题行折进顶栏，得画在顶栏那一层才不会被玻璃盖住，
 * 所以 [segment] 与 [onSegment] 都由外壳持有。
 */
@Composable
fun FeedScreen(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    segment: Int,
    onSegment: (Int) -> Unit,
    onCollapse: (Float) -> Unit,
    pagerState: PagerState,
    refreshTick: Int,
    primaryTapTick: Int = 0,
) {
    val recommendState = rememberLazyGridState()
    val rankingState = rememberLazyGridState()
    val bangumiState = rememberLazyGridState()
    val liveState = rememberLazyGridState()
    val segmentTop = topInset + 8.dp

    // 点胶囊翻页；滑完一页再把选中态写回去。
    // 回写必须等翻页停稳：动画途中 currentPage 会先经过中间那页，写回去会把动画打断在半路。
    LaunchedEffect(segment) {
        if (pagerState.settledPage != segment) pagerState.animateScrollToPage(segment)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(onSegment)
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { FEED_SEGMENTS[it] },
        ) { page ->
            when (page) {
                0 -> RecommendFeed(
                    container = container,
                    nav = nav,
                    topInset = segmentTop,
                    listState = recommendState,
                    active = segment == 0,
                    onCollapse = onCollapse,
                    refreshTick = refreshTick,
                    primaryTapTick = primaryTapTick,
                )

                1 -> RankingSection(
                    container = container,
                    nav = nav,
                    topInset = segmentTop,
                    listState = rankingState,
                    active = segment == 1,
                    onCollapse = onCollapse,
                    refreshTick = refreshTick,
                    primaryTapTick = primaryTapTick,
                )

                2 -> BangumiRankSection(
                    container = container,
                    nav = nav,
                    topInset = segmentTop,
                    gridState = bangumiState,
                    active = segment == 2,
                    onCollapse = onCollapse,
                    refreshTick = refreshTick,
                    primaryTapTick = primaryTapTick,
                )

                3 -> LiveListScreen(
                    container = container,
                    nav = nav,
                    topInset = segmentTop,
                    gridState = liveState,
                    active = segment == 3,
                    onCollapse = onCollapse,
                    refreshTick = refreshTick,
                    primaryTapTick = primaryTapTick,
                )
            }
        }
    }
}

/**
 * 底栏「推荐」按钮单击响应：
 * 未在顶部时先平滑回到顶部；已在顶部（或正在回顶途中再次点击）时直接触发下拉刷新。
 */
@Composable
fun HandleFeedPrimaryTap(
    primaryTapTick: Int,
    active: Boolean,
    isRefreshing: Boolean,
    gridState: LazyGridState,
    pullState: FullPagePullToRefreshState,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var scrollToTopJob by remember { mutableStateOf<Job?>(null) }
    val baseline = remember { mutableIntStateOf(primaryTapTick) }

    LaunchedEffect(primaryTapTick) {
        if (primaryTapTick == baseline.intValue) return@LaunchedEffect
        baseline.intValue = primaryTapTick
        if (!active || isRefreshing) return@LaunchedEffect
        val wasScrollingToTop = scrollToTopJob?.isActive == true
        val atTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        if (atTop || wasScrollingToTop) {
            scrollToTopJob?.cancel()
            scrollToTopJob = null
            if (!atTop) gridState.scrollToItem(0)
            pullState.snapToRefreshing { onRefresh() }
        } else {
            scrollToTopJob = scope.launch {
                try {
                    if (gridState.firstVisibleItemIndex > 6) {
                        gridState.scrollToItem(6)
                    }
                    gridState.animateScrollToItem(0)
                } finally {
                    if (scrollToTopJob === coroutineContext[Job]) {
                        scrollToTopJob = null
                    }
                }
            }
        }
    }
}

/** 分类胶囊占掉的一条高度；列表和下拉刷新都要把它让出来。 */
internal val FeedSegmentRowHeight = 44.dp

/** 推荐与排行榜的列数：竖屏一律两列，和番剧榜对齐。 */
internal const val FeedColumns = 2

/** 推荐 tab 的四个分段标题；直播原先占一个底栏格子，现在收在这里。 */
internal val FEED_SEGMENTS = listOf("推荐", "排行榜", "番剧", "直播")
