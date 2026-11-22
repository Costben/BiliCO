package erl.bilico.feature.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import erl.bilico.data.model.MessageKind
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.LikeOutlineIcon
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.Route
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.AtSignIcon
import erl.bilico.ui.components.BellOutlineIcon
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.MailOutlineIcon
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.LocalPlatformAppBarMaterialEnabled
import io.github.suqi8.coui.kmp.basic.SearchResultContainer
import io.github.suqi8.coui.kmp.basic.SearchResultContainerDefaults
import io.github.suqi8.coui.kmp.basic.SearchResultsRiseTrigger
import io.github.suqi8.coui.kmp.basic.ScrollToTopPage
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.rememberSearchResultsTransitionState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.GridView
import io.github.suqi8.coui.kmp.icon.extended.Reply
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun MessageScreen(
    container: AppContainer,
    nav: NavState,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val viewModel: MessageViewModel = viewModel {
        MessageViewModel(container.messages, container.im, container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val initialPageIndex = remember {
        MESSAGE_TABS.indexOfFirst { it.kind == viewModel.initialKind }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPageIndex) { MESSAGE_TABS.size }
    val scope = rememberCoroutineScope()
    val pageColor = pageContainerColor()
    val layerBackdrop = rememberLayerBackdrop()
    val isTop = LocalRouteIsTop.current
    var wasOnTop by remember { mutableStateOf(isTop) }
    LaunchedEffect(isTop) {
        if (isTop && !wasOnTop) {
            viewModel.refreshOnReturn()
        }
        wasOnTop = isTop
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200L)
            notice = null
        }
    }

    val chipTabItems = remember(state.unread) {
        MESSAGE_TABS.map { tab ->
            val unreadCount = state.unread.countOf(tab.kind)
            ChipTabData(
                label = tab.label,
                icon = tab.iconVector,
                showRedDot = unreadCount > 0,
                count = unreadCount.takeIf { it > 0 },
            )
        }
    }

    // 滑停一页就补拉这一类；点标签走的是同一个入口。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.ensureLoaded(MESSAGE_TABS[it].kind) }
    }
    LaunchedEffect(account) {
        if (account != null) viewModel.ensureLoaded(MESSAGE_TABS[pagerState.settledPage].kind)
    }

    val density = LocalDensity.current
    // 顶栏加分类栏的总高度；列表从它下面起排，静止时第一条不会被分类栏压住。
    var headerHeight by remember { mutableStateOf(HEADER_HEIGHT_FALLBACK) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageColor),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, layerBackdrop)
                .background(pageColor),
            beyondViewportPageCount = 1,
        ) { index ->
            ScrollToTopPage(active = index == pagerState.settledPage) {
                val kind = MESSAGE_TABS[index].kind
                if (kind == MessageKind.SESSION) {
                    SessionList(
                        state = state,
                        loggedIn = account != null,
                        contentTop = headerHeight + 8.dp,
                        viewModel = viewModel,
                        nav = nav,
                    )
                } else {
                    MessageList(
                        kind = kind,
                        state = state.of(kind),
                        loggedIn = account != null,
                        contentTop = headerHeight + 8.dp,
                        viewModel = viewModel,
                        nav = nav,
                        onNotice = { notice = it },
                    )
                }
            }
        }

        if (notice != null) {
            Text(
                text = notice.orEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp + navBottomPadding())
                    .clip(SquircleShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = Color.White,
            )
        }

        // 顶栏通过 bottomContent 承载分类页签栏，整块区域在同一个渐变毛玻璃材质下，
        // 从状态栏一直连贯模糊覆盖到分类栏下方，消除顶栏与分类栏之间的断层。
        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) layerBackdrop else null,
            LocalEdgeToEdge provides true,
            // 窗口级材质在本页会掉帧：分类菜单、弹层开关都会让本页的 backdrop 录制启停，
            // 系统随即丢几帧模糊（Shell 底栏当年就是同一个毛病）。改走 COUI 的引擎，
            // 采样上面那块 layerBackdrop，录制是由我们掌控的图层，不会被合成器掐掉。
            LocalPlatformAppBarMaterialEnabled provides false,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = "消息",
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .onSizeChanged { size ->
                            with(density) { headerHeight = size.height.toDp() }
                        },
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = BlurEffectsEnabled,
                    gradientGlassExtraHeight = 16.dp,
                    navigationIcon = { BackIconButton(onClick = nav::pop) },
                    bottomContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                        ) {
                            ChipTabGroup(
                                tabs = chipTabItems,
                                selectedIndex = pagerState.currentPage,
                                onSelectedChange = { index ->
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = COUIIcons.GridView,
                                onLeadingIconClick = { menuExpanded = true },
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                // 页面 backdrop：胶囊里是背景的模糊副本，跟便签那排一样是磨砂玻璃。
                                // 模糊关闭时 LocalBackdrop 为 null，组件自动退回平涂。
                                backdrop = LocalBackdrop.current,
                            )
                            // 菜单按这个盒子的位置定位：盒子正好盖在库画的那颗网格胶囊上。
                            Box(
                                Modifier
                                    .padding(start = 16.dp)
                                    .size(ChipTabDefaults.Height),
                            ) {
                                CouiPopupMenu(
                                    show = menuExpanded,
                                    onDismiss = { menuExpanded = false },
                                    items = MESSAGE_TABS.mapIndexed { index, tab ->
                                        CouiMenuItem(
                                            label = tab.label,
                                            selected = pagerState.currentPage == index,
                                            icon = tab.iconVector?.let { vector ->
                                                { modifier ->
                                                    Icon(
                                                        imageVector = vector,
                                                        contentDescription = null,
                                                        modifier = modifier,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** 首次测量前的内容起点估算：状态栏 + 标题行 + 分类栏。 */
private val HEADER_HEIGHT_FALLBACK = 116.dp

@Composable
private fun MessageList(
    kind: MessageKind,
    state: MessageKindState,
    loggedIn: Boolean,
    contentTop: Dp,
    viewModel: MessageViewModel,
    nav: NavState,
    onNotice: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    BindScrollToTop(listState)
    val itemCount by rememberUpdatedState(state.items.size)
    val transitionSpec = SearchResultContainerDefaults.transitionSpec(
        riseTrigger = SearchResultsRiseTrigger.OnlyAfterLoading,
    )
    val transitionState = rememberSearchResultsTransitionState(
        showingResults = true,
        loading = loggedIn && state.isLoading,
        queryKey = state.loaded,
        spec = transitionSpec,
    )

    LaunchedEffect(kind, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= 0 && itemCount > 0 && lastVisible >= itemCount - 3) {
                    viewModel.loadMore(kind)
                }
            }
    }

    SearchResultContainer(
        state = transitionState,
        modifier = Modifier.fillMaxSize(),
        loadingTopPadding = contentTop,
        loadingContent = { LoadingState() },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = contentTop,
                end = 12.dp,
                bottom = 24.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!loggedIn) {
                item(key = "login") {
                    EmptyState(
                        text = "登录后查看",
                        action = { TextButton(text = "去登录", onClick = nav::popToMe) },
                    )
                }
                return@LazyColumn
            }

            if (!resultsVisible) return@LazyColumn

            when {
                state.error != null -> item(key = "state") {
                    ErrorState(state.error.orEmpty(), onRetry = { viewModel.reload(kind) })
                }
                state.loaded && state.items.isEmpty() -> item(key = "state") {
                    EmptyState("暂时没有新消息")
                }
            }

            items(
                count = state.items.size,
                key = { state.items[it].key },
                contentType = { "message" },
            ) { index ->
                val entry = state.items[index]
                val canOpen = entry.target != null || entry.senderMid > 0L
                MessageRow(
                    entry = entry,
                    highlighted = entry.key in state.highlightedKeys,
                    onClick = if (canOpen) {
                        {
                            viewModel.openEntry(
                                entry = entry,
                                onNavigate = { nav.push(it) },
                                onError = onNotice,
                            )
                        }
                    } else {
                        null
                    },
                    onOpenUser = entry.senderMid.takeIf { it > 0L }?.let { mid ->
                        { nav.push(Route.Space(mid)) }
                    },
                )
            }

            if (state.isLoadingMore) {
                item(key = "loading-more") { LoadingState() }
            }
        }
    }
}

private data class MessageTabItem(
    val kind: MessageKind,
    val label: String,
    val iconVector: ImageVector? = null,
)

private val MESSAGE_TABS = listOf(
    MessageTabItem(MessageKind.SESSION, "我的私信", MailOutlineIcon),
    MessageTabItem(MessageKind.REPLY, "回复我的", COUIIcons.Reply),
    MessageTabItem(MessageKind.AT, "提及我的", AtSignIcon),
    MessageTabItem(MessageKind.LIKE, "收到的赞", LikeOutlineIcon),
    MessageTabItem(MessageKind.SYSTEM, "系统消息", BellOutlineIcon),
)
