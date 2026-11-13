package erl.bilico.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import erl.bilico.ui.TitleRowHeight
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup

internal val SearchOrders = listOf(
    "综合排序" to "totalrank",
    "最新发布" to "pubdate",
    "最多播放" to "click",
    "最多弹幕" to "dm",
    "最多收藏" to "stow",
)

internal val SearchVideoTids = listOf(
    "全部分区" to 0,
    "动画" to 1,
    "游戏" to 4,
    "知识" to 36,
    "科技" to 188,
    "生活" to 160,
)

/** 排序圆钮的起始留白，与胶囊行内容的内边距一致。 */
internal val SearchSortMenuMargin = 16.dp

/**
 * 顶栏筛选行左侧排序圆钮弹出的级联菜单。
 *
 * 菜单在根 Scaffold 里渲染，所以锚点放哪儿都行，只要坐标与圆钮重合：调用方用一个
 * [ChipTabDefaults.Height] 见方的盒子把它落在圆钮上即可。
 */
@Composable
internal fun SearchSortMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    state: SearchUiState,
    onTab: (Int) -> Unit,
    onOrder: (String) -> Unit,
    onTid: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val dropdownPositionProvider = remember {
        ListPopupDefaults.dropdownPositionProvider(horizontalMargin = SearchSortMenuMargin)
    }
    val menuEntries = remember(state.tab, state.order, state.tid, haptic) {
        val categoryItems = SearchResultTabs.mapIndexed { index, tab ->
            DropdownItem(
                text = tab.label,
                selected = state.tab == index,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTab(index)
                },
            )
        }
        val orderChildren = SearchOrders.map { (label, value) ->
            DropdownItem(
                text = label,
                selected = state.order == value,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onOrder(value)
                },
            )
        }
        val tidChildren = SearchVideoTids.map { (label, value) ->
            DropdownItem(
                text = label,
                selected = state.tid == value,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onTid(value)
                },
            )
        }
        val filterItems = listOf(
            DropdownItem(
                text = "排序方式",
                enabled = state.tab != 1,
                children = orderChildren,
            ),
            DropdownItem(
                text = "视频分区",
                enabled = state.tab == 0,
                children = tidChildren,
            ),
        )
        listOf(
            DropdownEntry(items = categoryItems),
            DropdownEntry(items = filterItems),
        )
    }

    Box(modifier = modifier) {
        OverlayCascadingListPopup(
            show = expanded,
            entries = menuEntries,
            onDismissRequest = { onExpandedChange(false) },
            popupPositionProvider = dropdownPositionProvider,
            alignment = PopupPositionProvider.Align.Start,
            collapseOnSelection = true,
        )
    }
}

/**
 * 排序圆钮的菜单宿主：菜单渲染在根 Scaffold，这里只负责把锚点落到胶囊行左侧的圆钮上。
 *
 * 调用方把整块放在顶栏所在的 `PinnedBarDensity` 里，锚点与 `FeedTitleRow` 的胶囊行共用
 * 同一套行高和间距，所以圆钮挪动时菜单会跟着走，不用两边各写一份坐标。
 */
@Composable
internal fun SearchSortMenuHost(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    state: SearchUiState,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = statusBarTop + TitleRowHeight + SearchChipRowGap),
    ) {
        SearchSortMenu(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            state = state,
            onTab = viewModel::setTab,
            onOrder = viewModel::setOrder,
            onTid = viewModel::setTid,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = SearchSortMenuMargin)
                .size(ChipTabDefaults.Height),
        )
    }
}
