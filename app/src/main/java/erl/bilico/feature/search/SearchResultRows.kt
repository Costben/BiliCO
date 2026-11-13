package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.SearchArticle
import erl.bilico.data.model.SearchUser
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.util.Formatters
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.RowScrollState
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.attachToRow
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.expandOrigin
import erl.bilico.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

private val SearchVideoCoverWidth = 144.dp
private val SearchVideoCoverHeight = SearchVideoCoverWidth * 9f / 16f

/**
 * 搜索结果与顶部分类胶囊绑在同一份 LazyColumn 里：
 * 首项放 [SearchHeaderRow]，随正文结果一起上下滑动，滑上去后藏入单行玻璃顶栏之下。
 */
internal fun LazyListScope.searchResultItems(
    state: SearchUiState,
    nav: NavState,
    viewModel: SearchViewModel,
    rowScrollState: RowScrollState,
    topBarBounds: TopBarBounds,
    onRetry: () -> Unit,
) {
    item(key = "search_results_header") {
        SearchHeaderRow(
            state = state,
            viewModel = viewModel,
        )
    }

    when {
        state.isLoading -> item(key = "loading") {
            Box(Modifier.padding(horizontal = 16.dp)) { LoadingState() }
        }

        state.error != null -> item(key = "error") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                ErrorState(state.error.orEmpty(), onRetry = onRetry)
            }
        }

        state.tab == 0 && state.videos.isEmpty() -> item(key = "empty") {
            Box(Modifier.padding(horizontal = 16.dp)) { EmptyState("没有找到相关视频") }
        }

        state.tab == 1 && state.users.isEmpty() -> item(key = "empty") {
            Box(Modifier.padding(horizontal = 16.dp)) { EmptyState("没有找到相关用户") }
        }

        state.tab == 2 && state.articles.isEmpty() -> item(key = "empty") {
            Box(Modifier.padding(horizontal = 16.dp)) { EmptyState("没有找到相关专栏") }
        }
    }

    when (state.tab) {
        0 -> items(
            items = state.videos,
            key = { "video_${it.bvid}" },
            contentType = { "search-video" },
        ) { video ->
            val origin = rememberExpandOrigin()
            SideEffect {
                origin.attachToRow(rowScrollState, "video_${video.bvid}", topBarBounds)
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                SearchVideoRow(
                    video = video,
                    modifier = Modifier.expandOrigin(origin),
                    onClick = { nav.push(Route.Video(video.bvid), origin) },
                )
            }
        }

        1 -> items(
            items = state.users,
            key = { "user_${it.mid}" },
            contentType = { "search-user" },
        ) { user ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                SearchUserRow(
                    user = user,
                    onClick = { nav.push(Route.Space(user.mid)) },
                )
            }
        }

        else -> items(
            items = state.articles,
            key = { "article_${it.id}" },
            contentType = { "search-article" },
        ) { article ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                SearchArticleRow(
                    article = article,
                    onClick = { nav.push(Route.Article(article.id, article.title)) },
                )
            }
        }
    }

    if (state.isLoadingMore) {
        item(key = "loading-more") {
            Box(Modifier.padding(horizontal = 16.dp)) { LoadingState() }
        }
    }
}

@Composable
private fun SearchVideoRow(
    video: SearchVideo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { SearchVideoCoverWidth.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val coverUrl = remember(video.cover, coverWidthPx) {
        Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(8.dp),
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(SearchVideoCoverWidth)
                    .height(SearchVideoCoverHeight)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                if (video.playText.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = COUIIcons.Demibold.Play,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = Color.White,
                        )
                        Text(
                            text = video.playText,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
                if (video.durationText.isNotBlank()) {
                    Text(
                        text = video.durationText,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SearchVideoCoverHeight)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
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
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (video.pubdateText.isNotBlank()) {
                        Text(
                            text = video.pubdateText,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = video.author.ifBlank { "未知 UP" },
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
