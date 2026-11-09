package erl.bilico.feature.dynamic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.util.Formatters
import erl.bilico.feature.comment.PixelSerialText
import erl.bilico.feature.comment.parseCommentColor
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 动态卡片头部：头像（带头像框）、昵称（大会员粉色）、右侧装扮名牌与粉丝编号，
 * 最右是三个点菜单（稍后再看 / 分享动态）。
 */
@Composable
internal fun DynamicHeader(
    item: DynamicItemUi,
    avatarSize: Dp,
    onClick: () -> Unit,
    onWatchLater: ((DynamicVideoCard) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val defaultNameColor = COUITheme.colorScheme.onSurface
    val nameColor = remember(item.authorNameColor, defaultNameColor) {
        parseCommentColor(item.authorNameColor) ?: defaultNameColor
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DynamicAvatar(item = item, size = avatarSize, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 昵称先吃满这一行剩下的宽度，名牌跟在它后面；320dp 的屏上 14sp 才放得下
                // 「龙哥哥今天又鸽了」这种八字的昵称。
                Text(
                    text = item.authorName.ifBlank { "未知用户" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick),
                )
                if (item.nameplateUrl.isNotBlank() || item.nameplateNumber.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    DynamicNameplate(item = item)
                }
            }
            val time = item.displayTime()
            val hasBadge = item.authorBadgeIcon.isNotBlank() || item.authorBadge.isNotBlank()
            if (time.isNotBlank() || hasBadge) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (time.isNotBlank()) {
                        Text(
                            text = time,
                            fontSize = 11.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                        )
                    }
                    DynamicAuthorBadge(item = item)
                }
            }
        }
        DynamicMoreButton(
            item = item,
            onWatchLater = onWatchLater,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** 昵称下方的小角标（如「充电专属」）：接口给的是小图，取不到就退回文字。 */
@Composable
private fun DynamicAuthorBadge(item: DynamicItemUi) {
    val iconUrl = item.authorBadgeIcon
    if (iconUrl.isNotBlank()) {
        AsyncImage(
            model = iconUrl,
            contentDescription = item.authorBadge.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(start = 6.dp)
                .height(14.dp),
        )
    } else if (item.authorBadge.isNotBlank()) {
        Text(
            text = item.authorBadge,
            fontSize = 11.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun DynamicAvatar(
    item: DynamicItemUi,
    size: Dp,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val avatarPx = with(density) { size.roundToPx() }
    val pendantSize = size * 1.45f
    val pendantPx = with(density) { pendantSize.roundToPx() }
    val avatarUrl = remember(item.authorFace, avatarPx) {
        Formatters.sizedImageUrl(item.authorFace, avatarPx * 3, avatarPx * 3)
    }
    val pendantUrl = remember(item.authorPendant, pendantPx) {
        Formatters.sizedImageUrl(item.authorPendant, pendantPx * 2, pendantPx * 2)
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Avatar(
            url = avatarUrl,
            size = size * 0.86f,
            modifier = Modifier.clickable(onClick = onClick),
        )
        if (pendantUrl.isNotBlank()) {
            AsyncImage(
                model = pendantUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.requiredSize(pendantSize),
            )
        }
    }
}

/** 装扮卡片：编号压在卡片图左侧的留白里，和 PiliPlus 的排布一致。 */
@Composable
private fun DynamicNameplate(
    item: DynamicItemUi,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cardWidth = 72.dp
    val cardHeight = 24.dp
    val widthPx = with(density) { cardWidth.roundToPx() }
    val heightPx = with(density) { cardHeight.roundToPx() }
    val cardUrl = remember(item.nameplateUrl, widthPx, heightPx) {
        Formatters.sizedImageUrl(item.nameplateUrl, widthPx * 2, heightPx * 2)
    }
    val serialColor = remember(item.nameplateColor) {
        parseCommentColor(item.nameplateColor) ?: Color(0xFFE0584B)
    }

    Box(
        modifier = modifier.size(width = cardWidth, height = cardHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (cardUrl.isNotBlank()) {
            AsyncImage(
                model = cardUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (item.nameplateNumber.isNotBlank()) {
            Box(Modifier.padding(start = 2.dp)) {
                PixelSerialText(
                    text = item.nameplateNumber.uppercase(),
                    color = serialColor,
                    glyphHeight = 9.dp,
                )
            }
        }
    }
}

@Composable
private fun DynamicMoreButton(
    item: DynamicItemUi,
    onWatchLater: ((DynamicVideoCard) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = true },
                )
                .semantics { contentDescription = "更多" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = COUIIcons.More,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }

        CouiPopupMenu(
            show = expanded,
            onDismiss = { expanded = false },
            items = buildList {
                val video = item.video
                if (video != null && onWatchLater != null) {
                    add(CouiMenuItem("稍后再看", { onWatchLater(video) }))
                }
                add(CouiMenuItem("分享动态", { shareDynamic(context, item) }))
            },
        )
    }
}
