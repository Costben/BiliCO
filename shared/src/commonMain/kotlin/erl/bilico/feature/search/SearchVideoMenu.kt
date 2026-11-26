package erl.bilico.feature.search

import erl.bilico.platform.PlatformContext
import erl.bilico.platform.shareText
import erl.bilico.platform.showToast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.repository.BlockKeywordScope
import erl.bilico.di.LocalAppContainer
import erl.bilico.feature.download.FeedVideoDownloadDialog
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.KeywordBlockDialog
import erl.bilico.ui.components.ReportDialog
import erl.bilico.ui.components.ReportTarget
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

/**
 * 搜索视频卡片通用「更多」菜单（单列与双列共用，支持长按卡片与点击右下角三点图标弹出）：
 * 稍后再看、离线缓存、分享、关键词屏蔽、屏蔽该视频、屏蔽该 UP 主、举报视频。
 */
@Composable
internal fun rememberSearchVideoMenuItems(video: SearchVideo): List<CouiMenuItem> {
    val context = LocalPlatformContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var showDownload by remember(video.bvid) { mutableStateOf(false) }
    var showKeyword by remember(video.bvid) { mutableStateOf(false) }
    var showReport by remember(video.bvid) { mutableStateOf(false) }

    if (showDownload && container != null) {
        FeedVideoDownloadDialog(
            container = container,
            bvid = video.bvid,
            onEnqueued = { count ->
                if (count > 0) {
                    showToast(context, "已加入离线缓存队列 ($count)")
                }
            },
            onDismiss = { showDownload = false },
        )
    }

    if (showKeyword) {
        KeywordBlockDialog(
            allowedScopes = listOf(BlockKeywordScope.VIDEO_TITLE, BlockKeywordScope.UP_NAME),
            initialScope = BlockKeywordScope.VIDEO_TITLE,
            scopeInitialKeywords = mapOf(
                BlockKeywordScope.VIDEO_TITLE to video.title,
                BlockKeywordScope.UP_NAME to video.author,
            ),
            onDismiss = { showKeyword = false },
        )
    }

    if (showReport && video.aid > 0L) {
        ReportDialog(
            target = ReportTarget.Video(
                aid = video.aid,
                bvid = video.bvid,
                title = video.title,
            ),
            onDismiss = { showReport = false },
        )
    }

    return remember(video, container, context, scope) {
        buildList {
            if (container != null) {
                add(
                    CouiMenuItem(
                        label = "稍后再看",
                        onClick = {
                            if (!container.session.isLoggedIn()) {
                                showToast(context, "登录后可用")
                            } else {
                                scope.launch {
                                    val res = if (video.aid > 0L) {
                                        container.interactions.addToWatchLater(video.aid)
                                    } else {
                                        container.interactions.addToWatchLaterByBvid(video.bvid)
                                    }
                                    res.onSuccess {
                                        showToast(context, "已加入稍后再看")
                                    }.onFailure { err ->
                                        showToast(context, err.message ?: "加入稍后再看失败")
                                    }
                                }
                            }
                        },
                    ),
                )
                add(CouiMenuItem(label = "离线缓存", onClick = { showDownload = true }))
            }
            add(CouiMenuItem(label = "分享", onClick = { shareSearchVideo(context, video) }))
            if (container != null) {
                add(CouiMenuItem(label = "关键词屏蔽", onClick = { showKeyword = true }, group = 1))
                add(
                    CouiMenuItem(
                        label = "屏蔽该视频",
                        group = 1,
                        onClick = {
                            container.blocks.blockVideo(
                                bvid = video.bvid,
                                title = video.title,
                                upName = video.author,
                                upMid = video.upMid,
                            )
                            showToast(context, "已屏蔽该视频")
                        },
                    ),
                )
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
                                            name = video.author,
                                        ).onSuccess {
                                            showToast(context, "已屏蔽 UP 主（已同步账号黑名单）")
                                        }.onFailure { err ->
                                            container.blocks.blockUserLocally(video.upMid, video.author)
                                            showToast(context, "已本地屏蔽 UP 主 ${video.author}（云端同步失败：${err.message ?: "未知错误"}）")
                                        }
                                    }
                                } else {
                                    container.blocks.blockUserLocally(video.upMid, video.author)
                                    showToast(context, "已本地屏蔽 UP 主 ${video.author}")
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

/** 搜索视频卡片右下角的「三个点」更多按钮。 */
@Composable
internal fun SearchVideoMoreButton(
    items: List<CouiMenuItem>,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 20.dp,
    iconSize: Dp = 16.dp,
) {
    val scheme = COUITheme.colorScheme
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
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
            items = items,
        )
    }
}

private fun shareSearchVideo(context: PlatformContext, video: SearchVideo) {
    val link = "https://www.bilibili.com/video/${video.bvid}"
    shareText(context, "${video.title}\n$link", "分享视频")
}
