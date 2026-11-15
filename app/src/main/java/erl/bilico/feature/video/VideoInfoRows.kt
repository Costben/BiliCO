package erl.bilico.feature.video

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoEpisode
import erl.bilico.data.model.VideoPart
import erl.bilico.data.util.Formatters
import erl.bilico.feature.feed.MoreMenu
import erl.bilico.feature.feed.shareVideo
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.LoadingImage
import erl.bilico.ui.components.StatLabel
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

internal const val TITLE_COLLAPSED_LINES = 2
internal const val DESC_COLLAPSED_LINES = 2
internal val DESC_ICON_SIZE = 14.dp
internal val DESC_ICON_GAP = 5.dp

/** 量「…展开」时在右侧留出的余量，避免它正好顶到内容边缘。 */
internal val DESC_END_RESERVE = 8.dp

/** UP 主行：头像、名字与粉丝数，右侧是关注按钮。 */
@Composable
internal fun UpRow(
    detail: VideoDetail,
    followers: Long?,
    following: Boolean,
    avatarPx: Int,
    onOpenUser: (Long) -> Unit,
    onFollow: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = Formatters.sizedImageUrl(detail.upFace, avatarPx, avatarPx),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(enabled = detail.upMid > 0L) { onOpenUser(detail.upMid) },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = detail.upName.ifBlank { "未知 UP" },
                style = COUITheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = upMetaText(followers, detail.pubdate),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        FollowButton(following = following, onClick = onFollow)
    }
}

private fun upMetaText(followers: Long?, pubdate: Long): String = buildList {
    if (followers != null && followers > 0L) add("${Formatters.formatStat(followers)}粉丝")
    Formatters.formatTimeAgo(pubdate).takeIf { it.isNotBlank() }?.let(::add)
}.joinToString("  ")

@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Text(
        text = if (following) "已关注" else "关注",
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (following) scheme.surfaceContainer else scheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        style = COUITheme.textStyles.footnote1,
        fontWeight = FontWeight.Medium,
        color = if (following) scheme.onSurfaceSecondary else scheme.onPrimary,
    )
}

/**
 * 收起态简介：整段塞得进两行就原样返回，塞不下就裁到恰好放得下「…展开」。
 * 交给省略号自己截断的话，「展开」会被一起吃掉，只剩一个孤零零的省略号。
 */
internal fun collapsedDescText(
    measurer: TextMeasurer,
    preview: String,
    style: TextStyle,
    accent: Color,
    widthPx: Int,
): AnnotatedString {
    val maxWidth = widthPx.coerceAtLeast(1)

    fun fits(text: AnnotatedString): Boolean = !measurer.measure(
        text = text,
        style = style,
        maxLines = DESC_COLLAPSED_LINES,
        constraints = Constraints(maxWidth = maxWidth),
    ).hasVisualOverflow

    val plain = AnnotatedString(preview)
    if (fits(plain)) return plain

    fun withSuffix(count: Int) = buildAnnotatedString {
        append(preview.take(count))
        append("…")
        withStyle(SpanStyle(color = accent)) { append("展开") }
    }

    var low = 0
    var high = preview.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(withSuffix(mid))) low = mid else high = mid - 1
    }
    // 别把代理对劈开。
    val end = if (low in 1 until preview.length && Character.isLowSurrogate(preview[low])) low - 1 else low
    return withSuffix(end)
}

/** 简介下面那一栏：左边缩略图，右边标题、UP 行与播放弹幕数（三点菜单在右下角）。 */
@Composable
internal fun RelatedVideoRow(
    video: FeedVideo,
    onWatchLater: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverWidth = 152.dp
    val coverWidthPx = with(density) { coverWidth.roundToPx() }
    val coverHeightPx = coverWidthPx * 9 / 16
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .width(coverWidth)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            LoadingImage(
                url = Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx),
                modifier = Modifier.fillMaxSize(),
            )
            if (video.durationText.isNotBlank()) {
                Text(
                    text = video.durationText,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = coverWidth * 9f / 16f),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = video.title,
                style = COUITheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.upName.ifBlank { "未知 UP" },
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatLabel(COUIIcons.Play, video.playCountText)
                    if (video.danmakuText.isNotBlank()) {
                        Spacer(Modifier.width(10.dp))
                        StatLabel(COUIIcons.Messages, video.danmakuText)
                    }
                    Spacer(Modifier.weight(1f))
                    MoreMenu(
                        onWatchLater = onWatchLater,
                        onShare = { shareVideo(context, video) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun VideoAction(
    icon: ImageVector,
    label: String,
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) {
        COUITheme.colorScheme.primary
    } else {
        COUITheme.colorScheme.onSurfaceSecondary
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.height(3.dp))
        Text(text, fontSize = 11.sp, color = tint, maxLines = 1)
    }
}

@Composable
internal fun CollectionRow(
    index: Int,
    episode: VideoEpisode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) COUITheme.colorScheme.pressBackground else COUITheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            modifier = Modifier.width(28.dp),
            style = COUITheme.textStyles.footnote1,
            color = if (selected) {
                COUITheme.colorScheme.primary
            } else {
                COUITheme.colorScheme.onSurfaceSecondary
            },
        )
        Text(
            text = episode.title.ifBlank { "第 $index 集" },
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.body2,
            color = if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (episode.durationSec > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = Formatters.formatDuration(episode.durationSec),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

@Composable
internal fun PartRow(part: VideoPart, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) COUITheme.colorScheme.pressBackground else COUITheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "P${part.index}",
            modifier = Modifier.width(36.dp),
            style = COUITheme.textStyles.footnote1,
            color = if (selected) {
                COUITheme.colorScheme.primary
            } else {
                COUITheme.colorScheme.onSurfaceSecondary
            },
        )
        Text(
            text = part.title.ifBlank { "第 ${part.index} P" },
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.body2,
            color = if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (part.durationSec > 0) {
            Text(
                text = Formatters.formatDuration(part.durationSec),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}
