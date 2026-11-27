package erl.bilico.ui

import erl.bilico.platform.showToast
import erl.bilico.platform.PlatformBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.settings.HomeTabType
import erl.bilico.di.AppContainer
import erl.bilico.player.PlaybackBridge
import erl.bilico.feature.dynamic.DynamicFilters
import erl.bilico.feature.dynamic.DynamicPublishOverlay
import erl.bilico.feature.dynamic.DynamicScreen
import erl.bilico.feature.dynamic.DynamicScrollStates
import erl.bilico.feature.dynamic.rememberDynamicScrollStates
import erl.bilico.feature.feed.FeedScreen
import erl.bilico.feature.feed.FeedScrollStates
import erl.bilico.feature.feed.rememberFeedScrollStates
import erl.bilico.feature.me.MeScreen
import erl.bilico.feature.me.ProfileEditHost
import erl.bilico.feature.search.HomeSearchOverlay
import erl.bilico.feature.search.SearchSortMenuHost
import erl.bilico.feature.search.SearchViewModel
import erl.bilico.feature.search.showingResults
import io.github.suqi8.coui.kmp.anim.COUIInEase
import io.github.suqi8.coui.kmp.anim.COUIOutEase
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.LocalPlatformBottomBarMaterialEnabled
import io.github.suqi8.coui.kmp.basic.LocalTransitionFastGlass
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.rememberSearchResultsTransitionState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import kotlin.math.abs

/** 底栏切页的交叉淡入时长。 */
internal const val MainTabFadeMillis = 220

/** 搜索整屏面板淡入淡出时长（与 Cobo COUI `SearchPanelFadeMillis` 一致）。 */
private const val SearchPanelFadeMillis = 220

/** 顶栏标题行高度；列表滚动时标题就折这么高。 */
internal val TitleRowHeight = 44.dp

/** 底栏三格：推荐、动态、我的。直播收进推荐页顶部的分段胶囊里。 */
internal val tabTitles = listOf("推荐", "动态", "我的")

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
    val searchBackdrop = rememberLayerBackdrop()
    val topBarBounds = remember { TopBarBounds() }
    var feedCollapse by remember { mutableFloatStateOf(0f) }
    var dynamicCollapse by remember { mutableFloatStateOf(0f) }
    var dynamicFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var feedRefreshTick by remember { mutableIntStateOf(0) }
    var dynamicRefreshTick by remember { mutableIntStateOf(0) }

    val searchViewModel: SearchViewModel = viewModel { SearchViewModel(container.search) }
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val searchHistory by container.settings.searchHistory.collectAsStateWithLifecycle()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val panelProgress = remember { Animatable(0f) }
    val panelAlpha = remember { Animatable(0f) }
    val showingResults = searchState.showingResults()
    val panelMounted by remember {
        derivedStateOf {
            searchExpanded || panelProgress.value > 0.001f || panelAlpha.value > 0.001f
        }
    }
    val feedPanelCovering = tab == 0 && panelMounted
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val openFeedSearch: () -> Unit = {
        searchViewModel.reset()
        searchExpanded = true
    }
    val cancelFeedSearch: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus()
        searchExpanded = false
    }
    val submitHomeSearch: (String) -> Unit = { raw ->
        val keyword = raw.trim()
        if (keyword.isNotEmpty()) {
            keyboard?.hide()
            focusManager.clearFocus()
            container.settings.addSearchHistory(keyword)
            searchViewModel.submit(keyword)
        }
    }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            panelProgress.snapTo(1f)
            panelAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = SearchPanelFadeMillis, easing = COUIInEase),
            )
        } else if (panelProgress.value > 0f || panelAlpha.value > 0f) {
            panelAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = SearchPanelFadeMillis, easing = COUIOutEase),
            )
            panelProgress.snapTo(0f)
            if (!searchExpanded) {
                searchViewModel.reset()
            }
        }
    }

    LaunchedEffect(tab) {
        if (tab != 0 && (searchExpanded || panelProgress.value > 0f || panelAlpha.value > 0f)) {
            keyboard?.hide()
            focusManager.clearFocus()
            searchExpanded = false
            panelProgress.snapTo(0f)
            panelAlpha.snapTo(0f)
            searchViewModel.reset()
        }
    }

    // 排序菜单只属于结果态：退回发现/建议页或清空提交词时收起，避免漂浮在没有锚点的位置。
    LaunchedEffect(showingResults) {
        if (!showingResults) sortMenuExpanded = false
    }

    val searchTransition = rememberSearchResultsTransitionState(
        showingResults = showingResults,
        loading = searchState.isLoading,
        queryKey = searchState.submitted,
    )

    val topBarHeight = pinnedHeight(TitleRowHeight)
    val account by container.session.account.collectAsStateWithLifecycle()
    var dynamicComposerOpen by remember { mutableStateOf(false) }
    var editProfileOpen by remember { mutableStateOf(false) }
    val homeTabsLayout by container.settings.feed.homeTabs.collectAsStateWithLifecycle()
    val feedTabs = remember(homeTabsLayout) { homeTabsLayout.visibleTabs() }
    val feedTabLabels = remember(feedTabs) { feedTabs.map { it.label } }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(feedTabs.size) {
        if (segment >= feedTabs.size) segment = (feedTabs.size - 1).coerceAtLeast(0)
    }
    val feedPager = rememberPagerState(
        initialPage = segment.coerceIn(0, (feedTabs.size - 1).coerceAtLeast(0)),
        pageCount = { feedTabs.size },
    )
    val dynamicPager = rememberPagerState(initialPage = dynamicFilterIndex, pageCount = { DynamicFilters.size })
    val feedScrollStates = rememberFeedScrollStates()
    val dynamicScrollStates = rememberDynamicScrollStates()
    val meListState = rememberLazyListState()
    val searchListState = rememberLazyListState()

    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    val collapsePx = when (tab) {
        0 -> feedCollapse
        1 -> dynamicCollapse
        else -> 0f
    }
    val rawCollapseTarget = (collapsePx / titleHeightPx).coerceIn(0f, 1f)
    // 搜索展开期间锁定标题行不折叠；收起后恢复跟踪列表滚动。
    val collapseTarget = if (tab == 0 && searchExpanded) 0f else rawCollapseTarget
    val collapseShown = remember { Animatable(collapseTarget) }
    var wasSearchExpanded by remember { mutableStateOf(searchExpanded) }
    LaunchedEffect(collapseTarget, searchExpanded) {
        val searchTransition = (wasSearchExpanded != searchExpanded) || (searchExpanded && collapseShown.value > 0f)
        wasSearchExpanded = searchExpanded
        if (abs(collapseTarget - collapseShown.value) > 0.6f || searchTransition) {
            collapseShown.animateTo(collapseTarget, tween(MainTabFadeMillis))
        } else {
            collapseShown.snapTo(collapseTarget)
        }
    }
    val collapsed: () -> Float = { collapseShown.value }

    val onMainTab by remember(nav) {
        derivedStateOf { nav.stack.lastOrNull { !it.exiting }?.route == Route.Main }
    }
    // 首页带着融合球点开新视频时，进场这一秒里底栏不该先把融合球收起来再放回去：
    // 那三个弹簧会把整条底栏连同它背后的模糊层一起拖着重画。
    val rawOrbVisible = nav.minimizedEntry != null || nav.isMinimizingToOrb
    val enteringOverMain by remember(nav) {
        derivedStateOf {
            val topEntry = nav.stack.lastOrNull { !it.exiting }
            nav.stack.count { !it.exiting } == 2 && topEntry != null && topEntry.expand &&
                !topEntry.previewing && topEntry.progress.value < 1f
        }
    }
    var settledOrbVisible by remember { mutableStateOf(rawOrbVisible) }
    SideEffect { if (!enteringOverMain) settledOrbVisible = rawOrbVisible }

    val searchFullyCovered by remember {
        derivedStateOf {
            tab == 0 && searchExpanded && panelAlpha.value >= 0.999f && panelProgress.value >= 0.999f
        }
    }

    val context = LocalPlatformContext.current
    Box(Modifier.fillMaxSize()) {
        // Layer 1~3：首页内容 + 常态顶栏 + 悬浮底栏。搜索面板展开/收起过渡期间冻结为单张快照，
        // 避免底层首页与上层搜索面板 5 层模糊管线同帧并发导致 iOS 展开/退出掉帧。
        ExitSnapshotHost(
            exiting = feedPanelCovering && !searchFullyCovered,
            hasSurfaceView = false,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Layer 1: 页面主内容。搜索面板完全不透明覆盖时跳过重绘底层首页/顶栏/底栏，大幅降低搜索页滚动 GPU 开销。
                CompositionLocalProvider(LocalTopBarBounds provides topBarBounds) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (!searchFullyCovered) drawContent()
                    }
                    .graphicsLayer {}
                    .layerBackdropIf(BlurEffectsEnabled, backdrop)
                    .background(pageContainerColor())
                    .then(if (feedPanelCovering) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
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
                                tabs = feedTabs,
                                segment = segment,
                                onSegment = { segment = it },
                                onCollapse = { feedCollapse = it },
                                pagerState = feedPager,
                                refreshTick = feedRefreshTick,
                                scrollStates = feedScrollStates,
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
                                scrollStates = dynamicScrollStates,
                            )

                            else -> MeScreen(
                                container = container,
                                nav = nav,
                                topInset = topBarHeight,
                                listState = meListState,
                            )
                        }
                    }
                }
            }
        }

        // Layer 2: 顶栏模糊材质层 + 分段胶囊行（在搜索面板之下，展开时会被面板盖住）
        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                MainBarUnderlay(
                    tab = tab,
                    loggedIn = account != null,
                    collapsed = collapsed,
                    feedTabs = feedTabs,
                    feedTabLabels = feedTabLabels,
                    feedPager = feedPager,
                    feedScrollStates = feedScrollStates,
                    onSegment = { segment = it },
                    dynamicPager = dynamicPager,
                    dynamicScrollStates = dynamicScrollStates,
                    meListState = meListState,
                    onDynamicFilter = { dynamicFilterIndex = it },
                    onFeedReselect = { feedRefreshTick++ },
                    onDynamicReselect = { dynamicRefreshTick++ },
                    onDoubleTap = {
                        when (tab) {
                            0 -> feedRefreshTick++
                            1 -> dynamicRefreshTick++
                        }
                    },
                    searchExpanded = searchExpanded,
                    panelCovering = feedPanelCovering,
                    bounds = topBarBounds,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .drawWithContent { if (!searchFullyCovered) drawContent() }
                        .graphicsLayer {},
                )
            }
        }

        // Layer 3: 悬浮底部导航栏（在搜索面板之下，展开时被面板盖住）
        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalPlatformBottomBarMaterialEnabled provides false,
        ) {
            PinnedBarDensity {
                FloatingBottomBar(
                    tab = tab,
                    onTab = { target -> if (!feedPanelCovering) nav.setTab(target) },
                    onReselect = { index ->
                        if (!feedPanelCovering) {
                            when (index) {
                                0 -> feedRefreshTick++
                                1 -> dynamicRefreshTick++
                            }
                        }
                    },
                    barBounds = if (feedPanelCovering) null else topBarBounds,
                    orbVisible = if (enteringOverMain) settledOrbVisible else rawOrbVisible,
                    leftOrbVisible = tab == DYNAMIC_TAB_INDEX,
                    onLeftOrbClick = {
                        if (!feedPanelCovering) {
                            if (account == null) nav.push(Route.Login) else dynamicComposerOpen = true
                        }
                    },
                    orbOrigin = nav.orbOrigin,
                    orbArtworkUrl = PlaybackBridge.currentArtworkUrl,
                    orbIsPlaying = PlaybackBridge.currentIsPlaying && onMainTab,
                    onOrbClick = { if (!feedPanelCovering) nav.restoreMinimized() },
                    onOrbTogglePlay = { PlaybackBridge.togglePlayPause(context) },
                    onOrbStop = { nav.dismissMinimized(pausePlayback = true) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .drawWithContent { if (!searchFullyCovered) drawContent() }
                        .then(if (feedPanelCovering) Modifier.clearAndSetSemantics {} else Modifier),
                )
            }
        }
            }
        }

        // Layer 4: 首页搜索整屏面板（从屏幕顶 0 铺满到屏幕底部，位于常态顶栏与底栏之上、搜索胶囊之下）
        if (feedPanelCovering) {
            HomeSearchOverlay(
                topBarHeight = topBarHeight,
                searchBackdrop = searchBackdrop,
                searchExpanded = searchExpanded,
                panelProgress = { panelProgress.value },
                panelAlpha = { panelAlpha.value },
                state = searchState,
                transitionState = searchTransition,
                history = searchHistory,
                viewModel = searchViewModel,
                container = container,
                nav = nav,
                barBounds = topBarBounds,
                onSelectKeyword = submitHomeSearch,
                modifier = Modifier.fillMaxSize(),
                listState = searchListState,
            )
        }

        // Layer 4.5: 搜索面板之上的融合球。面板铺满整屏，会盖住底栏上的那颗，
        // 从搜索结果的视频返回时球就被面板吞掉。这里补一颗，面板收起后交还底栏那颗。
        if (feedPanelCovering && (nav.minimizedEntry != null || nav.isMinimizingToOrb)) {
            nav.orbOrigin?.let { origin ->
                CompositionLocalProvider(
                    LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
                ) {
                    SecondaryPlaybackOrbOverlay(nav = nav, orbOrigin = origin)
                }
            }
        }

        // Layer 5: 标题行与展开后的 TopAppBarSearchBar（在最顶层，确保搜索胶囊和“取消”按钮不被面板遮挡）
        // 搜索面板挂载期间稳定采样 searchBackdrop，避免收起首帧切换 Backdrop 重建 CouiBackdrop。
        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) {
                if (feedPanelCovering) searchBackdrop else backdrop
            } else {
                null
            },
            LocalEdgeToEdge provides true,
            LocalTransitionFastGlass provides (platformReduceExitRouteBlur && feedPanelCovering && !searchExpanded),
        ) {
            PinnedBarDensity {
                val searchBarDensity = LocalDensity.current
                MainTitleRow(
                    tab = tab,
                    nav = nav,
                    collapsed = collapsed,
                    avatarUrl = account?.face,
                    onAvatarClick = { nav.setTab(ME_TAB_INDEX) },
                    searchState = searchState,
                    onQueryChange = searchViewModel::onQueryChange,
                    searchExpanded = searchExpanded,
                    onSearchOpen = openFeedSearch,
                    onSearchCancel = cancelFeedSearch,
                    onSearchSubmit = submitHomeSearch,
                    showingResults = searchTransition.filtersVisible,
                    onTabChange = searchViewModel::setTab,
                    onSortMenuOpen = { sortMenuExpanded = true },
                    onEditProfile = {
                        if (account == null) showToast(context, "登录后可用")
                        else editProfileOpen = true
                    },
                    panelAlpha = {
                        panelAlpha.value * AppBarBlurOverlayDefaults.scrollAlpha(searchListState.topBarScrollPx(), searchBarDensity)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .graphicsLayer {}
                        .then(if (feedPanelCovering) Modifier.reportTopBarBottom(topBarBounds) else Modifier),
                )
                // 排序圆钮的菜单宿主：圆钮画在 FeedTitleRow 的胶囊行里，这里只把锚点落到它身上。
                if (feedPanelCovering && showingResults) {
                    SearchSortMenuHost(
                        expanded = sortMenuExpanded,
                        onExpandedChange = { sortMenuExpanded = it },
                        state = searchState,
                        viewModel = searchViewModel,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }

        DynamicPublishOverlay(
            container = container,
            composerOpen = dynamicComposerOpen,
            onDismissComposer = { dynamicComposerOpen = false },
            onPublished = { dynamicRefreshTick++ },
        )
        ProfileEditHost(open = editProfileOpen, container = container, nav = nav, onDismiss = { editProfileOpen = false })
    }

    val inHomeSearchResults =
        nav.stack.count { !it.exiting } == 1 && tab == 0 && searchExpanded && searchState.submitted.isNotBlank()
    if (inHomeSearchResults) {
        PlatformBackHandler {
            searchViewModel.onQueryChange("")
        }
    }
}

@Composable
private fun MainBarUnderlay(
    tab: Int,
    loggedIn: Boolean,
    collapsed: () -> Float,
    feedTabs: List<HomeTabType>,
    feedTabLabels: List<String>,
    feedPager: PagerState,
    feedScrollStates: FeedScrollStates,
    onSegment: (Int) -> Unit,
    dynamicPager: PagerState,
    dynamicScrollStates: DynamicScrollStates,
    meListState: LazyListState,
    onDynamicFilter: (Int) -> Unit,
    onFeedReselect: () -> Unit,
    onDynamicReselect: () -> Unit,
    onDoubleTap: () -> Unit,
    searchExpanded: Boolean,
    panelCovering: Boolean,
    bounds: TopBarBounds,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageBarHeight = statusBarTop + TitleRowHeight
    val titleHeightPx = with(density) { TitleRowHeight.toPx() }
    val doubleTap = Modifier.pointerInput(onDoubleTap) {
        detectTapGestures(onDoubleTap = { onDoubleTap() })
    }
    val feedWeight = animateFloatAsState(if (tab == 0) 1f else 0f, tween(MainTabFadeMillis), label = "mainBarFeedWeight")
    val dynamicWeight = animateFloatAsState(if (tab == 1) 1f else 0f, tween(MainTabFadeMillis), label = "mainBarDynamicWeight")
    val meWeight = animateFloatAsState(if (tab == 2) 1f else 0f, tween(MainTabFadeMillis), label = "mainBarMeWeight")

    Box(modifier) {
        AppBarSurface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, 0) }
                .then(if (panelCovering) Modifier else Modifier.reportTopBarBottom(bounds)),
            containerColor = pageContainerColor(),
            scrollFraction = {
                val feedAlpha = pagerTopBarScrollAlpha(feedPager, feedTabs.size, density) { idx ->
                    feedTabs.getOrNull(idx)?.let { feedScrollStates.stateFor(it).topBarScrollPx() } ?: 0f
                }
                val dynamicAlpha = if (!loggedIn) 0f else {
                    pagerTopBarScrollAlpha(dynamicPager, DynamicFilters.size, density) { idx ->
                        DynamicFilters.getOrNull(idx)?.first?.let(dynamicScrollStates::scrollPxFor) ?: 0f
                    }
                }
                val meAlpha = AppBarBlurOverlayDefaults.scrollAlpha(meListState.topBarScrollPx(), density)
                (feedWeight.value * feedAlpha +
                    dynamicWeight.value * dynamicAlpha +
                    meWeight.value * meAlpha).coerceIn(0f, 1f)
            },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(pageBarHeight)
                    .then(doubleTap),
            )
        }

        val capsuleLabels = when (tab) {
            0 -> feedTabLabels
            1 -> DynamicFilters.map { it.second }
            else -> null
        }
        if (capsuleLabels != null) {
            AnimatedVisibility(
                visible = !searchExpanded || tab != 0,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarTop)
                        .then(if (panelCovering) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    Spacer(Modifier.height(TitleRowHeight))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .graphicsLayer { translationY = -collapsed() * titleHeightPx },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val barDensity = LocalDensity.current.density
                        val pageDensity = barDensity
                        // 高光块跟着翻页的落点走：拖动越过翻页阈值、或者松手后的惯性还没停，
                        // 它就开始滑，不等停稳后回写选中态。点标签时 pager 的动画目标也会立刻更新。
                        val selectedTabIndex = if (tab == 0) feedPager.targetPage else dynamicPager.targetPage
                        TabRowWithContour(
                            tabs = capsuleLabels,
                            selectedTabIndex = selectedTabIndex.coerceIn(0, capsuleLabels.lastIndex),
                            onTabSelected = if (tab == 0) onSegment else onDynamicFilter,
                            onTabReselected = when (tab) {
                                0 -> { _: Int -> onFeedReselect() }
                                1 -> { _: Int -> onDynamicReselect() }
                                else -> null
                            },
                            commitOnRelease = true,
                            backdrop = LocalBackdrop.current,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CapsuleSideMargin * (pageDensity / barDensity)),
                        )
                    }
                }
            }
        }
    }
}
