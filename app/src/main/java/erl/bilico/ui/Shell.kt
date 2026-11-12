package erl.bilico.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicFilters
import erl.bilico.feature.dynamic.DynamicScreen
import erl.bilico.feature.feed.FEED_SEGMENTS
import erl.bilico.feature.feed.FeedScreen
import erl.bilico.feature.me.MeScreen
import erl.bilico.feature.search.FeedSearchDropdown
import erl.bilico.feature.search.SearchUiState
import erl.bilico.feature.search.SearchViewModel
import erl.bilico.ui.components.SearchClearButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.LocalPlatformBottomBarMaterialEnabled
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs

/** 底栏切页的交叉淡入时长。 */
private const val MainTabFadeMillis = 220

/** 顶栏标题行高度；列表滚动时标题就折这么高。 */
internal val TitleRowHeight = 44.dp

/**
 * 搜索展开时标题行多让出来的高度。搜索框本身 40dp，挤在 44dp 的行里上下各只剩 2dp，
 * 贴着状态栏和下面的标签栏都太紧；便签那种排法是上下各留一行约 17dp 的呼吸位。
 */
internal val SearchBarExtraHeight = 8.dp

/** 底栏三格：推荐、动态、我的。直播收进推荐页顶部的分段胶囊里。 */
private val tabTitles = listOf("推荐", "动态", "我的")

/**
 * 分段胶囊轨道左右各留的边距，和下面卡片列表的 12dp 缩进对齐。
 * 列表跟着系统密度走，顶栏钉在 360dp 密度上，所以这是「页面 dp」，用的时候要换算。
 */
private val CapsuleSideMargin = 12.dp

@Composable
fun MainShell(nav: NavState, container: AppContainer) {
    val tab = nav.mainTab
    val holder = rememberSaveableStateHolder()
    val backdrop = rememberLayerBackdrop()
    // 搜索结果自己的录制层：下拉一展开，页顶栏就得改采它，不然顶栏糊的还是底下那一屏首页。
    val searchBackdrop = rememberLayerBackdrop()
    // 底栏浮在所有页面之上，它停在哪条线以下由这里统一报出去。
    val topBarBounds = remember { TopBarBounds() }
    var feedCollapse by remember { mutableFloatStateOf(0f) }
    var dynamicCollapse by remember { mutableFloatStateOf(0f) }
    var dynamicFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    // 双击当前分段回顶刷新：每双击一次加一，分段那边看到变化就归位重拉。
    var feedRefreshTick by remember { mutableIntStateOf(0) }
    var dynamicRefreshTick by remember { mutableIntStateOf(0) }

    val searchViewModel: SearchViewModel = viewModel { SearchViewModel(container.search) }
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val searchHistory by container.settings.searchHistory.collectAsStateWithLifecycle()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    // 下拉里正在显示结果列表（不是历史/热搜，也不是输入建议）：只有这时顶栏背后才有东西可糊。
    val searchShowingResults = run {
        val trimmed = searchState.query.trim()
        val suggesting = trimmed.isNotEmpty() && trimmed != searchState.submitted
        searchExpanded && !suggesting && searchState.submitted.isNotBlank()
    }

    LaunchedEffect(tab) {
        if (tab != 0 && searchExpanded) {
            searchExpanded = false
            searchViewModel.reset()
        }
    }

    // 搜索展开时标题行往下多让一行：搜索框上下都要留出和便签一样的呼吸位。
    val searchBarExtra = if (searchExpanded && tab == 0) SearchBarExtraHeight else 0.dp
    val topBarHeight = pinnedHeight(TitleRowHeight, searchBarExtra)

    val account by container.session.account.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    // 翻页状态提到外壳：分段胶囊要跟着 pager 连续移动，不能等切完页再动。
    val feedPager = rememberPagerState(initialPage = segment, pageCount = { FEED_SEGMENTS.size })
    val dynamicPager = rememberPagerState(initialPage = dynamicFilterIndex, pageCount = { DynamicFilters.size })

    // 标题行的折叠量只在这里算一次：顶栏和分段胶囊读同一个值，滚动时才不会各折各的。
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    val collapsePx = when {
        searchExpanded -> 0f
        tab == 0 -> feedCollapse
        tab == 1 -> dynamicCollapse
        else -> 0f
    }
    val collapseTarget = (collapsePx / titleHeightPx).coerceIn(0f, 1f)
    val collapseShown = remember { Animatable(collapseTarget) }
    LaunchedEffect(collapseTarget) {
        if (abs(collapseTarget - collapseShown.value) > 0.6f) {
            collapseShown.animateTo(collapseTarget, tween(MainTabFadeMillis))
        } else {
            collapseShown.snapTo(collapseTarget)
        }
    }
    // 传 lambda 而不是数值：折叠每帧都在变，读值的地方都在绘制阶段，不会把整页重组一遍。
    val collapsed: () -> Float = { collapseShown.value }

    val topEntry = nav.stack.lastOrNull()
    val mainVisible = topEntry == null ||
        topEntry.route == Route.Main ||
        (nav.stack.size == 2 && (topEntry.progress.value < 1f || topEntry.exiting || topEntry.previewing))

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTopBarBounds provides topBarBounds) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdropIf(BlurEffectsEnabled && mainVisible, backdrop)
                    .background(pageContainerColor())
            ) {
                // 底栏只在点击时切换，页面自己承担转场：两边都留在同一个状态容器里交叉淡入。
                Crossfade(
                    targetState = tab,
                    animationSpec = tween(MainTabFadeMillis),
                    modifier = Modifier.fillMaxSize(),
                    label = "mainTabPage",
                ) { page ->
                    holder.SaveableStateProvider(page) {
                        when (page) {
                            0 -> FeedScreen(
                                container = container,
                                nav = nav,
                                topInset = topBarHeight,
                                segment = segment,
                                onSegment = { segment = it },
                                onCollapse = { feedCollapse = it },
                                pagerState = feedPager,
                                refreshTick = feedRefreshTick,
                            )

                            1 -> DynamicScreen(
                                container = container,
                                nav = nav,
                                topInset = topBarHeight,
                                filterIndex = dynamicFilterIndex,
                                onFilterSelect = { dynamicFilterIndex = it },
                                pagerState = dynamicPager,
                                onCollapse = { dynamicCollapse = it },
                                refreshTick = dynamicRefreshTick,
                            )

                            else -> MeScreen(
                                container = container,
                                nav = nav,
                                topInset = topBarHeight,
                            )
                        }
                    }
                }
            }
        }

        // 展开之后，在搜索栏正下方同步向下展开热搜、实时搜索建议与搜索结果；
        // 遮罩整屏淡入压暗包括底栏在内的背景，下拉面板本身从顶栏处竖直展开。
        AnimatedVisibility(
            visible = searchExpanded && tab == 0,
            enter = fadeIn(animationSpec = tween(220)),
            exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
        ) {
            FeedSearchDropdown(
                state = searchState,
                history = searchHistory,
                viewModel = searchViewModel,
                container = container,
                nav = nav,
                barBottom = topBarHeight,
                backdrop = searchBackdrop,
                onClose = {
                    searchExpanded = false
                    searchViewModel.reset()
                },
                modifier = Modifier.fillMaxSize(),
                dropdownModifier = Modifier.animateEnterExit(
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(320, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)),
                    ),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(260, easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)),
                    ),
                ),
            )
        }

        CompositionLocalProvider(
            // 结果列表展开时顶栏改采搜索那份录制，滚动时才糊得到结果；其余时候仍是首页。
            LocalBackdrop provides if (BlurEffectsEnabled) {
                if (searchShowingResults) searchBackdrop else backdrop
            } else null,
            // 内容铺到状态栏下面，顶栏玻璃才有东西可采样；否则 COUI 会把首行抹成平色横带。
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                MainTopBar(
                    tab = tab,
                    nav = nav,
                    collapsed = collapsed,
                    avatarUrl = account?.face,
                    onAvatarClick = { nav.setTab(ME_TAB_INDEX) },
                    segment = segment,
                    onSegment = { segment = it },
                    dynamicFilterIndex = dynamicFilterIndex,
                    onDynamicFilter = { dynamicFilterIndex = it },
                    pager = feedPager,
                    dynamicPager = dynamicPager,
                    onFeedReselect = { feedRefreshTick++ },
                    onDynamicReselect = { dynamicRefreshTick++ },
                    onDoubleTap = {
                        // 双击状态栏 / 标题空白：当前 tab 回到顶部并重新刷新。
                        when (tab) {
                            0 -> feedRefreshTick++
                            1 -> dynamicRefreshTick++
                        }
                    },
                    searchExpanded = searchExpanded,
                    onSearchExpandedChange = { searchExpanded = it },
                    searchState = searchState,
                    searchViewModel = searchViewModel,
                    container = container,
                    bounds = topBarBounds,
                    barExtra = searchBarExtra,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(3f),
                )
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            // 窗口级材质在弹层开关引起 backdrop 录制切换时会丢帧导致底栏闪烁，
            // 改用 COUI 的 Compose 玻璃引擎，采样同一块页面 backdrop。
            LocalPlatformBottomBarMaterialEnabled provides false,
        ) {
            PinnedBarDensity {
                FloatingBottomBar(
                    tab = tab,
                    onTab = nav::setTab,
                    onReselect = { index ->
                        // 双击当前 tab：回到顶部并重新刷新。
                        when (index) {
                            0 -> feedRefreshTick++
                            1 -> dynamicRefreshTick++
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f),
                )
            }
        }
    }
}

@Composable
private fun MainTopBar(
    tab: Int,
    nav: NavState,
    collapsed: () -> Float,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    segment: Int,
    onSegment: (Int) -> Unit,
    dynamicFilterIndex: Int,
    onDynamicFilter: (Int) -> Unit,
    pager: PagerState,
    dynamicPager: PagerState,
    onFeedReselect: () -> Unit,
    onDynamicReselect: () -> Unit,
    onDoubleTap: () -> Unit,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchState: SearchUiState,
    searchViewModel: SearchViewModel,
    container: AppContainer,
    bounds: TopBarBounds,
    barExtra: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageBarHeight = statusBarTop + TitleRowHeight + barExtra

    Box(modifier) {
        AppBarSurface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, 0) }
                .reportTopBarBottom(bounds),
            containerColor = pageContainerColor(),
            scrollFraction = { 1f },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(pageBarHeight)
                    .pointerInput(onDoubleTap) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    },
            )
        }
        Crossfade(
            targetState = tab,
            animationSpec = tween(MainTabFadeMillis),
            label = "mainBarContent",
        ) { page ->
            BarContent(
                tab = page,
                nav = nav,
                collapsed = collapsed,
                avatarUrl = avatarUrl,
                onAvatarClick = onAvatarClick,
                segment = segment,
                onSegment = onSegment,
                dynamicFilterIndex = dynamicFilterIndex,
                onDynamicFilter = onDynamicFilter,
                pager = pager,
                dynamicPager = dynamicPager,
                onFeedReselect = onFeedReselect,
                onDynamicReselect = onDynamicReselect,
                searchExpanded = searchExpanded,
                onSearchExpandedChange = onSearchExpandedChange,
                searchState = searchState,
                searchViewModel = searchViewModel,
                container = container,
                barExtra = barExtra,
            )
        }
    }
}

@Composable
private fun BarContent(
    tab: Int,
    nav: NavState,
    collapsed: () -> Float,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    segment: Int,
    onSegment: (Int) -> Unit,
    dynamicFilterIndex: Int,
    onDynamicFilter: (Int) -> Unit,
    pager: PagerState,
    dynamicPager: PagerState,
    onFeedReselect: () -> Unit,
    onDynamicReselect: () -> Unit,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchState: SearchUiState,
    searchViewModel: SearchViewModel,
    container: AppContainer,
    barExtra: Dp,
) {
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        if (tab == 0) {
            // 推荐页：使用前端库最新的按钮展开式搜索栏
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TitleRowHeight + barExtra)
                    .graphicsLayer {
                        if (!searchExpanded) {
                            translationY = -collapsed() * titleHeightPx
                            alpha = 1f - collapsed()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                TopAppBarSearchBar(
                    query = searchState.query,
                    onQueryChange = searchViewModel::onQueryChange,
                    expanded = searchExpanded,
                    onExpandedChange = { isExpanded ->
                        onSearchExpandedChange(isExpanded)
                        if (!isExpanded) searchViewModel.reset()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "搜索视频 / 用户 / 专栏",
                    cancelText = "取消",
                    onSearch = { keyword ->
                        container.settings.addSearchHistory(keyword)
                        searchViewModel.submit(keyword)
                    },
                    onCancel = {
                        onSearchExpandedChange(false)
                        searchViewModel.reset()
                    },
                    gradientColor = Color.Transparent,
                    titleContent = {
                        FeedAvatar(
                            url = avatarUrl,
                            onClick = onAvatarClick,
                        )
                    },
                    extraActions = {
                        HighlightedIconButton(
                            onClick = { nav.push(Route.Messages) },
                            enabled = !searchExpanded,
                            modifier = Modifier.couiTopAppBarActionItem(),
                        ) {
                            Icon(
                                imageVector = COUIIcons.Email,
                                contentDescription = "消息",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    trailingIcon = {
                        if (searchState.query.isNotEmpty()) {
                            SearchClearButton(
                                onClick = { searchViewModel.onQueryChange("") },
                            )
                        }
                    },
                )
            }
        } else {
            Box(Modifier.fillMaxWidth().height(TitleRowHeight)) {
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = -collapsed() * titleHeightPx
                                alpha = 1f - collapsed()
                            },
                    ) {
                        Text(
                            tabTitles.getOrElse(tab) { "" },
                            color = COUITheme.colorScheme.onSurface,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 6.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = TopAppBarDefaults.ActionIconPadding)
                        .graphicsLayer {
                            translationY = -collapsed() * titleHeightPx / 2f
                            alpha = 1f - collapsed()
                        },
                    horizontalArrangement = Arrangement.spacedBy(
                        TopAppBarDefaults.ActionMenuItemSpacing,
                        Alignment.End,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabActions(tab, nav, onSearch = {})
                }
            }
        }

        // 分类标签栏画在顶栏这一层：搜索展开时自动折叠淡出
        val capsuleLabels = when (tab) {
            0 -> FEED_SEGMENTS
            1 -> DynamicFilters.map { it.second }
            else -> null
        }
        if (capsuleLabels != null) {
            AnimatedVisibility(
                visible = !searchExpanded || tab != 0,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150)),
            ) {
                // 标签栏统一用系统那套玻璃分段控件：一条半透明模糊底槽，
                // 槽里一颗会跟着手势滑动的胶囊高光。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .graphicsLayer { translationY = -collapsed() * titleHeightPx },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val barDensity = LocalDensity.current.density
                    val pageDensity = LocalContext.current.resources.displayMetrics.density
                    TabRowWithContour(
                        tabs = capsuleLabels,
                        selectedTabIndex = if (tab == 0) segment else dynamicFilterIndex,
                        onTabSelected = if (tab == 0) onSegment else onDynamicFilter,
                        onTabReselected = when (tab) {
                            0 -> { _: Int -> onFeedReselect() }
                            1 -> { _: Int -> onDynamicReselect() }
                            else -> null
                        },
                        // 拖动时只挪高光块，抬手才切页，免得翻页动画把高光块的弹簧打断。
                        commitOnRelease = true,
                        backdrop = LocalBackdrop.current,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 列表跟着页面密度走，顶栏钉在 360dp，边距要按比例换算。
                            .padding(horizontal = CapsuleSideMargin * (pageDensity / barDensity)),
                    )
                }
            }
        }
    }
}
