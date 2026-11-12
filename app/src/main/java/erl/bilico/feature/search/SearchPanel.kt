package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.ui.AppBarSurface
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.reportTopBarBottom
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Album
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Sort
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 顶栏搜索面板：点放大镜就地展开，不另开一页。
 *
 * 上半截是搜索栏，下面依输入态切分：空输入显示历史与热搜，输入中显示实时搜索建议，提交后显示结果。
 */
@Composable
fun SearchPanel(
    nav: NavState,
    container: AppContainer,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel { SearchViewModel(container.search) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by container.settings.searchHistory.collectAsStateWithLifecycle()

    val pageColor = pageContainerColor()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val trimmedQuery = state.query.trim()
    val suggesting = trimmedQuery.isNotEmpty() && trimmedQuery != state.submitted
    val showingResults = !suggesting && state.submitted.isNotBlank()

    // 每次展开都从搜索首页开始：历史、热搜，而不是上次那一屏结果。
    LaunchedEffect(Unit) { viewModel.reset() }

    SearchListEffects(
        listState = listState,
        submitted = state.submitted,
        suggesting = suggesting,
    )

    val dismiss: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        onClose()
    }
    val pickKeyword: (String) -> Unit = { keyword ->
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        container.settings.addSearchHistory(keyword)
        viewModel.submit(keyword)
    }
    // 搜索框在两种布局里都要，一份定义两处用：普通态压在列表上方，结果态浮在整页列表上。
    // 结果态那张列表已被录成 backdrop，搜索框采它做玻璃；上面没有列表可采时退回平涂。
    val searchBar: @Composable (Modifier, Backdrop?) -> Unit = { mod, backdrop ->
        SearchBar(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = pickKeyword,
            onExpandedChange = { if (!it) dismiss() },
            modifier = mod,
            expanded = true,
            label = "搜索视频 / 用户 / 专栏",
            cancelText = "取消",
            onCancel = dismiss,
            backdrop = backdrop,
        )
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                ),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (showingResults) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.heightIn(max = screenHeight * 0.66f)
                    },
                )
                .clip(
                    if (showingResults) {
                        RectangleShape
                    } else {
                        SquircleShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
                    },
                )
                .background(pageColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            if (showingResults) {
                SearchResultsPager(
                    state = state,
                    nav = nav,
                    viewModel = viewModel,
                    onRetry = viewModel::retry,
                    header = { searchBar(Modifier.statusBarsPadding(), LocalBackdrop.current) },
                    backdrop = rememberLayerBackdrop(),
                )
            } else {
                Column(Modifier.fillMaxWidth()) {
                    searchBar(Modifier.statusBarsPadding(), null)
                    SearchLazyList(
                        state = state,
                        history = history,
                        suggesting = suggesting,
                        listState = listState,
                        topPadding = 8.dp,
                        container = container,
                        onPick = pickKeyword,
                    )
                }
            }
        }
    }
}

/**
 * 推荐页顶栏展开的搜索下拉内容区：紧贴在顶栏展开的搜索输入框下方向下展开。
 *
 * 展开状态下：
 * - 空输入：展示搜索历史与热搜；
 * - 正在输入且与当前结果不同：实时查询并展示搜索建议；
 * - 提交关键词后：展示视频 / 用户 / 专栏分类结果；
 * - 点击遮罩区域或按返回键收起。
 */
@Composable
fun FeedSearchDropdown(
    state: SearchUiState,
    history: List<String>,
    viewModel: SearchViewModel,
    container: AppContainer,
    nav: NavState,
    barBottom: Dp,
    backdrop: LayerBackdrop,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    dropdownModifier: Modifier = Modifier,
) {
    val pageColor = pageContainerColor()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val trimmedQuery = state.query.trim()
    val suggesting = trimmedQuery.isNotEmpty() && trimmedQuery != state.submitted
    val showingResults = !suggesting && state.submitted.isNotBlank()

    SearchListEffects(
        listState = listState,
        submitted = state.submitted,
        suggesting = suggesting,
    )

    val dismiss: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        onClose()
    }
    val pickKeyword: (String) -> Unit = { keyword ->
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        container.settings.addSearchHistory(keyword)
        viewModel.submit(keyword)
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                ),
        )

        Column(
            dropdownModifier
                .fillMaxWidth()
                .then(
                    if (showingResults) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.heightIn(max = screenHeight * 0.72f)
                    },
                )
                .clip(
                    if (showingResults) {
                        RectangleShape
                    } else {
                        SquircleShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
                    },
                )
                .background(pageColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (showingResults) {
                    SearchResultsPager(
                        state = state,
                        nav = nav,
                        viewModel = viewModel,
                        onRetry = viewModel::retry,
                        // 这个下拉的搜索框在页顶栏上，标签栏自己就是整条顶栏。
                        header = {},
                        // 列表从屏幕顶开始铺，页顶栏那一行也算进首项留白：滚动时结果会钻到
                        // 搜索框底下，顶栏的玻璃才有东西可糊。
                        topInset = barBottom,
                        backdrop = backdrop,
                    )
                } else {
                    SearchLazyList(
                        state = state,
                        history = history,
                        suggesting = suggesting,
                        listState = listState,
                        topPadding = barBottom + 16.dp,
                        container = container,
                        onPick = pickKeyword,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchListEffects(
    listState: LazyListState,
    submitted: String,
    suggesting: Boolean,
) {
    // 换关键词或进出建议态时，搜索首页 / 建议列表回到顶部。
    // 结果页的三份列表由各自的 pager 页管，不经过这里。
    LaunchedEffect(submitted, suggesting) {
        listState.scrollToItem(0)
    }
}

@Composable
private fun SearchLazyList(
    state: SearchUiState,
    history: List<String>,
    suggesting: Boolean,
    listState: LazyListState,
    topPadding: Dp,
    container: AppContainer,
    onPick: (String) -> Unit,
) {
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomPadding = maxOf(24.dp + navBottomPadding(), imeBottom + 16.dp)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = topPadding,
            end = 16.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (suggesting) {
            searchSuggestSection(
                query = state.query,
                suggestions = state.suggestions,
                suggestionsLoading = state.suggestionsLoading,
                onPick = onPick,
            )
        } else {
            searchHomeSection(
                hot = state.hot,
                hotLoading = state.hotLoading,
                history = history,
                onPick = onPick,
                onDeleteHistory = { keyword -> container.settings.removeSearchHistory(keyword) },
                onClearHistory = { container.settings.clearSearchHistory() },
            )
        }
    }
}

/**
 * 搜索结果：整页列表铺满，搜索框和胶囊标签浮在它上面。
 *
 * 顶栏不再是一条实心色条，而是压在列表上的一层渐变玻璃：列表滚到它下面会被磨砂压暗、渐隐，
 * 但内容仍然看得见。胶囊各自再采一次 backdrop，跟便签那排一样是磨砂玻璃。
 *
 * 左边那颗圆圈胶囊打开筛选菜单：排序方式，以及视频分区。
 *
 * @param backdrop 列表这一层的录制目标，由顶栏和胶囊采样。录的时候页面底色一起录进去：
 *   模糊读到的是图层自己的 clear color，透明的底会把胶囊和顶栏洗成近白。调用方传同一份录制
 *   进来，页顶栏才能跟着糊到这块列表。
 * @param topInset 列表上方还压着几行别人的栏（下拉搜索时页顶栏挂在外壳上）。这段既算进首项
 *   留白，也让标签栏整体下移，不然标签栏正好躲到页顶栏背后。
 */
@Composable
private fun SearchResultsPager(
    state: SearchUiState,
    nav: NavState,
    viewModel: SearchViewModel,
    onRetry: () -> Unit,
    header: @Composable () -> Unit,
    backdrop: LayerBackdrop,
    topInset: Dp = 0.dp,
) {
    val tabs = remember {
        listOf(
            ChipTabData(label = "视频", icon = COUIIcons.Play),
            ChipTabData(label = "用户", icon = COUIIcons.Contacts),
            ChipTabData(label = "专栏", icon = COUIIcons.Album),
        )
    }
    val pagerState = rememberPagerState(initialPage = state.tab, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val barBounds = remember { TopBarBounds() }
    var headerHeight by remember { mutableStateOf(160.dp) }
    var menuExpanded by remember { mutableStateOf(false) }

    // 滑停一页就补这一类的数据；点标签走的是同一个入口。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.setTab(it) }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, backdrop)
                .background(pageContainerColor()),
            beyondViewportPageCount = 1,
        ) { page ->
            SearchResultPage(
                tab = page,
                state = state,
                nav = nav,
                topBarBounds = barBounds,
                contentTop = topInset + headerHeight + 8.dp,
                onRetry = onRetry,
                viewModel = viewModel,
            )
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
        ) {
            AppBarSurface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    // 结果列表从屏幕顶铺上去之后，标签栏得自己让出页顶栏那一行；
                    // 不让的话它正好被页顶栏盖住，什么都看不到。
                    .offset(y = topInset)
                    .reportTopBarBottom(barBounds)
                    .onSizeChanged { size ->
                        with(density) { headerHeight = size.height.toDp() }
                    },
                scrollFraction = { 1f },
                // 这一页自己录了列表的 backdrop，走 COUI 的引擎采样它；系统窗口材质会把自己的输出吃回去。
                platformRenderer = false,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    header()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // 顶栏到标签栏之间按便签的间距留白：挤在 6dp 时标签栏像粘在搜索框上。
                            .padding(top = 11.dp, bottom = 6.dp),
                    ) {
                        ChipTabGroup(
                            tabs = tabs,
                            selectedIndex = pagerState.currentPage,
                            onSelectedChange = { index ->
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = COUIIcons.Sort,
                            onLeadingIconClick = { menuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            backdrop = LocalBackdrop.current,
                        )
                        // 菜单按这颗圆圈胶囊的位置展开：盒子正好盖在库画的那颗 Sort 上。
                        Box(
                            Modifier
                                .padding(start = 16.dp)
                                .size(ChipTabDefaults.Height),
                        ) {
                            CouiPopupMenu(
                                show = menuExpanded,
                                onDismiss = { menuExpanded = false },
                                items = searchMenuItems(state, viewModel),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 圆圈胶囊里的筛选菜单：先是排序方式，视频那一页再跟一档分区。 */
private fun searchMenuItems(
    state: SearchUiState,
    viewModel: SearchViewModel,
): List<CouiMenuItem> {
    val items = mutableListOf<CouiMenuItem>()
    val orders = listOf(
        "综合排序" to "totalrank",
        "最新发布" to "pubdate",
        "最多播放" to "click",
        "最多弹幕" to "dm",
        "最多收藏" to "stow",
    )
    orders.forEach { (label, value) ->
        items += CouiMenuItem(label, { viewModel.setOrder(value) }, selected = state.order == value)
    }
    if (state.tab == 0) {
        val tids = listOf(
            "全部分区" to 0,
            "动画" to 1,
            "游戏" to 4,
            "知识" to 36,
            "科技" to 188,
            "生活" to 160,
        )
        tids.forEach { (label, value) ->
            items += CouiMenuItem(label, { viewModel.setTid(value) }, selected = state.tid == value)
        }
    }
    return items
}

@Composable
private fun SearchResultPage(
    tab: Int,
    state: SearchUiState,
    nav: NavState,
    topBarBounds: TopBarBounds,
    contentTop: Dp,
    onRetry: () -> Unit,
    viewModel: SearchViewModel,
) {
    val listState = rememberLazyListState()
    val rowScrollState = remember(listState) { listState.asRowScrollState() }
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomPadding = maxOf(24.dp + navBottomPadding(), imeBottom + 16.dp)

    // 只有停在当前这一页时才翻页加载，旁边的预览页不跟着请求。
    LaunchedEffect(tab, state.tab, state.submitted) {
        if (tab != state.tab) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val current = viewModel.state.value
                val size = when (tab) {
                    0 -> current.videos.size
                    1 -> current.users.size
                    else -> current.articles.size
                }
                // 列表首项是顶栏留白，索引比数据多一项。
                if (lastVisible != null && size > 0 && lastVisible >= size - 1) {
                    viewModel.loadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 顶栏留白做成首项而不是 contentPadding：带 padding 的 LazyColumn 会把它上面裁掉，
        // 列表永远钻不到浮层底下，磨砂顶栏就没有背后内容可糊。
        item(key = "top-inset") { Spacer(Modifier.height(contentTop)) }
        searchResultItems(
            tab = tab,
            state = state,
            nav = nav,
            rowScrollState = rowScrollState,
            topBarBounds = topBarBounds,
            onRetry = onRetry,
        )
    }
}
