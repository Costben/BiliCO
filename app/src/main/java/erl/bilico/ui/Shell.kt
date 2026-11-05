package erl.bilico.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicScreen
import erl.bilico.feature.feed.FeedScreen
import erl.bilico.feature.live.LiveListScreen
import erl.bilico.feature.me.MeScreen
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBar
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
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs

/** 底栏切页的交叉淡入时长。 */
private const val MainTabFadeMillis = 220

/** 顶栏标题行高度；列表滚动时标题就折这么高。 */
internal val TitleRowHeight = 44.dp

/** 推荐页的标题行才会跟着列表折起来，其余页面固定一条。 */
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
                                onCollapse = { feedCollapse = it },
                                onScrollIdle = { feedCollapse = 0f },
                            )

                            1 -> DynamicScreen(
                                container = container,
                                nav = nav,
                                topInset = topBarHeight,
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
                    collapsePx = feedCollapse,
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
    }
}

@Composable
private fun MainTopBar(
    tab: Int,
    nav: NavState,
    collapsePx: Float,
    bounds: TopBarBounds,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageBarHeight = statusBarTop + TitleRowHeight
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    val collapseTarget = if (tab == 0) (collapsePx / titleHeightPx).coerceIn(0f, 1f) else 0f
    // 折叠量只认 Animatable 的当前值这一条来源，避免目标一步跨过阈值时先折没一帧再弹回来。
    val collapseShown = remember { Animatable(collapseTarget) }
    LaunchedEffect(collapseTarget) {
        if (abs(collapseTarget - collapseShown.value) > 0.6f) {
            collapseShown.animateTo(collapseTarget, tween(MainTabFadeMillis))
        } else {
            collapseShown.snapTo(collapseTarget)
        }
    }
    val collapsed = collapseShown.value

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
            )
        }
    }
}

@Composable
private fun BarContent(
    tab: Int,
    nav: NavState,
    collapsed: Float,
) {
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        Box(Modifier.fillMaxWidth().height(TitleRowHeight)) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationY = -collapsed * titleHeightPx }) {
                    Text(
                        tabTitles.getOrElse(tab) { "" },
                        color = COUITheme.colorScheme.onSurface.copy(alpha = 1f - collapsed),
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
                        translationY = -collapsed * titleHeightPx / 2f
                        alpha = 1f - collapsed
                    },
                horizontalArrangement = Arrangement.spacedBy(
                    TopAppBarDefaults.ActionMenuItemSpacing,
                    Alignment.End,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabActions(tab, nav)
            }
        }
    }
}

@Composable
private fun RowScope.TabActions(tab: Int, nav: NavState) {
    when (tab) {
        0 -> HighlightedIconButton(
            onClick = { nav.push(Route.Search) },
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
            .navigationBarsPadding()
    ) {
        // 只有四个 tab，不带左右配件：底栏就是一条胶囊。
        BottomFloatingAccessoryNavigationBar(modifier = Modifier) {
            BottomFloatingNavigationBarItem(
                selected = tab == 0,
                onClick = { onTab(0) },
                icon = COUIIcons.Home,
                label = "推荐",
                modifier = Modifier.testTag("bottom_tab_feed"),
            )
            BottomFloatingNavigationBarItem(
                selected = tab == 1,
                onClick = { onTab(1) },
                icon = COUIIcons.Community,
                label = "动态",
                modifier = Modifier.testTag("bottom_tab_dynamic"),
            )
            BottomFloatingNavigationBarItem(
                selected = tab == 2,
                onClick = { onTab(2) },
                icon = COUIIcons.Play,
                label = "直播",
                modifier = Modifier.testTag("bottom_tab_live"),
            )
            BottomFloatingNavigationBarItem(
                selected = tab == 3,
                onClick = { onTab(3) },
                icon = COUIIcons.Contacts,
                label = "我的",
                modifier = Modifier.testTag("bottom_tab_me"),
            )
        }
    }
}
