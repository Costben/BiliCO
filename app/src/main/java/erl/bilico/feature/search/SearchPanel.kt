@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.glassBarContentTop
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.TwoRowTopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Album
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Play
import kotlinx.coroutines.flow.distinctUntilChanged

/** 搜索结果的三个分类。 */
internal val SearchResultTabs = listOf(
    ChipTabData(label = "视频", icon = COUIIcons.Play),
    ChipTabData(label = "用户", icon = COUIIcons.Contacts),
    ChipTabData(label = "专栏", icon = COUIIcons.Album),
)

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
 * - 搜索发现、搜索建议、搜索结果共用底层同一份 [LazyColumn]（录进 [searchBackdrop]）；
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
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val trimmedQuery = state.query.trim()
    val suggesting = trimmedQuery.isNotEmpty() && trimmedQuery != state.submitted
    val showingResults = state.showingResults()

    val secondRowTotalHeight = SearchChipRowGap + TwoRowTopAppBarDefaults.SecondRowHeight + TwoRowTopAppBarDefaults.SecondRowBottom
    val resultsBarHeight = pinnedHeight(TitleRowHeight, secondRowTotalHeight)
    val contentTop = if (showingResults) {
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
                        top = contentTop,
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
                            rowScrollState = rowScrollState,
                            topBarBounds = barBounds,
                            onRetry = viewModel::retry,
                        )
                    }
                }
            }
        }
    }
}
