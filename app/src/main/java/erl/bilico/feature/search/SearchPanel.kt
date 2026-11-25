@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.glassBarContentTop
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.SearchResultContainer
import io.github.suqi8.coui.kmp.basic.SearchResultsTransitionState
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.flow.distinctUntilChanged

/** 按当前各分类结果数量构造胶囊标签；超过 99 显示 99+。 */
internal fun buildSearchResultTabs(tabCounts: Map<SearchTab, Int>): List<ChipTabData> =
    SearchTab.entries.map { tab ->
        val countText = tabCounts[tab]?.let(::formatSearchCount)
        ChipTabData(
            label = if (countText != null) "${tab.label} $countText" else tab.label,
            icon = tab.iconVector(),
        )
    }

/**
 * 结果态：已提交过关键词，且输入框里仍是那个词。输入一变就退回建议态。
 *
 * 面板的列表内容与顶栏的分类行都读这一个判定，避免两处各写一份后漂移。
 */
internal fun SearchUiState.showingResults(): Boolean =
    submitted.isNotBlank() && query.trim() == submitted

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
 * 首页搜索整屏面板（Layer 4）：
 * - 从屏幕顶 0 铺满到屏幕底部，位于常态顶栏/底栏之上、搜索胶囊（Layer 5）之下；
 * - 搜索发现、搜索建议、搜索结果共用底层同一份 [LazyColumn]（录进 [searchBackdrop]），
 *   加载球与结果上浮/淡入淡出过渡统一交由 coui 的 [SearchResultContainer] 编排；
 * - 顶栏（搜索行 + 结果态的分类胶囊）不在这一层绘制，由 [FeedTitleRow] 走 coui 的
 *   `SearchFilterTopAppBar`，材质、两行布局与胶囊采样都在那里统一处理。
 */
@Composable
internal fun HomeSearchOverlay(
    topBarHeight: Dp,
    searchBackdrop: LayerBackdrop,
    searchExpanded: Boolean,
    panelProgress: () -> Float,
    panelAlpha: () -> Float,
    state: SearchUiState,
    transitionState: SearchResultsTransitionState,
    history: List<String>,
    viewModel: SearchViewModel,
    container: AppContainer,
    nav: NavState,
    barBounds: TopBarBounds,
    onSelectKeyword: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val density = LocalDensity.current
    val topBarHeightPx = with(density) { topBarHeight.toPx() }
    Box(modifier) {
        SearchPanelBody(
            topBarHeight = topBarHeight,
            searchBackdrop = searchBackdrop,
            searchExpanded = searchExpanded,
            state = state,
            transitionState = transitionState,
            history = history,
            viewModel = viewModel,
            container = container,
            nav = nav,
            barBounds = barBounds,
            onSelectKeyword = onSelectKeyword,
            listState = listState,
            modifier = Modifier
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
                .then(if (searchExpanded) Modifier.testTag("home_search_panel") else Modifier),
        )
    }
}

/** 搜索面板主体内容（发现/建议/结果），供首页搜索浮层与独立搜索页复用。 */
@Composable
internal fun SearchPanelBody(
    topBarHeight: Dp,
    searchBackdrop: LayerBackdrop,
    searchExpanded: Boolean,
    state: SearchUiState,
    transitionState: SearchResultsTransitionState,
    history: List<String>,
    viewModel: SearchViewModel,
    container: AppContainer,
    nav: NavState,
    barBounds: TopBarBounds,
    onSelectKeyword: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    val surfaceColor = pageContainerColor()
    BindScrollToTop(listState)
    val rowScrollState = remember(listState) { listState.asRowScrollState() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val trimmedQuery = state.query.trim()
    val suggesting = trimmedQuery.isNotEmpty() && trimmedQuery != state.submitted
    val showingResults = state.showingResults()

    val secondRowTotalHeight = SearchChipRowGap + TwoRowTopAppBarDefaults.SecondRowHeight + TwoRowTopAppBarDefaults.SecondRowBottom
    val resultsBarHeight = pinnedHeight(TitleRowHeight, secondRowTotalHeight)
    val contentTop = if (transitionState.shownResults) {
        resultsBarHeight + 6.dp
    } else {
        glassBarContentTop(topBarHeight, 8.dp)
    }

    LaunchedEffect(showingResults) {
        if (showingResults) {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(transitionState.shownResults, state.submitted, suggesting, state.tab) {
        // 淡出过程中旧内容还在画，先把滚动位置钉住；换完内容再回到顶部。
        if (transitionState.shownResults != showingResults) return@LaunchedEffect
        listState.scrollToItem(0)
    }

    val currentCount = state.currentTabItemCount()
    LaunchedEffect(listState, state.submitted, state.tab, currentCount) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) ->
                if (state.submitted.isBlank() || currentCount == 0 || total == 0) return@collect
                if (last >= total - 2) viewModel.loadMore()
            }
    }

    val activeSelectKeyword: (String) -> Unit = { keyword ->
        if (searchExpanded) onSelectKeyword(keyword)
    }

    Box(
        modifier
            .fillMaxSize()
            .layerBackdropIf(BlurEffectsEnabled, searchBackdrop)
            .background(surfaceColor)
            .pointerInput(Unit) {},
    ) {
        CompositionLocalProvider(LocalBackdrop provides if (BlurEffectsEnabled) searchBackdrop else null) {
            val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val bottomPadding = maxOf(16.dp + navBottomPadding(), imeBottom + 16.dp)
            val blocks by container.blocks.snapshot.collectAsStateWithLifecycle()
            val coverAspect by container.settings.feed.coverAspect.collectAsStateWithLifecycle()
            val searchHotGrid by container.settings.searchHotGrid.collectAsStateWithLifecycle()
            val searchResultGrid by container.settings.searchResultGrid.collectAsStateWithLifecycle()
            val visibleState = remember(state, blocks) {
                state.copy(
                    allVideos = state.allVideos.filter { blocks.shouldShowSearchVideo(it) },
                    videos = state.videos.filter { blocks.shouldShowSearchVideo(it) },
                    allUsers = state.allUsers.filterNot {
                        blocks.isMidBlocked(it.mid) || blocks.matchesUpName(it.name)
                    },
                    users = state.users.filterNot {
                        blocks.isMidBlocked(it.mid) || blocks.matchesUpName(it.name)
                    },
                )
            }
            SearchResultContainer(
                state = transitionState,
                modifier = Modifier.fillMaxSize(),
                loadingTopPadding = resultsBarHeight + 6.dp,
                loadingContent = {
                    Box(Modifier.padding(horizontal = 16.dp)) { LoadingState() }
                },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        start = 0.dp,
                        top = contentTop,
                        end = 0.dp,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!shownResults) {
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
                                hotGrid = searchHotGrid,
                                history = history,
                                onPick = activeSelectKeyword,
                                onDeleteHistory = { container.settings.removeSearchHistory(it) },
                                onClearHistory = { container.settings.clearSearchHistory() },
                            )
                        }
                    } else if (resultsVisible) {
                        searchResultItems(
                            state = visibleState,
                            nav = nav,
                            rowScrollState = rowScrollState,
                            topBarBounds = barBounds,
                            gridMode = searchResultGrid,
                            coverAspect = coverAspect,
                            onToggleGrid = { container.settings.setSearchResultGrid(!searchResultGrid) },
                            onRetry = viewModel::retry,
                        )
                    }
                }
            }
        }
    }
}
