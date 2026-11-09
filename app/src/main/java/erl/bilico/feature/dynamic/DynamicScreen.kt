package erl.bilico.feature.dynamic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.repository.DynamicFeedType
import erl.bilico.di.AppContainer
import erl.bilico.feature.feed.FeedSegmentRowHeight
import erl.bilico.ui.NavState
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.Route
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.FullPageRefreshHoldMillis
import erl.bilico.ui.components.FullPageRefreshTexts
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun DynamicScreen(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    filterIndex: Int,
    onFilterSelect: (Int) -> Unit,
    pagerState: PagerState,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val loggedIn = account != null
    var viewer by remember { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }
    val onWatchLater = rememberDynamicWatchLater(container)

    // 点胶囊翻页；滑完一页再把选中态写回去。
    // 回写必须等翻页停稳：动画途中 currentPage 会先经过中间那页，写回去会把动画打断在半路。
    LaunchedEffect(filterIndex) {
        if (pagerState.settledPage != filterIndex) pagerState.animateScrollToPage(filterIndex)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(onFilterSelect)
    }

    if (!loggedIn) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset + 48.dp, bottom = 66.dp + navBottomPadding()),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                text = "登录后查看动态",
                action = {
                    TextButton(text = "去登录", onClick = { nav.popToMe() })
                },
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { DynamicFilters[it].second },
        ) { page ->
            val filter = DynamicFilters[page].first
            when (filter) {
                DynamicFilter.LIVE -> DynamicLiveTab(
                    container = container,
                    nav = nav,
                    topInset = topInset,
                    active = pagerState.settledPage == page,
                    onCollapse = onCollapse,
                    refreshTick = refreshTick,
                )

                DynamicFilter.ALL, DynamicFilter.VIDEO -> {
                    val type = if (filter == DynamicFilter.ALL) DynamicFeedType.ALL else DynamicFeedType.VIDEO
                    // 每个筛选一份自己的列表：滑到对面那页时内容已经加载好，不会先空一下再重新拉。
                    val viewModel: DynamicViewModel = viewModel(key = "dynamic-${type.name}") {
                        DynamicViewModel(container.dynamic, container.session, type)
                    }
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(loggedIn) { viewModel.onLoginStateChanged(loggedIn) }

                    DynamicContent(
                        state = state,
                        viewModel = viewModel,
                        topInset = topInset,
                        active = pagerState.settledPage == page,
                        onCollapse = onCollapse,
                        refreshTick = refreshTick,
                        onOpenUser = { nav.push(Route.Space(it)) },
                        onOpenVideo = { nav.push(Route.Video(it)) },
                        onOpenImage = { images, index -> viewer = images to index },
                        onOpenDynamic = { item ->
                            val articleId = item.article?.articleId ?: 0L
                            val dynId = item.dynamicId.ifBlank { item.article?.dynamicId.orEmpty() }
                            val headerTitle = item.title.ifBlank { item.authorName }
                            val video = item.video
                            when {
                                articleId > 0L -> nav.push(Route.Article(articleId, headerTitle))
                                dynId.isNotBlank() -> nav.push(Route.Opus(dynId, headerTitle))
                                video != null -> nav.push(Route.Video(video.bvid))
                            }
                        },
                        onWatchLater = onWatchLater,
                    )
                }
            }
        }
    }

    viewer?.let { (images, index) ->
        DynamicImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { viewer = null },
        )
    }
}

@Composable
private fun DynamicContent(
    state: DynamicUiState,
    viewModel: DynamicViewModel,
    topInset: Dp,
    active: Boolean,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    onOpenDynamic: (DynamicItemUi) -> Unit,
    onWatchLater: (DynamicVideoCard) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }

    // 和推荐流同一套折叠规则：翻过第一条就收起，回到顶部才展开。只有自己是当前页时才上报。
    LaunchedEffect(listState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || first.index > 0) titleHeightPx
            else (-first.offset).toFloat().coerceIn(0f, titleHeightPx)
        }
            .distinctUntilChanged()
            .collect(onCollapse)
    }
    LaunchedEffect(listState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    val segmentTop = topInset + 8.dp

    // 双击当前筛选或双击顶栏：回到第一条并重新拉一遍。
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0 || !active) return@LaunchedEffect
        listState.scrollToItem(0)
        viewModel.refresh()
    }

    Box(Modifier.fillMaxSize()) {
        FullPagePullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = {
                scope.launch {
                    // 刷新会把新内容填到最前面，列表先归位，刷完直接看到最新的那条。
                    if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
                    viewModel.refresh()
                }
            },
            modifier = Modifier.fillMaxSize(),
            state = pullState,
            // 和列表一样让出分段胶囊高度，刷新球才不会被胶囊压住。
            contentPadding = PaddingValues(top = segmentTop + FeedSegmentRowHeight),
            colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
            refreshTexts = FullPageRefreshTexts,
            completeHoldDurationMillis = FullPageRefreshHoldMillis,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = segmentTop + FeedSegmentRowHeight,
                    end = 12.dp,
                    bottom = 66.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.items.isEmpty()) {
                    item(key = "state") {
                        when {
                            state.isInitialLoading -> LoadingState()
                            state.error != null -> ErrorState(state.error.orEmpty()) {
                                scope.launch { viewModel.refresh() }
                            }

                            else -> EmptyState("暂时没有动态")
                        }
                    }
                }
                items(
                    items = state.items,
                    key = { it.id },
                    contentType = { "dynamic-card" },
                ) { item ->
                    DynamicCard(
                        item = item,
                        onOpenUser = onOpenUser,
                        onOpenVideo = onOpenVideo,
                        onOpenImage = onOpenImage,
                        onOpenDynamic = onOpenDynamic,
                        onWatchLater = onWatchLater,
                    )
                }
                if (state.items.isNotEmpty()) {
                    item(key = "footer") {
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
}

/** 动态页顶部的筛选分段；胶囊画在顶栏那一层，所以这份表外壳也要用。 */
internal enum class DynamicFilter { ALL, VIDEO, LIVE }

internal val DynamicFilters = listOf(
    DynamicFilter.ALL to "全部",
    DynamicFilter.VIDEO to "视频",
    DynamicFilter.LIVE to "直播",
)
