package erl.bilico.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.components.CouiPopupMenu
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play

/**
 * 竖屏视频在详情页里滑到底之后顶替视频槽位的那一条：
 * 左边返回，右边依次为继续播放/暂停、首页与更多按钮，统一采用 `HighlightedIconButton`。
 */
@Composable
internal fun CollapsedVideoBar(
    isPlaying: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onDanmakuSettings: (() -> Unit)? = null,
    onWatchLater: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    playbackEndMode: PlaybackEndMode = PlaybackEndMode.AUTO_NEXT,
    onSelectPlaybackEndMode: ((PlaybackEndMode) -> Unit)? = null,
    backdrop: Backdrop? = LocalBackdrop.current,
) {
    val statusBar = with(LocalDensity.current) {
        rememberStableStatusBarHeightPx().toDp()
    }
    var moreOpen by remember { mutableStateOf(false) }
    val effectiveBackdrop = if (BlurEffectsEnabled) backdrop else null

    // 照 COUI 顶栏：40dp 圆形按键，两端各留 16dp，相邻两颗隔 10dp（ActionMenuItemSpacing）。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = statusBar)
            .height(CollapsedBarHeight)
            .padding(
                start = TopAppBarDefaults.NavigationIconPadding,
                end = TopAppBarDefaults.ActionIconPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightedIconButton(onClick = onBack, backdrop = effectiveBackdrop) {
            Icon(COUIIcons.Back, "返回", Modifier.size(IconSize))
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TopAppBarDefaults.ActionMenuItemSpacing),
        ) {
            HighlightedIconButton(
                onClick = {
                    onExpand()
                    onPlayPause()
                },
                backdrop = effectiveBackdrop,
            ) {
                Icon(
                    imageVector = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                    contentDescription = if (isPlaying) "暂停" else "继续播放",
                    modifier = Modifier.size(IconSize),
                )
            }
            HighlightedIconButton(onClick = onHome, backdrop = effectiveBackdrop) {
                Icon(COUIIcons.Home, "首页", Modifier.size(IconSize))
            }
            Box {
                HighlightedIconButton(onClick = { moreOpen = true }, backdrop = effectiveBackdrop) {
                    Icon(COUIIcons.More, "更多", Modifier.size(IconSize))
                }
                val moreItems = rememberPlayerMoreMenuItems(
                    onDanmakuSettings = onDanmakuSettings,
                    playbackEndMode = playbackEndMode,
                    onSelectPlaybackEndMode = onSelectPlaybackEndMode,
                    onWatchLater = onWatchLater,
                    onShare = onShare,
                )
                CouiPopupMenu(
                    show = moreOpen,
                    onDismiss = { moreOpen = false },
                    items = moreItems,
                )
            }
        }
    }
}

/** COUI 顶栏图标 24dp。 */
private val IconSize = 24.dp
