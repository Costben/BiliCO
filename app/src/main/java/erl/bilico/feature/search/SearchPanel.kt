@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.ui.AppBarSurface
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.glassBarContentTop
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.reportTopBarBottom
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Album
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Sort
import io.github.suqi8.coui.kmp.overlay.OverlayCascadingListPopup
import kotlinx.coroutines.flow.distinctUntilChanged

/** 搜索结果的三个分类。 */
internal val SearchResultTabs = listOf(
    ChipTabData(label = "视频", icon = COUIIcons.Play),
    ChipTabData(label = "用户", icon = COUIIcons.Contacts),
    ChipTabData(label = "专栏", icon = COUIIcons.Album),
)

private val SearchOrders = listOf(
    "综合排序" to "totalrank",
    "最新发布" to "pubdate",
    "最多播放" to "click",
    "最多弹幕" to "dm",
    "最多收藏" to "stow",
)

private val SearchVideoTids = listOf(
    "全部分区" to 0,
    "动画" to 1,
    "游戏" to 4,
    "知识" to 36,
    "科技" to 188,
    "生活" to 160,
)

/** 上下扩展时的裁切形状：从搜索栏下沿往下放，裁掉还没展开的部分。 */
private class SearchPanelClipShape(
    private val progress: Float,
    private val topBarHeightPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = progress.coerceIn(0f, 1f)
        val bottom = (topBarHeightPx + (size.height - topBarHeightPx) * p)
            .coerceIn(0f, size.height)
        return Outline.Rectangle(Rect(0f, 0f, size.width, bottom))
    }

    override fun equals(other: Any?): Boolean =
        other is SearchPanelClipShape &&
            other.progress == progress &&
            other.topBarHeightPx == topBarHeightPx

    override fun hashCode(): Int = 31 * progress.hashCode() + topBarHeightPx.hashCode()
}

/**
 * 1:1 复刻 Cobo COUI 的首页搜索整屏面板（Layer 4）：
 * - 从屏幕顶 0 铺满到屏幕底部，位于常态顶栏/底栏之上、搜索胶囊之下；
 * - 搜索发现、搜索建议、搜索结果共用同一个 [LazyColumn]；
 * - 分类胶囊 [SearchHeaderRow] 作为结果列表首项绑定在正文中，随列表一起上下滑走；
 * - 面板自身在顶部覆盖单行高度的 [AppBarSurface] 玻璃材质。
 */
@Composable
internal fun HomeSearchOverlay(
    topBarHeight: Dp,
    searchBackdrop: LayerBackdrop,
    searchExpanded: Boolean,
    panelProgress: () -> Float,
    panelAlpha: () -> Float,
    state: SearchUiState,
    history: List<String>,
    viewModel: SearchViewModel,
    container: AppContainer,
    nav: NavState,
    barBounds: TopBarBounds,
    onSelectKeyword: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = pageContainerColor()
    val listState = rememberLazyListState()
    val rowScrollState = remember(listState) { listState.asRowScrollState() }
    val density = LocalDensity.current
    val topBarHeightPx = with(density) { topBarHeight.toPx() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val trimmedQuery = state.query.trim()
    val suggesting = trimmedQuery.isNotEmpty() && trimmedQuery != state.submitted
    val showingResults = state.submitted.isNotBlank() && !suggesting && trimmedQuery.isNotEmpty()

    LaunchedEffect(showingResults) {
        if (showingResults) {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(showingResults, state.submitted, suggesting, state.tab) {
        listState.scrollToItem(0)
    }

    val currentCount = when (state.tab) {
        0 -> state.videos.size
        1 -> state.users.size
        else -> state.articles.size
    }
    LaunchedEffect(listState, state.submitted, state.tab, currentCount) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (state.submitted.isBlank() || currentCount == 0) return@collect
                if (last >= currentCount - 1) viewModel.loadMore()
            }
    }

    val activeSelectKeyword: (String) -> Unit = { keyword ->
        if (searchExpanded) onSelectKeyword(keyword)
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = panelProgress()
                    alpha = panelAlpha().coerceIn(0f, 1f)
                    if (p < 0.999f) {
                        clip = true
                        shape = SearchPanelClipShape(p, topBarHeightPx)
                    } else {
                        clip = false
                    }
                }
                .layerBackdropIf(BlurEffectsEnabled, searchBackdrop)
                .background(surfaceColor)
                .pointerInput(Unit) {}
                .then(if (searchExpanded) Modifier.testTag("home_search_panel") else Modifier),
        ) {
            CompositionLocalProvider(LocalBackdrop provides if (BlurEffectsEnabled) searchBackdrop else null) {
                val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val bottomPadding = maxOf(16.dp + navBottomPadding(), imeBottom + 16.dp)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        top = glassBarContentTop(topBarHeight, 8.dp),
                        end = 0.dp,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!showingResults) {
                        if (suggesting) {
                            searchSuggestSection(
                                query = state.query,
                                suggestions = state.suggestions,
                                suggestionsLoading = state.suggestionsLoading,
                                onPick = activeSelectKeyword,
                            )
                        } else {
                            searchHomeSection(
                                hot = state.hot,
                                hotLoading = state.hotLoading,
                                history = history,
                                onPick = activeSelectKeyword,
                                onDeleteHistory = { container.settings.removeSearchHistory(it) },
                                onClearHistory = { container.settings.clearSearchHistory() },
                            )
                        }
                    } else {
                        searchResultItems(
                            state = state,
                            nav = nav,
                            viewModel = viewModel,
                            rowScrollState = rowScrollState,
                            topBarBounds = barBounds,
                            onRetry = viewModel::retry,
                        )
                    }
                }
            }
        }

        // 搜索面板自己的单行顶栏渐变模糊材质（固定单行高度，避免尺寸变化引起平台材质黑条）。
        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) searchBackdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                val barHeight = statusBarTop + TitleRowHeight
                AppBarSurface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .reportTopBarBottom(barBounds),
                    containerColor = surfaceColor,
                    scrollFraction = panelAlpha,
                ) {
                    Box(Modifier.fillMaxWidth().height(barHeight))
                }
            }
        }
    }
}

/**
 * 搜索结果头部控制栏：作为结果列表首项随正文一起上下滚动。
 * 位于 `searchBackdrop` 录制容器内部，胶囊不传 backdrop（走平涂样式）以免自采样。
 */
@Composable
internal fun SearchHeaderRow(
    state: SearchUiState,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val horizontalPadding = 16.dp
    val dropdownPositionProvider = remember(horizontalPadding) {
        ListPopupDefaults.dropdownPositionProvider(horizontalMargin = horizontalPadding)
    }
    val menuEntries = remember(state.tab, state.order, state.tid, haptic) {
        val categoryItems = SearchResultTabs.mapIndexed { index, tab ->
            DropdownItem(
                text = tab.label,
                selected = state.tab == index,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.setTab(index)
                },
            )
        }
        val orderChildren = SearchOrders.map { (label, value) ->
            DropdownItem(
                text = label,
                selected = state.order == value,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.setOrder(value)
                },
            )
        }
        val tidChildren = SearchVideoTids.map { (label, value) ->
            DropdownItem(
                text = label,
                selected = state.tid == value,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.setTid(value)
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

    Box(modifier = modifier.fillMaxWidth()) {
        ChipTabGroup(
            tabs = SearchResultTabs,
            selectedIndex = state.tab,
            onSelectedChange = viewModel::setTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("search_tabs"),
            leadingIcon = COUIIcons.Sort,
            onLeadingIconClick = { menuExpanded = true },
            contentPadding = PaddingValues(horizontal = horizontalPadding),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = horizontalPadding)
                .size(ChipTabDefaults.Height),
        ) {
            OverlayCascadingListPopup(
                show = menuExpanded,
                entries = menuEntries,
                onDismissRequest = { menuExpanded = false },
                popupPositionProvider = dropdownPositionProvider,
                alignment = PopupPositionProvider.Align.Start,
                collapseOnSelection = true,
            )
        }
    }
}
