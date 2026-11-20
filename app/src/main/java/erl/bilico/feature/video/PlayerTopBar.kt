package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.settings.PlayerTopBarItem
import erl.bilico.ui.components.NavHomeOutlineIcon
import erl.bilico.ui.components.PiliGlyph
import erl.bilico.ui.components.PiliIcon
import erl.bilico.ui.components.PipIcon
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Music
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.ScreenMirroring
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 每个顶栏按钮的图形，设置页里也用它做行首图标。 */
@Composable
internal fun PlayerTopBarItemIcon(
    item: PlayerTopBarItem,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.White,
) {
    when (item) {
        PlayerTopBarItem.BACK -> Icon(COUIIcons.Back, null, modifier.size(size), tint)
        PlayerTopBarItem.TITLE -> Icon(COUIIcons.Notes, null, modifier.size(size), tint)
        PlayerTopBarItem.WATCH_LATER -> PiliIcon(PiliGlyph.WatchLater, null, modifier, size, tint)
        PlayerTopBarItem.DANMAKU_SETTINGS -> PiliIcon(PiliGlyph.DmSettings, null, modifier, size, tint)
        PlayerTopBarItem.DANMAKU_TOGGLE -> PiliIcon(PiliGlyph.DmOn, null, modifier, size, tint)
        PlayerTopBarItem.CAST -> Icon(COUIIcons.ScreenMirroring, null, modifier.size(size), tint)
        PlayerTopBarItem.SHARE -> PiliIcon(PiliGlyph.Share, null, modifier, size, tint)
        PlayerTopBarItem.HOME -> Icon(NavHomeOutlineIcon, null, modifier.size(size), tint)
        PlayerTopBarItem.AUDIO_ONLY -> Icon(COUIIcons.Music, null, modifier.size(size), tint)
        PlayerTopBarItem.PIP -> Icon(PipIcon, null, modifier.size(size), tint)
        PlayerTopBarItem.STATS -> Icon(COUIIcons.Info, null, modifier.size(size), tint)
        PlayerTopBarItem.MORE -> Icon(COUIIcons.More, null, modifier.size(size), tint)
    }
}

/** 顶栏按钮的回调集合，[PlayerTopBar] 内部逐个按条目取用。 */
internal class PlayerTopBarActions(
    val onBack: () -> Unit,
    val onWatchLater: (() -> Unit)?,
    val onDanmakuSettings: (() -> Unit)?,
    val onToggleDanmaku: () -> Unit,
    val onShare: (() -> Unit)?,
    val onCast: (() -> Unit)?,
    val onShowStats: (() -> Unit)?,
    val onHome: (() -> Unit)?,
    val onToggleAudioOnly: (() -> Unit)?,
    val onEnterPip: (() -> Unit)?,
    val onOpenMoreMenu: () -> Unit,
    val portraitMoreMenu: @Composable () -> Unit,
)

/**
 * 播放器顶栏。按 [items] 的顺序摆按钮，左边最多返回加标题，其余往右排；
 * 竖屏全屏画面只有屏幕宽，单行会挤在一起，所以从标题之后断开成上下两行。
 */
@Composable
internal fun PlayerTopBar(
    title: String,
    items: List<PlayerTopBarItem>,
    fullscreen: Boolean,
    portraitFullscreen: Boolean,
    danmakuEnabled: Boolean,
    audioOnly: Boolean,
    horizontalBarPad: Dp,
    portraitTopPad: Dp,
    actions: PlayerTopBarActions,
    modifier: Modifier = Modifier,
) {
    val hasTitle = PlayerTopBarItem.TITLE in items
    val split = when {
        hasTitle -> items.indexOf(PlayerTopBarItem.TITLE) + 1
        PlayerTopBarItem.BACK in items -> items.indexOf(PlayerTopBarItem.BACK) + 1
        else -> 0
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                // 两行顶栏比单行高得多，第二行下方就是画面，单段渐变淡到那里已经几乎看不见，
                // 所以多给一段并留出淡出余量，让白色图标始终压在暗底上。
                if (portraitFullscreen) {
                    Brush.verticalGradient(
                        listOf(Color(0xE6000000), Color(0xB3000000), Color.Transparent),
                    )
                } else {
                    Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
                },
            )
            .padding(
                PaddingValues(
                    start = horizontalBarPad,
                    end = horizontalBarPad,
                    top = 8.dp + portraitTopPad,
                    bottom = if (portraitFullscreen) 20.dp else 8.dp,
                ),
            ),
    ) {
        if (portraitFullscreen) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.take(split).forEach { Item(it, title, fullscreen, danmakuEnabled, audioOnly, actions) }
                    if (!hasTitle) Spacer(Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    items.drop(split).forEach { Item(it, title, fullscreen, danmakuEnabled, audioOnly, actions) }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    if (!hasTitle && index == split) Spacer(Modifier.weight(1f))
                    Item(item, title, fullscreen, danmakuEnabled, audioOnly, actions)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Item(
    item: PlayerTopBarItem,
    title: String,
    fullscreen: Boolean,
    danmakuEnabled: Boolean,
    audioOnly: Boolean,
    actions: PlayerTopBarActions,
) {
    when (item) {
        PlayerTopBarItem.BACK -> PlayerIconButton(COUIIcons.Back, "返回", actions.onBack)
        PlayerTopBarItem.TITLE -> Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            style = COUITheme.textStyles.body2,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PlayerTopBarItem.WATCH_LATER -> actions.onWatchLater?.let {
            PlayerGlyphButton(PiliGlyph.WatchLater, "稍后再看", it)
        }
        // 弹幕的两个按钮只在全屏出现：竖屏的弹幕设置和开关在下方「发弹幕」输入框里。
        PlayerTopBarItem.DANMAKU_SETTINGS -> if (fullscreen) actions.onDanmakuSettings?.let {
            PlayerGlyphButton(PiliGlyph.DmSettings, "弹幕设置", it)
        }
        PlayerTopBarItem.DANMAKU_TOGGLE -> if (fullscreen) {
            PlayerGlyphButton(
                glyph = if (danmakuEnabled) PiliGlyph.DmOn else PiliGlyph.DmOff,
                label = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                onClick = actions.onToggleDanmaku,
                tint = if (danmakuEnabled) Color.White else Color.White.copy(alpha = 0.45f),
            )
        }
        PlayerTopBarItem.CAST -> actions.onCast?.let {
            PlayerIconButton(COUIIcons.ScreenMirroring, "投屏", it)
        }
        PlayerTopBarItem.SHARE -> actions.onShare?.let {
            PlayerGlyphButton(PiliGlyph.Share, "分享", it)
        }
        PlayerTopBarItem.HOME -> actions.onHome?.let {
            PlayerIconButton(NavHomeOutlineIcon, "首页", it)
        }
        PlayerTopBarItem.AUDIO_ONLY -> actions.onToggleAudioOnly?.let {
            PlayerIconButton(
                icon = COUIIcons.Music,
                label = if (audioOnly) "退出黑听" else "黑听（仅听音频）",
                onClick = it,
                tint = if (audioOnly) COUITheme.colorScheme.primary else Color.White,
            )
        }
        PlayerTopBarItem.PIP -> actions.onEnterPip?.let {
            PlayerIconButton(PipIcon, "小窗", it)
        }
        PlayerTopBarItem.STATS -> actions.onShowStats?.let {
            PlayerIconButton(COUIIcons.Info, "视频信息统计", it)
        }
        // 「顶栏设置」和「视频信息统计」挂在更多菜单下面。
        PlayerTopBarItem.MORE -> if (fullscreen) {
            PlayerIconButton(COUIIcons.More, "更多", actions.onOpenMoreMenu)
        } else {
            actions.portraitMoreMenu()
        }
    }
}
