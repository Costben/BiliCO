package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoPart
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.feed.MoreMenu
import erl.bilico.feature.feed.StatLabel
import erl.bilico.feature.feed.rememberWatchLaterAction
import erl.bilico.feature.feed.shareVideo
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.AddCircle
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Promotions
import io.github.suqi8.coui.kmp.icon.extended.Stopwatch
import io.github.suqi8.coui.kmp.icon.extended.Store
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun VideoInfoTab(
    detail: VideoDetail?,
    isLoading: Boolean,
    error: String?,
    selectedCid: Long,
    onRetry: () -> Unit,
    onSelectPart: (Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    related: List<FeedVideo>,
    container: AppContainer,
    liked: Boolean,
    coined: Int,
    favorited: Boolean,
    following: Boolean,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        detail == null && isLoading -> LoadingState(modifier)

        detail == null -> ErrorState(
            message = error ?: "视频加载失败",
            modifier = modifier,
            onRetry = onRetry,
        )

        else -> InfoContent(
            detail = detail,
            selectedCid = selectedCid,
            onSelectPart = onSelectPart,
            onOpenUser = onOpenUser,
            onOpenVideo = onOpenVideo,
            related = related,
            container = container,
            liked = liked,
            coined = coined,
            favorited = favorited,
            following = following,
            onLike = onLike,
            onCoin = onCoin,
            onFavorite = onFavorite,
            onWatchLater = onWatchLater,
            onFollow = onFollow,
            modifier = modifier,
        )
    }
}

@Composable
private fun InfoContent(
    detail: VideoDetail,
    selectedCid: Long,
    onSelectPart: (Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    related: List<FeedVideo>,
    container: AppContainer,
    liked: Boolean,
    coined: Int,
    favorited: Boolean,
    following: Boolean,
    onLike: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onWatchLater: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var descExpanded by rememberSaveable(detail.bvid) { mutableStateOf(false) }
    val density = LocalDensity.current
    val avatarPx = with(density) { 40.dp.roundToPx() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Text(
                text = detail.title,
                style = COUITheme.textStyles.title3,
                fontWeight = FontWeight.Medium,
            )
        }

        item(key = "stats") {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("播放", Formatters.formatStat(detail.view), Modifier.weight(1f))
                StatCell("点赞", Formatters.formatStat(detail.like), Modifier.weight(1f))
                StatCell("投币", Formatters.formatStat(detail.coin), Modifier.weight(1f))
                StatCell("收藏", Formatters.formatStat(detail.favorite), Modifier.weight(1f))
            }
        }

        item(key = "up") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = detail.upMid > 0L) { onOpenUser(detail.upMid) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    url = Formatters.sizedImageUrl(detail.upFace, avatarPx, avatarPx),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = detail.upName.ifBlank { "未知 UP" },
                        style = COUITheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = Formatters.formatTimeAgo(detail.pubdate),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }

        item(key = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoAction(
                    icon = COUIIcons.Promotions,
                    label = if (liked) "已赞" else "点赞",
                    active = liked,
                    onClick = onLike,
                )
                VideoAction(
                    icon = COUIIcons.Store,
                    label = if (coined > 0) "已投币" else "投币",
                    active = coined > 0,
                    onClick = onCoin,
                )
                VideoAction(
                    icon = COUIIcons.Favorites,
                    label = if (favorited) "已收藏" else "收藏",
                    active = favorited,
                    onClick = onFavorite,
                )
                VideoAction(
                    icon = COUIIcons.Stopwatch,
                    label = "稍后再看",
                    active = false,
                    onClick = onWatchLater,
                )
                VideoAction(
                    icon = COUIIcons.AddCircle,
                    label = if (following) "已关注" else "关注",
                    active = following,
                    onClick = onFollow,
                )
            }
        }

        val desc = detail.desc.trim()
        if (desc.isNotEmpty()) {
            item(key = "desc") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(COUITheme.colorScheme.surfaceVariant)
                        .clickable { descExpanded = !descExpanded }
                        .padding(12.dp),
                ) {
                    Text(
                        text = desc,
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = if (descExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!descExpanded && desc.length > 80) {
                        Text(
                            text = "展开",
                            modifier = Modifier.padding(top = 6.dp),
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (detail.pages.size > 1) {
            item(key = "parts-title") {
                Text(
                    text = "分P（${detail.pages.size}）",
                    style = COUITheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                )
            }
            itemsIndexed(detail.pages, key = { _, part -> part.cid }) { index, part ->
                PartRow(
                    part = part,
                    selected = part.cid == selectedCid,
                    onClick = { onSelectPart(index) },
                )
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

/** 简介下面那一栏：左边缩略图，右边标题、播放弹幕数与 UP 行（含三点菜单）。 */
@Composable
private fun RelatedVideoRow(
    video: FeedVideo,
    onWatchLater: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverWidth = 152.dp
    val coverWidthPx = with(density) { coverWidth.roundToPx() }
    val coverHeightPx = coverWidthPx * 9 / 16
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .width(coverWidth)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            CoverImage(
                url = Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx),
                modifier = Modifier.fillMaxSize(),
            )
            if (video.durationText.isNotBlank()) {
                Text(
                    text = video.durationText,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = coverWidth * 9f / 16f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = video.title,
                style = COUITheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatLabel(COUIIcons.Play, video.playCountText)
                    if (video.danmakuText.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        StatLabel(COUIIcons.Messages, video.danmakuText)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.upName.ifBlank { "未知 UP" },
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MoreMenu(
                        onWatchLater = onWatchLater,
                        onShare = { shareVideo(context, video) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) {
        COUITheme.colorScheme.primary
    } else {
        COUITheme.colorScheme.onSurfaceSecondary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 11.sp, color = tint, maxLines = 1)
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = COUITheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
        )
        Box(Modifier.height(2.dp))
        Text(
            text = label,
            style = COUITheme.textStyles.footnote2,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@Composable
private fun PartRow(part: VideoPart, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) COUITheme.colorScheme.pressBackground else COUITheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "P${part.index}",
            modifier = Modifier.width(36.dp),
            style = COUITheme.textStyles.footnote1,
            color = if (selected) {
                COUITheme.colorScheme.primary
            } else {
                COUITheme.colorScheme.onSurfaceSecondary
            },
        )
        Text(
            text = part.title.ifBlank { "第 ${part.index} P" },
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.body2,
            color = if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (part.durationSec > 0) {
            Text(
                text = Formatters.formatDuration(part.durationSec),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}
