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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicKind
import erl.bilico.data.model.DynamicLiveCard
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.util.Formatters
import erl.bilico.feature.comment.LikeOutlineIcon
import erl.bilico.ui.components.LoadingImage
import erl.bilico.ui.components.RichCommentText
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Share
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
    onWatchLater: ((DynamicVideoCard) -> Unit)? = null,
    onOpenLive: (Long) -> Unit = {},
) {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val linkBlue = if (isDark) Color(0xFF5CA8FF) else Color(0xFF1367B1)
    val video = item.video
    val live = item.live

    // 视频动态整张卡片都进播放页，开播动态整张卡片都进直播间：只有头像、点赞这类显式控件才拦下点击。
    val onCardClick: () -> Unit = {
        when {
            live != null -> onOpenLive(live.roomId)
            video != null -> onOpenVideo(video.bvid)
            else -> onOpenDynamic(item)
        }
    }

    val cardCornerRadius = COUITheme.dimens.card.cornerRadius
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCornerRadius)),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = if (!isDetail) onCardClick else null,
    ) {
        DynamicHeader(
            item = item,
            avatarSize = 40.dp,
            onClick = { if (item.authorMid > 0L) onOpenUser(item.authorMid) else onCardClick() },
            onWatchLater = onWatchLater,
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

        // 充电专属：正文被锁，接口只给一段锁定说明，面板顶在正文位置。
        if (item.chargeLocked) {
            val locked = item.locked
            if (locked != null) {
                ChargeLockedPanel(locked = locked, modifier = Modifier.padding(top = 8.dp))
            } else {
                ChargeLockedPlaceholder(modifier = Modifier.padding(top = 8.dp))
            }
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

        val forward = item.forward
        when {
            live != null -> DynamicLiveCardView(
                live = live,
                onOpenLive = onOpenLive,
                modifier = Modifier.padding(top = 8.dp),
            )

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

        if (!isDetail) {
            DynamicHotReplies(
                replies = item.hotReplies,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // 转评赞三个按钮：左右各贴到正文边，中间那组真正居中。时间已经挪到昵称下面，这里不再重复。
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                DynamicStatAction(
                    icon = COUIIcons.Share,
                    label = "转发",
                    count = item.forwardCount,
                    onClick = { shareDynamic(context, item) },
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DynamicStatAction(
                    icon = COUIIcons.Community,
                    label = "评论",
                    count = item.commentCount,
                    onClick = { onOpenDynamic(item) },
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                DynamicStatAction(
                    icon = LikeOutlineIcon,
                    label = "赞",
                    count = item.likeCount,
                    onClick = { onOpenDynamic(item) },
                )
            }
        }
    }
}

/** 卡片底部的单个统计按钮：图标 22dp、计数 15sp，整块可点。 */
@Composable
private fun DynamicStatAction(
    icon: ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
) {
    val tint = COUITheme.colorScheme.onSurfaceSecondary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            // 横向不留内边距：左边那组的图标正好压在正文左边缘上，右边那组贴到右边缘。
            .padding(vertical = 8.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = Formatters.formatStat(count.toLong()),
            fontSize = 15.sp,
            color = tint,
            maxLines = 1,
        )
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            LoadingImage(
                url = coverUrl,
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

/**
 * 开播动态：封面右上角是「N人看过」与「直播中」，左下角是分区名，封面下面是直播间标题。
 * 整块点进直播间。
 */
@Composable
private fun DynamicLiveCardView(
    live: DynamicLiveCard,
    onOpenLive: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val contentWidth = cardContentWidthDp()
    val widthPx = remember(density, contentWidth) {
        with(density) { contentWidth.roundToPx() }
    }
    val coverUrl = remember(live.cover, widthPx) {
        Formatters.sizedImageUrl(live.cover, widthPx, widthPx * 9 / 16)
    }
    val onClick = remember(live.roomId, onOpenLive) { { onOpenLive(live.roomId) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            LoadingImage(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (live.watchedText.isNotBlank()) {
                    Text(
                        text = live.watchedText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x66000000))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                if (live.living) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFB7299))
                            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveSignalBars()
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "直播中",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (live.areaName.isNotBlank()) {
                Text(
                    text = live.areaName,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    style = TextStyle(
                        shadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 1f), blurRadius = 4f),
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (live.title.isNotBlank()) {
            Text(
                text = live.title,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「直播中」角标左边那三根信号格：由矮到高，纯装饰。 */
@Composable
private fun LiveSignalBars() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        listOf(4.dp, 7.dp, 10.dp).forEach { height ->
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White),
            )
        }
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
            LoadingImage(
                url = url,
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
                        LoadingImage(
                            url = url,
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

internal fun DynamicItemUi.displayTime(): String {
    if (timeSec > 0L) return Formatters.formatTimeAgo(timeSec)
    return pubTimeText
}
