package erl.bilico.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicFilters
import erl.bilico.feature.dynamic.DynamicScreen
import erl.bilico.feature.feed.FEED_SEGMENTS
import erl.bilico.feature.feed.FeedScreen
import erl.bilico.feature.me.MeScreen
import erl.bilico.feature.search.SearchPanel
import erl.bilico.ui.components.CouiSegmentCapsuleShape
import erl.bilico.ui.components.NavDynamicFilledIcon
import erl.bilico.ui.components.NavDynamicOutlineIcon
import erl.bilico.ui.components.NavHomeFilledIcon
import erl.bilico.ui.components.NavHomeOutlineIcon
import erl.bilico.ui.components.NavMeFilledIcon
import erl.bilico.ui.components.NavMeOutlineIcon
import erl.bilico.ui.components.SegmentCapsule
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SpotLightDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTouchMotion
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs

/** 底栏切页的交叉淡入时长。 */
private const val MainTabFadeMillis = 220

/** 底栏双击判定窗口，和系统双击超时一致。 */
private const val BottomBarDoubleTapMillis = 300L

/** 顶栏标题行高度；列表滚动时标题就折这么高。 */
internal val TitleRowHeight = 44.dp

/** 底栏三格的总宽：三格各 50dp、两处间距 8dp、左右内边距各 12dp，加起来 190dp。 */
private val BottomBarWidth = 190.dp

/** 底栏三格：推荐、动态、我的。直播收进推荐页顶部的分段胶囊里。 */
private val tabTitles = listOf("推荐", "动态", "我的")

/** 分段胶囊每段的默认宽度，和 Cobo 的分段一致。 */
private val DefaultSegmentWidth = 84.dp

/**
 * 分段胶囊轨道左右各留的边距，和下面卡片列表的 12dp 缩进对齐。
 * 列表跟着系统密度走，顶栏钉在 360dp 密度上，所以这是「页面 dp」，用的时候要换算。
 */
private val CapsuleSideMargin = 12.dp

/** SegmentCapsule 轨道自带的左右内边距（各 4dp），算每段宽度时要扣掉。 */
private val CapsuleTrackPadding = 8.dp

@Composable
fun MainShell(nav: NavState, container: AppContainer) {
    val tab = nav.mainTab
    val holder = rememberSaveableStateHolder()
    val backdrop = rememberLayerBackdrop()
    // 底栏浮在所有页面之上，它停在哪条线以下由这里统一报出去。
    val topBarBounds = remember { TopBarBounds() }
    val topBarHeight = pinnedHeight(TitleRowHeight)
    var feedCollapse by remember { mutableFloatStateOf(0f) }
    var dynamicCollapse by remember { mutableFloatStateOf(0f) }
    var dynamicFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    // 双击当前分段回顶刷新：每双击一次加一，分段那边看到变化就归位重拉。
    var feedRefreshTick by remember { mutableIntStateOf(0) }
    var dynamicRefreshTick by remember { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val account by container.session.account.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    // 翻页状态提到外壳：分段胶囊要跟着 pager 连续移动，不能等切完页再动。
    val feedPager = rememberPagerState(initialPage = segment, pageCount = { FEED_SEGMENTS.size })
    val dynamicPager = rememberPagerState(initialPage = dynamicFilterIndex, pageCount = { DynamicFilters.size })

    // 标题行的折叠量只在这里算一次：顶栏和分段胶囊读同一个值，滚动时才不会各折各的。
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    val collapsePx = when (tab) {
        0 -> feedCollapse
        1 -> dynamicCollapse
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

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
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
                    onSearch = { searchOpen = true },
                    bounds = topBarBounds,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                )
            }
        }

        CompositionLocalProvider(LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null) {
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
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // 搜索面板盖在整页之上：点放大镜就地展开，不另开一页。
        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(animationSpec = tween(MainTabFadeMillis)),
            exit = fadeOut(animationSpec = tween(MainTabFadeMillis)),
        ) {
            SearchPanel(
                nav = nav,
                container = container,
                onClose = { searchOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
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
    onSearch: () -> Unit,
    bounds: TopBarBounds,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageBarHeight = statusBarTop + TitleRowHeight

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
                onSearch = onSearch,
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
    onSearch: () -> Unit,
) {
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
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
                    // 推荐页的左上角是登录用户自己的头像，其余 tab 仍是文字标题。
                    if (tab == 0) {
                        FeedAvatar(
                            url = avatarUrl,
                            onClick = onAvatarClick,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 8.dp),
                        )
                    } else {
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
                TabActions(tab, nav, onSearch)
            }
        }

        // 分段胶囊画在顶栏这一层：盖在玻璃之上才不会随折叠一起被模糊掉。
        // 推荐 tab 的胶囊跟着 pager 连续移动，动态 tab 的筛选只有点击切换。
        // 弹性（按下缩放、拖动橡皮筋形变、跟随手指的高光）由 COUI 的 couiTouchMotion 提供；
        // 轮廓用我们自己的连续曲率 CouiSegmentCapsuleShape —— COUI 的 TabRowWithContour 在
        // 40dp 高度下会因浮点比较掉进 addRoundRect 分支，画出正圆端，所以不用它。
        val capsuleLabels = when (tab) {
            0 -> FEED_SEGMENTS
            1 -> DynamicFilters.map { it.second }
            else -> null
        }
        if (capsuleLabels != null) {
            val dark = COUITheme.colorScheme.background.luminance() < 0.5f
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .graphicsLayer { translationY = -collapsed() * titleHeightPx },
                contentAlignment = Alignment.TopCenter,
            ) {
                // 推荐 tab 现在是四段，84dp 一段会在窄屏上顶出边界：按可用宽度均分再封顶。
                // 轨道外缘 = 段宽 × 段数 + 轨道内边距，要和卡片列表左右缩进对齐。
                val barDensity = LocalDensity.current.density
                val pageDensity = LocalContext.current.resources.displayMetrics.density
                val sideMargin = CapsuleSideMargin * (pageDensity / barDensity)
                val capsuleWidth = minOf(
                    DefaultSegmentWidth,
                    ((maxWidth - sideMargin * 2 - CapsuleTrackPadding) / capsuleLabels.size)
                        .coerceAtLeast(1.dp),
                )
                SegmentCapsule(
                    labels = capsuleLabels,
                    selectedIndex = if (tab == 0) segment else dynamicFilterIndex,
                    onSelect = if (tab == 0) onSegment else onDynamicFilter,
                    // 两个 tab 的胶囊都跟着各自的 pager 走，拖动时指示块贴住手指。
                    pager = if (tab == 0) pager else dynamicPager,
                    segmentWidth = capsuleWidth,
                    // 双击当前选中项回顶刷新：推荐 tab 刷当前分段，动态 tab 刷当前筛选（含直播）。
                    onReselect = when (tab) {
                        0 -> onFeedReselect
                        1 -> onDynamicReselect
                        else -> null
                    },
                    modifier = Modifier.couiTouchMotion(
                        enabled = true,
                        shape = CouiSegmentCapsuleShape,
                        lightParams = SpotLightDefaults.capsuleParams2(dark),
                        // 胶囊自己画背景，这里只借它的形变和缩放，不接管绘制。
                        surface = Modifier,
                        pressScaleEnabled = true,
                        // 拖动留给胶囊内的点击和 pager，形变只做观察。
                        consumeDrag = false,
                        lightEnabled = true,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(
    tab: Int,
    onTab: (Int) -> Unit,
    onReselect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 底栏 item 用的是 combinedClickable，没有双击回调：自己记上一次点在同一格的时刻，
    // 判定窗口内再点一次就当双击，回顶刷新当前 tab。
    var lastTapTab by remember { mutableIntStateOf(-1) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val click: (Int) -> Unit = { index ->
        val now = SystemClock.uptimeMillis()
        when {
            index != tab -> {
                lastTapTab = -1
                onTab(index)
            }

            lastTapTab == index && now - lastTapTime < BottomBarDoubleTapMillis -> {
                lastTapTab = -1
                onReselect(index)
            }

            else -> {
                lastTapTab = index
                lastTapTime = now
            }
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 底栏就三个 tab：推荐、动态、我的。直播是推荐页里的第四个分段。
        // 宽度收在 190dp：三格各 50dp，和 Cobo 底栏的格子一样宽；放开的话每格会用满 70dp，
        // 三个 tab 撑得很散。
        BottomFloatingNavigationBar(modifier = Modifier.width(BottomBarWidth)) {
            BottomFloatingNavigationBarItem(
                selected = tab == 0,
                onClick = { click(0) },
                icon = NavHomeOutlineIcon,
                selectedIcon = NavHomeFilledIcon,
                label = "推荐",
                modifier = Modifier.testTag("bottom_tab_feed"),
            )
            BottomFloatingNavigationBarItem(
                selected = tab == 1,
                onClick = { click(1) },
                icon = NavDynamicOutlineIcon,
                selectedIcon = NavDynamicFilledIcon,
                label = "动态",
                modifier = Modifier.testTag("bottom_tab_dynamic"),
            )
            BottomFloatingNavigationBarItem(
                selected = tab == ME_TAB_INDEX,
                onClick = { click(ME_TAB_INDEX) },
                icon = NavMeOutlineIcon,
                selectedIcon = NavMeFilledIcon,
                label = "我的",
                modifier = Modifier.testTag("bottom_tab_me"),
            )
        }
    }
}
