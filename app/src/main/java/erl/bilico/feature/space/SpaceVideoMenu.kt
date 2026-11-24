package erl.bilico.feature.space

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.model.SpaceProfile
import erl.bilico.data.repository.BlockKeywordScope
import erl.bilico.di.AppContainer
import erl.bilico.feature.download.FeedVideoDownloadDialog
import erl.bilico.feature.feed.rememberWatchLaterAction
import erl.bilico.feature.feed.shareVideo
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.KeywordBlockDialog
import erl.bilico.ui.components.ReportDialog
import erl.bilico.ui.components.ReportTarget

/**
 * 空间投稿卡片的更多菜单（长按与右下角三点共用）。
 *
 * 顺序：稍后再看 / 离线缓存 / 分享 ——分割线—— 关键词屏蔽 / 举报视频。
 * 关键词屏蔽与举报视频同属一组，中间不画分割线。
 */
@Composable
internal fun rememberSpaceVideoMenuItems(
    video: LibraryVideo,
    profile: SpaceProfile?,
    container: AppContainer,
): List<CouiMenuItem> {
    val context = LocalContext.current
    var showDownload by remember(video.bvid) { mutableStateOf(false) }
    var showKeyword by remember(video.bvid) { mutableStateOf(false) }
    var showReport by remember(video.bvid) { mutableStateOf(false) }

    // 分享、稍后再看这些动作都吃 FeedVideo，这里按空间里的条目拼一份最小的。
    val feedVideo = remember(video, profile) {
        FeedVideo(
            bvid = video.bvid,
            aid = video.aid,
            title = video.title,
            cover = video.cover,
            upName = profile?.name.orEmpty(),
            upMid = profile?.mid ?: 0L,
            upFace = profile?.face.orEmpty(),
            playCount = 0L,
            durationSec = video.durationSec,
            playCountText = video.playText,
            danmakuText = video.danmakuText,
            durationText = video.durationText,
        )
    }
    val onWatchLater = rememberWatchLaterAction(container, feedVideo)

    if (showDownload) {
        FeedVideoDownloadDialog(
            container = container,
            bvid = video.bvid,
            onEnqueued = { count ->
                if (count > 0) {
                    Toast.makeText(context, "已加入离线缓存队列 ($count)", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showDownload = false },
        )
    }
    if (showKeyword) {
        val upName = feedVideo.upName
        val allowedScopes = if (upName.isNotBlank()) {
            listOf(BlockKeywordScope.VIDEO_TITLE, BlockKeywordScope.UP_NAME)
        } else {
            listOf(BlockKeywordScope.VIDEO_TITLE)
        }
        val initialKeywords = buildMap {
            put(BlockKeywordScope.VIDEO_TITLE, video.title)
            if (upName.isNotBlank()) put(BlockKeywordScope.UP_NAME, upName)
        }
        KeywordBlockDialog(
            allowedScopes = allowedScopes,
            initialScope = BlockKeywordScope.VIDEO_TITLE,
            scopeInitialKeywords = initialKeywords,
            onDismiss = { showKeyword = false },
        )
    }
    if (showReport && video.aid > 0L) {
        ReportDialog(
            target = ReportTarget.Video(aid = video.aid, bvid = video.bvid, title = video.title),
            onDismiss = { showReport = false },
        )
    }

    return remember(feedVideo, onWatchLater, container) {
        buildList {
            add(CouiMenuItem("稍后再看", onWatchLater))
            add(CouiMenuItem("离线缓存", { showDownload = true }))
            add(CouiMenuItem("分享", { shareVideo(context, feedVideo) }))
            add(CouiMenuItem("关键词屏蔽", onClick = { showKeyword = true }, group = 1))
            if (video.aid > 0L) {
                add(CouiMenuItem("举报视频", onClick = { showReport = true }, group = 1))
            }
        }
    }
}
