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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Album
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Sort
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 搜索结果的三个分类，顺序即 pager 的页序。 */
internal val SearchResultTabs = listOf(
    ChipTabData(label = "视频", icon = COUIIcons.Play),
    ChipTabData(label = "用户", icon = COUIIcons.Contacts),
    ChipTabData(label = "专栏", icon = COUIIcons.Album),
)

/**
 * 推荐页顶栏展开的搜索下拉内容区：紧贴在顶栏展开的搜索输入框下方向下展开。
 *
 * 展开状态下：
 * - 空输入：展示搜索历史与热搜；
 * - 正在输入且与当前结果不同：实时查询并展示搜索建议；
 * - 提交关键词后：展示视频 / 用户 / 专栏分类结果；
 * - 点击遮罩区域或按返回键收起。
 *
 * 结果态顶栏那两行（搜索框 + 分类胶囊）不在这里画：它们由外壳的顶栏统一承载，
 * 整条栏只有一层材质。这里只负责列表本身。
 *
 * @param barBottom 外壳顶栏此刻的下沿，列表首项要给它让出位置，滚动时结果才能钻到玻璃底下。
 * @param barBounds 外壳顶栏报出的下沿，结果卡片展开变全屏时按它避让。
 */
@Composable
fun FeedSearchDropdown(
    state: SearchUiState,
    history: List<String>,
    viewModel: SearchViewModel,
    container: AppContainer,
    nav: NavState,
    barBottom: Dp,
    barBounds: TopBarBounds,
    pager: PagerState,
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
            if (showingResults) {
                SearchResultsPager(
                    state = state,
                    nav = nav,
                    viewModel = viewModel,
                    onRetry = viewModel::retry,
                    pager = pager,
                    barBounds = barBounds,
                    // 列表从屏幕顶开始铺，顶栏那两行也算进首项留白：滚动时结果会钻到
                    // 搜索框和胶囊底下，顶栏的玻璃才有东西可糊。
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

/**
 * 结果态顶栏的第二行：三颗分类胶囊，左边那颗圆圈切开筛选菜单。
 *
 * 排进去的是外壳顶栏的胶囊行，自己不画材质——顶栏那一层玻璃已经盖到这一行下面了；
 * 胶囊只采 [LocalBackdrop] 做自己那点亮面。
 */
@Composable
internal fun SearchResultChipRow(
    state: SearchUiState,
    viewModel: SearchViewModel,
    pager: PagerState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        ChipTabGroup(
            tabs = SearchResultTabs,
            selectedIndex = pager.currentPage,
            onSelectedChange = { index ->
                scope.launch { pager.animateScrollToPage(index) }
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
 * 搜索结果：整页列表铺满，顶栏那两行浮在它上面。
 *
 * 列表录进 [backdrop]，外壳顶栏采样它做那一层玻璃；三页左右切换，滑停一页才补这一类的数据。
 *
 * @param backdrop 列表这一层的录制目标，由顶栏和胶囊采样。录的时候页面底色一起录进去：
 *   模糊读到的是图层自己的 clear color，透明的底会把胶囊和顶栏洗成近白。
 * @param topInset 列表上方压着的整条顶栏（搜索行 + 胶囊行），既算进首项留白，
 *   也让结果卡片知道栏此刻真正的下沿在哪。
 */
@Composable
private fun SearchResultsPager(
    state: SearchUiState,
    nav: NavState,
    viewModel: SearchViewModel,
    onRetry: () -> Unit,
    pager: PagerState,
    barBounds: TopBarBounds,
    backdrop: LayerBackdrop,
    topInset: Dp = 0.dp,
) {
    // 滑停一页就补这一类的数据；点胶囊走的是同一个入口。
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.setTab(it) }
    }
    // 分类被别处改掉（换关键词后回到视频页）时把 pager 跟过去。点击胶囊自己会先动画，
    // 停稳后 [state.tab] 已经等于脚标，这里不会来回拉。
    LaunchedEffect(state.tab) {
        if (pager.settledPage != state.tab) pager.scrollToPage(state.tab)
    }

    HorizontalPager(
        state = pager,
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
            contentTop = topInset + 8.dp,
            onRetry = onRetry,
            viewModel = viewModel,
        )
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
