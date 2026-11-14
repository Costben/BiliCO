package erl.bilico.feature.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.Comment
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.LoadingImage
import erl.bilico.ui.components.RichCommentText
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 评论正文默认折叠到这个行数，超出才给一行「展开」。 */
private const val FOLDED_COMMENT_LINES = 8

/**
 * 标准评论卡片：严格参照目标样式还原——
 * - 左栏圆形头像，右栏顶部左侧为「完整不截断用户名 + 实心几何 LV 徽章」与第二行「日期 · IP属地」；
 * - 右上角为装扮角色立绘 + 方折数码号码牌（NO. / 032469）；
 * - 中部为高对比度评论正文与带图评论缩略图（支持点击看大图）；
 * - 底部操作栏左侧为左弯回复图标 +「回复」，右侧为点踩与点赞图标 + 计数；
 * - 下方圆角灰底卡片展示子回复预览与「共N条回复」。
 */
@Composable
internal fun BiliCommentItem(
    comment: Comment,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onReply: () -> Unit,
    onOpenReplies: ((Comment) -> Unit)? = null,
    onOpenUser: (Long) -> Unit = {},
    onOpenImage: (List<DynamicImage>, Int) -> Unit = { _, _ -> },
    isSubReply: Boolean = false,
    expandable: Boolean = true,
) {
    var expanded by remember(comment.id) { mutableStateOf(false) }
    // 正文真的被截断了才给「展开」；短评论不占这一行。
    var folded by remember(comment.id) { mutableStateOf(false) }
    // 不可折叠时整段正文始终铺满，点击正文也不再翻转展开态。
    val unfolded = !expandable || expanded
    val density = LocalDensity.current
    val avatarDp = if (isSubReply) 32.dp else 38.dp
    val avatarPx = with(density) { avatarDp.roundToPx() }
    val openUser = remember(comment.mid, onOpenUser) {
        { if (comment.mid > 0L) onOpenUser(comment.mid) }
    }
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val linkBlue = if (isDark) Color(0xFF5CA8FF) else Color(0xFF1367B1)
    // 楼中楼：白卡片上压一块中性浅灰，和列表底色拉开层次（取微博的配色）。
    val subBoxBg = if (isDark) Color(0xFF2B2D31) else Color(0xFFF2F3F5)
    // 灰块和白卡的亮度差只有 12 级，补一根极淡的描边，免得看着像没渲染出来。
    val subBoxOutline = if (isDark) Color(0xFF3A3D42) else Color(0xFFE6E8EC)
    val defaultNameColor = if (isDark) Color(0xFFB8BCC4) else Color(0xFF61666D)
    val nameColor = remember(comment.vipColorHex, defaultNameColor) {
        parseCommentColor(comment.vipColorHex) ?: defaultNameColor
    }
    val metaColor = if (isDark) Color(0xFF848994) else Color(0xFF9499A0)

    Row(modifier = Modifier.fillMaxWidth()) {
        CoverImage(
            url = Formatters.sizedImageUrl(comment.avatar, avatarPx, avatarPx),
            modifier = Modifier
                .size(avatarDp)
                .clip(CircleShape)
                .clickable(onClick = openUser),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    UserNameWithBadges(
                        userName = comment.userName,
                        level = comment.level,
                        isUp = comment.isUp,
                        nameColor = nameColor,
                        onClick = openUser,
                    )
                    val metaText = remember(comment.timeSec, comment.location) {
                        val datePart = Formatters.formatCommentDate(comment.timeSec)
                        when {
                            datePart.isNotEmpty() && comment.location.isNotEmpty() ->
                                "$datePart · ${comment.location}"
                            comment.location.isNotEmpty() -> comment.location
                            else -> datePart
                        }
                    }
                    if (metaText.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = metaText,
                            style = COUITheme.textStyles.footnote2.copy(
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                            ),
                            color = metaColor,
                        )
                    }
                }

                comment.sailing?.let { sailing ->
                    Spacer(Modifier.width(8.dp))
                    CommentSailingBadge(sailing = sailing, isDark = isDark)
                }
            }

            if (comment.content.isNotBlank() || comment.emotes.isNotEmpty() || comment.pictures.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                RichCommentText(
                    content = comment.content,
                    emotes = comment.emotes,
                    badgeLabel = if (!isSubReply && comment.isTop) "TOP" else null,
                    modifier = Modifier.then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier),
                    style = COUITheme.textStyles.body1.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = COUITheme.colorScheme.onSurface,
                    prefixColor = linkBlue,
                    maxLines = if (unfolded) Int.MAX_VALUE else FOLDED_COMMENT_LINES,
                    onTextLayout = { result ->
                        // 展开态下 maxLines 不再截断，别把标记清掉，否则「收起」会跟着消失。
                        if (expandable && !expanded && result.hasVisualOverflow != folded) {
                            folded = result.hasVisualOverflow
                        }
                    },
                )
                if (expandable && folded) {
                    Text(
                        text = if (expanded) "收起" else "展开",
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = COUITheme.colorScheme.primary,
                    )
                }
            }

            if (comment.pictures.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CommentPicturesGrid(
                    pictures = comment.pictures,
                    isSubReply = isSubReply,
                    onOpenImage = onOpenImage,
                )
            }

            Spacer(Modifier.height(10.dp))

            CommentActionBar(
                liked = comment.liked,
                disliked = comment.disliked,
                likeCount = comment.like,
                metaColor = metaColor,
                onReply = onReply,
                onDislike = onDislike,
                onLike = onLike,
            )

            if (!isSubReply && onOpenReplies != null &&
                (comment.replies.isNotEmpty() || comment.replyCount > 0)
            ) {
                val previewReplies = remember(comment.replies) { comment.replies.take(2) }
                val entryLabel = remember(comment.subReplyEntryText, comment.replyCount) {
                    comment.subReplyEntryText.ifBlank { "共${comment.replyCount}条回复" }
                }
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(subBoxBg)
                        .border(0.5.dp, subBoxOutline, RoundedCornerShape(8.dp))
                        .clickable { onOpenReplies(comment) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    previewReplies.forEach { reply ->
                        RichCommentText(
                            content = reply.content,
                            emotes = reply.emotes,
                            prefixUserName = reply.userName,
                            compactEmotes = true,
                            style = COUITheme.textStyles.body2.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 19.5.sp,
                            ),
                            color = COUITheme.colorScheme.onSurface,
                            prefixColor = linkBlue,
                            maxLines = 2,
                        )
                        if (reply.pictures.isNotEmpty()) {
                            CommentPicturesGrid(
                                pictures = reply.pictures,
                                isSubReply = true,
                                compactPreview = true,
                                onOpenImage = onOpenImage,
                            )
                        }
                    }
                    if (comment.replyCount > 0) {
                        Text(
                            text = entryLabel,
                            style = COUITheme.textStyles.footnote1.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = linkBlue,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentPicturesGrid(
    pictures: List<DynamicImage>,
    isSubReply: Boolean,
    compactPreview: Boolean = false,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
) {
    val density = LocalDensity.current
    if (pictures.size == 1 && !compactPreview) {
        val pic = pictures.first()
        val ratio = if (pic.width > 0 && pic.height > 0) {
            (pic.width.toFloat() / pic.height.toFloat()).coerceIn(0.65f, 1.85f)
        } else {
            4f / 3f
        }
        val thumbW = if (isSubReply) 136.dp else 164.dp
        val thumbPx = remember(density, thumbW) { with(density) { (thumbW * 1.5f).roundToPx() } }
        val url = remember(pic.url, thumbPx) { Formatters.widthImageUrl(pic.url, thumbPx) }
        Box(
            modifier = Modifier
                .width(thumbW)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenImage(pictures, 0) },
        ) {
            LoadingImage(
                url = url,
                contentDescription = "评论图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        return
    }

    val cellDp = when {
        compactPreview -> 58.dp
        isSubReply -> 74.dp
        else -> 86.dp
    }
    val cellPx = remember(density, cellDp) { with(density) { (cellDp * 1.5f).roundToPx() } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pictures.take(9).chunked(3).forEachIndexed { rowIdx, rowPics ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPics.forEachIndexed { colIdx, pic ->
                    val index = rowIdx * 3 + colIdx
                    val url = remember(pic.url, cellPx) {
                        Formatters.sizedImageUrl(pic.url, cellPx, cellPx)
                    }
                    Box(
                        modifier = Modifier
                            .size(cellDp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenImage(pictures, index) },
                    ) {
                        LoadingImage(
                            url = url,
                            contentDescription = "评论图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserNameWithBadges(
    userName: String,
    level: Int,
    isUp: Boolean,
    nameColor: Color,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val lvWidthSp = with(density) { 24.dp.toSp() }
    val lvHeightSp = with(density) { 12.dp.toSp() }
    val upWidthSp = with(density) { 21.dp.toSp() }

    val (annotated, inlineContent) = remember(userName, level, isUp, lvWidthSp, lvHeightSp, upWidthSp) {
        val inline = LinkedHashMap<String, InlineTextContent>()
        val builder = AnnotatedString.Builder()
        builder.append(userName)
        if (level > 0) {
            builder.append(" ")
            builder.appendInlineContent("lv_badge", "LV$level")
            inline["lv_badge"] = InlineTextContent(
                Placeholder(lvWidthSp, lvHeightSp, PlaceholderVerticalAlign.Center),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BiliLevelBadge(level = level)
                }
            }
        }
        if (isUp) {
            builder.append(" ")
            builder.appendInlineContent("up_badge", "UP")
            inline["up_badge"] = InlineTextContent(
                Placeholder(upWidthSp, lvHeightSp, PlaceholderVerticalAlign.Center),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BiliUpBadge()
                }
            }
        }
        builder.toAnnotatedString() to inline
    }

    Text(
        text = annotated,
        modifier = Modifier.clickable(onClick = onClick),
        style = COUITheme.textStyles.body2.copy(
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        ),
        color = nameColor,
        softWrap = true,
        overflow = TextOverflow.Visible,
        inlineContent = inlineContent,
    )
}

@Composable
private fun CommentActionBar(
    liked: Boolean,
    disliked: Boolean,
    likeCount: Int,
    metaColor: Color,
    onReply: () -> Unit,
    onDislike: () -> Unit,
    onLike: () -> Unit,
) {
    val activeColor = COUITheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onReply)
                .padding(vertical = 2.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ReplyCurveIcon,
                contentDescription = "回复",
                modifier = Modifier.size(15.dp),
                tint = metaColor,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "回复",
                style = COUITheme.textStyles.footnote1.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                ),
                color = metaColor,
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDislike)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (disliked) DislikeFilledIcon else DislikeOutlineIcon,
                contentDescription = if (disliked) "取消点踩" else "点踩",
                modifier = Modifier.size(16.dp),
                tint = if (disliked) activeColor else metaColor,
            )
        }

        Spacer(Modifier.width(14.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onLike)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (liked) LikeFilledIcon else LikeOutlineIcon,
                contentDescription = if (liked) "已赞" else "点赞",
                modifier = Modifier.size(16.dp),
                tint = if (liked) activeColor else metaColor,
            )
            if (likeCount > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = Formatters.formatStat(likeCount.toLong()),
                    style = COUITheme.textStyles.footnote1.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (liked) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (liked) activeColor else metaColor,
                )
            }
        }
    }
}
