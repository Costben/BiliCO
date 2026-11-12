package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 顶栏搜索面板：点放大镜就地展开，不另开一页。
 *
 * 上半截是搜索栏，下面依输入态切分：空输入显示历史与热搜，输入中显示实时搜索建议，提交后显示结果。
 */
@Composable
fun SearchPanel(
    nav: NavState,
    container: AppContainer,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel { SearchViewModel(container.search) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by container.settings.searchHistory.collectAsStateWithLifecycle()

    val pageColor = pageContainerColor()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val trimmedQuery = state.query.trim()
    val suggesting = trimmedQuery.isNotEmpty() && trimmedQuery != state.submitted
    val showingResults = !suggesting && state.submitted.isNotBlank()

    // 每次展开都从搜索首页开始：历史、热搜，而不是上次那一屏结果。
    LaunchedEffect(Unit) { viewModel.reset() }

    SearchListEffects(
        listState = listState,
        viewModel = viewModel,
        submitted = state.submitted,
        suggesting = suggesting,
        showingResults = showingResults,
    )

    val dismiss: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        onClose()
    }
    val pickKeyword: (String) -> Unit = { keyword ->
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        container.settings.addSearchHistory(keyword)
        viewModel.submit(keyword)
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                ),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (showingResults) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.heightIn(max = screenHeight * 0.66f)
                    },
                )
                .clip(
                    if (showingResults) {
                        RectangleShape
                    } else {
                        SquircleShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
                    },
                )
                .background(pageColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = pickKeyword,
                onExpandedChange = { if (!it) dismiss() },
                modifier = Modifier.statusBarsPadding(),
                expanded = true,
                label = "搜索视频 / 用户 / 专栏",
                cancelText = "取消",
                onCancel = dismiss,
            )

            SearchLazyList(
                state = state,
                history = history,
                suggesting = suggesting,
                showingResults = showingResults,
                listState = listState,
                pageColor = pageColor,
                topPadding = 8.dp,
                viewModel = viewModel,
                container = container,
                nav = nav,
                onPick = pickKeyword,
            )
        }
    }
}

/**
 * 推荐页顶栏展开的搜索下拉内容区：紧贴在顶栏展开的搜索输入框下方向下展开。
 *
 * 展开状态下：
 * - 空输入：展示搜索历史与热搜；
 * - 正在输入且与当前结果不同：实时查询并展示搜索建议；
 * - 提交关键词后：展示视频 / 用户 / 专栏分类结果；
 * - 点击遮罩区域或按返回键收起。
 */
@Composable
fun FeedSearchDropdown(
    state: SearchUiState,
    history: List<String>,
    viewModel: SearchViewModel,
    container: AppContainer,
    nav: NavState,
    barBottom: Dp,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    dropdownModifier: Modifier = Modifier,
) {
    val pageColor = pageContainerColor()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val trimmedQuery = state.query.trim()
    val suggesting = trimmedQuery.isNotEmpty() && trimmedQuery != state.submitted
    val showingResults = !suggesting && state.submitted.isNotBlank()

    SearchListEffects(
        listState = listState,
        viewModel = viewModel,
        submitted = state.submitted,
        suggesting = suggesting,
        showingResults = showingResults,
    )

    val dismiss: () -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        onClose()
    }
    val pickKeyword: (String) -> Unit = { keyword ->
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        container.settings.addSearchHistory(keyword)
        viewModel.submit(keyword)
    }

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismiss,
                ),
        )

        Column(
            dropdownModifier
                .fillMaxWidth()
                .then(
                    if (showingResults) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.heightIn(max = screenHeight * 0.72f)
                    },
                )
                .clip(
                    if (showingResults) {
                        RectangleShape
                    } else {
                        SquircleShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
                    },
                )
                .background(pageColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = barBottom),
            ) {
                SearchLazyList(
                    state = state,
                    history = history,
                    suggesting = suggesting,
                    showingResults = showingResults,
                    listState = listState,
                    pageColor = pageColor,
                    topPadding = if (showingResults) 6.dp else 16.dp,
                    viewModel = viewModel,
                    container = container,
                    nav = nav,
                    onPick = pickKeyword,
                )
            }
        }
    }
}

@Composable
private fun SearchListEffects(
    listState: LazyListState,
    viewModel: SearchViewModel,
    submitted: String,
    suggesting: Boolean,
    showingResults: Boolean,
) {
    LaunchedEffect(submitted, suggesting) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState, showingResults) {
        if (!showingResults) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val current = viewModel.state.value
                val size = when (current.tab) {
                    0 -> current.videos.size
                    1 -> current.users.size
                    else -> current.articles.size
                }
                if (lastVisible != null && size > 0 && lastVisible >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }
}

@Composable
private fun SearchLazyList(
    state: SearchUiState,
    history: List<String>,
    suggesting: Boolean,
    showingResults: Boolean,
    listState: LazyListState,
    pageColor: Color,
    topPadding: Dp,
    viewModel: SearchViewModel,
    container: AppContainer,
    nav: NavState,
    onPick: (String) -> Unit,
) {
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomPadding = maxOf(24.dp + navBottomPadding(), imeBottom + 16.dp)
    val stickyBarBounds = remember { TopBarBounds() }
    val rowScrollState = remember(listState) { listState.asRowScrollState() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = topPadding,
            end = 16.dp,
            bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            suggesting -> searchSuggestSection(
                query = state.query,
                suggestions = state.suggestions,
                suggestionsLoading = state.suggestionsLoading,
                onPick = onPick,
            )

            !showingResults -> searchHomeSection(
                hot = state.hot,
                hotLoading = state.hotLoading,
                history = history,
                onPick = onPick,
                onDeleteHistory = { keyword -> container.settings.removeSearchHistory(keyword) },
                onClearHistory = { container.settings.clearSearchHistory() },
            )

            else -> searchResultSection(
                state = state,
                pageColor = pageColor,
                nav = nav,
                rowScrollState = rowScrollState,
                topBarBounds = stickyBarBounds,
                onSelectTab = viewModel::setTab,
                onRetry = viewModel::retry,
            )
        }
    }
}
