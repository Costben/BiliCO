package erl.bilico.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.bilico.feature.video.speedLabel
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownArrowEndAction
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.basic.PopupListDivider
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.SearchTextMatcher
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.overlay.OverlayListPopup
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 设置页「长按倍速」项：沿用 COUI 下拉菜单的外观与手势锚点，
 * 并在展开菜单顶部附带互斥的「可调变速」与「下滑锁定」开关与分割线。
 */
@Composable
internal fun LongPressSpeedPreference(
    speedOptions: List<Int>,
    selectedPercent: Int,
    adjustableSpeed: Boolean,
    swipeDownLock: Boolean,
    onSelectPercent: (Int) -> Unit,
    onAdjustableChange: (Boolean) -> Unit,
    onSwipeDownLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cardListPosition: CardListPosition = CardListPosition.Middle,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var expanded by remember { mutableStateOf(false) }
    var holdDown by remember { mutableStateOf(false) }
    val preciseClickState = rememberPreciseClickState()
    val haptic = LocalHapticFeedback.current
    val selectedIndex = speedOptions.indexOf(selectedPercent).coerceAtLeast(0)
    val valueText = speedLabel(speedOptions[selectedIndex] / 100f)
    val titleText = "长按倍速"
    val baseSummary = when {
        adjustableSpeed -> "可调变速：上下滑动调节，步进 0.25x"
        swipeDownLock -> "下滑锁定：长按倍速时下滑锁定当前倍速"
        else -> null
    }
    val searchCtx = LocalSettingsSearchRenderContext.current
    val primaryColor = COUITheme.colorScheme.primary
    val resolvedSummary = if (searchCtx.isSearchResult) {
        baseSummary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    } else {
        baseSummary
    }
    val highlightedTitle = remember(titleText, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(titleText, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    val endActionsContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Text(
            text = valueText,
            modifier = Modifier
                .widthIn(max = 162.dp)
                .padding(start = 8.dp, end = 4.dp)
                .align(Alignment.CenterVertically)
                .weight(1f, fill = false),
            fontSize = COUITheme.textStyles.body2.fontSize,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
        )
        DropdownArrowEndAction(actionColor = COUITheme.colorScheme.onSurfaceVariantActions)
        OverlayListPopup(
            show = expanded,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { expanded = false },
            onDismissFinished = { holdDown = false },
            preciseClickState = preciseClickState,
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                LongPressModeToggleRow(
                    title = "可调变速",
                    summary = "上下滑动调节变速",
                    checked = adjustableSpeed,
                    topPadding = 16.dp,
                    bottomPadding = 10.dp,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onAdjustableChange(!adjustableSpeed)
                    },
                    onCheckedChange = onAdjustableChange,
                )
                LongPressModeToggleRow(
                    title = "下滑锁定",
                    summary = "向下滑动锁定倍速",
                    checked = swipeDownLock,
                    topPadding = 10.dp,
                    bottomPadding = DropdownDefaults.MiddleVerticalPadding,
                    onToggle = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSwipeDownLockChange(!swipeDownLock)
                    },
                    onCheckedChange = onSwipeDownLockChange,
                )
                PopupListDivider()
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    propagateMinConstraints = true,
                ) {
                    ListPopupColumn {
                        speedOptions.forEachIndexed { index, percent ->
                            DropdownImpl(
                                item = DropdownItem(text = speedLabel(percent / 100f)),
                                optionSize = speedOptions.size,
                                isSelected = index == selectedIndex,
                                index = index,
                                isFirst = false,
                                isLast = index == speedOptions.lastIndex,
                                groupKey = "long_press_speed",
                                onSelectedIndexChange = { picked ->
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onSelectPercent(speedOptions[picked])
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    val onRowClick: () -> Unit = {
        expanded = !expanded
        if (expanded) {
            holdDown = true
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        }
    }

    if (searchCtx.isSearchResult) {
        BasicComponent(
            title = highlightedTitle,
            summary = highlightedSummary,
            modifier = modifier.preciseClickAnchor(preciseClickState),
            interactionSource = interactionSource,
            cardListPosition = cardListPosition,
            endActions = endActionsContent,
            onClick = onRowClick,
            role = Role.DropdownList,
            holdDownState = holdDown,
        )
    } else {
        BasicComponent(
            modifier = modifier.preciseClickAnchor(preciseClickState),
            interactionSource = interactionSource,
            cardListPosition = cardListPosition,
            title = titleText,
            summary = baseSummary,
            endActions = endActionsContent,
            onClick = onRowClick,
            role = Role.DropdownList,
            holdDownState = holdDown,
        )
    }
}

@Composable
private fun LongPressModeToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .heightIn(min = DropdownDefaults.PopupItemMinHeight)
            .padding(
                start = DropdownDefaults.InsideHorizontalPadding,
                end = DropdownDefaults.InsideHorizontalPadding,
                top = topPadding,
                bottom = bottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.widthIn(max = DropdownDefaults.MaxItemTextWidth)) {
            Text(
                text = title,
                style = COUITheme.textStyles.body1,
                fontSize = DropdownDefaults.PopupItemTitleFontSize,
                fontWeight = FontWeight.Normal,
                color = COUITheme.colorScheme.onSurfaceContainer,
            )
            Text(
                text = summary,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
