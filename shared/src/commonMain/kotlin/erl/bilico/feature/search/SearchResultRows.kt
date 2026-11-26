package erl.bilico.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.Composable
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.settings.CoverAspect
import erl.bilico.data.util.Formatters
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.RowScrollState
import erl.bilico.ui.TopBarBounds
import erl.bilico.ui.attachToRow
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.expandOrigin
import erl.bilico.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

private val SearchVideoCoverWidth = 144.dp
private val SearchVideoCoverHeight = SearchVideoCoverWidth * 9f / 16f

/**
 * 搜索结果列表项：分类标签栏已固定在顶栏第二行（随顶栏玻璃材质悬在列表上方），
 * 这里负责铺全部分类（综合/视频/番剧/影视/直播间/用户/专栏）的结果卡片与加载状态。
 */
internal fun LazyListScope.searchResultItems(
    state: SearchUiState,
    nav: NavState,
    rowScrollState: RowScrollState,
    topBarBounds: TopBarBounds,
    gridMode: Boolean = false,
    coverAspect: CoverAspect = CoverAspect.WIDE,
    onToggleGrid: (() -> Unit)? = null,
    onRetry: () -> Unit,
) {
    if (state.isLoading) return

    val tab = state.currentTab
    val emptyMessage = when {
        state.error != null -> null
        state.currentTabItemCount() > 0 -> null
        tab == SearchTab.All -> "没有找到相关内容"
        tab == SearchTab.Video -> "没有找到相关视频"
        tab == SearchTab.Bangumi -> "没有找到相关番剧"
        tab == SearchTab.Movie -> "没有找到相关影视"
        tab == SearchTab.LiveRoom -> "没有找到相关直播间"
        tab == SearchTab.User -> "没有找到相关用户"
        else -> "没有找到相关专栏"
    }

    when {
        state.error != null -> item(key = "error") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                ErrorState(state.error.orEmpty(), onRetry = onRetry)
            }
        }

        emptyMessage != null -> item(key = "empty_${tab.name}") {
            Box(Modifier.padding(horizontal = 16.dp)) { EmptyState(emptyMessage) }
        }
    }

    when (tab) {
        SearchTab.All -> {
            if (state.allUsers.isNotEmpty()) {
                items(
                    items = state.allUsers,
                    key = { "all_user_${it.mid}" },
                    contentType = { "search-user" },
                ) { user ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        SearchUserRow(
                            user = user,
                            onClick = { nav.push(Route.Space(user.mid)) },
                            onVideoClick = { video, origin -> nav.push(Route.Video(video.bvid), origin) },
                            rowScrollState = rowScrollState,
                            rowKey = "all_user_${user.mid}",
                            topBarBounds = topBarBounds,
                        )
                    }
                }
            }
            if (state.allPgc.size == 1) {
                val pgc = state.allPgc.first()
                item(key = "all_pgc_${pgc.seasonId}", contentType = "search-pgc") {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        SearchPgcRow(
                            pgc = pgc,
                            onClick = { nav.push(Route.Pgc(pgc.seasonId)) },
                        )
                    }
                }
            } else if (state.allPgc.size > 1) {
                item(key = "all_pgc_strip", contentType = "search-pgc-strip") {
                    SearchAllPgcStrip(
                        items = state.allPgc,
                        onClick = { pgc -> nav.push(Route.Pgc(pgc.seasonId)) },
                    )
                }
            }
            if (state.allVideos.isNotEmpty() && onToggleGrid != null) {
                item(key = "all_layout_toggle", contentType = "search-layout-toggle") {
                    SearchLayoutToggleRow(
                        orderLabel = state.videoOrder.label,
                        gridMode = gridMode,
                        onToggleGrid = onToggleGrid,
                    )
                }
            }
            if (gridMode) {
                searchVideoGridItems(
                    videos = state.allVideos,
                    keyPrefix = "all_video",
                    nav = nav,
                    rowScrollState = rowScrollState,
                    topBarBounds = topBarBounds,
                    coverAspect = coverAspect,
                )
            } else {
                searchVideoItems(
                    videos = state.allVideos,
                    keyPrefix = "all_video",
                    nav = nav,
                    rowScrollState = rowScrollState,
                    topBarBounds = topBarBounds,
                )
            }
        }

        SearchTab.Video -> {
            if (state.videos.isNotEmpty() && onToggleGrid != null) {
                item(key = "video_layout_toggle", contentType = "search-layout-toggle") {
                    SearchLayoutToggleRow(
                        orderLabel = state.videoOrder.label,
                        gridMode = gridMode,
                        onToggleGrid = onToggleGrid,
                    )
                }
            }
            if (gridMode) {
                searchVideoGridItems(
                    videos = state.videos,
                    keyPrefix = "video",
                    nav = nav,
                    rowScrollState = rowScrollState,
                    topBarBounds = topBarBounds,
                    coverAspect = coverAspect,
                )
            } else {
                searchVideoItems(
                    videos = state.videos,
                    keyPrefix = "video",
                    nav = nav,
                    rowScrollState = rowScrollState,
                    topBarBounds = topBarBounds,
                )
            }
        }

        SearchTab.Bangumi -> items(
            items = state.bangumis,
            key = { "bangumi_${it.seasonId}" },
            contentType = { "search-pgc" },
        ) { pgc ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                SearchPgcRow(
                    pgc = pgc,
                    onClick = { nav.push(Route.Pgc(pgc.seasonId)) },
                )
            }
        }

        SearchTab.Movie -> items(
            items = state.movies,
            key = { "movie_${it.seasonId}" },
            contentType = { "search-pgc" },
        ) { pgc ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                SearchPgcRow(
                    pgc = pgc,
                    onClick = { nav.push(Route.Pgc(pgc.seasonId)) },
                )
            }
        }

        SearchTab.LiveRoom -> items(
            items = state.liveRooms,
            key = { "live_${it.roomId}" },
            contentType = { "search-live" },
        ) { live ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                SearchLiveRow(
                    live = live,
                    onClick = { nav.push(Route.LiveRoom(live.roomId)) },
                )
            }
        }

        SearchTab.User -> items(
            items = state.users,
            key = { "user_${it.mid}" },
            contentType = { "search-user" },
        ) { user ->
            Box(Modifier.padding(horizontal = 16.dp)) {
                SearchUserRow(
                    user = user,
                    onClick = { nav.push(Route.Space(user.mid)) },
                    onVideoClick = { video, origin -> nav.push(Route.Video(video.bvid), origin) },
                    rowScrollState = rowScrollState,
                    rowKey = "user_${user.mid}",
                    topBarBounds = topBarBounds,
                )
            }
        }

        SearchTab.Article -> items(
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

private fun LazyListScope.searchVideoItems(
    videos: List<SearchVideo>,
    keyPrefix: String,
    nav: NavState,
    rowScrollState: RowScrollState,
    topBarBounds: TopBarBounds,
) {
    items(
        items = videos,
        key = { "${keyPrefix}_${it.bvid}" },
        contentType = { "search-video" },
    ) { video ->
        val rowKey = "${keyPrefix}_${video.bvid}"
        val origin = rememberExpandOrigin()
        SideEffect {
            origin.attachToRow(rowScrollState, rowKey, topBarBounds, itemKey = video.bvid)
        }
        Box(Modifier.padding(horizontal = 16.dp)) {
            SearchVideoRow(
                video = video,
                modifier = Modifier.expandOrigin(origin),
                onClick = { nav.push(Route.Video(video.bvid), origin) },
            )
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

    val pressState = rememberPreciseClickState()
    var longPressMenuExpanded by remember { mutableStateOf(false) }
    val menuItems = rememberSearchVideoMenuItems(video)

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .preciseClickAnchor(pressState),
            insideMargin = PaddingValues(8.dp),
            onClick = onClick,
            onLongPress = { longPressMenuExpanded = true },
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(SearchVideoCoverWidth)
                        .height(SearchVideoCoverHeight)
                        .clip(SquircleShape(8.dp)),
                ) {
                    CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                    SearchVideoCoverBadges(playText = video.playText, durationText = video.durationText)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
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
                        SearchVideoMoreButton(
                            items = menuItems,
                            buttonSize = 22.dp,
                            iconSize = 17.dp,
                        )
                    }
                }
            }
        }

        CouiPopupMenu(
            show = longPressMenuExpanded,
            onDismiss = { longPressMenuExpanded = false },
            items = menuItems,
            preciseClickState = pressState,
        )
    }
}

@Composable
internal fun BoxScope.SearchVideoCoverBadges(playText: String, durationText: String) {
    if (playText.isNotBlank()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .clip(SquircleShape(4.dp))
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
                text = playText,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
    if (durationText.isNotBlank()) {
        Text(
            text = durationText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(SquircleShape(4.dp))
                .background(Color(0xB3000000))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = Color.White,
            maxLines = 1,
        )
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
                    .clip(SquircleShape(8.dp))
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
