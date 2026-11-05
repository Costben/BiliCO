package erl.bilico.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicKind
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun DynamicCard(
    item: DynamicItemUi,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
) {
    val timeText = remember(item.timeSec, item.pubTimeText) { item.displayTime() }
    val statsText = remember(item.forwardCount, item.commentCount, item.likeCount) {
        item.statsText()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        DynamicHeader(
            name = item.authorName,
            face = item.authorFace,
            timeText = timeText,
            avatarSize = 40.dp,
        )

        if (item.content.isNotBlank()) {
            Text(
                text = item.content,
                modifier = Modifier.padding(top = 8.dp),
                style = COUITheme.textStyles.body2,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (item.kind) {
            DynamicKind.VIDEO -> item.video?.let {
                DynamicVideoCardView(
                    video = it,
                    onOpenVideo = onOpenVideo,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            DynamicKind.IMAGES -> if (item.images.isNotEmpty()) {
                DynamicImageGrid(
                    images = item.images,
                    onOpenImage = onOpenImage,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            DynamicKind.FORWARD -> item.forward?.let {
                ForwardBox(
                    item = it,
                    onOpenVideo = onOpenVideo,
                    onOpenImage = onOpenImage,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            DynamicKind.UNSUPPORTED -> Text(
                text = "暂不支持的动态类型",
                modifier = Modifier.padding(top = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )

            DynamicKind.TEXT -> Unit
        }

        Text(
            text = statsText,
            modifier = Modifier.padding(top = 10.dp),
            style = COUITheme.textStyles.footnote2,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@Composable
private fun DynamicHeader(
    name: String,
    face: String,
    timeText: String,
    avatarSize: androidx.compose.ui.unit.Dp,
) {
    val density = LocalDensity.current
    val avatarUrl = remember(face, avatarSize, density) {
        with(density) {
            Formatters.sizedImageUrl(face, avatarSize.roundToPx(), avatarSize.roundToPx())
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(avatarSize).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                text = name.ifBlank { "未知用户" },
                style = COUITheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (timeText.isNotBlank()) {
                Text(
                    text = timeText,
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val coverUrl = remember(video.cover, screenWidthDp) {
        val widthPx = with(density) { (screenWidthDp.dp - 32.dp).roundToPx() }
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
                    style = COUITheme.textStyles.footnote2,
                    color = Color.White,
                )
            }
        }
        Text(
            text = video.title,
            modifier = Modifier.padding(top = 8.dp),
            style = COUITheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ForwardBox(
    item: DynamicItemUi,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(COUITheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        val timeText = remember(item.timeSec, item.pubTimeText) { item.displayTime() }
        DynamicHeader(
            name = item.authorName,
            face = item.authorFace,
            timeText = timeText,
            avatarSize = 24.dp,
        )
        if (item.content.isNotBlank()) {
            Text(
                text = item.content,
                modifier = Modifier.padding(top = 6.dp),
                style = COUITheme.textStyles.footnote1,
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
    }
}

@Composable
private fun DynamicImageGrid(
    images: List<DynamicImage>,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val columns = if (images.size == 2 || images.size == 4) 2 else 3

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        images.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEachIndexed { columnIndex, image ->
                    val index = rowIndex * columns + columnIndex
                    val cellPx = with(density) {
                        ((screenWidthDp.dp - 32.dp - 8.dp) / columns).roundToPx()
                    }
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
