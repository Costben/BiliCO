package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.components.PageTabRow
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.reportTopBarBottom
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
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

        Column(
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
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = pickKeyword,
                onExpandedChange = { if (!it) dismiss() },
                modifier = Modifier.statusBarsPadding(),
                expanded = true,
                label = "搜索视频 / 用户 / 专栏",
                cancelText = "取消",
                onCancel = dismiss,
            )

            if (showingResults) {
                SearchResultsPager(
                    state = state,
                    nav = nav,
                    viewModel = viewModel,
                    onRetry = viewModel::retry,
                )
            } else {
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
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = barBottom),
            ) {
                if (showingResults) {
                    SearchResultsPager(
                        state = state,
                        nav = nav,
                        viewModel = viewModel,
                        onRetry = viewModel::retry,
                    )
                } else {
                    SearchLazyList(
                        state = state,
                        history = history,
                        suggesting = suggesting,
                        listState = listState,
                        topPadding = 16.dp,
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
 * 搜索结果：标签栏浮在列表上方，视频 / 用户 / 专栏各占一页，左右滑动换分类。
 *
 * 标签栏不铺底色，底下那页列表从它的胶囊空隙里透出来；切页只补当前这一类的数据。
 */
@Composable
private fun SearchResultsPager(
    state: SearchUiState,
    nav: NavState,
    viewModel: SearchViewModel,
    onRetry: () -> Unit,
) {
    val tabs = remember { listOf("视频", "用户", "专栏") }
    val pagerState = rememberPagerState(initialPage = state.tab, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val barBounds = remember { TopBarBounds() }
    // 标签栏盖在列表上，列表的第一条要从它下面起排。先给个估值，量到真实高度再修正。
    var barHeight by remember { mutableStateOf(56.dp) }
    // 列表这一层自己录一份，标签栏采样它才有东西可糊；采整页的 backdrop 会绕回栏自己。
    val backdrop = rememberLayerBackdrop()

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
                .layerBackdropIf(BlurEffectsEnabled, backdrop),
            beyondViewportPageCount = 1,
        ) { page ->
            SearchResultPage(
                tab = page,
                state = state,
                nav = nav,
                topBarBounds = barBounds,
                contentTop = barHeight + 4.dp,
                onRetry = onRetry,
                viewModel = viewModel,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                // 和列表内容同一档左右边距，底板不贴到屏幕边。
                .padding(horizontal = 16.dp)
                .reportTopBarBottom(barBounds)
                .onSizeChanged { size ->
                    with(density) { barHeight = size.height.toDp() }
                }
                .padding(top = 10.dp, bottom = 6.dp),
        ) {
            PageTabRow(
                tabs = tabs,
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.fillMaxWidth(),
                backdrop = if (BlurEffectsEnabled) backdrop else null,
            )
        }
    }
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
                if (lastVisible != null && size > 0 && lastVisible >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentTop,
            end = 16.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
