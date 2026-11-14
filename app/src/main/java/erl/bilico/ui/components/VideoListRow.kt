package erl.bilico.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

private val RowCoverWidth = 132.dp

/** 空间投稿、收藏夹内容、稍后再看共用的横排视频卡：左侧封面，右侧标题与副标题。 */
@Composable
fun LibraryVideoRow(
    video: LibraryVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(8.dp),
        onClick = onClick,
    ) {
        LibraryVideoBody(video = video, trailing = trailing)
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
    trailing: (@Composable () -> Unit)? = null,
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
    // 有弹幕数的条目（稍后再看）把播放量与弹幕数单独放一行；其余页面保持「UP · xx播放」。
    val hasStatRow = video.danmakuText.isNotBlank()

    Row(modifier) {
        Box(
            modifier = Modifier
                .width(RowCoverWidth)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
            if (video.durationText.isNotBlank()) {
                Text(
                    text = video.durationText,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
            if (hasStatRow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (video.playText.isNotBlank()) {
                        StatLabel(COUIIcons.Play, video.playText)
                        Spacer(Modifier.width(10.dp))
                    }
                    StatLabel(COUIIcons.Messages, video.danmakuText)
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
