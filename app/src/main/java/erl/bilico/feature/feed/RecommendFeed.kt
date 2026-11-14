package erl.bilico.feature.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.util.Formatters
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
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

/** 列表末尾还剩这么多项时就开始拉下一页（两列，提前约三行）。 */
private const val FeedPrefetchDistance = 6

/** 每次列表变化时，把末尾这么多张封面提前解码进内存缓存。 */
private const val CoverPrefetchCount = 16

/** 刷新拼接线的固定 key，重排时不会和视频卡片抢位置。 */
private const val RefreshDividerKey = "feed-refresh-divider"

/** 刷新换批时旧列表淡出、新列表淡入的时长。 */
private const val SwapFadeOutMillis = 90
private const val SwapFadeInMillis = 150

/** 首屏大约能看到的卡片数，淡出的同时先把这些封面拉起来。 */
private const val SwapCoverCount = 8

/** 列表当前真正画出来的那一批；刷新结果要等淡出完才换进来。 */
private data class ShownFeed(
    val items: List<FeedVideo>,
    val inserted: Int,
    val serial: Int,
)

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
        with(density) { ((screenWidthDp.dp - 34.dp) / 2f).roundToPx() }.coerceAtMost(600)
    }
    val coverHeightPx = coverWidthPx * 9 / 16

    // 列表末尾的封面提前解码进内存缓存：滑到那儿时图已经在，不会当着面加载。
    val context = LocalContext.current
    LaunchedEffect(state.items.size, coverWidthPx) {
        val tail = state.items.takeLast(CoverPrefetchCount)
        if (tail.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        tail.forEach { video ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx))
                    .size(coverWidthPx, coverHeightPx)
                    .build(),
            )
        }
    }

    // 刷新结果不直接换上：旧列表原地淡出，看不见时换成新批次并回到第一条，再淡入。
    // 这样不会先在顶上冒出拼接占位、再从中间闪回顶部。
    var shown by remember {
        mutableStateOf(ShownFeed(state.items, state.refreshInsertedCount, state.refreshSerial))
    }
    val listAlpha = remember { Animatable(1f) }
    LaunchedEffect(state.items, state.refreshSerial) {
        val next = ShownFeed(state.items, state.refreshInsertedCount, state.refreshSerial)
        if (next.serial == shown.serial || shown.items.isEmpty()) {
            shown = next
            listAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        val loader = SingletonImageLoader.get(context)
        next.items.take(SwapCoverCount).forEach { video ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx))
                    .size(coverWidthPx, coverHeightPx)
                    .build(),
            )
        }
        listAlpha.animateTo(0f, tween(SwapFadeOutMillis))
        shown = next
        listState.scrollToItem(0)
        listAlpha.animateTo(1f, tween(SwapFadeInMillis))
    }

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

    // 双击底栏当前 Tab / 双击分段标题：直接停在被下拉位置刷新，回顶对齐该位置，刷新完再回顶。
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

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - FeedPrefetchDistance) {
                    viewModel.loadMore()
                }
            }
    }

    FullPagePullToRefresh(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() },
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
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .graphicsLayer { alpha = listAlpha.value },
            contentPadding = PaddingValues(
                start = 12.dp,
                top = topInset + FeedSegmentRowHeight,
                end = 12.dp,
                bottom = 66.dp + navBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shown.items.isEmpty()) {
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
            val inserted = shown.inserted.coerceIn(0, shown.items.size)
            val freshItems = shown.items.take(inserted)
            val olderItems = shown.items.drop(inserted)
            val card: @Composable (FeedVideo) -> Unit = { video ->
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
            items(
                items = freshItems,
                key = { it.bvid },
                contentType = { "feed-card" },
            ) { card(it) }
            if (inserted in 1 until shown.items.size) {
                item(key = RefreshDividerKey, span = { GridItemSpan(maxLineSpan) }) {
                    RefreshDivider(onBackToTop = { scope.launch { listState.animateScrollToItem(0) } })
                }
            }
            items(
                items = olderItems,
                key = { it.bvid },
                contentType = { "feed-card" },
            ) { card(it) }
            if (shown.items.isNotEmpty()) {
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

/**
 * 推荐流刷新后的拼接占位：整块和一张卡片差不多高，提示这批新内容看完了。
 * 点一下滚回列表顶部。
 */
@Composable
private fun RefreshDivider(onBackToTop: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        insideMargin = PaddingValues(0.dp),
        onClick = onBackToTop,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "本次刷新的内容已看完",
                fontSize = 12.sp,
                color = scheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "回到顶部",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.primary,
            )
        }
    }
}
