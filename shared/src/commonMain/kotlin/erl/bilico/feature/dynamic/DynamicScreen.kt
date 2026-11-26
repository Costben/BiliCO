package erl.bilico.feature.dynamic

import erl.bilico.platform.showToast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicHotReply
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.repository.DynamicFeedType
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.CommentComposerSheet
import erl.bilico.feature.feed.FeedSegmentRowHeight
import erl.bilico.ui.ExpandOrigin
import erl.bilico.ui.LocalImageOriginRowKey
import erl.bilico.ui.LocalImageOriginScope
import erl.bilico.ui.NavState
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.Route
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.rememberImageOriginScope
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.FullPageRefreshHoldMillis
import erl.bilico.ui.components.FullPageRefreshTexts
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.RefreshDivider
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.topBarScrollPx
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

class DynamicScrollStates(
    val all: LazyListState,
    val video: LazyListState,
    val live: LazyGridState,
) {
    internal fun scrollPxFor(filter: DynamicFilter): Float = when (filter) {
        DynamicFilter.ALL -> all.topBarScrollPx()
        DynamicFilter.VIDEO -> video.topBarScrollPx()
        DynamicFilter.LIVE -> live.topBarScrollPx()
    }
}

@Composable
fun rememberDynamicScrollStates(): DynamicScrollStates {
    val all = rememberLazyListState()
    val video = rememberLazyListState()
    val live = rememberLazyGridState()
    return remember(all, video, live) { DynamicScrollStates(all, video, live) }
}

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
    scrollStates: DynamicScrollStates = rememberDynamicScrollStates(),
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val loggedIn = account != null
    val currentMid = account?.mid ?: container.session.mid()
    val context = LocalPlatformContext.current
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
                    gridState = scrollStates.live,
                )

                DynamicFilter.ALL, DynamicFilter.VIDEO -> {
                    val type = if (filter == DynamicFilter.ALL) DynamicFeedType.ALL else DynamicFeedType.VIDEO
                    val tabListState = if (filter == DynamicFilter.ALL) scrollStates.all else scrollStates.video
                    // 每个筛选一份自己的列表：滑到对面那页时内容已经加载好，不会先空一下再重新拉。
                    val viewModel: DynamicViewModel = viewModel(key = "dynamic-${type.name}") {
                        DynamicViewModel(container.dynamic, container.session, type)
                    }
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val blocks by container.blocks.snapshot.collectAsStateWithLifecycle()
                    LaunchedEffect(loggedIn) { viewModel.onLoginStateChanged(loggedIn) }

                    DynamicContent(
                        state = state,
                        blocks = blocks,
                        viewModel = viewModel,
                        listState = tabListState,
                        topInset = topInset,
                        active = pagerState.settledPage == page,
                        onCollapse = onCollapse,
                        refreshTick = refreshTick,
                        onOpenUser = { nav.push(Route.Space(it)) },
                        onOpenVideo = { bvid, origin -> nav.push(Route.Video(bvid), origin) },
                        onOpenImage = { images, index -> viewer = images to index },
                        onOpenDynamic = { item, origin ->
                            val articleId = item.article?.articleId ?: 0L
                            val dynId = item.dynamicId.ifBlank { item.article?.dynamicId.orEmpty() }
                            val headerTitle = item.title.ifBlank { item.authorName }
                            val video = item.video
                            when {
                                articleId > 0L -> nav.push(Route.Article(articleId, headerTitle))
                                dynId.isNotBlank() -> nav.push(Route.Opus(dynId, headerTitle), origin)
                                video != null -> nav.push(Route.Video(video.bvid), origin)
                            }
                        },
                        onWatchLater = onWatchLater,
                        onOpenLive = { roomId -> nav.push(Route.LiveRoom(roomId)) },
                        currentMid = currentMid,
                        onDelete = { target ->
                            viewModel.deleteDynamic(target) { _, message ->
                                showToast(context, message)
                            }
                        },
                        onLike = { target ->
                            viewModel.toggleLike(target) { ok, message ->
                                if (!ok) showToast(context, message)
                            }
                        },
                        onOpenHotReply = { target, reply, origin ->
                            val video = target.video
                            val articleId = target.article?.articleId ?: 0L
                            val dynId = target.dynamicId.ifBlank { target.article?.dynamicId.orEmpty() }
                            val headerTitle = target.title.ifBlank { target.authorName }
                            when {
                                video != null -> nav.push(
                                    Route.Video(
                                        bvid = video.bvid,
                                        commentRootId = reply.rpid,
                                        commentMatchAuthor = reply.authorName,
                                        commentMatchText = reply.contentText,
                                    ),
                                    origin,
                                )

                                articleId > 0L -> nav.push(
                                    Route.Article(
                                        cvId = articleId,
                                        title = headerTitle,
                                        commentRootId = reply.rpid,
                                        commentMatchAuthor = reply.authorName,
                                        commentMatchText = reply.contentText,
                                    ),
                                )

                                dynId.isNotBlank() -> nav.push(
                                    Route.Opus(
                                        dynamicId = dynId,
                                        title = headerTitle,
                                        commentRootId = reply.rpid,
                                        commentMatchAuthor = reply.authorName,
                                        commentMatchText = reply.contentText,
                                    ),
                                    origin,
                                )
                            }
                        },
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

/** 刷新换批时旧列表淡出、新列表淡入的时长。 */
private const val SwapFadeOutMillis = 90
private const val SwapFadeInMillis = 150

/** 刷新拼接线的固定 key，重排时不会和动态卡片抢位置。 */
private const val RefreshDividerKey = "dynamic-refresh-divider"

/** 列表当前真正画出来的那一批；刷新结果要等淡出完才换进来。 */
private data class ShownDynamic(
    val items: List<DynamicItemUi>,
    val inserted: Int,
    val serial: Int,
)

@Composable
private fun DynamicContent(
    state: DynamicUiState,
    blocks: erl.bilico.data.repository.BlockSnapshot,
    viewModel: DynamicViewModel,
    listState: LazyListState,
    topInset: Dp,
    active: Boolean,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String, ExpandOrigin?) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    onOpenDynamic: (DynamicItemUi, ExpandOrigin?) -> Unit,
    onWatchLater: (DynamicVideoCard) -> Unit,
    onOpenLive: (Long) -> Unit,
    currentMid: Long,
    onDelete: (DynamicItemUi) -> Unit,
    onLike: (DynamicItemUi) -> Unit,
    onOpenHotReply: (DynamicItemUi, DynamicHotReply, ExpandOrigin?) -> Unit,
) {
    BindScrollToTop(listState, enabled = active)
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

    // 刷新结果不直接换上：旧列表原地淡出，看不见时换成新批次并回到第一条，再淡入。
    // 这样不会先在顶上冒出拼接占位、再从中间闪回顶部。
    var shown by remember {
        mutableStateOf(ShownDynamic(state.items, state.refreshInsertedCount, state.refreshSerial))
    }
    val listAlpha = remember { Animatable(1f) }

    LaunchedEffect(state.items, state.refreshSerial) {
        val next = ShownDynamic(state.items, state.refreshInsertedCount, state.refreshSerial)
        if (next.serial == shown.serial || shown.items.isEmpty()) {
            shown = next
            listAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        listAlpha.animateTo(0f, tween(SwapFadeOutMillis))
        shown = next
        listState.scrollToItem(0)
        listAlpha.animateTo(1f, tween(SwapFadeInMillis))
    }

    // 双击底栏当前 Tab / 双击当前筛选 / 双击顶栏：直接停在被下拉位置刷新，回顶对齐该位置，刷新完再回顶。
    // 切走底栏会把这一页拆掉。进来时先记住当时的计数，回来重组不会把上次那次再刷一遍。
    val refreshBaseline = remember { mutableIntStateOf(refreshTick) }
    LaunchedEffect(refreshTick) {
        if (refreshTick == refreshBaseline.intValue || !active) return@LaunchedEffect
        pullState.snapToRefreshing { viewModel.refresh() }
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            if (listState.firstVisibleItemIndex > 6) {
                listState.scrollToItem(6)
            }
            listState.animateScrollToItem(0)
        }
    }

    val rowScrollState = remember(listState) { listState.asRowScrollState() }
    Box(Modifier.fillMaxSize()) {
        FullPagePullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
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
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .graphicsLayer { alpha = listAlpha.value },
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = segmentTop + FeedSegmentRowHeight,
                    end = 12.dp,
                    bottom = 66.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (shown.items.isEmpty()) {
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
                val inserted = shown.inserted.coerceIn(0, shown.items.size)
                val freshItems = shown.items.take(inserted).filter { blocks.shouldShowDynamic(it) }
                val olderItems = shown.items.drop(inserted).filter { blocks.shouldShowDynamic(it) }
                val card: @Composable (DynamicItemUi) -> Unit = { item ->
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalImageOriginScope provides rememberImageOriginScope(listState),
                        LocalImageOriginRowKey provides item.id,
                    ) {
                        DynamicCard(
                            item = item,
                            onOpenUser = onOpenUser,
                            onOpenVideo = onOpenVideo,
                            onOpenImage = onOpenImage,
                            onOpenDynamic = onOpenDynamic,
                            onWatchLater = onWatchLater,
                            onOpenLive = onOpenLive,
                            currentMid = currentMid,
                            onDelete = onDelete,
                            onLike = onLike,
                            onOpenHotReply = onOpenHotReply,
                            rowScrollState = rowScrollState,
                            rowKey = item.id,
                        )
                    }
                }

                items(
                    items = freshItems,
                    key = { it.id },
                    contentType = { "dynamic-card" },
                ) { card(it) }

                if (inserted in 1 until shown.items.size) {
                    item(key = RefreshDividerKey) {
                        RefreshDivider(
                            onBackToTop = {
                                viewModel.refresh()
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                        )
                    }
                }

                items(
                    items = olderItems,
                    key = { it.id },
                    contentType = { "dynamic-card" },
                ) { card(it) }

                if (shown.items.isNotEmpty()) {
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

@Composable
internal fun BoxScope.DynamicPublishOverlay(
    container: AppContainer,
    composerOpen: Boolean,
    onDismissComposer: () -> Unit,
    onPublished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2400)
            toast = null
        }
    }
    toast?.let { text ->
        Text(
            text = text,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 104.dp)
                .clip(SquircleShape(8.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = COUITheme.textStyles.footnote1,
            color = Color.White,
        )
    }
    if (composerOpen) {
        CommentComposerSheet(
            container = container,
            hint = "发布动态",
            allowPictures = true,
            submitLabel = "发布",
            allowBlankWithPictures = true,
            onSubmit = { text, pictures, atMids ->
                onDismissComposer()
                scope.launch {
                    container.dynamic.publishDynamic(text, pictures, atMids)
                        .onSuccess {
                            toast = "动态已发布"
                            onPublished()
                        }
                        .onFailure {
                            toast = it.message ?: "发布动态失败"
                        }
                }
            },
            onDismiss = onDismissComposer,
        )
    }
}
