package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
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
    topBar: (@Composable () -> Unit)? = null,
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
                if (topBar != null) {
                    Box(Modifier.align(Alignment.TopCenter)) {
                        topBar()
                    }
                } else {
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
 * 标签栏作为 [SmallTopAppBar] 的 bottomContent 挂接在顶栏毛玻璃内部，整体通透连续，
 * 避免中途出现突兀不透明色块；列表内容从毛玻璃下方滑过被优雅采样；
 * 首项内容通过动态测量的顶栏高度留出充足间距，绝不被标签栏遮挡。
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
    val density = LocalDensity.current
    // 状态栏 + 顶栏标题行 44dp + 标签行约 48dp + 呼吸间距 6dp，避免首帧为 0 导致内容跳动
    val defaultContentTop = with(density) {
        val statusPx = WindowInsets.statusBars.getTop(density)
        statusPx.toDp() + 44.dp + 48.dp + 6.dp
    }
    var measuredContentTop by remember { mutableStateOf(defaultContentTop) }

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
                page(index, measuredContentTop)
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = title,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .onSizeChanged {
                            val h = with(density) { it.height.toDp() }
                            // 顶栏物理底沿落在 ChipTab 胶囊下方 6dp；首项留出 6dp 使得第一张卡片
                            // 距离 ChipTab 胶囊底沿正好 12dp，严丝合缝对齐系统便签规范。
                            measuredContentTop = h + 6.dp
                        },
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = BlurEffectsEnabled,
                    navigationIcon = { BackIconButton(onClick = onBack) },
                    actions = actions,
                    bottomContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 6.dp),
                        ) {
                            header()
                        }
                    },
                )
            }
        }
    }
}
