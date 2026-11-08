package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SectionTitle
import erl.bilico.ui.components.SegmentCapsule
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 顶栏搜索面板：点放大镜就地展开，不另开一页。
 *
 * 上半截是搜索栏，下面先给搜索历史与热搜；提交关键词后同一个面板换成结果列表。
 * 点面板外的遮罩、按返回或点「取消」都收起。
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
    val listState = rememberLazyListState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val showingResults = state.submitted.isNotBlank()

    // 每次展开都从搜索首页开始：历史、热搜，而不是上次那一屏结果。
    LaunchedEffect(Unit) { viewModel.reset() }

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

    val dismiss: () -> Unit = {
        focusManager.clearFocus(force = true)
        onClose()
    }

    Box(modifier) {
        // 面板外的遮罩：点一下就收起。压得比面板底色暗一档，面板边界才看得出来。
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
                .heightIn(max = if (showingResults) screenHeight else screenHeight * 0.66f)
                // 只给收起态加底部圆角：那一刻它是吊在顶上的浮层；出结果时它就是一整页。
                .clip(
                    if (showingResults) {
                        RectangleShape
                    } else {
                        RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                    },
                )
                .background(pageColor),
        ) {
            // 用 COUI 自带「取消」的那版 SearchBar：胶囊按钮和搜索框之间是原生的 12dp，
            // 开了模糊还会走融球。通用的 outsideEndAction 槽位是给旧的纯文字取消按钮算的边距，
            // 塞胶囊按钮进去会空出 20dp。
            SearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { keyword ->
                    container.settings.addSearchHistory(keyword)
                    viewModel.submit(keyword)
                },
                onExpandedChange = { if (!it) dismiss() },
                modifier = Modifier.statusBarsPadding(),
                expanded = true,
                label = "搜索视频 / 用户 / 专栏",
                cancelText = "取消",
                onCancel = dismiss,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 8.dp,
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
                SectionTitle("搜索历史", Modifier.weight(1f), horizontalPadding = 6.dp)
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

    item(key = "hot-title") { SectionTitle("热搜", horizontalPadding = 6.dp) }

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
