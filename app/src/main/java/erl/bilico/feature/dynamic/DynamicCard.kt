package erl.bilico.feature.dynamic

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicKind
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.util.Formatters
import erl.bilico.feature.comment.LikeFilledIcon
import erl.bilico.feature.comment.LikeOutlineIcon
import erl.bilico.ui.ExpandOrigin
import erl.bilico.ui.LocalTopBarBounds
import erl.bilico.ui.RowScrollState
import erl.bilico.ui.attachToRow
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.LoadingImage
import erl.bilico.ui.components.RichCommentText
import erl.bilico.ui.expandOrigin
import erl.bilico.ui.rememberExpandOrigin
import io.github.suqi8.coui.kmp.basic.CapsuleOutlineShape
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Share
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun DynamicCard(
    item: DynamicItemUi,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String, ExpandOrigin?) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    onOpenDynamic: (DynamicItemUi, ExpandOrigin?) -> Unit,
    modifier: Modifier = Modifier,
    isDetail: Boolean = false,
    onWatchLater: ((DynamicVideoCard) -> Unit)? = null,
    onOpenLive: (Long) -> Unit = {},
    currentMid: Long = 0L,
    onDelete: ((DynamicItemUi) -> Unit)? = null,
    onLike: ((DynamicItemUi) -> Unit)? = null,
    rowScrollState: RowScrollState? = null,
    rowKey: Any? = null,
) {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val linkBlue = if (isDark) Color(0xFF5CA8FF) else Color(0xFF1367B1)
    val video = item.video
    val live = item.live
    val cardCornerRadius = COUITheme.dimens.card.cornerRadius
    val topBar = LocalTopBarBounds.current
    val canExpandCard = !isDetail && live == null && (
        video != null || ((item.article?.articleId ?: 0L) <= 0L && item.dynamicId.isNotBlank())
    )

    val cardOrigin = if (canExpandCard) {
        rememberExpandOrigin(cornerRadius = cardCornerRadius).also { origin ->
            SideEffect {
                origin.attachToRow(rowScrollState, rowKey, topBar)
            }
        }
    } else {
        null
    }

    // 视频动态整张卡片都进播放页，开播动态整张卡片都进直播间：只有头像、点赞这类显式控件才拦下点击。
    val onCardClick: () -> Unit = {
        when {
            live != null -> onOpenLive(live.roomId)
            video != null -> onOpenVideo(video.bvid, cardOrigin)
            else -> onOpenDynamic(item, cardOrigin)
        }
    }

    val canDelete = currentMid > 0L && item.authorMid == currentMid && item.dynamicId.isNotBlank() && onDelete != null
    val pressAnchor = rememberPreciseClickState()
    var longPressMenuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val menuItems = rememberDynamicMenuItems(
        item = item,
        onWatchLater = onWatchLater,
        onDelete = if (canDelete) { { confirmDelete = true } } else null,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (canDelete) Modifier.preciseClickAnchor(pressAnchor) else Modifier),
    ) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (cardOrigin != null) Modifier.expandOrigin(cardOrigin) else Modifier)
            .clip(SquircleShape(cardCornerRadius)),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = if (!isDetail) onCardClick else null,
        onLongPress = if (canDelete) { { longPressMenuExpanded = true } } else null,
    ) {
        DynamicHeader(
            item = item,
            avatarSize = 40.dp,
            onClick = { if (item.authorMid > 0L) onOpenUser(item.authorMid) else onCardClick() },
            menuItems = menuItems,
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

        // 充电专属：正文被锁时顶在正文位置；视频等自带主体的动态只有拿到真实锁定面板才展示，避免封面顶出空白占位。
        val locked = item.locked
        if (locked != null) {
            ChargeLockedPanel(locked = locked, modifier = Modifier.padding(top = 8.dp))
        } else if (item.pendingLocked && item.isEmptyLockedShell()) {
            ChargeLockedPlaceholder(modifier = Modifier.padding(top = 8.dp))
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
                showTitle = !isDuplicateVideoTitle(item.title, video.title) &&
                    !isDuplicateVideoTitle(content, video.title),
                externalOrigin = cardOrigin,
                rowScrollState = rowScrollState,
                rowKey = rowKey,
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
                rowScrollState = rowScrollState,
                rowKey = rowKey,
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

        // 点赞、评论、转发：左右各贴到正文边，中间那组真正居中。时间已经挪到昵称下面，这里不再重复。
        val context = LocalContext.current
        val likedColor = COUITheme.colorScheme.primary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                DynamicStatAction(
                    icon = if (item.liked) LikeFilledIcon else LikeOutlineIcon,
                    label = if (item.liked) "已赞" else "赞",
                    count = item.likeCount,
                    tint = if (item.liked) likedColor else null,
                    onClick = { onLike?.invoke(item) },
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DynamicStatAction(
                    icon = COUIIcons.Community,
                    label = "评论",
                    count = item.commentCount,
                    onClick = { onOpenDynamic(item, cardOrigin) },
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                DynamicStatAction(
                    icon = COUIIcons.Share,
                    label = "转发",
                    count = item.forwardCount,
                    onClick = { shareDynamic(context, item) },
                )
            }
        }
    }

    if (canDelete) {
        CouiPopupMenu(
            show = longPressMenuExpanded,
            onDismiss = { longPressMenuExpanded = false },
            items = menuItems,
            preciseClickState = pressAnchor,
        )
    }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除动态",
            message = "确定删除这条动态吗？删除后无法恢复。",
            confirmText = "删除",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete?.invoke(item)
            },
        )
    }
}

/** 卡片底部的单个统计按钮：图标 22dp、计数 15sp，整块可点，按下是一颗胶囊。 */
@Composable
private fun DynamicStatAction(
    icon: ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val resolvedTint = tint ?: COUITheme.colorScheme.onSurfaceSecondary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 438.65f),
        label = "statPress",
    )
    // 浅色压一层黑，深色压一层白，和 COUI 按钮按下的浓度一致。
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val pressColor = if (isDark) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.12f)
    val capsule = remember { CapsuleOutlineShape() }
    Row(
        modifier = Modifier
            .clip(capsule)
            .drawBehind {
                if (pressAlpha > 0f) {
                    drawRect(color = pressColor, alpha = pressAlpha)
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            // 横向不留内边距：左边那组的图标正好压在正文左边缘上，右边那组贴到右边缘。
            .padding(vertical = 8.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = resolvedTint,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = Formatters.formatStat(count.toLong()),
            fontSize = 15.sp,
            color = resolvedTint,
            maxLines = 1,
        )
    }
}

@Composable
private fun DynamicVideoCardView(
    video: DynamicVideoCard,
    onOpenVideo: (String, ExpandOrigin?) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    externalOrigin: ExpandOrigin? = null,
    rowScrollState: RowScrollState? = null,
    rowKey: Any? = null,
) {
    val density = LocalDensity.current
    val contentWidth = cardContentWidthDp()
    val widthPx = remember(density, contentWidth) {
        with(density) { contentWidth.roundToPx() }
    }
    val coverUrl = remember(video.cover, widthPx) {
        Formatters.sizedImageUrl(video.cover, widthPx, widthPx * 9 / 16)
    }
    val topBar = LocalTopBarBounds.current
    val ownOrigin = if (externalOrigin == null) {
        rememberExpandOrigin(cornerRadius = 10.dp).also { origin ->
            SideEffect {
                origin.attachToRow(rowScrollState, rowKey, topBar)
            }
        }
    } else {
        null
    }
    val activeOrigin = externalOrigin ?: ownOrigin
    val onClick = remember(video.bvid, activeOrigin, onOpenVideo) {
        { onOpenVideo(video.bvid, activeOrigin) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (ownOrigin != null) Modifier.expandOrigin(ownOrigin) else Modifier)
            .clip(SquircleShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(SquircleShape(10.dp)),
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
                        .clip(SquircleShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    color = Color.White,
                )
            }
        }
        if (showTitle && video.title.isNotBlank()) {
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
}

@Composable
private fun ForwardBox(
    item: DynamicItemUi,
    isDark: Boolean,
    linkBlue: Color,
    isDetail: Boolean,
    onOpenUser: (Long) -> Unit,
    onOpenVideo: (String, ExpandOrigin?) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    onOpenDynamic: (DynamicItemUi, ExpandOrigin?) -> Unit,
    rowScrollState: RowScrollState? = null,
    rowKey: Any? = null,
    modifier: Modifier = Modifier,
) {
    val shape = SquircleShape(10.dp)
    val bgColor = if (isDark) Color(0xFF2B2D31) else Color(0xFFF2F3F5)
    val borderColor = if (isDark) Color(0xFF3A3D42) else Color(0x14000000)
    val topBar = LocalTopBarBounds.current
    val canExpandForward = item.video != null || ((item.article?.articleId ?: 0L) <= 0L && item.dynamicId.isNotBlank())
    val forwardOrigin = if (canExpandForward) {
        rememberExpandOrigin(cornerRadius = 10.dp).also { origin ->
            SideEffect {
                origin.attachToRow(rowScrollState, rowKey, topBar)
            }
        }
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (forwardOrigin != null) Modifier.expandOrigin(forwardOrigin) else Modifier)
            .clip(shape)
            .background(bgColor)
            .border(width = 0.5.dp, color = borderColor, shape = shape)
            .clickable { onOpenDynamic(item, forwardOrigin) }
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
                        if (item.authorMid > 0L) onOpenUser(item.authorMid) else onOpenDynamic(item, forwardOrigin)
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
                showTitle = !isDuplicateVideoTitle(item.title, video.title) &&
                    !isDuplicateVideoTitle(item.content, video.title),
                rowScrollState = rowScrollState,
                rowKey = rowKey,
            )

            item.images.isNotEmpty() -> DynamicImageGrid(
                images = item.images.take(9),
                onOpenImage = onOpenImage,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 转发或正文已写出完整标题（常见「原标题 | 作者名」仅尾部多了作者后缀）时，不再在封面下方重复打印视频标题。 */
private fun isDuplicateVideoTitle(text: String, videoTitle: String): Boolean {
    val a = text.trim()
    val b = videoTitle.trim()
    if (a.isEmpty() || b.isEmpty()) return false
    if (a.equals(b, ignoreCase = true)) return true
    if (a.length >= 6 && b.startsWith(a, ignoreCase = true)) return true
    if (b.length >= 6 && a.startsWith(b, ignoreCase = true)) return true
    val baseTitle = b.substringBeforeLast('|').trim()
    if (baseTitle != b && baseTitle.length >= 6) {
        if (a.equals(baseTitle, ignoreCase = true) ||
            baseTitle.startsWith(a, ignoreCase = true) ||
            a.startsWith(baseTitle, ignoreCase = true)
        ) {
            return true
        }
    }
    return (a.length >= 8 && b.contains(a, ignoreCase = true)) ||
        (b.length >= 8 && a.contains(b, ignoreCase = true))
}

internal fun DynamicItemUi.displayTime(): String {
    if (timeSec > 0L) return Formatters.formatTimeAgo(timeSec)
    return pubTimeText
}
