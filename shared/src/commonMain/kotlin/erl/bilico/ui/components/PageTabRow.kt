package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 画在页面内容里的标签栏。
 *
 * 默认不采样任何 backdrop，用底下这套底色和描边把结果补成和顶栏那条带真模糊的标签栏同一档
 * 灰度。传 [backdrop] 时改走库里的玻璃材质：整条容器模糊背后的列表，和便签那排胶囊一样。
 *
 * 注意 backdrop 必须是**这个栏下面那一层**（比如列表所在的 pager），不能是整页的 backdrop。
 * 栏若落在被采样图层自己的录制范围里，等于让图层画它自己，HWUI 会在 prepareTreeImpl 里递归
 * 到栈溢出。
 */
@Composable
internal fun PageTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
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
        backdrop = backdrop,
        modifier = modifier,
    )
}
