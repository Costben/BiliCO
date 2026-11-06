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
import erl.bilico.data.model.DynamicArticleCard
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.repository.DynamicFeedType
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.Route
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.FullPageRefreshHoldMillis
import erl.bilico.ui.components.FullPageRefreshTexts
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SegmentCapsule
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
    onCollapse: (Float) -> Unit,
) {
    val viewModel: DynamicViewModel = viewModel {
        DynamicViewModel(container.dynamic, container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()
    val loggedIn = account != null

    LaunchedEffect(loggedIn) { viewModel.onLoginStateChanged(loggedIn) }

    var viewer by remember { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }

    if (!loggedIn) {
        Box(Modifier.fillMaxSize()) {
            EmptyState(
                text = "登录后查看动态",
                modifier = Modifier.fillMaxSize(),
                action = {
                    TextButton(text = "去登录", onClick = { nav.popToMe() })
                },
            )
        }
        return
    }

    DynamicContent(
        state = state,
        viewModel = viewModel,
        topInset = topInset,
        onCollapse = onCollapse,
        onOpenUser = { nav.push(Route.Space(it)) },
        onOpenVideo = { nav.push(Route.Video(it)) },
        onOpenImage = { images, index -> viewer = images to index },
        onOpenContent = { card ->
            if (card.articleId > 0L) {
                nav.push(Route.Article(card.articleId, card.title))
            } else if (card.dynamicId.isNotBlank()) {
                nav.push(Route.Opus(card.dynamicId, card.title))
            }
        },
    )

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
    onCollapse: (Float) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    onOpenContent: (DynamicArticleCard) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }

    // 和推荐流同一套折叠规则：翻过第一条就收起，回到顶部才展开。
    LaunchedEffect(listState) {
        snapshotFlow {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || first.index > 0) titleHeightPx
            else (-first.offset).toFloat().coerceIn(0f, titleHeightPx)
        }
            .distinctUntilChanged()
            .collect(onCollapse)
    }
    LaunchedEffect(listState) {
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

    Box(Modifier.fillMaxSize()) {
        FullPagePullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { scope.launch { viewModel.refresh() } },
            modifier = Modifier.fillMaxSize(),
            state = pullState,
            contentPadding = PaddingValues(top = segmentTop),
            colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
            refreshTexts = FullPageRefreshTexts,
            completeHoldDurationMillis = FullPageRefreshHoldMillis,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = segmentTop + 40.dp,
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
                        onOpenContent = onOpenContent,
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

        // 分段胶囊浮在列表上方，和顶栏玻璃同一层，不随内容滚动。
        SegmentCapsule(
            labels = FILTERS.map { it.second },
            selectedIndex = FILTERS.indexOfFirst { it.first == state.filter }.coerceAtLeast(0),
            onSelect = { index -> viewModel.setFilter(FILTERS[index].first) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = segmentTop),
        )
    }
}

private val FILTERS = listOf(
    DynamicFeedType.ALL to "全部",
    DynamicFeedType.VIDEO to "视频",
)
