package erl.bilico.feature.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.feature.pgc.BangumiRankSection
import erl.bilico.feature.rank.RankingSection
import erl.bilico.ui.NavState
import erl.bilico.ui.components.SegmentCapsule

/** 推荐 tab 的三段：推荐流、排行榜、番剧。分段胶囊浮在列表上方，切换时列表位置各自保留。 */
@Composable
fun FeedScreen(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    onCollapse: (Float) -> Unit,
    onScrollIdle: () -> Unit,
) {
    var segment by rememberSaveable { mutableIntStateOf(0) }
    val recommendState = rememberLazyListState()
    val rankingState = rememberLazyListState()
    val bangumiState = rememberLazyGridState()
    val segmentTop = topInset + 8.dp

    // 标题行的折叠只属于推荐流，切走时把它展开。
    LaunchedEffect(segment) {
        if (segment != 0) onCollapse(0f)
    }

    Box(Modifier.fillMaxSize()) {
        when (segment) {
            0 -> RecommendFeed(
                container = container,
                nav = nav,
                topInset = segmentTop,
                listState = recommendState,
                onCollapse = onCollapse,
                onScrollIdle = onScrollIdle,
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

        SegmentCapsule(
            labels = FEED_SEGMENTS,
            selectedIndex = segment,
            onSelect = { segment = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = segmentTop),
        )
    }
}

private val FEED_SEGMENTS = listOf("推荐", "排行榜", "番剧")
