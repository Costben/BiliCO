package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.ui.TitleRowHeight
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.DatePicker
import io.github.suqi8.coui.kmp.basic.DateValue
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 排序圆钮的起始留白，与胶囊行内容的内边距一致。 */
internal val SearchSortMenuMargin = 16.dp

private val DateLabelFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
    viewModel: SearchViewModel,
    onOpenCustomDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val dropdownPositionProvider = remember {
        ListPopupDefaults.dropdownPositionProvider(horizontalMargin = SearchSortMenuMargin)
    }
    val pubTimeSummary = remember(state.videoPubTime, state.customPubBegin, state.customPubEnd) {
        if (state.videoPubTime == VideoPubTimeOption.Custom && state.customPubBegin != null && state.customPubEnd != null) {
            "${state.customPubBegin.format(DateLabelFormatter)} ~ ${state.customPubEnd.format(DateLabelFormatter)}"
        } else {
            state.videoPubTime.label
        }
    }

    val scheme = COUITheme.colorScheme
    val menuEntries = remember(
        state.tab,
        state.tabCounts,
        state.videoOrder,
        state.videoPubTime,
        pubTimeSummary,
        state.videoDuration,
        state.videoZone,
        state.userOrder,
        state.userType,
        state.articleOrder,
        state.articleZone,
        scheme,
        haptic,
    ) {
        val categoryItems = SearchTab.entries.mapIndexed { index, tab ->
            val selected = state.tab == index
            val countText = state.tabCounts[tab]?.let(::formatSearchCount)
            val titleColor = if (selected) scheme.onTertiaryContainer else scheme.onSurfaceContainer
            val hintColor = if (selected) scheme.onTertiaryContainer else scheme.onSurfaceSecondary
            DropdownItem(
                text = "",
                selected = selected,
                icon = { mod ->
                    // Fixed-width icon+label block keeps DropdownImpl's SpaceBetween hint column aligned across 2/3-char labels.
                    Row(
                        modifier = mod.width(96.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = tab.iconVector(),
                            contentDescription = null,
                            modifier = Modifier.size(DropdownDefaults.IconMinSize),
                            tint = titleColor,
                        )
                        Spacer(Modifier.width(DropdownDefaults.IconEndPadding))
                        Text(
                            text = tab.label,
                            style = COUITheme.textStyles.body1,
                            fontSize = DropdownDefaults.PopupItemTitleFontSize,
                            fontWeight = FontWeight.Normal,
                            color = titleColor,
                            maxLines = 1,
                        )
                    }
                },
                hint = if (state.tabCounts.isNotEmpty()) {
                    {
                        Box(
                            modifier = Modifier.width(34.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            if (countText != null) {
                                Text(
                                    text = countText,
                                    fontSize = 13.sp,
                                    color = hintColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.setTab(index)
                },
            )
        }
        val filterItems = when (state.currentTab) {
            SearchTab.All, SearchTab.Video -> buildVideoFilterItems(
                state = state,
                pubTimeSummary = pubTimeSummary,
                onOrder = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.setVideoOrder(it)
                },
                onPubTime = { option ->
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (option == VideoPubTimeOption.Custom) {
                        onOpenCustomDate()
                    } else {
                        viewModel.setVideoPubTime(option)
                    }
                },
                onDuration = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.setVideoDuration(it)
                },
                onZone = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.setVideoZone(it)
                },
            )

            SearchTab.User -> listOf(
                DropdownItem(
                    text = "排序方式",
                    summary = state.userOrder.label,
                    children = UserOrderOption.entries.map { option ->
                        DropdownItem(
                            text = option.label,
                            selected = state.userOrder == option,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.setUserOrder(option)
                            },
                        )
                    },
                ),
                DropdownItem(
                    text = "用户分类",
                    summary = state.userType.label,
                    children = UserTypeOption.entries.map { option ->
                        DropdownItem(
                            text = option.label,
                            selected = state.userType == option,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.setUserType(option)
                            },
                        )
                    },
                ),
            )

            SearchTab.Article -> listOf(
                DropdownItem(
                    text = "排序方式",
                    summary = state.articleOrder.label,
                    children = ArticleOrderOption.entries.map { option ->
                        DropdownItem(
                            text = option.label,
                            selected = state.articleOrder == option,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.setArticleOrder(option)
                            },
                        )
                    },
                ),
                DropdownItem(
                    text = "专栏分区",
                    summary = state.articleZone.label,
                    children = ArticleZoneOption.entries.map { option ->
                        DropdownItem(
                            text = option.label,
                            selected = state.articleZone == option,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                viewModel.setArticleZone(option)
                            },
                        )
                    },
                ),
            )

            SearchTab.Bangumi, SearchTab.Movie, SearchTab.LiveRoom -> emptyList()
        }

        buildList {
            add(DropdownEntry(items = categoryItems))
            if (filterItems.isNotEmpty()) {
                add(DropdownEntry(items = filterItems))
            }
        }
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

private fun buildVideoFilterItems(
    state: SearchUiState,
    pubTimeSummary: String,
    onOrder: (VideoOrderOption) -> Unit,
    onPubTime: (VideoPubTimeOption) -> Unit,
    onDuration: (VideoDurationOption) -> Unit,
    onZone: (VideoZoneOption) -> Unit,
): List<DropdownItem> = listOf(
    DropdownItem(
        text = "排序方式",
        summary = state.videoOrder.label,
        children = VideoOrderOption.entries.map { option ->
            DropdownItem(
                text = option.label,
                selected = state.videoOrder == option,
                onClick = { onOrder(option) },
            )
        },
    ),
    DropdownItem(
        text = "发布时间",
        summary = pubTimeSummary,
        children = VideoPubTimeOption.entries.map { option ->
            DropdownItem(
                text = option.label,
                selected = state.videoPubTime == option,
                onClick = { onPubTime(option) },
            )
        },
    ),
    DropdownItem(
        text = "视频时长",
        summary = state.videoDuration.label,
        children = VideoDurationOption.entries.map { option ->
            DropdownItem(
                text = option.label,
                selected = state.videoDuration == option,
                onClick = { onDuration(option) },
            )
        },
    ),
    DropdownItem(
        text = "视频分区",
        summary = state.videoZone.label,
        children = VideoZoneOption.entries.map { option ->
            DropdownItem(
                text = option.label,
                selected = state.videoZone == option,
                onClick = { onZone(option) },
            )
        },
    ),
)

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
    var customDateDialogOpen by remember { mutableStateOf(false) }
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
            viewModel = viewModel,
            onOpenCustomDate = { customDateDialogOpen = true },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = SearchSortMenuMargin)
                .size(ChipTabDefaults.Height),
        )
    }

    if (customDateDialogOpen) {
        SearchCustomDateDialog(
            initialBegin = state.customPubBegin,
            initialEnd = state.customPubEnd,
            onConfirm = { begin, end ->
                viewModel.setVideoCustomPubRange(begin, end)
            },
            onDismiss = { customDateDialogOpen = false },
        )
    }
}

@Composable
private fun SearchCustomDateDialog(
    initialBegin: LocalDate?,
    initialEnd: LocalDate?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var show by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    var editingEndpoint by remember { mutableIntStateOf(0) }
    var beginDate by remember { mutableStateOf(initialBegin ?: today.withDayOfMonth(1)) }
    var endDate by remember { mutableStateOf(initialEnd ?: today) }

    LaunchedEffect(Unit) { show = true }

    val activeDate = if (editingEndpoint == 0) beginDate else endDate
    val scheme = COUITheme.colorScheme

    OverlayDialog(
        show = show,
        title = "自定义发布时间",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                if (confirmed) {
                    onConfirm(beginDate, endDate)
                }
                onDismiss()
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateEndpointChip(
                    label = "开始日期",
                    date = beginDate,
                    selected = editingEndpoint == 0,
                    onClick = { editingEndpoint = 0 },
                    modifier = Modifier.weight(1f),
                )
                DateEndpointChip(
                    label = "结束日期",
                    date = endDate,
                    selected = editingEndpoint == 1,
                    onClick = { editingEndpoint = 1 },
                    modifier = Modifier.weight(1f),
                )
            }
            DatePicker(
                value = DateValue(activeDate.year, activeDate.monthValue, activeDate.dayOfMonth),
                onValueChange = { picked ->
                    val candidate = runCatching {
                        LocalDate.of(picked.year, picked.month, picked.day)
                    }.getOrNull() ?: return@DatePicker
                    val clamped = if (candidate.isAfter(today)) today else candidate
                    if (editingEndpoint == 0) {
                        beginDate = clamped
                        if (beginDate.isAfter(endDate)) endDate = beginDate
                    } else {
                        endDate = clamped
                        if (endDate.isBefore(beginDate)) beginDate = endDate
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                yearRange = 2009..today.year,
                yearLabel = { "${it}年" },
                monthLabel = { "${it}月" },
                dayLabel = { "${it}日" },
            )
            Text(
                text = "已选范围：${beginDate.format(DateLabelFormatter)} 至 ${endDate.format(DateLabelFormatter)}",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = "确定",
                    onClick = {
                        confirmed = true
                        show = false
                    },
                ),
            )
        }
    }
}

@Composable
private fun DateEndpointChip(
    label: String,
    date: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val bg = if (selected) scheme.tertiaryContainer else scheme.surfaceContainerHigh
    val titleColor = if (selected) scheme.onTertiaryContainer else scheme.onSurfaceSecondary
    val valueColor = if (selected) scheme.onTertiaryContainer else scheme.onSurface
    Column(
        modifier = modifier
            .clip(SquircleShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, fontSize = 11.sp, color = titleColor)
        Text(
            text = date.format(DateLabelFormatter),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 1,
        )
    }
}
