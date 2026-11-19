package erl.bilico.feature.comment

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.CommentSort
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour

/** 评论排序迷你分段标签栏（最热 / 最新），对应 COUI `SegmentButton.Tiny` 规格。 */
@Composable
internal fun CommentSortTabRow(
    sort: CommentSort,
    onSelectSort: (CommentSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    MiniSortTabRow(
        tabs = COMMENT_SORT_TABS,
        selectedIndex = if (sort == CommentSort.Hot) 0 else 1,
        onSelectIndex = { index ->
            onSelectSort(if (index == 0) CommentSort.Hot else CommentSort.New)
        },
        minWidth = 56.dp,
        modifier = modifier,
    )
}

/** 评论详情（楼中楼）排序分段标签栏（最旧 / 最热 / 最新）。 */
@Composable
internal fun ReplySortTabRow(
    sort: ReplySort,
    onSelectSort: (ReplySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = REPLY_SORT_ORDER.indexOf(sort).coerceAtLeast(0)
    MiniSortTabRow(
        tabs = REPLY_SORT_TABS,
        selectedIndex = selectedIndex,
        onSelectIndex = { index ->
            onSelectSort(REPLY_SORT_ORDER.getOrElse(index) { ReplySort.Old })
        },
        minWidth = 48.dp,
        modifier = modifier,
    )
}

@Composable
private fun MiniSortTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    minWidth: Dp,
    modifier: Modifier = Modifier,
) {
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelectIndex,
        minWidth = minWidth,
        height = TabRowDefaults.TabRowWithContourTinyHeight,
        cornerRadius = TabRowDefaults.TabRowWithContourTinyCornerRadius,
        contourPadding = TabRowDefaults.TabRowWithContourTinyPadding,
        segmentPadding = 10.dp,
        colors = TabRowDefaults.tabRowColors(),
        modifier = modifier.wrapContentWidth(unbounded = true),
    )
}

private val COMMENT_SORT_TABS = listOf("最热", "最新")
private val REPLY_SORT_TABS = listOf("最旧", "最热", "最新")
private val REPLY_SORT_ORDER = listOf(ReplySort.Old, ReplySort.Hot, ReplySort.New)
