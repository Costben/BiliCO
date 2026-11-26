package erl.bilico.feature.comment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.Comment
import io.github.suqi8.coui.kmp.basic.Card

internal class SortCrossfadeState<T>(
    val currentItems: List<T>,
    val exitingItems: List<T>?,
    val exitAlpha: Float,
    val exitScrollIndex: Int,
    val exitScrollOffset: Int,
)

/**
 * 切换评论/楼中楼排序时保留旧列表直到新排序数据返回；
 * 新列表就绪后在底层以不透明立即切入，顶层旧列表同步淡出（1f -> 0f），避免中途露出底色闪烁。
 */
@Composable
internal fun <T, S> rememberSortCrossfadeTransition(
    renderedSort: S,
    items: List<T>,
    resetKey: Any = Unit,
    scrollIndexProvider: () -> Int = { 0 },
    scrollOffsetProvider: () -> Int = { 0 },
): SortCrossfadeState<T> {
    var displayedSort by remember(resetKey) { mutableStateOf(renderedSort) }
    var displayedItems by remember(resetKey) { mutableStateOf(items) }
    var exitingItems by remember(resetKey) { mutableStateOf<List<T>?>(null) }
    var exitScrollIndex by remember(resetKey) { mutableIntStateOf(0) }
    var exitScrollOffset by remember(resetKey) { mutableIntStateOf(0) }
    val exitAlpha = remember(resetKey) { Animatable(0f) }
    val latestItems by rememberUpdatedState(items)

    LaunchedEffect(items, renderedSort, resetKey) {
        if (displayedSort == renderedSort) {
            displayedItems = items
        }
    }

    LaunchedEffect(renderedSort, resetKey) {
        if (displayedSort == renderedSort) return@LaunchedEffect
        val outgoing = displayedItems
        displayedSort = renderedSort
        displayedItems = latestItems
        if (outgoing.isNotEmpty()) {
            exitScrollIndex = scrollIndexProvider()
            exitScrollOffset = scrollOffsetProvider()
            exitingItems = outgoing
            exitAlpha.snapTo(1f)
            exitAlpha.animateTo(0f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
            exitingItems = null
        } else {
            exitingItems = null
            exitAlpha.snapTo(0f)
        }
    }

    return SortCrossfadeState(
        currentItems = displayedItems,
        exitingItems = exitingItems,
        exitAlpha = exitAlpha.value,
        exitScrollIndex = exitScrollIndex,
        exitScrollOffset = exitScrollOffset,
    )
}

/** 叠加在主 LazyColumn 之上的旧排序列表淡出层：保持头部高度对齐，触摸穿透不干扰底层列表。 */
@Composable
internal fun <T> SortCrossfadeListOverlay(
    exitingItems: List<T>?,
    exitAlpha: Float,
    exitScrollIndex: Int,
    exitScrollOffset: Int,
    contentPadding: PaddingValues,
    spacerHeightsPx: List<Int>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    if (exitingItems.isNullOrEmpty() || exitAlpha <= 0.001f) return
    val density = LocalDensity.current
    val exitListState = remember(exitingItems, exitScrollIndex, exitScrollOffset) {
        LazyListState(exitScrollIndex, exitScrollOffset)
    }
    LazyColumn(
        state = exitListState,
        userScrollEnabled = false,
        contentPadding = contentPadding,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exitAlpha },
    ) {
        items(spacerHeightsPx.size, key = { "exit-spacer-$it" }) { idx ->
            Spacer(Modifier.height(with(density) { spacerHeightsPx[idx].toDp() }))
        }
        items(exitingItems, key = key) { item ->
            itemContent(item)
        }
    }
}

/** 仅用于淡出过渡的静态评论/子回复卡片，避免与底层真实列表争抢点击与图片锚点。 */
@Composable
internal fun ExitCommentCard(
    comment: Comment,
    currentMid: Long,
    isSubReply: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BiliCommentItem(
            comment = comment,
            onLike = {},
            onDislike = {},
            onReply = {},
            onOpenReplies = if (isSubReply) null else ({}),
            onOpenUser = {},
            onOpenImage = { _, _ -> },
            isSubReply = isSubReply,
            expandable = !isSubReply,
            currentMid = currentMid,
        )
    }
}

/** 动态详情等非 LazyColumn 场景使用的旧排序评论淡出层。 */
@Composable
internal fun SortCrossfadeColumnOverlay(
    exitingComments: List<Comment>?,
    exitAlpha: Float,
    oid: Long,
    type: Int,
    currentMid: Long,
) {
    if (exitingComments.isNullOrEmpty() || exitAlpha <= 0.001f) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = exitAlpha },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CompositionLocalProvider(LocalCommentTarget provides (oid to type)) {
            exitingComments.forEach { comment ->
                BiliCommentItem(
                    comment = comment,
                    onLike = {},
                    onDislike = {},
                    onReply = {},
                    onOpenReplies = {},
                    onOpenUser = {},
                    onOpenImage = { _, _ -> },
                    currentMid = currentMid,
                )
            }
        }
    }
}
