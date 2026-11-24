package erl.bilico.feature.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.HistoryBusiness
import erl.bilico.data.model.HistoryItem
import erl.bilico.data.model.HistoryStat
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.AppBarSurface
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.DanmakuStatIcon
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.PageTabRow
import erl.bilico.ui.components.SearchClearButton
import erl.bilico.ui.components.SecondaryPagerPage
import erl.bilico.ui.components.StatLabel
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pagerTopBarScrollAlpha
import erl.bilico.ui.topBarScrollPx
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    container: AppContainer,
    nav: NavState,
) {
    val loggedIn by container.session.account.collectAsStateWithLifecycle()
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(container.history) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { HistoryTab.entries.size })
    val tabListStates = HistoryTab.entries.associateWith { rememberLazyListState() }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, loggedIn) {
        if (loggedIn != null) {
            viewModel.ensureTabLoaded(HistoryTab.entries[pagerState.currentPage])
        }
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
            val density = androidx.compose.ui.platform.LocalDensity.current
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
                                                        Toast.makeText(context, "登录后可用", Toast.LENGTH_SHORT).show()
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
                                                                Toast.makeText(
                                                                    context,
                                                                    if (paused) "已暂停记录观看历史" else "已恢复记录观看历史",
                                                                    Toast.LENGTH_SHORT,
                                                                ).show()
                                                            }
                                                            .onFailure { err ->
                                                                Toast.makeText(context, err.message ?: "操作失败", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, err.message ?: "恢复失败", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "已清空历史记录", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { err ->
                            Toast.makeText(context, err.message ?: "清空失败", Toast.LENGTH_SHORT).show()
                        }
                }
            },
        )
    }
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

@Composable
private fun HistoryRow(
    item: HistoryItem,
    stat: HistoryStat?,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { 132.dp.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val coverUrl = remember(item.cover, coverWidthPx, coverHeightPx) {
        Formatters.sizedImageUrl(item.cover, coverWidthPx, coverHeightPx)
    }
    val playedFraction = remember(item.progressSec, item.durationSec) {
        if (item.businessType == HistoryBusiness.VIDEO && item.durationSec > 0 && item.progressSec > 0) {
            (item.progressSec.toFloat() / item.durationSec).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(8.dp),
        onClick = onClick,
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 9f)
                    .clip(SquircleShape(8.dp)),
            ) {
                CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                if (playedFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(playedFraction)
                            .height(3.dp)
                            .background(scheme.primary),
                    )
                }
                if (item.badge.isNotBlank()) {
                    val badgeBg = if (item.businessType == HistoryBusiness.LIVE && item.liveStatus == 1) {
                        scheme.primary
                    } else {
                        Color.Black.copy(alpha = 0.65f)
                    }
                    Text(
                        text = item.badge,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(SquircleShape(5.dp))
                            .background(badgeBg)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
                    .height(132.dp * 9 / 16),
            ) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(item.upName.ifBlank { "未知 UP" })
                        val time = Formatters.formatViewDate(item.viewAtSec)
                        if (time.isNotBlank()) append(" · ").append(time)
                    },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                if (stat != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatLabel(COUIIcons.Play, Formatters.formatStat(stat.view))
                        if (stat.danmaku > 0L) {
                            Spacer(Modifier.width(10.dp))
                            StatLabel(DanmakuStatIcon, Formatters.formatStat(stat.danmaku))
                        }
                    }
                }
            }
        }
    }
}
