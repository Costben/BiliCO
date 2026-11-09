package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.glassBarContentTop
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop

/**
 * 二级页的统一外壳：玻璃顶栏 + 铺到屏幕边缘的列表。
 *
 * 列表录进 backdrop，顶栏浮在上面采样它做渐变模糊；末项靠 contentPadding 抬到导航条之上。
 * 页面内容全部以列表项给出，滚动时顶栏才有东西可采。
 */
@Composable
fun SecondaryPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    Box(modifier.fillMaxSize().background(pageColor)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, backdrop)
                .background(pageColor)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, 10.dp),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = title,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = BlurEffectsEnabled,
                    navigationIcon = { BackIconButton(onClick = onBack) },
                    actions = actions,
                )
            }
        }
    }
}

/** [SecondaryPage] 的双列版本：番剧、图集这类以封面为主的列表用它。 */
@Composable
fun SecondaryGridPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit,
) {
    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    Box(modifier.fillMaxSize().background(pageColor)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, backdrop)
                .background(pageColor)
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, 10.dp),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = title,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = BlurEffectsEnabled,
                    navigationIcon = { BackIconButton(onClick = onBack) },
                )
            }
        }
    }
}

/**
 * [SecondaryPage] 的横向分页版本：顶栏与一行标签钉住，内容按页左右滑动。
 *
 * [header] 画在顶栏下方、盖在页面之上，用页面底色挡住从底下滚过的内容；量出来的高度交给 [page]，
 * 让每页列表把这一条让出来，于是列表照常滚到玻璃顶栏下面被采样。
 */
@Composable
fun SecondaryPagerPage(
    title: String,
    onBack: () -> Unit,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    header: @Composable () -> Unit,
    page: @Composable (index: Int, contentTop: Dp) -> Unit,
) {
    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val contentTop = glassBarContentTop(topBarHeight, 10.dp)
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }

    Box(modifier.fillMaxSize().background(pageColor)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, backdrop)
                .background(pageColor)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { index ->
                page(index, contentTop + headerHeight)
            }

            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(contentTop))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(pageColor)
                        .padding(vertical = 6.dp)
                        .onSizeChanged { headerHeight = with(density) { it.height.toDp() } },
                ) {
                    header()
                }
                Spacer(Modifier.weight(1f))
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = title,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = BlurEffectsEnabled,
                    navigationIcon = { BackIconButton(onClick = onBack) },
                    actions = actions,
                )
            }
        }
    }
}
