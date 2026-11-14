package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 竖屏视频在详情页里滑到底之后顶替视频槽位的那一条：
 * 左边返回、首页，中间「继续播放」，右边更多。点「继续播放」把视频重新展开并开始播放。
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
    autoPlayNext: Boolean = true,
    onToggleAutoPlayNext: (() -> Unit)? = null,
) {
    val statusBar = with(LocalDensity.current) {
        rememberStableStatusBarHeightPx().toDp()
    }
    var moreOpen by remember { mutableStateOf(false) }

    // 照 COUI 顶栏：40dp 圆形按键，两端各留 16dp，相邻两颗隔 10dp（ActionMenuItemSpacing）。
    // 左边只放返回，其余都在右端；中间那颗占掉两端剩下的空档再居中——
    // 居中在「返回」和「首页」之间，而不是整屏正中：右边多一颗，按屏幕居中会偏向首页那边。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(pageContainerColor())
            .padding(top = statusBar)
            .height(CollapsedBarHeight)
            .padding(
                start = TopAppBarDefaults.NavigationIconPadding,
                end = TopAppBarDefaults.ActionIconPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightedIconButton(onClick = onBack) {
            Icon(COUIIcons.Back, "返回", Modifier.size(IconSize))
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        onExpand()
                        onPlayPause()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = COUITheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isPlaying) "暂停" else "继续播放",
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurface,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TopAppBarDefaults.ActionMenuItemSpacing),
        ) {
            HighlightedIconButton(onClick = onHome) {
                Icon(COUIIcons.Home, "首页", Modifier.size(IconSize))
            }
            Box {
                HighlightedIconButton(onClick = { moreOpen = true }) {
                    Icon(COUIIcons.More, "更多", Modifier.size(IconSize))
                }
                CouiPopupMenu(
                    show = moreOpen,
                    onDismiss = { moreOpen = false },
                    items = buildList {
                        onDanmakuSettings?.let { add(CouiMenuItem("弹幕设置", it)) }
                        onToggleAutoPlayNext?.let { add(autoPlayNextMenuItem(autoPlayNext, it)) }
                        onWatchLater?.let { add(CouiMenuItem("稍后再看", it)) }
                        onShare?.let { add(CouiMenuItem("分享", it)) }
                    },
                )
            }
        }
    }
}

/** COUI 顶栏图标 24dp。 */
private val IconSize = 24.dp
