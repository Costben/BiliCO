package erl.bilico.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.SearchTextMatcher
import io.github.suqi8.coui.kmp.basic.Slider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 支持搜索高亮与路径副标题的箭头设置项，直接复用 COUI [ArrowPreference] 的 String / AnnotatedString 重载。 */
@Composable
internal fun SettingsArrowRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    cardListPosition: CardListPosition = CardListPosition.None,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val searchCtx = LocalSettingsSearchRenderContext.current
    if (!searchCtx.isSearchResult) {
        ArrowPreference(
            title = title,
            modifier = modifier,
            summary = summary,
            endActions = endActions,
            cardListPosition = cardListPosition,
            onClick = onClick,
            enabled = enabled,
        )
        return
    }
    val primaryColor = COUITheme.colorScheme.primary
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    ArrowPreference(
        title = highlightedTitle,
        modifier = modifier,
        summary = highlightedSummary,
        endActions = endActions,
        cardListPosition = cardListPosition,
        onClick = onClick,
        enabled = enabled,
    )
}

/** 支持搜索高亮与路径副标题的开关设置项，直接复用 COUI [SwitchPreference] 的 String / AnnotatedString 重载。 */
@Composable
internal fun SettingsSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    cardListPosition: CardListPosition = CardListPosition.None,
    enabled: Boolean = true,
) {
    val searchCtx = LocalSettingsSearchRenderContext.current
    if (!searchCtx.isSearchResult) {
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            summary = summary,
            modifier = modifier,
            cardListPosition = cardListPosition,
            enabled = enabled,
        )
        return
    }
    val primaryColor = COUITheme.colorScheme.primary
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = highlightedTitle,
        summary = highlightedSummary,
        modifier = modifier,
        cardListPosition = cardListPosition,
        enabled = enabled,
    )
}

/** 支持搜索高亮与路径副标题的下拉设置项（字符串列表版本）。 */
@Composable
internal fun SettingsDropdownRow(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    cardListPosition: CardListPosition = CardListPosition.None,
    enabled: Boolean = true,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val searchCtx = LocalSettingsSearchRenderContext.current
    if (!searchCtx.isSearchResult) {
        OverlayDropdownPreference(
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            summary = summary,
            modifier = modifier,
            cardListPosition = cardListPosition,
            enabled = enabled,
            onSelectedIndexChange = onSelectedIndexChange,
        )
        return
    }
    val primaryColor = COUITheme.colorScheme.primary
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    OverlayDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = highlightedTitle,
        summary = highlightedSummary,
        modifier = modifier,
        cardListPosition = cardListPosition,
        enabled = enabled,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

/** 支持搜索高亮与路径副标题的下拉设置项（[DropdownEntry] 版本）。 */
@Composable
internal fun SettingsDropdownRow(
    entry: DropdownEntry,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    cardListPosition: CardListPosition = CardListPosition.None,
    enabled: Boolean = true,
) {
    val searchCtx = LocalSettingsSearchRenderContext.current
    if (!searchCtx.isSearchResult) {
        OverlayDropdownPreference(
            entry = entry,
            title = title,
            summary = summary,
            modifier = modifier,
            cardListPosition = cardListPosition,
            enabled = enabled,
        )
        return
    }
    val primaryColor = COUITheme.colorScheme.primary
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    OverlayDropdownPreference(
        entry = entry,
        title = highlightedTitle,
        summary = highlightedSummary,
        modifier = modifier,
        cardListPosition = cardListPosition,
        enabled = enabled,
    )
}

/** 支持搜索高亮与路径副标题的滑块设置项。 */
@Composable
internal fun SettingsSliderRow(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    valueText: String? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    cardListPosition: CardListPosition = CardListPosition.None,
) {
    val searchCtx = LocalSettingsSearchRenderContext.current
    if (!searchCtx.isSearchResult) {
        SliderPreference(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            title = title,
            summary = summary,
            valueText = valueText,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            modifier = modifier,
            cardListPosition = cardListPosition,
        )
        return
    }
    val primaryColor = COUITheme.colorScheme.primary
    val resolvedSummary = summary?.takeIf { it.isNotBlank() } ?: searchCtx.breadcrumb.takeIf { it.isNotBlank() }
    val highlightedTitle = remember(title, searchCtx.terms, primaryColor) {
        SearchTextMatcher.highlightText(title, searchCtx.terms, primaryColor)
    }
    val highlightedSummary = remember(resolvedSummary, searchCtx.terms, primaryColor) {
        resolvedSummary?.let { SearchTextMatcher.highlightText(it, searchCtx.terms, primaryColor) }
    }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    BasicComponent(
        title = highlightedTitle,
        summary = highlightedSummary,
        modifier = modifier,
        cardListPosition = cardListPosition,
        enabled = enabled,
        endActions = if (valueText != null) {
            {
                Row(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = valueText,
                        fontSize = COUITheme.textStyles.body2.fontSize,
                        color = if (enabled) {
                            COUITheme.colorScheme.onSurfaceSecondary
                        } else {
                            COUITheme.colorScheme.disabledOnSecondaryVariant
                        },
                    )
                }
            }
        } else {
            null
        },
        bottomAction = {
            Column {
                Slider(
                    value = value,
                    onValueChange = { currentOnValueChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    valueRange = valueRange,
                    steps = steps,
                    onValueChangeFinished = currentOnValueChangeFinished,
                )
            }
        },
    )
}
