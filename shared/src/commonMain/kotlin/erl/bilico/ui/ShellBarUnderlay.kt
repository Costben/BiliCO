package erl.bilico.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import erl.bilico.data.settings.HomeTabType
import erl.bilico.feature.dynamic.DynamicFilters
import erl.bilico.feature.dynamic.DynamicScrollStates
import erl.bilico.feature.feed.FeedScrollStates
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.blur.LocalBackdrop

@Composable
internal fun MainBarUnderlay(
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
