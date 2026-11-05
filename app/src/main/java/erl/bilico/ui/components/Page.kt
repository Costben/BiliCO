package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
