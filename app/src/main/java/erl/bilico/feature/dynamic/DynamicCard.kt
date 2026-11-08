package erl.bilico.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicKind
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.RichCommentText
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
    onOpenDynamic: (DynamicItemUi) -> Unit,
    modifier: Modifier = Modifier,
    isDetail: Boolean = false,
) {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val linkBlue = if (isDark) Color(0xFF5CA8FF) else Color(0xFF1367B1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isDetail) Modifier.clickable { onOpenDynamic(item) } else Modifier),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        DynamicHeader(
            item = item,
            avatarSize = 40.dp,
            onClick = { if (item.authorMid > 0L) onOpenUser(item.authorMid) else if (!isDetail) onOpenDynamic(item) },
        )

        if (item.title.isNotBlank() && item.title != item.content) {
            Text(
                text = item.title,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = COUITheme.colorScheme.onSurface,
                maxLines = if (isDetail) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 正文图还在取的时候不画那串「[图片]」，直接把位置让给下面的占位块。
        val pendingImages = item.pendingImages
        val content = if (pendingImages) item.content.withoutImagePlaceholders() else item.content

        if (content.isNotBlank() || item.emotes.isNotEmpty()) {
            RichCommentText(
                content = content,
                emotes = item.emotes,
                modifier = Modifier.padding(top = 8.dp),
                style = COUITheme.textStyles.body1.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                ),
                color = COUITheme.colorScheme.onSurface,
                prefixColor = linkBlue,
                maxLines = if (isDetail) Int.MAX_VALUE else 8,
            )
        }

        val video = item.video
        val forward = item.forward
        when {
            video != null -> DynamicVideoCardView(
                video = video,
                onOpenVideo = onOpenVideo,
                modifier = Modifier.padding(top = 8.dp),
            )

            forward != null -> ForwardBox(
                item = forward,
                isDark = isDark,
                linkBlue = linkBlue,
                isDetail = isDetail,
                onOpenUser = onOpenUser,
                onOpenVideo = onOpenVideo,
                onOpenImage = onOpenImage,
                onOpenDynamic = onOpenDynamic,
                modifier = Modifier.padding(top = 8.dp),
            )

            pendingImages -> OpusImagePlaceholder(modifier = Modifier.padding(top = 8.dp))

            item.images.isNotEmpty() -> DynamicImageGrid(
                images = item.images,
                onOpenImage = onOpenImage,
                modifier = Modifier.padding(top = 8.dp),
            )

            item.kind == DynamicKind.UNSUPPORTED -> Text(
                text = "暂不支持的动态类型",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
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
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick),
        ) {
            Avatar(
                url = remember(item.authorFace, avatarSize) {
                    val px = avatarSize.value.toInt()
                    Formatters.sizedImageUrl(item.authorFace, px * 3, px * 3)
                },
                size = avatarSize,
            )
        }
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
    isDark: Boolean,
    linkBlue: Color,
    isDetail: Boolean,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    onOpenDynamic: (DynamicItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val bgColor = if (isDark) Color(0x22FFFFFF) else Color(0xFFF2F3F5)
    val borderColor = if (isDark) Color(0x1FFFFFFF) else Color(0x14000000)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(width = 0.5.dp, color = borderColor, shape = shape)
            .clickable { onOpenDynamic(item) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (item.authorName.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "@${item.authorName}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = linkBlue,
                    modifier = Modifier.clickable {
                        if (item.authorMid > 0L) onOpenUser(item.authorMid) else onOpenDynamic(item)
                    },
                )
                val time = item.displayTime()
                if (time.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = time,
                        fontSize = 11.5.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (item.title.isNotBlank() && item.title != item.content) {
            Text(
                text = item.title,
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = COUITheme.colorScheme.onSurface,
                maxLines = if (isDetail) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.content.isNotBlank() || item.emotes.isNotEmpty()) {
            RichCommentText(
                content = item.content,
                emotes = item.emotes,
                modifier = Modifier.padding(top = 6.dp),
                style = COUITheme.textStyles.body2.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.5.sp,
                ),
                color = COUITheme.colorScheme.onSurface,
                prefixColor = linkBlue,
                maxLines = if (isDetail) Int.MAX_VALUE else 6,
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

/**
 * 长文型动态的正文图还在取时占位：形状和后面的真实九宫格一致（三列方格），
 * 取到图直接换成同一块，卡片不会先闪一串「[图片]」再重排。
 */
@Composable
private fun OpusImagePlaceholder(modifier: Modifier = Modifier) {
    val cell = COUITheme.colorScheme.surfaceContainer
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cell),
                    )
                }
            }
        }
    }
}

/** feed 里长文图片的占位写法。 */
private const val IMAGE_PLACEHOLDER = "[图片]"

/** 去掉占位标记，顺便把因此空出来的行收掉。 */
private fun String.withoutImagePlaceholders(): String =
    replace(IMAGE_PLACEHOLDER, "")
        .lines()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

@Composable
private fun DynamicImageGrid(
    images: List<DynamicImage>,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val contentWidth = cardContentWidthDp()

    if (images.size == 1) {
        val image = images.first()
        val ratio = if (image.width > 0 && image.height > 0) {
            (image.width.toFloat() / image.height.toFloat()).coerceIn(0.65f, 1.85f)
        } else {
            4f / 3f
        }
        val singleWidthDp = (contentWidth * 0.68f).coerceAtLeast(160.dp)
        val singleWidthPx = remember(density, singleWidthDp) {
            with(density) { singleWidthDp.roundToPx() }
        }
        val url = remember(image.url, singleWidthPx) {
            Formatters.widthImageUrl(image.url, singleWidthPx)
        }
        Box(
            modifier = modifier
                .width(singleWidthDp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenImage(images, 0) },
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        return
    }

    val columns = if (images.size == 2 || images.size == 4) 2 else 3
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
