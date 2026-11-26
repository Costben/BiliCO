package erl.bilico.feature.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import erl.bilico.data.settings.HomeTabType
import erl.bilico.data.settings.HomeTabsLayout
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.layout.SheetActionButton
import io.github.suqi8.coui.kmp.layout.SheetCloseCapsule
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/**
 * 首页分类设置：开关每个顶部分类，按住任意一行上下拖动即可调整展示顺序。
 */
@Composable
internal fun HomeTabsSettingsDialog(
    layout: HomeTabsLayout,
    onToggle: (String, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayBottomSheet(
        show = show,
        title = null,
        insideMargin = DpSize(12.dp, 0.dp),
        startAction = {
            Box(Modifier.fillMaxWidth()) {
                SheetCloseCapsule(
                    onClick = { show = false },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    text = "首页分类",
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = COUITheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        },
        endAction = {
            SheetActionButton(
                onClick = onReset,
                modifier = Modifier.testTag("home_tabs_reset"),
            ) {
                Icon(COUIIcons.Refresh, "恢复默认", Modifier.size(24.dp))
            }
        },
        onDismissRequest = { show = false },
        onDismissFinished = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .testTag("home_tabs_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hint") {
                Text(
                    text = "开关要显示的首页分类，按住任意一行上下拖动即可调整顺序",
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            item(key = "list") {
                HomeTabsCard(layout = layout, onToggle = onToggle, onMove = onMove)
            }
            item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HomeTabsCard(
    layout: HomeTabsLayout,
    onToggle: (String, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val items = layout.orderedTabs()
    val visibleCount = layout.visibleTabs().size
    val latestItems = rememberUpdatedState(items)
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    val haptics = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var workingOrder by remember { mutableStateOf(items) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        insideMargin = PaddingValues(0.dp),
        weight = CardDefaults.GroupWeight,
    ) {
        items.forEachIndexed { index, tab ->
            val dragging = draggingId == tab.id
            val checked = tab.id !in layout.hidden
            val isLastVisible = checked && visibleCount <= 1
            key(tab.id) {
                Box(
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            if (dragging) {
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        }
                        .onGloballyPositioned { rowHeights[tab.id] = it.size.height }
                        .pointerInput(tab.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = tab.id
                                    dragOffset = 0f
                                    workingOrder = latestItems.value
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    var currentIndex = workingOrder.indexOfFirst { it.id == tab.id }
                                    while (currentIndex >= 0 && dragOffset > 0f) {
                                        val next = workingOrder.getOrNull(currentIndex + 1) ?: break
                                        val height = rowHeights[next.id]?.toFloat() ?: break
                                        if (height <= 0f || dragOffset < height / 2f) break
                                        workingOrder = workingOrder.toMutableList().apply {
                                            add(currentIndex + 1, removeAt(currentIndex))
                                        }
                                        onMove(currentIndex, currentIndex + 1)
                                        dragOffset -= height
                                        currentIndex++
                                    }
                                    while (currentIndex > 0 && dragOffset < 0f) {
                                        val prev = workingOrder.getOrNull(currentIndex - 1) ?: break
                                        val height = rowHeights[prev.id]?.toFloat() ?: break
                                        if (height <= 0f || -dragOffset < height / 2f) break
                                        workingOrder = workingOrder.toMutableList().apply {
                                            add(currentIndex - 1, removeAt(currentIndex))
                                        }
                                        onMove(currentIndex, currentIndex - 1)
                                        dragOffset += height
                                        currentIndex--
                                    }
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                    workingOrder = latestItems.value
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                    workingOrder = latestItems.value
                                },
                            )
                        },
                ) {
                    SwitchPreference(
                        checked = checked,
                        onCheckedChange = { wantVisible ->
                            if (wantVisible || visibleCount > 1) {
                                onToggle(tab.id, wantVisible)
                            }
                        },
                        title = tab.label,
                        summary = if (isLastVisible) "${tab.description()}（至少保留一个分类）" else tab.description(),
                        modifier = Modifier.testTag("home_tab_${tab.id}"),
                        cardListPosition = when (index) {
                            0 -> CardListPosition.Head
                            items.lastIndex -> CardListPosition.Tail
                            else -> CardListPosition.Middle
                        },
                    )
                }
            }
        }
    }
}

private fun HomeTabType.description(): String = when (this) {
    HomeTabType.RECOMMEND -> "个性化视频推荐"
    HomeTabType.HOT -> "全站综合热门视频"
    HomeTabType.RANKING -> "全站及各分区排行榜"
    HomeTabType.BANGUMI -> "番剧区热门排行"
    HomeTabType.CINEMA -> "电影、电视剧、纪录片与综艺排行"
    HomeTabType.LIVE -> "关注主播与热门直播"
}
