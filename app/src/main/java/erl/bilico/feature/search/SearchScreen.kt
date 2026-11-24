@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import erl.bilico.ui.reportTopBarBottom
import erl.bilico.ui.topBarScrollPx
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.rememberSearchResultsTransitionState
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop

/**
 * 独立搜索页（如点击视频标签后直达搜索）：复用首页搜索面板与双行玻璃顶栏。
 */
@Composable
fun SearchScreen(
    container: AppContainer,
    nav: NavState,
    initialQuery: String = "",
) {
    val entryId = LocalNavEntry.current?.id ?: initialQuery
    val viewModel: SearchViewModel = viewModel(key = "search-$entryId") {
        SearchViewModel(container.search, initialQuery)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by container.settings.searchHistory.collectAsStateWithLifecycle()
    val searchBackdrop = rememberLayerBackdrop()
    val barBounds = remember { TopBarBounds() }
    val searchListState = rememberLazyListState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val showingResults = state.showingResults()
    val searchTransition = rememberSearchResultsTransitionState(
        showingResults = showingResults,
        loading = state.isLoading,
        queryKey = state.submitted,
    )
    val topBarHeight = pinnedHeight(TitleRowHeight)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // TopAppBarSearchBar 展开时默认拉起输入法；从视频标签直接进入结果态时先压住首次自动聚焦，避免键盘闪出。
    var suppressInitialFocus by remember { mutableStateOf(initialQuery.isNotBlank()) }
    LaunchedEffect(Unit) {
        val keyword = initialQuery.trim()
        if (keyword.isNotEmpty()) {
            container.settings.addSearchHistory(keyword)
        }
        if (suppressInitialFocus) {
            repeat(2) { withFrameNanos { } }
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            suppressInitialFocus = false
        }
    }

    LaunchedEffect(showingResults) {
        if (!showingResults) sortMenuExpanded = false
    }

    val submitSearch: (String) -> Unit = { raw ->
        val keyword = raw.trim()
        if (keyword.isNotEmpty()) {
            keyboard?.hide()
            focusManager.clearFocus()
            container.settings.addSearchHistory(keyword)
            viewModel.submit(keyword)
        }
    }
    val closeSearch: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        nav.pop()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageContainerColor())
            .testTag("search_screen"),
    ) {
        SearchPanelBody(
            topBarHeight = topBarHeight,
            searchBackdrop = searchBackdrop,
            searchExpanded = true,
            state = state,
            transitionState = searchTransition,
            history = history,
            viewModel = viewModel,
            container = container,
            nav = nav,
            barBounds = barBounds,
            onSelectKeyword = submitSearch,
            listState = searchListState,
        )

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) searchBackdrop else null,
            LocalEdgeToEdge provides true,
            LocalSoftwareKeyboardController provides if (suppressInitialFocus) null else keyboard,
        ) {
            PinnedBarDensity {
                val searchBarDensity = LocalDensity.current
                SearchTopAppBar(
                    searchState = state,
                    onQueryChange = viewModel::onQueryChange,
                    searchExpanded = true,
                    onSearchOpen = {},
                    onSearchCancel = closeSearch,
                    onSearchSubmit = submitSearch,
                    showingResults = searchTransition.filtersVisible,
                    onTabChange = viewModel::setTab,
                    onSortMenuOpen = { sortMenuExpanded = true },
                    panelAlpha = {
                        AppBarBlurOverlayDefaults.scrollAlpha(
                            searchListState.topBarScrollPx(),
                            searchBarDensity,
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .reportTopBarBottom(barBounds),
                )
                if (showingResults) {
                    SearchSortMenuHost(
                        expanded = sortMenuExpanded,
                        onExpandedChange = { sortMenuExpanded = it },
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}
