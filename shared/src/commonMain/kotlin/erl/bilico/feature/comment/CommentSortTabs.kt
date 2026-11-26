package erl.bilico.feature.comment

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentSort
import io.github.suqi8.coui.kmp.basic.TabRowDefaults
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import kotlin.math.ceil

data class CommentUiState(
    val items: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val isSwitchingSort: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val total: Int = 0,
    val message: String? = null,
    val sort: CommentSort = CommentSort.Hot,
    val renderedSort: CommentSort = CommentSort.Hot,
    val focusRootId: Long = 0L,
    val pendingReplyDialog: Pair<Comment, Long>? = null,
)

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
        minWidth = 54.dp,
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
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // COUI SegmentedControl 按 14sp（字号缩放上限 1.35x）+ Medium 渲染，
    // 且 padding(horizontal) 两侧独立四舍五入；这里显式按实测像素宽加余量托底，
    // 防止大字体或非整数倍密度下每段短 1~2px 触发「最...」截断。
    val effectiveMinWidth = remember(tabs, minWidth, density, measurer) {
        val adaptedFontScale = density.fontScale.coerceIn(1f, COUI_SEGMENT_MAX_FONT_SCALE)
        val adaptedPx = 14f * density.density * adaptedFontScale
        val measureStyle = TextStyle(
            fontSize = with(density) { adaptedPx.toSp() },
            fontWeight = FontWeight.Medium,
        )
        val maxLabelPx = tabs.maxOfOrNull { label ->
            ceil(measurer.measure(text = label, style = measureStyle, maxLines = 1).size.width.toFloat()).toInt()
        } ?: 0
        val padPx = with(density) { MINI_SEGMENT_PADDING.roundToPx() * 2 }
        val safetyPx = with(density) { MINI_SEGMENT_SAFETY_MARGIN.roundToPx() }
        val requiredDp = with(density) { (maxLabelPx + padPx + safetyPx).toDp() }
        maxOf(minWidth, requiredDp)
    }

    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedIndex,
        onTabSelected = onSelectIndex,
        minWidth = effectiveMinWidth,
        height = TabRowDefaults.TabRowWithContourTinyHeight,
        cornerRadius = TabRowDefaults.TabRowWithContourTinyCornerRadius,
        contourPadding = TabRowDefaults.TabRowWithContourTinyPadding,
        segmentPadding = MINI_SEGMENT_PADDING,
        colors = TabRowDefaults.tabRowColors(),
        modifier = modifier.wrapContentWidth(unbounded = true),
    )
}

private const val COUI_SEGMENT_MAX_FONT_SCALE = 1.35f
private val MINI_SEGMENT_PADDING = 8.dp
private val MINI_SEGMENT_SAFETY_MARGIN = 6.dp
private val COMMENT_SORT_TABS = listOf("最热", "最新")
private val REPLY_SORT_TABS = listOf("最旧", "最热", "最新")
private val REPLY_SORT_ORDER = listOf(ReplySort.Old, ReplySort.Hot, ReplySort.New)
