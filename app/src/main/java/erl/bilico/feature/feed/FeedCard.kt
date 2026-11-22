package erl.bilico.feature.feed

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import io.github.suqi8.coui.kmp.squircle.SquircleShape
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.settings.CoverAspect
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.DanmakuStatIcon
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/** 不同列数下的卡片排版密度参数，保证 1~4 列都清晰不拥挤。 */
private data class FeedCardMetrics(
    val edgeInset: Dp,
    val chipGap: Dp,
    val chipFontSize: TextUnit,
    val chipIconSize: Dp,
    val chipPaddingH: Dp,
    val showDanmakuOnCover: Boolean,
    val infoPadding: PaddingValues,
    val titleFontSize: TextUnit,
    val titleLineHeight: TextUnit,
    val rowSpacer: Dp,
    val showAvatar: Boolean,
    val avatarSize: Dp,
    val avatarGap: Dp,
    val upFontSize: TextUnit,
    val moreButtonSize: Dp,
    val moreIconSize: Dp,
) {
    companion object {
        fun forColumns(columns: Int): FeedCardMetrics = when {
            columns >= 4 -> FeedCardMetrics(
                edgeInset = 2.dp,
                chipGap = 3.dp,
                chipFontSize = 8.sp,
                chipIconSize = 8.dp,
                chipPaddingH = 3.dp,
                showDanmakuOnCover = false,
                infoPadding = PaddingValues(start = 5.dp, end = 2.dp, top = 5.dp, bottom = 4.dp),
                titleFontSize = 11.sp,
                titleLineHeight = 14.sp,
                rowSpacer = 3.dp,
                showAvatar = false,
                avatarSize = 14.dp,
                avatarGap = 4.dp,
                upFontSize = 10.sp,
                moreButtonSize = 16.dp,
                moreIconSize = 14.dp,
            )

            columns == 3 -> FeedCardMetrics(
                edgeInset = 2.dp,
                chipGap = 5.dp,
                chipFontSize = 9.sp,
                chipIconSize = 9.dp,
                chipPaddingH = 3.dp,
                showDanmakuOnCover = false,
                infoPadding = PaddingValues(start = 7.dp, end = 2.dp, top = 6.dp, bottom = 5.dp),
                titleFontSize = 13.sp,
                titleLineHeight = 17.sp,
                rowSpacer = 4.dp,
                showAvatar = true,
                avatarSize = 14.dp,
                avatarGap = 4.dp,
                upFontSize = 11.sp,
                moreButtonSize = 18.dp,
                moreIconSize = 16.dp,
            )

            else -> FeedCardMetrics(
                edgeInset = 3.dp,
                chipGap = 10.dp,
                chipFontSize = 10.sp,
                chipIconSize = 10.dp,
                chipPaddingH = 4.dp,
                showDanmakuOnCover = true,
                infoPadding = PaddingValues(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 6.dp),
                titleFontSize = 15.sp,
                titleLineHeight = 21.sp,
                rowSpacer = 6.dp,
                showAvatar = true,
                avatarSize = 18.dp,
                avatarGap = 6.dp,
                upFontSize = 12.sp,
                moreButtonSize = 20.dp,
                moreIconSize = 18.dp,
            )
        }
    }
}

/** 推荐流与排行榜共用的视频卡片。 */
@Composable
internal fun FeedCard(
    video: FeedVideo,
    aspect: CoverAspect,
    coverWidthPx: Int,
    coverHeightPx: Int,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    onOpen: () -> Unit,
    onWatchLater: () -> Unit,
) {
    val metrics = remember(columns) { FeedCardMetrics.forColumns(columns) }
    val coverUrl = remember(video.cover, coverWidthPx, coverHeightPx) {
        Formatters.sizedImageUrl(video.cover, coverWidthPx, coverHeightPx)
    }
    val density = LocalDensity.current
    val avatarUrl = remember(video.upFace, density, metrics.avatarSize) {
        val pixels = with(density) { metrics.avatarSize.roundToPx() }.coerceAtLeast(1)
        val thumbnailPx = ((pixels + 31) / 32) * 32
        Formatters.sizedImageUrl(video.upFace, thumbnailPx, thumbnailPx)
    }
    val context = LocalContext.current
    val scheme = COUITheme.colorScheme
    // 长按卡片在手指位置弹同一个三点菜单，不跳到播放页也不走卡片点击。
    val pressState = rememberPreciseClickState()
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .preciseClickAnchor(pressState),
            insideMargin = PaddingValues(0.dp),
            onClick = onOpen,
            onLongPress = { menuExpanded = true },
        ) {
        // 封面顶到卡片上沿和两侧，圆角由卡片自己的轮廓裁出来，不再单独套内边距。
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect.ratio),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 底部一行：左边统计角标、右边时长。统计那侧是带权重的槽位，
            // 数字再长也只会在自己槽位里截断，不会顶到时长上。
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(metrics.edgeInset),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = metrics.chipGap),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    CoverStats(
                        playText = video.playCountText,
                        danmakuText = if (metrics.showDanmakuOnCover) video.danmakuText else "",
                        fontSize = metrics.chipFontSize,
                        iconSize = metrics.chipIconSize,
                        paddingHorizontal = metrics.chipPaddingH,
                    )
                }
                if (video.durationText.isNotBlank()) {
                    Text(
                        text = video.durationText,
                        modifier = Modifier
                            .clip(CoverChipShape)
                            .background(CoverChipBackground)
                            .padding(horizontal = metrics.chipPaddingH, vertical = 1.dp),
                        fontSize = metrics.chipFontSize,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(metrics.infoPadding),
        ) {
            Text(
                text = video.title,
                fontSize = metrics.titleFontSize,
                lineHeight = metrics.titleLineHeight,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(metrics.rowSpacer))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (metrics.showAvatar && video.upFace.isNotBlank()) {
                    Avatar(avatarUrl, metrics.avatarSize, officialType = video.officialType)
                    Spacer(Modifier.width(metrics.avatarGap))
                }
                Text(
                    text = video.upName.ifBlank { "未知 UP" },
                    fontSize = metrics.upFontSize,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 播放量搬进封面之后，三点菜单跟 UP 主同一行靠右，卡片少一行。
                val cardMenuItems = rememberVideoCardMenuItems(
                    video = video,
                    onWatchLater = onWatchLater,
                )
                MoreMenu(
                    onWatchLater = onWatchLater,
                    onShare = { shareVideo(context, video) },
                    items = cardMenuItems,
                    buttonSize = metrics.moreButtonSize,
                    iconSize = metrics.moreIconSize,
                )
            }
        }
    }

        val longPressItems = rememberVideoCardMenuItems(
            video = video,
            onWatchLater = onWatchLater,
        )
        CouiPopupMenu(
            show = menuExpanded,
            onDismiss = { menuExpanded = false },
            items = longPressItems,
            preciseClickState = pressState,
        )
    }
}

private val CoverChipShape = SquircleShape(4.dp)
private val CoverChipBackground = Color(0x80000000)

/** 封面左下角的统计角标：播放量与弹幕数放在同一枚半透明胶囊里，中间只留一点缝。 */
@Composable
private fun CoverStats(
    playText: String,
    danmakuText: String,
    fontSize: TextUnit,
    iconSize: Dp,
    paddingHorizontal: Dp,
) {
    if (playText.isBlank() && danmakuText.isBlank()) return
    Row(
        modifier = Modifier
            .clip(CoverChipShape)
            .background(CoverChipBackground)
            .padding(horizontal = paddingHorizontal, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playText.isNotBlank()) {
            Icon(COUIIcons.Play, null, Modifier.size(iconSize), tint = Color.White)
            Spacer(Modifier.width(2.dp))
            Text(
                text = playText,
                fontSize = fontSize,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (danmakuText.isNotBlank()) {
            if (playText.isNotBlank()) Spacer(Modifier.width(6.dp))
            Icon(DanmakuStatIcon, null, Modifier.size(iconSize), tint = Color.White)
            Spacer(Modifier.width(2.dp))
            Text(
                text = danmakuText,
                fontSize = fontSize,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun rememberVideoCardMenuItems(
    video: FeedVideo,
    onWatchLater: () -> Unit,
): List<CouiMenuItem> {
    val context = LocalContext.current
    val container = erl.bilico.di.LocalAppContainer.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showDislike by remember(video.bvid) { mutableStateOf(false) }
    var showReport by remember(video.bvid) { mutableStateOf(false) }
    var showKeyword by remember(video.bvid) { mutableStateOf(false) }

    if (showDislike) {
        erl.bilico.ui.components.FeedDislikeDialog(
            video = video,
            onDismiss = { showDislike = false },
        )
    }
    if (showReport && video.aid > 0L) {
        erl.bilico.ui.components.ReportDialog(
            target = erl.bilico.ui.components.ReportTarget.Video(
                aid = video.aid,
                bvid = video.bvid,
                title = video.title,
            ),
            onDismiss = { showReport = false },
        )
    }
    if (showKeyword) {
        erl.bilico.ui.components.KeywordBlockDialog(
            allowedScopes = listOf(
                erl.bilico.data.repository.BlockKeywordScope.VIDEO_TITLE,
                erl.bilico.data.repository.BlockKeywordScope.UP_NAME,
            ),
            initialScope = erl.bilico.data.repository.BlockKeywordScope.VIDEO_TITLE,
            scopeInitialKeywords = mapOf(
                erl.bilico.data.repository.BlockKeywordScope.VIDEO_TITLE to video.title,
                erl.bilico.data.repository.BlockKeywordScope.UP_NAME to video.upName,
            ),
            onDismiss = { showKeyword = false },
        )
    }

    return remember(video, onWatchLater, container, context, scope) {
        buildList {
            add(CouiMenuItem("稍后再看", onWatchLater))
            add(CouiMenuItem("分享", { shareVideo(context, video) }))
            if (container != null) {
                add(CouiMenuItem("我不想看", onClick = { showDislike = true }, group = 1))
                add(CouiMenuItem("关键词屏蔽", onClick = { showKeyword = true }, group = 1))
                if (video.upMid > 0L) {
                    add(
                        CouiMenuItem(
                            label = "屏蔽该 UP 主",
                            group = 1,
                            onClick = {
                                if (container.session.isLoggedIn() && container.session.csrf().isNotBlank()) {
                                    scope.launch {
                                        container.blocks.toggleServerBlacklist(
                                            mid = video.upMid,
                                            add = true,
                                            name = video.upName,
                                            face = video.upFace,
                                        ).onSuccess {
                                            Toast.makeText(context, "已屏蔽 UP 主（已同步账号黑名单）", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            container.blocks.blockUserLocally(video.upMid, video.upName, video.upFace)
                                            Toast.makeText(
                                                context,
                                                "已本地屏蔽 UP 主 ${video.upName}（云端同步失败：${err.message ?: "未知错误"}）",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                } else {
                                    container.blocks.blockUserLocally(video.upMid, video.upName, video.upFace)
                                    Toast.makeText(context, "已本地屏蔽 UP 主 ${video.upName}", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ),
                    )
                }
                if (video.aid > 0L) {
                    add(CouiMenuItem("举报视频", onClick = { showReport = true }, group = 2))
                }
            }
        }
    }
}

/** 卡片右下角的三个点：COUI 的玻璃弹层菜单，稍后再看、分享、屏蔽与举报。 */
@Composable
internal fun MoreMenu(
    onWatchLater: () -> Unit,
    onShare: () -> Unit,
    items: List<CouiMenuItem>? = null,
    buttonSize: Dp = 20.dp,
    iconSize: Dp = 18.dp,
) {
    val scheme = COUITheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(buttonSize)
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
                modifier = Modifier.size(iconSize),
                tint = scheme.onSurfaceSecondary,
            )
        }

        CouiPopupMenu(
            show = expanded,
            onDismiss = { expanded = false },
            items = items ?: listOf(
                CouiMenuItem("稍后再看", onWatchLater),
                CouiMenuItem("分享", onShare),
            ),
        )
    }
}

internal fun shareVideo(context: Context, video: FeedVideo) {
    val link = "https://www.bilibili.com/video/${video.bvid}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${video.title}\n$link")
    }
    context.startActivity(Intent.createChooser(intent, "分享视频"))
}
