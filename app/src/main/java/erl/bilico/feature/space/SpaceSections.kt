@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.space

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.SpaceCollection
import erl.bilico.data.model.SpaceOpusUi
import erl.bilico.data.util.Formatters
import erl.bilico.feature.comment.LikeFilledIcon
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.nestedBlockColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 个人空间头部卡片内的直播间快捷跳转条。 */
@Composable
internal fun LiveRoomShortcutRow(
    isLive: Boolean,
    liveTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .background(nestedBlockColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isLive) Color(0xFFFF4D6A) else COUITheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (isLive) "直播中" else "直播间",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isLive) Color(0xFFFF4D6A) else COUITheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = liveTitle.ifBlank { "进入 TA 的直播间" },
            fontSize = 12.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isLive) "去观看" else "进入",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = COUITheme.colorScheme.primary,
            maxLines = 1,
        )
        Icon(
            imageVector = COUIIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = COUITheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun SpaceCollectionRow(
    collection: SpaceCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inset = 8.dp
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { 132.dp.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val coverUrl = remember(collection.cover, coverWidthPx) {
        Formatters.sizedImageUrl(collection.cover, coverWidthPx, coverHeightPx)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(inset),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 9f)
                    .clip(SquircleShape((CardDefaults.CornerRadius - inset).coerceAtLeast(0.dp), weight = CardDefaults.Weight)),
            ) {
                CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                Text(
                    text = if (collection.isSeason) "合集" else "系列",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(SquircleShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
                if (collection.total > 0) {
                    Text(
                        text = "${collection.total}个视频",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(SquircleShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 10.sp,
                        color = Color.White,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = collection.name,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (collection.description.isNotBlank()) {
                    Text(
                        text = collection.description,
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 根据列数返回瀑布流卡片间距（与推荐页保持一致）。 */
internal fun spaceOpusGridSpacing(columns: Int): Dp = when {
    columns >= 4 -> 6.dp
    columns == 3 -> 8.dp
    else -> 10.dp
}

@Composable
internal fun rememberOpusColWidthPx(columns: Int, spacing: Dp): Int {
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return remember(screenWidthDp, density, columns, spacing) {
        val cols = columns.coerceAtLeast(1)
        val totalGap = 24.dp + spacing * (cols - 1)
        with(density) { ((screenWidthDp.dp - totalGap) / cols.toFloat()).roundToPx() }
    }
}

@Composable
internal fun SpaceOpusWaterfallCard(
    opus: SpaceOpusUi,
    colWidthPx: Int,
    onClick: () -> Unit,
) {
    val aspect = remember(opus.coverWidth, opus.coverHeight) {
        if (opus.coverWidth > 0 && opus.coverHeight > 0) {
            (opus.coverWidth.toFloat() / opus.coverHeight.toFloat()).coerceIn(0.68f, 1.65f)
        } else {
            4f / 3f
        }
    }
    val coverUrl = remember(opus.coverUrl, colWidthPx, aspect) {
        if (opus.coverUrl.isBlank()) ""
        else Formatters.sizedImageUrl(opus.coverUrl, colWidthPx, (colWidthPx / aspect).toInt())
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (coverUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect),
                ) {
                    CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = opus.content.ifBlank { "图文动态" },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (opus.pubTime.isNotBlank() || (opus.likeText.isNotBlank() && opus.likeText != "0")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (opus.pubTime.isNotBlank()) {
                            Text(
                                text = opus.pubTime,
                                fontSize = 11.sp,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (opus.likeText.isNotBlank() && opus.likeText != "0") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = LikeFilledIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = COUITheme.colorScheme.onSurfaceSecondary,
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = opus.likeText,
                                    fontSize = 11.sp,
                                    color = COUITheme.colorScheme.onSurfaceSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
