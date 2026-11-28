package erl.bilico.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.PinnedScrollBehavior
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.rememberAppBarVeiledBackdrop as couiRememberAppBarVeiledBackdrop
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import kotlin.math.abs

/**
 * 玻璃效果总开关。BiliCO 没有把它做成设置项：Cobo 的观感靠顶栏与底栏的模糊和高光撑着，
 * 关掉之后整条栏会退回平涂，和设计稿对不上。
 */
const val BlurEffectsEnabled = true

/** 模糊关闭时连 backdrop 的录制一起省掉；没人采样它，每层每帧重录都是白费。 */
fun Modifier.layerBackdropIf(enabled: Boolean, backdrop: LayerBackdrop): Modifier =
    if (enabled) layerBackdrop(backdrop) else this

/**
 * 列表用于驱动顶栏渐变材质的滚动像素：位于顶部时严格为 `0f`，翻过首项后直接封顶，
 * 交给 [AppBarBlurOverlayDefaults.scrollAlpha] 在前 30dp 内平滑渐显。
 */
fun LazyListState.topBarScrollPx(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()

/** 网格版本的顶栏滚动像素计算，与 [LazyListState.topBarScrollPx] 口径一致。 */
fun LazyGridState.topBarScrollPx(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()

/** 瀑布流版本的顶栏滚动像素计算，与 [LazyListState.topBarScrollPx] 口径一致。 */
fun LazyStaggeredGridState.topBarScrollPx(): Float =
    if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat()

/**
 * 横向分页容器对应的顶栏滚动透明度：左右滑动翻页时在当前页与相邻页之间线性插值，
 * 两页都在顶部时严格返回 `0f`，避免顶部常驻阴影或翻页瞬间跳变。
 */
fun pagerTopBarScrollAlpha(
    pagerState: PagerState,
    pageCount: Int,
    density: Density,
    scrollPxForPage: (Int) -> Float,
): Float {
    if (pageCount <= 0) return 0f
    val page = pagerState.currentPage.coerceIn(0, pageCount - 1)
    val alphaA = AppBarBlurOverlayDefaults.scrollAlpha(scrollPxForPage(page), density)
    val offset = pagerState.currentPageOffsetFraction
    if (abs(offset) < 0.001f) return alphaA
    val neighbor = (if (offset > 0f) page + 1 else page - 1).coerceIn(0, pageCount - 1)
    val alphaB = AppBarBlurOverlayDefaults.scrollAlpha(scrollPxForPage(neighbor), density)
    val fraction = abs(offset).coerceIn(0f, 1f)
    return alphaA + (alphaB - alphaA) * fraction
}

/**
 * 为绑定 [LazyListState] 的 `SmallTopAppBar(gradientGlass = true)` 提供防漂移的 [PinnedScrollBehavior]：
 * 手势拖动由 `nestedScrollConnection` 同帧更新，同时监听列表回到首项时的真实偏移，
 * 保证程序回顶、筛选重置或条目高度变化后顶部严格归零、不残留顶栏阴影。
 */
@Composable
fun rememberListTopBarScrollBehavior(listState: LazyListState): ScrollBehavior {
    val behavior = PinnedScrollBehavior()
    val density = LocalDensity.current
    val thresholdPx = with(density) { AppBarBlurOverlayDefaults.ScrollAlphaThresholdDp.dp.toPx() }
    LaunchedEffect(listState, behavior, thresholdPx) {
        snapshotFlow {
            if (listState.firstVisibleItemIndex > 0) -thresholdPx
            else -listState.firstVisibleItemScrollOffset.toFloat().coerceAtMost(thresholdPx)
        }.collect { target ->
            behavior.state.contentOffset = target
        }
    }
    return behavior
}

/** 网格版本的 [rememberListTopBarScrollBehavior]。 */
@Composable
fun rememberGridTopBarScrollBehavior(gridState: LazyGridState): ScrollBehavior {
    val behavior = PinnedScrollBehavior()
    val density = LocalDensity.current
    val thresholdPx = with(density) { AppBarBlurOverlayDefaults.ScrollAlphaThresholdDp.dp.toPx() }
    LaunchedEffect(gridState, behavior, thresholdPx) {
        snapshotFlow {
            if (gridState.firstVisibleItemIndex > 0) -thresholdPx
            else -gridState.firstVisibleItemScrollOffset.toFloat().coerceAtMost(thresholdPx)
        }.collect { target ->
            behavior.state.contentOffset = target
        }
    }
    return behavior
}

/** 横向分页页面共用的 [PinnedScrollBehavior]，按当前可见页的滚动量平滑驱动顶栏材质。 */
@Composable
fun rememberPagerTopBarScrollBehavior(
    pagerState: PagerState,
    pageCount: Int,
    scrollPxForPage: (Int) -> Float,
): ScrollBehavior {
    val behavior = PinnedScrollBehavior(canScroll = { false })
    val density = LocalDensity.current
    val thresholdPx = with(density) { AppBarBlurOverlayDefaults.ScrollAlphaThresholdDp.dp.toPx() }
    LaunchedEffect(pagerState, pageCount, behavior, density, thresholdPx) {
        snapshotFlow {
            -pagerTopBarScrollAlpha(pagerState, pageCount, density, scrollPxForPage) * thresholdPx
        }.collect { target ->
            behavior.state.contentOffset = target
        }
    }
    return behavior
}

/**
 * 自定义顶栏的材质层：模糊开着走 [AppBarBlurOverlay]，关掉就画整块实色。
 * 两条路在关掉模糊后的观感不同，统一成实色才不会忽深忽浅。
 *
 * 材质只在 `Scaffold(edgeToEdge = true)` 里才画，而这里常常挂在 Scaffold 之外（浮在整页列表上），
 * 所以本组件自己把 [LocalEdgeToEdge] 置真；否则 [AppBarBlurOverlay] 会退化成一块什么都没有的壳，
 * 顶栏变成一条硬边实色。
 *
 * @param platformRenderer 交给系统窗口材质去采样。页面里有自己录 backdrop 的图层时传 `false`，
 *   走 COUI 的引擎采样那一层：系统材质采的是整个窗口，会把自己的输出吃回去，滚动时丢帧发黑。
 */
@Composable
fun AppBarSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = pageContainerColor(),
    scrollFraction: () -> Float = { 1f },
    platformRenderer: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    if (BlurEffectsEnabled) {
        CompositionLocalProvider(LocalEdgeToEdge provides true) {
            AppBarBlurOverlay(
                modifier = modifier,
                containerColor = containerColor,
                scrollFraction = scrollFraction,
                platformRenderer = platformRenderer,
                content = content,
            )
        }
        return
    }
    Box(
        modifier.drawBehind {
            val alpha = scrollFraction().coerceIn(0f, 1f)
            if (alpha > 0f) drawRect(containerColor, alpha = alpha)
        },
        content = content,
    )
}

/**
 * 为盖在 [AppBarSurface] 上方的兄弟顶栏控件提供带同源渐变蒙层的 [Backdrop]；
 * 直接复用 COUI 的 `rememberAppBarVeiledBackdrop`（基于 `DelegatingBackdrop` 透传采样保活，零双重模糊）。
 */
@Composable
internal fun rememberAppBarVeiledBackdrop(
    containerColor: Color = pageContainerColor(),
    upstream: Backdrop? = LocalBackdrop.current,
    scrollFraction: () -> Float = { 1f },
): Backdrop? = if (!BlurEffectsEnabled) {
    null
} else {
    couiRememberAppBarVeiledBackdrop(
        scrollFraction = scrollFraction,
        containerColor = containerColor,
        backdrop = upstream,
    )
}
