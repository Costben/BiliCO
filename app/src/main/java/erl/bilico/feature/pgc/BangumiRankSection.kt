package erl.bilico.feature.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.BangumiCard
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.feed.FeedSegmentRowHeight
import erl.bilico.ui.NavState
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.Route
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.FullPageRefreshHoldMillis
import erl.bilico.ui.components.FullPageRefreshTexts
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 番剧分段：默认给出番剧区排行榜，点封面进详情。 */
@Composable
fun BangumiRankSection(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    gridState: LazyGridState,
    active: Boolean,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
) {
    val viewModel: BangumiRankViewModel = viewModel { BangumiRankViewModel(container.pgc) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val coverWidthPx = remember(screenWidthDp, density) {
        with(density) { (screenWidthDp.dp / 2 - 40.dp).roundToPx() }.coerceAtMost(600)
    }
    val coverHeightPx = coverWidthPx * 4 / 3
    val titleHeightPx = with(density) { TitleRowHeight.toPx() }

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

    // 双击当前分段：直接停在被下拉位置刷新，回顶对齐该位置，刷新完再回顶。
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
            state = gridState,
            columns = GridCells.Fixed(2),
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
                item(key = "state", span = { GridItemSpan(2) }) {
                    Box(Modifier.fillMaxWidth()) {
                        when {
                            state.isLoading -> LoadingState()
                            state.error != null -> ErrorState(state.error.orEmpty()) {
                                viewModel.refresh()
                            }

                            else -> EmptyState("暂时没有番剧排行")
                        }
                    }
                }
            }
            items(
                items = state.items,
                key = { it.seasonId },
                contentType = { "bangumi-card" },
            ) { card ->
                BangumiRankCard(
                    card = card,
                    coverWidthPx = coverWidthPx,
                    coverHeightPx = coverHeightPx,
                    onClick = { nav.push(Route.Pgc(card.seasonId, 0L)) },
                )
            }
        }
    }
}

@Composable
internal fun BangumiRankCard(
    card: BangumiCard,
    coverWidthPx: Int,
    coverHeightPx: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = onClick,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            CoverImage(
                url = Formatters.sizedImageUrl(card.cover, coverWidthPx, coverHeightPx),
                modifier = Modifier.fillMaxSize(),
            )
            if (card.badge.isNotBlank()) {
                Text(
                    text = card.badge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = card.title,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = COUITheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = card.subtitle,
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            if (card.rating.isNotBlank()) {
                Text(
                    text = card.rating,
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.primary,
                )
            }
        }
    }
}
