package erl.bilico.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicFilters
import erl.bilico.feature.dynamic.DynamicScreen
import erl.bilico.feature.feed.FEED_SEGMENTS
import erl.bilico.feature.feed.FeedScreen
import erl.bilico.feature.live.LiveListScreen
import erl.bilico.feature.me.MeScreen
import erl.bilico.feature.search.SearchPanel
import erl.bilico.ui.components.NavDynamicFilledIcon
import erl.bilico.ui.components.NavDynamicOutlineIcon
import erl.bilico.ui.components.NavHomeFilledIcon
import erl.bilico.ui.components.NavHomeOutlineIcon
import erl.bilico.ui.components.NavLiveFilledIcon
import erl.bilico.ui.components.NavLiveOutlineIcon
import erl.bilico.ui.components.SegmentCapsule
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Search
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** 底栏切页的交叉淡入时长。 */
private const val MainTabFadeMillis = 220

/** 顶栏标题行高度；列表滚动时标题就折这么高。 */
internal val TitleRowHeight = 44.dp

/** 顶栏左上角头像的直径。 */
private val AvatarSize = 32.dp

/** 底栏三格的总宽：三格各 50dp、两处间距 8dp、左右内边距各 12dp，加起来 190dp。 */
private val BottomBarWidth = 190.dp

/** 推荐与动态的标题行会跟着列表折起来，直播页固定一条。 */
private val tabTitles = listOf("推荐", "动态", "直播", "我的")

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

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTopBarBounds provides topBarBounds) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdropIf(BlurEffectsEnabled, backdrop)
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
                            )

                            2 -> LiveListScreen(
                                container = container,
                                nav = nav,
                                topInset = topBarHeight,
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
                    onAvatarClick = { nav.setTab(3) },
                    segment = segment,
                    onSegment = { segment = it },
                    dynamicFilterIndex = dynamicFilterIndex,
                    onDynamicFilter = { dynamicFilterIndex = it },
                    pager = feedPager,
                    dynamicPager = dynamicPager,
                    onFeedReselect = { feedRefreshTick++ },
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
            Box(Modifier.fillMaxWidth().height(pageBarHeight))
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
        val capsuleLabels = when (tab) {
            0 -> FEED_SEGMENTS
            1 -> DynamicFilters.map { it.second }
            else -> null
        }
        if (capsuleLabels != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .graphicsLayer { translationY = -collapsed() * titleHeightPx },
                contentAlignment = Alignment.TopCenter,
            ) {
                SegmentCapsule(
                    labels = capsuleLabels,
                    selectedIndex = if (tab == 0) segment else dynamicFilterIndex,
                    onSelect = if (tab == 0) onSegment else onDynamicFilter,
                    // 两个 tab 的胶囊都跟着各自的 pager 走，拖动时指示块贴住手指。
                    pager = if (tab == 0) pager else dynamicPager,
                    onReselect = if (tab == 0) onFeedReselect else null,
                )
            }
        }
    }
}

/** 顶栏左上角的账号头像；未登录时退回一个中性图标。 */
@Composable
private fun FeedAvatar(
    url: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizePx = with(LocalDensity.current) { AvatarSize.roundToPx() }
    val model = remember(url, sizePx) { Formatters.sizedImageUrl(url, sizePx, sizePx) }
    Box(
        modifier = modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(COUITheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .testTag("top_avatar"),
        contentAlignment = Alignment.Center,
    ) {
        if (model.isBlank()) {
            Icon(COUIIcons.Contacts, "我的", Modifier.size(20.dp))
        } else {
            AsyncImage(
                model = model,
                contentDescription = "我的",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun RowScope.TabActions(tab: Int, nav: NavState, onSearch: () -> Unit) {
    when (tab) {
        0 -> HighlightedIconButton(
            onClick = onSearch,
            modifier = Modifier
                .couiTopAppBarActionItem()
                .testTag("top_search"),
        ) {
            Icon(COUIIcons.Basic.Search, "搜索", Modifier.size(24.dp))
        }

        3 -> HighlightedIconButton(
            onClick = { nav.push(Route.Settings) },
            modifier = Modifier
                .couiTopAppBarActionItem()
                .testTag("top_settings"),
        ) {
            Icon(COUIIcons.Settings, "设置", Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FloatingBottomBar(
    tab: Int,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 底栏就三个 tab。个人页从顶栏头像进，不再占底栏的位置。
        // 宽度收在 190dp：三格各 50dp，和 Cobo 底栏的格子一样宽；放开的话每格会用满 70dp，
        // 三个 tab 撑得很散。
        BottomFloatingNavigationBar(modifier = Modifier.width(BottomBarWidth)) {
            BottomFloatingNavigationBarItem(
                selected = tab == 0,
                onClick = { onTab(0) },
                icon = NavHomeOutlineIcon,
                selectedIcon = NavHomeFilledIcon,
                label = "推荐",
                modifier = Modifier.testTag("bottom_tab_feed"),
            )
            BottomFloatingNavigationBarItem(
                selected = tab == 1,
                onClick = { onTab(1) },
                icon = NavDynamicOutlineIcon,
                selectedIcon = NavDynamicFilledIcon,
                label = "动态",
                modifier = Modifier.testTag("bottom_tab_dynamic"),
            )
            BottomFloatingNavigationBarItem(
                selected = tab == 2,
                onClick = { onTab(2) },
                icon = NavLiveOutlineIcon,
                selectedIcon = NavLiveFilledIcon,
                label = "直播",
                modifier = Modifier.testTag("bottom_tab_live"),
            )
        }
    }
}
