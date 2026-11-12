package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 画在页面内容里的标签栏。
 *
 * 这些位置（空间页的列表项、搜索结果）本身就落在本页 backdrop 的录制范围里，让它采样这层
 * backdrop 等于让图层画它自己，HWUI 会在 prepareTreeImpl 里递归到栈溢出。所以这里不采样，
 * 用底下这套底色和描边把结果补成和顶栏那条带真模糊的标签栏同一档灰度。
 */
@Composable
internal fun PageTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = if (dark) Color(0x1F000000) else Color(0x33FFFFFF),
        ),
        containerMaterial = TabRowDefaults.containerStroke(dark),
        indicatorMaterial = TabRowDefaults.indicatorStroke(dark),
        modifier = modifier,
    )
}
