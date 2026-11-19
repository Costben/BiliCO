package erl.bilico.feature.video

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoEpisode
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.DislikeFilledIcon
import erl.bilico.feature.comment.DislikeOutlineIcon
import erl.bilico.feature.comment.LikeFilledIcon
import erl.bilico.feature.comment.LikeOutlineIcon
import erl.bilico.feature.feed.rememberWatchLaterAction
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.ExpandableSectionHeader
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.StatLabel
import io.github.suqi8.coui.kmp.basic.ExpandableList
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.icon.extended.Stopwatch
import io.github.suqi8.coui.kmp.icon.extended.Store
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

@Composable
internal fun VideoInfoTab(
    detail: VideoDetail?,
    isLoading: Boolean,
    error: String?,
    selectedCid: Long,
    onRetry: () -> Unit,
    onSelectPart: (Int) -> Unit,
    onSelectEpisode: (VideoEpisode) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    related: List<FeedVideo>,
    container: AppContainer,
    liked: Boolean,
    disliked: Boolean,
    coined: Int,
    favorited: Boolean,
    following: Boolean,
    upFollowers: Long?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onFollow: () -> Unit,
    onShare: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    chapters: List<VideoChapter> = emptyList(),
    positionMsProvider: () -> Long = { 0L },
    onSelectChapter: (VideoChapter) -> Unit = {},
    onOpenChapters: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when {
        detail == null && isLoading -> LoadingState(modifier.padding(top = topInset, bottom = bottomInset))

        detail == null -> ErrorState(
            message = error ?: "视频加载失败",
            modifier = modifier.padding(top = topInset, bottom = bottomInset),
            onRetry = onRetry,
        )

        else -> InfoContent(
            detail = detail,
            selectedCid = selectedCid,
            onSelectPart = onSelectPart,
            onSelectEpisode = onSelectEpisode,
            onOpenUser = onOpenUser,
            onOpenVideo = onOpenVideo,
            related = related,
            container = container,
            liked = liked,
            disliked = disliked,
            coined = coined,
            favorited = favorited,
            following = following,
            upFollowers = upFollowers,
            onLike = onLike,
            onDislike = onDislike,
            onCoin = onCoin,
            onFavorite = onFavorite,
            onWatchLater = onWatchLater,
            onFollow = onFollow,
            onShare = onShare,
            listState = listState,
            topInset = topInset,
            bottomInset = bottomInset,
            chapters = chapters,
            positionMsProvider = positionMsProvider,
            onSelectChapter = onSelectChapter,
            onOpenChapters = onOpenChapters,
            onTagClick = onTagClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun InfoContent(
    detail: VideoDetail,
    selectedCid: Long,
    onSelectPart: (Int) -> Unit,
    onSelectEpisode: (VideoEpisode) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    related: List<FeedVideo>,
    container: AppContainer,
    liked: Boolean,
    disliked: Boolean,
    coined: Int,
    favorited: Boolean,
    following: Boolean,
    upFollowers: Long?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onFollow: () -> Unit,
    onShare: () -> Unit,
    listState: LazyListState,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    chapters: List<VideoChapter> = emptyList(),
    positionMsProvider: () -> Long = { 0L },
    onSelectChapter: (VideoChapter) -> Unit = {},
    onOpenChapters: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 标题与简介共用一次展开：点一下标题，标题放开的同时简介也跟着放开。
    var expanded by rememberSaveable(detail.bvid) { mutableStateOf(false) }
    // 分P 与合集默认收起，几十条剧集不会一进页面就铺满。
    var collectionExpanded by rememberSaveable(detail.bvid) { mutableStateOf(false) }
    var partsExpanded by rememberSaveable(detail.bvid) { mutableStateOf(false) }
    val density = LocalDensity.current
    val avatarPx = with(density) { 44.dp.roundToPx() }
    val desc = detail.desc.trim()
    // 收起时把换行压成空格：简介里的空行会让省略号单独占一行，白吃一行高度。
    val descPreview = remember(desc) { desc.replace(Regex("\\s*\\n+\\s*"), " ").trim() }
    val accent = COUITheme.colorScheme.primary
    val descStyle = COUITheme.textStyles.body2
    val measurer = rememberTextMeasurer()

    LazyColumn(
        state = listState,
        modifier = modifier.scrollEndHaptic(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp + topInset,
            end = 16.dp,
            bottom = 12.dp + bottomInset,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "up") {
            UpRow(
                detail = detail,
                followers = upFollowers,
                following = following,
                avatarPx = avatarPx,
                onOpenUser = onOpenUser,
                onFollow = onFollow,
            )
        }

        item(key = "intro") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(10.dp))
                    .clickable { expanded = !expanded }
                    .animateContentSize(),
            ) {
                Text(
                    text = detail.title,
                    style = COUITheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (expanded) Int.MAX_VALUE else TITLE_COLLAPSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatLabel(COUIIcons.Play, Formatters.formatStat(detail.view))
                    Spacer(Modifier.width(10.dp))
                    StatLabel(COUIIcons.Messages, Formatters.formatStat(detail.danmaku))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = Formatters.formatDateTime(detail.pubdate),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                    )
                }

                if (desc.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // 留一点右侧余量，免得「…展开」正好顶到边缘。
                        val textWidthPx = with(density) {
                            (maxWidth - DESC_ICON_SIZE - DESC_ICON_GAP - DESC_END_RESERVE).roundToPx()
                        }
                        val descText = if (expanded) {
                            AnnotatedString(desc)
                        } else {
                            remember(descPreview, accent, textWidthPx) {
                                collapsedDescText(measurer, descPreview, descStyle, accent, textWidthPx)
                            }
                        }
                        Row {
                            Icon(
                                imageVector = COUIIcons.Info,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(DESC_ICON_SIZE),
                                tint = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                            Spacer(Modifier.width(DESC_ICON_GAP))
                            Text(
                                text = descText,
                                style = descStyle,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                                maxLines = if (expanded) Int.MAX_VALUE else DESC_COLLAPSED_LINES,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (detail.tags.isNotEmpty()) {
            item(key = "tags") { VideoTagChips(detail.tags, onTagClick = onTagClick) }
        }

        item(key = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoAction(
                    icon = if (liked) LikeFilledIcon else LikeOutlineIcon,
                    label = if (liked) "取消点赞" else "点赞",
                    text = Formatters.formatStat(detail.like),
                    active = liked,
                    onClick = onLike,
                )
                VideoAction(
                    icon = if (disliked) DislikeFilledIcon else DislikeOutlineIcon,
                    label = if (disliked) "取消点踩" else "点踩",
                    text = "点踩",
                    active = disliked,
                    onClick = onDislike,
                )
                VideoAction(
                    icon = COUIIcons.Store,
                    label = if (coined > 0) "已投币" else "投币",
                    text = Formatters.formatStat(detail.coin),
                    active = coined > 0,
                    onClick = onCoin,
                )
                VideoAction(
                    icon = COUIIcons.Favorites,
                    label = if (favorited) "取消收藏" else "收藏",
                    text = Formatters.formatStat(detail.favorite),
                    active = favorited,
                    onClick = onFavorite,
                )
                VideoAction(
                    icon = COUIIcons.Stopwatch,
                    label = "稍后再看",
                    text = "再看",
                    active = false,
                    onClick = onWatchLater,
                )
                VideoAction(
                    icon = COUIIcons.Share,
                    label = "分享",
                    text = "分享",
                    active = false,
                    onClick = onShare,
                )
            }
        }

        if (chapters.isNotEmpty()) {
            item(key = "chapters-section") {
                VideoChaptersSection(
                    chapters = chapters,
                    positionMs = positionMsProvider,
                    onSelectChapter = onSelectChapter,
                    onOpenAll = onOpenChapters,
                )
            }
        }

        val collection = detail.collection
        if (collection != null && collection.episodes.size > 1) {
            item(key = "collection-section") {
                ExpandableList(
                    groupCount = 1,
                    childCount = { collection.episodes.size },
                    expandedGroups = if (collectionExpanded) setOf(0) else emptySet(),
                    onExpandedGroupsChange = { collectionExpanded = 0 in it },
                    groupContent = { _, expanded ->
                        ExpandableSectionHeader(
                            text = "${collection.title.ifBlank { "合集" }}（${collection.episodes.size}）",
                            expanded = expanded,
                        )
                    },
                ) { _, index ->
                    val episode = collection.episodes[index]
                    CollectionRow(
                        index = index + 1,
                        episode = episode,
                        selected = episode.bvid == detail.bvid &&
                            (episode.cid <= 0L || episode.cid == selectedCid),
                        onClick = { onSelectEpisode(episode) },
                    )
                }
            }
        }

        if (detail.pages.size > 1) {
            item(key = "parts-section") {
                ExpandableList(
                    groupCount = 1,
                    childCount = { detail.pages.size },
                    expandedGroups = if (partsExpanded) setOf(0) else emptySet(),
                    onExpandedGroupsChange = { partsExpanded = 0 in it },
                    groupContent = { _, expanded ->
                        ExpandableSectionHeader(
                            text = "分P（${detail.pages.size}）",
                            expanded = expanded,
                        )
                    },
                ) { _, index ->
                    PartRow(
                        part = detail.pages[index],
                        selected = detail.pages[index].cid == selectedCid,
                        onClick = { onSelectPart(index) },
                    )
                }
            }
        }

        item(key = "divider") {
            HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
        }

        if (related.isNotEmpty()) {
            item(key = "related-title") {
                Text(
                    text = "其他推荐视频",
                    style = COUITheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                )
            }
            items(related, key = { "related-${it.bvid}" }) { video ->
                RelatedVideoRow(
                    video = video,
                    onWatchLater = rememberWatchLaterAction(container, video),
                    onOpen = { onOpenVideo(video.bvid) },
                )
            }
        }
    }
}

/** 简介下面的标签：按接口给的顺序铺开，点击后以该标签为关键词进入搜索。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoTagChips(
    tags: List<String>,
    onTagClick: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            Text(
                text = tag,
                modifier = Modifier
                    .clip(SquircleShape(6.dp))
                    .background(COUITheme.colorScheme.surfaceContainer)
                    .clickable(enabled = tag.isNotBlank()) { onTagClick(tag) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
