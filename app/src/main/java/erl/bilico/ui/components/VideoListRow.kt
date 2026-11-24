package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

private val RowCoverWidth = 132.dp

/** 封面圆角按外轮廓半径减去内边距收进去，两条弧才同心；间距不小于外圆角时退回直角。 */
private fun coverCorner(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(0.dp)

/** 空间投稿、收藏夹内容、稍后再看共用的横排视频卡：左侧封面，右侧标题与副标题。 */
@Composable
internal fun LibraryVideoRow(
    video: LibraryVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    longPressItems: List<CouiMenuItem> = emptyList(),
) {
    val inset = 8.dp
    // 长按菜单按手指位置弹出，和推荐流卡片用同一套锚点。
    val pressState = rememberPreciseClickState()
    var menuExpanded by remember { mutableStateOf(false) }
    val onLongPress: (() -> Unit)? = if (longPressItems.isEmpty()) {
        null
    } else {
        { menuExpanded = true }
    }
    Box(modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .preciseClickAnchor(pressState),
            insideMargin = PaddingValues(inset),
            onClick = onClick,
            onLongPress = onLongPress,
        ) {
            LibraryVideoBody(
                video = video,
                coverInset = inset,
                trailing = trailing,
                statTrailing = if (longPressItems.isNotEmpty()) {
                    { LibraryMoreMenuButton(items = longPressItems) }
                } else {
                    null
                },
            )
        }
        if (longPressItems.isNotEmpty()) {
            CouiPopupMenu(
                show = menuExpanded,
                onDismiss = { menuExpanded = false },
                items = longPressItems,
                preciseClickState = pressState,
            )
        }
    }
}

/**
 * 卡片里的内容本身，不带 Card 和点击：稍后再看要在外面套 COUI 的通知滑动盒，
 * 卡片的材质与内边距由外面那层负责。
 */
@Composable
fun LibraryVideoBody(
    video: LibraryVideo,
    modifier: Modifier = Modifier,
    coverInset: Dp = 8.dp,
    coverCornerRadius: Dp = CardDefaults.CornerRadius,
    coverCornerWeight: Float = CardDefaults.Weight,
    trailing: (@Composable () -> Unit)? = null,
    statTrailing: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { RowCoverWidth.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val coverUrl = remember(video.cover, coverWidthPx) {
        Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx)
    }
    val progressFraction = remember(video.progressSec, video.durationSec) {
        if (video.durationSec > 0 && video.progressSec > 0) {
            (video.progressSec.toFloat() / video.durationSec).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    // 有弹幕数或投稿时间的条目把播放量、弹幕数单独放一行；其余页面保持「UP · xx播放」。
    val hasStatRow = video.danmakuText.isNotBlank() || video.pubTimeText.isNotBlank()

    Row(modifier) {
        Box(
            modifier = Modifier
                .width(RowCoverWidth)
                .aspectRatio(16f / 9f)
                .clip(
                    SquircleShape(
                        coverCorner(coverCornerRadius, coverInset),
                        weight = coverCornerWeight,
                    ),
                ),
        ) {
            CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
            if (video.durationText.isNotBlank()) {
                Text(
                    text = video.durationText,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(SquircleShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progressFraction)
                        .height(3.dp)
                        .background(COUITheme.colorScheme.primary),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = video.title,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onSurface,
                // 带投稿时间的列表（空间）固定留两行标题，下面两行才不会随标题长短跳动。
                minLines = if (video.pubTimeText.isNotBlank()) 2 else 1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.pubTimeText.isNotBlank()) {
                Text(
                    text = video.pubTimeText,
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val subtitle = buildString {
                if (video.upName.isNotBlank()) append(video.upName)
                if (!hasStatRow && video.playText.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(video.playText).append("播放")
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasStatRow || statTrailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasStatRow) {
                        if (video.playText.isNotBlank()) {
                            StatLabel(COUIIcons.Play, video.playText)
                        }
                        if (video.danmakuText.isNotBlank()) {
                            if (video.playText.isNotBlank()) Spacer(Modifier.width(10.dp))
                            StatLabel(DanmakuStatIcon, video.danmakuText)
                        }
                    }
                    if (statTrailing != null) {
                        Spacer(Modifier.weight(1f))
                        statTrailing()
                    }
                }
            }
        }

        if (trailing != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(start = 6.dp, bottom = 2.dp),
            ) {
                trailing()
            }
        }
    }
}

@Composable
private fun LibraryMoreMenuButton(items: List<CouiMenuItem>) {
    val scheme = COUITheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(22.dp)
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
                modifier = Modifier.size(18.dp),
                tint = scheme.onSurfaceSecondary,
            )
        }

        CouiPopupMenu(
            show = expanded,
            onDismiss = { expanded = false },
            items = items,
            alignment = PopupPositionProvider.Align.End,
        )
    }
}
