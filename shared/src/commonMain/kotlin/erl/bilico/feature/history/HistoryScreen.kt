package erl.bilico.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.HistoryBusiness
import erl.bilico.data.model.HistoryItem
import erl.bilico.data.model.HistoryStat
import erl.bilico.di.AppContainer
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.showToast
import erl.bilico.ui.AppBarSurface
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.FullPageRefreshHoldMillis
import erl.bilico.ui.components.FullPageRefreshTexts
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.PageTabRow
import erl.bilico.ui.components.SearchClearButton
import erl.bilico.ui.components.SecondaryPagerPage
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pagerTopBarScrollAlpha
import erl.bilico.ui.topBarScrollPx
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val HEARTBEAT_SETTLE_DELAY_MS = 800L

@Composable
fun HistoryScreen(
    container: AppContainer,
    nav: NavState,
) {
    val loggedIn by container.session.account.collectAsStateWithLifecycle()
    val accountMid = loggedIn?.mid
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(container.history) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val pagerState = rememberPagerState(pageCount = { HistoryTab.entries.size })
    val tabListStates = HistoryTab.entries.associateWith { rememberLazyListState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val isTop = LocalRouteIsTop.current
    val isSettledTop = isTop && nav.stack.lastOrNull()?.route == Route.History
    val hasMinimized = nav.minimizedEntry != null

    LaunchedEffect(accountMid) {
        viewModel.syncAccount(accountMid)
    }

    LaunchedEffect(pagerState.currentPage, accountMid) {
        viewModel.syncAccount(accountMid)
        if (accountMid != null) {
            viewModel.ensureTabLoaded(HistoryTab.entries[pagerState.currentPage])
        }
    }

    // 初值给 false：每次重新打开历史页或从子页面（视频/番剧/直播/专栏）返回栈顶时静默刷新，
    // 解决 Activity 级 ViewModel 复用导致的历史记录长期停留在旧数据的问题。
    var wasSettledTop by remember { mutableStateOf(false) }
    var hadLeftTop by remember { mutableStateOf(false) }
    LaunchedEffect(isSettledTop, accountMid) {
        viewModel.syncAccount(accountMid)
        if (accountMid == null) {
            wasSettledTop = false
            return@LaunchedEffect
        }
        if (isSettledTop && !wasSettledTop) {
            val returningFromChild = hadLeftTop
            wasSettledTop = true
            viewModel.onEnterOrReturn(HistoryTab.entries[pagerState.currentPage])
            if (returningFromChild) {
                // 播放页退场销毁时异步补发末次心跳，稍后再静默对齐一次以拿到最新进度。
                delay(HEARTBEAT_SETTLE_DELAY_MS)
                viewModel.silentRefresh(HistoryTab.entries[pagerState.currentPage])
            }
        } else if (!isSettledTop) {
            if (wasSettledTop) hadLeftTop = true
            wasSettledTop = false
        }
    }

    // 停留在历史页期间关闭后台播放悬浮球时，播放器刚补发末次心跳，静默同步一次进度。
    var hadMinimized by remember { mutableStateOf(hasMinimized) }
    LaunchedEffect(hasMinimized, isSettledTop, accountMid) {
        if (accountMid != null && isSettledTop && hadMinimized && !hasMinimized) {
            delay(HEARTBEAT_SETTLE_DELAY_MS)
            viewModel.silentRefresh(HistoryTab.entries[pagerState.currentPage])
        }
        hadMinimized = hasMinimized
    }

    val currentAccountMid by rememberUpdatedState(accountMid)
    val currentIsSettledTop by rememberUpdatedState(isSettledTop)
    DisposableEffect(lifecycleOwner) {
        var seenPause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> seenPause = true
                Lifecycle.Event.ON_RESUME -> {
                    if (seenPause && currentIsSettledTop && currentAccountMid != null) {
                        seenPause = false
                        viewModel.onEnterOrReturn(HistoryTab.entries[pagerState.currentPage])
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openHistoryItem(item: HistoryItem) {
        when (item.businessType) {
            HistoryBusiness.LIVE -> {
                if (item.targetId > 0L) nav.push(Route.LiveRoom(item.targetId))
            }
            HistoryBusiness.ARTICLE -> {
                if (item.targetId > 0L) nav.push(Route.Article(cvId = item.targetId, title = item.title))
            }
            HistoryBusiness.VIDEO -> {
                if (item.isPgc) {
                    nav.push(Route.Pgc(item.seasonId, item.epId, item.progressSec.toLong()))
                } else {
                    nav.push(Route.Video(item.bvid, item.progressSec.toLong(), item.cid))
                }
            }
        }
    }

    SecondaryPagerPage(
        title = "历史记录",
        onBack = nav::pop,
        pagerState = pagerState,
        topBar = {
            val density = LocalDensity.current
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val currentTab = HistoryTab.entries[pagerState.currentPage]
            Box(Modifier.fillMaxWidth()) {
                AppBarSurface(
                    modifier = Modifier.matchParentSize(),
                    containerColor = pageContainerColor(),
                    scrollFraction = {
                        pagerTopBarScrollAlpha(pagerState, HistoryTab.entries.size, density) { idx ->
                            tabListStates[HistoryTab.entries[idx]]?.topBarScrollPx() ?: 0f
                        }
                    },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarTop),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TopAppBarSearchBar(
                            query = state.searchQuery,
                            onQueryChange = viewModel::onSearchQueryChange,
                            expanded = state.searchActive,
                            onExpandedChange = { active ->
                                if (active) viewModel.openSearch() else viewModel.closeSearch()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = "过滤${currentTab.label}记录",
                            cancelText = "取消",
                            onSearch = { /* 实时 filter 过滤，无需额外触发网络搜索 */ },
                            onCancel = viewModel::closeSearch,
                            searchButtonContentDescription = "搜索历史记录",
                            gradientColor = Color.Transparent,
                            titleContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BackIconButton(onClick = nav::pop)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "历史记录",
                                        style = COUITheme.textStyles.title2,
                                        color = COUITheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            },
                            extraActions = {
                                Box {
                                    HighlightedIconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.couiTopAppBarActionItem(),
                                    ) {
                                        Icon(COUIIcons.More, "更多", Modifier.size(24.dp))
                                    }
                                    CouiPopupMenu(
                                        show = menuExpanded,
                                        onDismiss = { menuExpanded = false },
                                        items = listOf(
                                            CouiMenuItem(
                                                label = "清空历史记录",
                                                onClick = {
                                                    if (!container.session.isLoggedIn()) {
                                                        showToast(context, "登录后可用")
                                                    } else {
                                                        confirmClear = true
                                                    }
                                                },
                                            ),
                                            CouiMenuItem(
                                                label = if (state.isPaused) "恢复观看记录" else "暂停观看记录",
                                                onClick = {
                                                    scope.launch {
                                                        viewModel.togglePaused()
                                                            .onSuccess { paused ->
                                                                showToast(context, if (paused) "已暂停记录观看历史" else "已恢复记录观看历史")
                                                            }
                                                            .onFailure { err ->
                                                                showToast(context, err.message ?: "操作失败")
                                                            }
                                                    }
                                                },
                                            ),
                                        ),
                                    )
                                }
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    SearchClearButton(
                                        onClick = { viewModel.onSearchQueryChange("") },
                                    )
                                }
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                    ) {
                        PageTabRow(
                            tabs = HistoryTab.entries.map { it.label },
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { index ->
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        },
        header = {},
    ) { pageIndex, contentTop ->
        val tab = HistoryTab.entries[pageIndex]
        val tabState = state.tabs[tab] ?: HistoryTabState()
        val filteredItems = remember(tabState.items, state.searchQuery) {
            state.filteredItems(tab)
        }
        HistoryTabList(
            loggedIn = loggedIn != null,
            tab = tab,
            active = pageIndex == pagerState.currentPage,
            listState = tabListStates.getValue(tab),
            tabState = tabState,
            items = filteredItems,
            isFiltering = state.searchQuery.isNotBlank(),
            stats = state.stats,
            isPaused = state.isPaused,
            contentTop = contentTop,
            onGoLogin = nav::popToMe,
            onRefresh = { viewModel.refresh(tab) },
            onLoadMore = { viewModel.loadMore(tab) },
            onRequestStats = { indices -> viewModel.requestStats(filteredItems, indices) },
            onResumeHistory = {
                scope.launch {
                    viewModel.togglePaused().onFailure { err ->
                        showToast(context, err.message ?: "恢复失败")
                    }
                }
            },
            onOpenItem = ::openHistoryItem,
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空历史记录",
            message = "确定要清空所有观看历史吗？清空后无法恢复。",
            confirmText = "清空",
            onDismiss = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                scope.launch {
                    viewModel.clearAllHistory()
                        .onSuccess {
                            showToast(context, "已清空历史记录")
                        }
                        .onFailure { err ->
                            showToast(context, err.message ?: "清空失败")
                        }
                }
            },
        )
    }
}

private class TopSnapState {
    var lastHeadKey: String? = null
    var pendingSnap: Boolean = false
}

@Composable
private fun HistoryTabList(
    loggedIn: Boolean,
    tab: HistoryTab,
    active: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    tabState: HistoryTabState,
    items: List<HistoryItem>,
    isFiltering: Boolean,
    stats: Map<String, HistoryStat>,
    isPaused: Boolean,
    contentTop: Dp,
    onGoLogin: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRequestStats: (List<Int>) -> Unit,
    onResumeHistory: () -> Unit,
    onOpenItem: (HistoryItem) -> Unit,
) {
    BindScrollToTop(listState, enabled = active)
    val pullState = rememberFullPagePullToRefreshState()

    // 用户原本停在顶部时，若静默刷新在表头插入了新看过的记录，保持视口在顶部以便直接看到新条目；
    // 用户已经往下翻阅时，交给 LazyColumn 按 key 锚定原滚动位置不跳动。
    val topSnapState = remember(tab) { TopSnapState() }
    val headKey = if (isFiltering) null else items.firstOrNull()?.key
    if (!isFiltering && headKey != topSnapState.lastHeadKey) {
        if (topSnapState.lastHeadKey != null &&
            headKey != null &&
            listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset == 0
        ) {
            topSnapState.pendingSnap = true
        }
        topSnapState.lastHeadKey = headKey
    }
    LaunchedEffect(headKey) {
        if (topSnapState.pendingSnap) {
            topSnapState.pendingSnap = false
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState, items.size, isFiltering) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = items.size
                if (!isFiltering && lastVisible != null && size > 0 && lastVisible >= size - 3) {
                    onLoadMore()
                }
            }
    }

    if (tab == HistoryTab.VIDEO) {
        LaunchedEffect(listState, items) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect { indices ->
                    if (items.isNotEmpty()) onRequestStats(indices)
                }
        }
    }

    val listContent: @Composable () -> Unit = {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = contentTop,
                end = 12.dp,
                bottom = 24.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!loggedIn) {
                item(key = "login-${tab.name}") {
                    EmptyState(
                        text = "登录后查看",
                        action = { TextButton(text = "去登录", onClick = onGoLogin) },
                    )
                }
                return@LazyColumn
            }
            if (isPaused) {
                item(key = "paused-banner-${tab.name}") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = CardDefaults.GroupCornerRadius,
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        weight = CardDefaults.GroupWeight,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "已暂停记录观看历史",
                                fontSize = 13.sp,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "恢复记录",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = COUITheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(SquircleShape(6.dp))
                                    .clickable(onClick = onResumeHistory)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                item(key = "state-${tab.name}") {
                    when {
                        tabState.items.isEmpty() && tabState.isInitialLoading -> LoadingState()
                        tabState.items.isEmpty() && tabState.error != null -> ErrorState(tabState.error, onRetry = onRefresh)
                        isFiltering && tabState.items.isNotEmpty() -> EmptyState("没有找到匹配的${tab.label}观看记录")
                        else -> EmptyState("还没有${tab.label}观看记录")
                    }
                }
            } else {
                items(
                    count = items.size,
                    key = { items[it].key },
                    contentType = { "history-row" },
                ) { index ->
                    val item = items[index]
                    HistoryRow(
                        item = item,
                        stat = stats[item.key],
                        onClick = { onOpenItem(item) },
                    )
                }
                if (!isFiltering && tabState.isLoadingMore) {
                    item(key = "loading-more-${tab.name}") { LoadingState() }
                }
            }
        }
    }

    if (loggedIn && !isFiltering) {
        FullPagePullToRefresh(
            isRefreshing = tabState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            state = pullState,
            contentPadding = PaddingValues(top = contentTop),
            colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
            refreshTexts = FullPageRefreshTexts,
            completeHoldDurationMillis = FullPageRefreshHoldMillis,
            content = listContent,
        )
    } else {
        listContent()
    }
}
