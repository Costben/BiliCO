package erl.bilico.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.feature.pgc.BangumiRankSection
import erl.bilico.feature.rank.RankingSection
import erl.bilico.ui.NavState
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 推荐 tab 的三段：推荐流、排行榜、番剧。左右滑动翻页，胶囊点击也一样翻。
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
) {
    val recommendState = rememberLazyListState()
    val rankingState = rememberLazyListState()
    val bangumiState = rememberLazyGridState()
    val segmentTop = topInset + 8.dp
    val pagerState = rememberPagerState(initialPage = segment, pageCount = { FEED_SEGMENTS.size })

    // 点胶囊翻页；滑完一页再把选中态写回去。两边都盯着同一个 page，不会被对方的写入绕成环。
    LaunchedEffect(segment) {
        if (pagerState.currentPage != segment) pagerState.animateScrollToPage(segment)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect(onSegment)
    }

    // 标题行的折叠只属于推荐流，切走时把它展开。
    LaunchedEffect(segment) {
        if (segment != 0) onCollapse(0f)
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
                )

                1 -> RankingSection(
                    container = container,
                    nav = nav,
                    topInset = segmentTop,
                    listState = rankingState,
                )

                else -> BangumiRankSection(
                    container = container,
                    nav = nav,
                    topInset = segmentTop,
                    gridState = bangumiState,
                )
            }
        }
    }
}

/** 分段胶囊占掉的一条高度；列表和下拉刷新都要把它让出来。 */
internal val FeedSegmentRowHeight = 44.dp

/** 推荐 tab 的三个分段标题。 */
internal val FEED_SEGMENTS = listOf("推荐", "排行榜", "番剧")
