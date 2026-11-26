package erl.bilico.feature.video

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import erl.bilico.data.settings.PlayerTopBarItem
import erl.bilico.data.settings.PlayerTopBarLayout
import erl.bilico.data.settings.PlayerTopBarMode
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.SheetCloseCapsule
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/**
 * 播放器顶栏设置：全屏播放与竖屏播放各一组，逐项开关，按住行可上下拖动排序。
 *
 * 竖屏走 COUI 底部面板；全屏横屏时面板要跟着播放器一起转，走 [PlayerModalHost] 贴在舞台里。
 */
@Composable
internal fun PlayerTopBarSettingsDialog(
    fullscreen: PlayerTopBarLayout,
    portrait: PlayerTopBarLayout,
    onToggle: (PlayerTopBarMode, String, Boolean) -> Unit,
    onMove: (PlayerTopBarMode, Int, Int) -> Unit,
    onDismiss: () -> Unit,
    inlineInStage: Boolean = false,
) {
    if (!inlineInStage) {
        PlayerTopBarSettingsSheet(fullscreen, portrait, onToggle, onMove, onDismiss)
        return
    }
    PlayerModalHost(
        inlineInStage = true,
        onDismiss = onDismiss,
        horizontalPadding = 12.dp,
        cornerRadius = 24.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("player_top_bar_settings_dialog"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "顶栏设置",
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = COUITheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                DragHint(topPadding = 6.dp)
                PlayerTopBarModeHeader(PlayerTopBarMode.FULLSCREEN, topPadding = 8.dp)
                PlayerTopBarItemCard(PlayerTopBarMode.FULLSCREEN, fullscreen, onToggle, onMove)
                PlayerTopBarModeHeader(PlayerTopBarMode.PORTRAIT, topPadding = 14.dp)
                PlayerTopBarItemCard(PlayerTopBarMode.PORTRAIT, portrait, onToggle, onMove)
            }
        }
    }
}

@Composable
private fun PlayerTopBarSettingsSheet(
    fullscreen: PlayerTopBarLayout,
    portrait: PlayerTopBarLayout,
    onToggle: (PlayerTopBarMode, String, Boolean) -> Unit,
    onMove: (PlayerTopBarMode, Int, Int) -> Unit,
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
                    text = "顶栏设置",
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = COUITheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        },
        onDismissRequest = { show = false },
        onDismissFinished = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .testTag("player_top_bar_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hint") { DragHint(topPadding = 2.dp) }
            item(key = "fullscreen-title") {
                PlayerTopBarModeHeader(PlayerTopBarMode.FULLSCREEN, topPadding = 4.dp)
            }
            item(key = "fullscreen") {
                PlayerTopBarItemCard(PlayerTopBarMode.FULLSCREEN, fullscreen, onToggle, onMove)
            }
            item(key = "portrait-title") {
                PlayerTopBarModeHeader(PlayerTopBarMode.PORTRAIT, topPadding = 12.dp)
            }
            item(key = "portrait") {
                PlayerTopBarItemCard(PlayerTopBarMode.PORTRAIT, portrait, onToggle, onMove)
            }
            item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DragHint(topPadding: Dp) {
    Text(
        text = "按住任意一行上下拖动，即可调整顶栏上的排列顺序",
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = topPadding),
    )
}

@Composable
private fun PlayerTopBarModeHeader(mode: PlayerTopBarMode, topPadding: Dp) {
    val text = when (mode) {
        PlayerTopBarMode.FULLSCREEN -> "全屏播放（含竖屏视频全屏）"
        PlayerTopBarMode.PORTRAIT -> "竖屏播放"
    }
    SectionTitle(text = text, topPadding = topPadding)
}

@Composable
private fun PlayerTopBarItemCard(
    mode: PlayerTopBarMode,
    layout: PlayerTopBarLayout,
    onToggle: (PlayerTopBarMode, String, Boolean) -> Unit,
    onMove: (PlayerTopBarMode, Int, Int) -> Unit,
) {
    val items = layout.orderedItems(mode)
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
        items.forEachIndexed { index, item ->
            val dragging = draggingId == item.id
            key(item.id) {
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
                        .onGloballyPositioned { rowHeights[item.id] = it.size.height }
                        .pointerInput(item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = item.id
                                    dragOffset = 0f
                                    workingOrder = latestItems.value
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    var index = workingOrder.indexOfFirst { it.id == item.id }
                                    while (index >= 0 && dragOffset > 0f) {
                                        val next = workingOrder.getOrNull(index + 1) ?: break
                                        val height = rowHeights[next.id]?.toFloat() ?: break
                                        if (height <= 0f || dragOffset < height / 2f) break
                                        workingOrder = workingOrder.toMutableList().apply {
                                            add(index + 1, removeAt(index))
                                        }
                                        onMove(mode, index, index + 1)
                                        dragOffset -= height
                                        index++
                                    }
                                    while (index > 0 && dragOffset < 0f) {
                                        val prev = workingOrder.getOrNull(index - 1) ?: break
                                        val height = rowHeights[prev.id]?.toFloat() ?: break
                                        if (height <= 0f || -dragOffset < height / 2f) break
                                        workingOrder = workingOrder.toMutableList().apply {
                                            add(index - 1, removeAt(index))
                                        }
                                        onMove(mode, index, index - 1)
                                        dragOffset += height
                                        index--
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
                    PlayerTopBarRow(
                        mode = mode,
                        item = item,
                        checked = item.id !in layout.hidden,
                        onToggle = onToggle,
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

@Composable
private fun PlayerTopBarRow(
    mode: PlayerTopBarMode,
    item: PlayerTopBarItem,
    checked: Boolean,
    onToggle: (PlayerTopBarMode, String, Boolean) -> Unit,
    cardListPosition: CardListPosition,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = { onToggle(mode, item.id, it) },
        title = item.label,
        summary = item.summary,
        startAction = {
            PlayerTopBarItemIcon(
                item = item,
                size = 22.dp,
                tint = COUITheme.colorScheme.onSurface,
            )
        },
        modifier = Modifier.testTag("top_bar_${mode.name}_${item.id}"),
        cardListPosition = cardListPosition,
    )
}
