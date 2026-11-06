package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.SearchArticle
import erl.bilico.data.model.SearchUser
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.AppBarSurface
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SectionTitle
import erl.bilico.ui.components.SegmentCapsule
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.glassBarContentTop
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

/** 搜索栏连同上下留白的高度；列表首项要让到它下面。 */
private val SearchBarHeight = 56.dp

@Composable
fun SearchScreen(
    nav: NavState,
    container: AppContainer,
) {
    val viewModel: SearchViewModel = viewModel { SearchViewModel(container.search) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by container.settings.searchHistory.collectAsStateWithLifecycle()

    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val barHeight = pinnedHeight(SearchBarHeight)
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = when (viewModel.state.value.tab) {
                    0 -> viewModel.state.value.videos.size
                    1 -> viewModel.state.value.users.size
                    else -> viewModel.state.value.articles.size
                }
                if (lastVisible != null && size > 0 && lastVisible >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, backdrop)
                .background(pageColor)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(barHeight, 8.dp),
                    end = 12.dp,
                    bottom = 24.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.submitted.isBlank()) {
                    suggestions(
                        hot = state.hot,
                        hotLoading = state.hotLoading,
                        history = history,
                        onPick = { keyword ->
                            container.settings.addSearchHistory(keyword)
                            viewModel.submit(keyword)
                        },
                        onClearHistory = { container.settings.clearSearchHistory() },
                    )
                } else {
                    stickyHeader(key = "tabs") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(pageColor)
                                .padding(vertical = 4.dp),
                        ) {
                            SegmentCapsule(
                                labels = listOf("视频", "用户", "专栏"),
                                selectedIndex = state.tab,
                                onSelect = viewModel::setTab,
                            )
                        }
                    }

                    when {
                        state.isLoading -> item(key = "loading") { LoadingState() }

                        state.error != null -> item(key = "error") {
                            ErrorState(state.error.orEmpty(), onRetry = viewModel::retry)
                        }

                        state.tab == 0 && state.videos.isEmpty() -> item(key = "empty") {
                            EmptyState("没有找到相关视频")
                        }

                        state.tab == 1 && state.users.isEmpty() -> item(key = "empty") {
                            EmptyState("没有找到相关用户")
                        }

                        state.tab == 2 && state.articles.isEmpty() -> item(key = "empty") {
                            EmptyState("没有找到相关专栏")
                        }
                    }

                    if (state.tab == 0) {
                        items(
                            items = state.videos,
                            key = { it.bvid },
                            contentType = { "search-video" },
                        ) { video ->
                            SearchVideoRow(
                                video = video,
                                onClick = { nav.push(Route.Video(video.bvid)) },
                            )
                        }
                    } else if (state.tab == 1) {
                        items(
                            items = state.users,
                            key = { it.mid },
                            contentType = { "search-user" },
                        ) { user ->
                            SearchUserRow(
                                user = user,
                                onClick = { nav.push(Route.Space(user.mid)) },
                            )
                        }
                    } else {
                        items(
                            items = state.articles,
                            key = { "article-${it.id}" },
                            contentType = { "search-article" },
                        ) { article ->
                            SearchArticleRow(
                                article = article,
                                onClick = { nav.push(Route.Article(article.id, article.title)) },
                            )
                        }
                    }

                    if (state.isLoadingMore) {
                        item(key = "loading-more") { LoadingState() }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                AppBarSurface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    containerColor = pageColor,
                    scrollFraction = { 1f },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BackIconButton(onClick = nav::pop)
                        InputField(
                            query = state.query,
                            onQueryChange = viewModel::onQueryChange,
                            onSearch = { keyword ->
                                container.settings.addSearchHistory(keyword)
                                viewModel.submit(keyword)
                            },
                            expanded = true,
                            onExpandedChange = { if (!it) nav.pop() },
                            label = "搜索视频 / 用户 / 专栏",
                            modifier = Modifier.weight(1f).testTag("search_input"),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.suggestions(
    hot: List<String>,
    hotLoading: Boolean,
    history: List<String>,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isNotEmpty()) {
        item(key = "history-title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("搜索历史", Modifier.weight(1f))
                Text(
                    text = "清空",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
        item(key = "history") {
            KeywordChips(keywords = history, onPick = onPick)
        }
    }

    item(key = "hot-title") { SectionTitle("热搜") }

    if (hotLoading && hot.isEmpty()) {
        item(key = "hot-loading") { LoadingState() }
    } else {
        item(key = "hot") {
            if (hot.isEmpty()) {
                EmptyState("暂时没有热搜")
            } else {
                KeywordChips(keywords = hot, onPick = onPick)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordChips(keywords: List<String>, onPick: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keywords.forEach { keyword ->
            Chip(
                text = keyword,
                onClick = remember(keyword, onPick) { { onPick(keyword) } },
            )
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(COUITheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        fontSize = 13.sp,
        color = COUITheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SearchVideoRow(video: SearchVideo, onClick: () -> Unit) {
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { 132.dp.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val coverUrl = remember(video.cover, coverWidthPx) {
        Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(8.dp),
        onClick = onClick,
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(video.author.ifBlank { "未知 UP" })
                        if (video.playText.isNotBlank()) append(" · ").append(video.playText).append("播放")
                    },
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchArticleRow(article: SearchArticle, onClick: () -> Unit) {
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { 96.dp.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 3 / 4 }
    val coverUrl = remember(article.cover, coverWidthPx) {
        Formatters.sizedImageUrl(article.cover, coverWidthPx, coverHeightPx)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = onClick,
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(COUITheme.colorScheme.surfaceContainerHigh),
            ) {
                if (article.cover.isNotBlank()) {
                    CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = article.title,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (article.desc.isNotBlank()) {
                    Text(
                        text = article.desc,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = buildString {
                        append(article.author.ifBlank { "未知作者" })
                        if (article.categoryName.isNotBlank()) append(" · ").append(article.categoryName)
                        if (article.viewText.isNotBlank()) append(" · ").append(article.viewText).append("阅读")
                        if (article.likeText.isNotBlank()) append(" · ").append(article.likeText).append("赞")
                    },
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchUserRow(user: SearchUser, onClick: () -> Unit) {
    val avatarUrl = remember(user.face) { Formatters.sizedImageUrl(user.face, 132, 132) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = avatarUrl, size = 44.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = user.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.sign.ifBlank { "${user.fansText} · ${user.videoCountText}" },
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
