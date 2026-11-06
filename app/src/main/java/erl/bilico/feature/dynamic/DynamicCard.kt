package erl.bilico.feature.dynamic

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicArticleCard
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicKind
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.Avatar
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 卡片内可用宽度：屏宽减去列表留白（24dp）与卡片内边距（28dp）。 */
@Composable
private fun cardContentWidthDp(): Dp = LocalConfiguration.current.screenWidthDp.dp - 52.dp

@Composable
internal fun DynamicCard(
    item: DynamicItemUi,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    onOpenContent: (DynamicArticleCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        DynamicHeader(
            item = item,
            avatarSize = 40.dp,
            onClick = { if (item.authorMid > 0L) onOpenUser(item.authorMid) },
        )

        if (item.content.isNotBlank()) {
            Text(
                text = item.content,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = COUITheme.colorScheme.onSurface,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (item.kind) {
            DynamicKind.VIDEO -> item.video?.let {
                DynamicVideoCardView(
                    video = it,
                    onOpenVideo = onOpenVideo,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            DynamicKind.IMAGES -> if (item.images.isNotEmpty()) {
                DynamicImageGrid(
                    images = item.images,
                    onOpenImage = onOpenImage,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            DynamicKind.FORWARD -> item.forward?.let {
                ForwardBox(
                    item = it,
                    onOpenUser = onOpenUser,
                    onOpenVideo = onOpenVideo,
                    onOpenImage = onOpenImage,
                    onOpenContent = onOpenContent,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            DynamicKind.UNSUPPORTED -> Text(
                text = "暂不支持的动态类型",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )

            DynamicKind.TEXT -> Unit
        }

        item.article?.let { article ->
            ArticleLinkBox(
                article = article,
                onClick = { onOpenContent(article) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.displayTime(),
                fontSize = 11.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = item.statsText(),
                fontSize = 11.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DynamicHeader(
    item: DynamicItemUi,
    avatarSize: Dp,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(
            url = remember(item.authorFace, avatarSize) {
                val px = avatarSize.value.toInt()
                Formatters.sizedImageUrl(item.authorFace, px * 3, px * 3)
            },
            size = avatarSize,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.authorName.ifBlank { "未知用户" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = COUITheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onClick),
            )
            val time = item.displayTime()
            if (time.isNotBlank()) {
                Text(
                    text = time,
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DynamicVideoCardView(
    video: DynamicVideoCard,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val contentWidth = cardContentWidthDp()
    val widthPx = remember(density, contentWidth) {
        with(density) { contentWidth.roundToPx() }
    }
    val coverUrl = remember(video.cover, widthPx) {
        Formatters.sizedImageUrl(video.cover, widthPx, widthPx * 9 / 16)
    }
    val onClick = remember(video.bvid, onOpenVideo) { { onOpenVideo(video.bvid) } }

    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (video.durationText.isNotBlank()) {
                Text(
                    text = video.durationText,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    color = Color.White,
                )
            }
        }
        Text(
            text = video.title,
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ForwardBox(
    item: DynamicItemUi,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    onOpenContent: (DynamicArticleCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(COUITheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        DynamicHeader(
            item = item,
            avatarSize = 24.dp,
            onClick = { if (item.authorMid > 0L) onOpenUser(item.authorMid) },
        )
        if (item.content.isNotBlank()) {
            Text(
                text = item.content,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = COUITheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val video = item.video
        when {
            video != null -> DynamicVideoCardView(
                video = video,
                onOpenVideo = onOpenVideo,
                modifier = Modifier.padding(top = 8.dp),
            )

            item.images.isNotEmpty() -> DynamicImageGrid(
                images = item.images.take(9),
                onOpenImage = onOpenImage,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item.article?.let { article ->
            ArticleLinkBox(
                article = article,
                onClick = { onOpenContent(article) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 专栏 / 图文长文的入口条：卡片里只留标题，正文在阅读页里展开。 */
@Composable
private fun ArticleLinkBox(
    article: DynamicArticleCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(COUITheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = article.title.ifBlank { "阅读全文" },
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = COUITheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "阅读",
            fontSize = 11.sp,
            color = COUITheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun DynamicImageGrid(
    images: List<DynamicImage>,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val columns = if (images.size == 2 || images.size == 4) 2 else 3
    val contentWidth = cardContentWidthDp()
    val cellPx = remember(density, columns, contentWidth) {
        with(density) { ((contentWidth - 8.dp) / columns).roundToPx() }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        images.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEachIndexed { columnIndex, image ->
                    val index = rowIndex * columns + columnIndex
                    val url = remember(image.url, cellPx) {
                        Formatters.sizedImageUrl(image.url, cellPx, cellPx)
                    }
                    val onClick = remember(images, index, onOpenImage) {
                        { onOpenImage(images, index) }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClick),
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun DynamicItemUi.displayTime(): String {
    if (timeSec > 0L) return Formatters.formatTimeAgo(timeSec)
    return pubTimeText
}

private fun DynamicItemUi.statsText(): String {
    val forward = Formatters.formatStat(forwardCount.toLong())
    val comment = Formatters.formatStat(commentCount.toLong())
    val like = Formatters.formatStat(likeCount.toLong())
    return "转发 $forward · 评论 $comment · 赞 $like"
}
