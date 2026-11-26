package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.settings.PlayerTopBarItem
import erl.bilico.player.SleepTimerState
import erl.bilico.ui.components.PiliGlyph
import erl.bilico.ui.components.PiliIcon
import erl.bilico.ui.components.TopBarAudioOnlyIcon
import erl.bilico.ui.components.TopBarAutoPlayIcon
import erl.bilico.ui.components.TopBarAutoPlayOffIcon
import erl.bilico.ui.components.TopBarBackIcon
import erl.bilico.ui.components.TopBarCastIcon
import erl.bilico.ui.components.TopBarHomeIcon
import erl.bilico.ui.components.TopBarMoreIcon
import erl.bilico.ui.components.TopBarPipIcon
import erl.bilico.ui.components.TopBarShareIcon
import erl.bilico.ui.components.TopBarSleepTimerIcon
import erl.bilico.ui.components.TopBarSponsorIcon
import erl.bilico.ui.components.TopBarStatsIcon
import erl.bilico.ui.components.TopBarWatchLaterIcon
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 顶栏图标按钮的横向尺寸：横屏全屏 40dp，竖屏收紧至 34dp 以让整排常用按钮免折行。 */
private val LocalTopBarButtonWidth = compositionLocalOf { 40.dp }
private val LocalTopBarButtonHeight = compositionLocalOf { 40.dp }

/** 播放器顶栏图标按钮：统一 20dp 居中线性图标，尺寸随横竖屏顶栏上下文自适应。 */
@Composable
internal fun PlayerTopBarIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(width = LocalTopBarButtonWidth.current, height = LocalTopBarButtonHeight.current)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

/** 播放器顶栏字体图标按钮：按 0.80 比例换算字号以对齐线性图标的视觉占框。 */
@Composable
private fun PlayerTopBarGlyphButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(width = LocalTopBarButtonWidth.current, height = LocalTopBarButtonHeight.current)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PiliIcon(
            glyph = glyph,
            contentDescription = label,
            size = 16.dp,
            tint = tint,
        )
    }
}

/** 顶栏定时关闭按钮：未开启时为标准图标，倒计时或播完停止生效时展示紧凑胶囊徽标。 */
@Composable
private fun PlayerTopBarSleepTimerButton(
    state: SleepTimerState,
    onClick: () -> Unit,
) {
    if (!state.isActive) {
        PlayerTopBarIconButton(TopBarSleepTimerIcon, "定时关闭", onClick)
        return
    }
    val primary = COUITheme.colorScheme.primary
    val pillShape = SquircleShape(12.dp)
    Box(
        modifier = Modifier
            .heightIn(min = LocalTopBarButtonHeight.current)
            .clip(SquircleShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(primary.copy(alpha = 0.22f))
                .border(0.75.dp, primary.copy(alpha = 0.72f), pillShape)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = TopBarSleepTimerIcon,
                contentDescription = state.label?.let { "定时关闭：$it" } ?: "定时关闭",
                modifier = Modifier.size(15.dp),
                tint = primary,
            )
            state.label?.let { label ->
                Text(
                    text = label,
                    style = COUITheme.textStyles.footnote2.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = primary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun PlayerTopBarAutoPlayButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PlayerTopBarIconButton(
        icon = if (enabled) TopBarAutoPlayIcon else TopBarAutoPlayOffIcon,
        label = if (enabled) "关闭详情页自动播放" else "开启详情页自动播放",
        onClick = onClick,
        tint = if (enabled) COUITheme.colorScheme.primary else Color.White.copy(alpha = 0.45f),
    )
}

/** 每个顶栏按钮的图形，设置页里也用它做行首图标；全套统一 1.80dp 线性与同等视觉占框。 */
@Composable
internal fun PlayerTopBarItemIcon(
    item: PlayerTopBarItem,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.White,
) {
    val vector = when (item) {
        PlayerTopBarItem.BACK -> TopBarBackIcon
        PlayerTopBarItem.TITLE -> COUIIcons.Notes
        PlayerTopBarItem.WATCH_LATER -> TopBarWatchLaterIcon
        PlayerTopBarItem.DANMAKU_SETTINGS, PlayerTopBarItem.DANMAKU_TOGGLE -> null
        PlayerTopBarItem.CAST -> TopBarCastIcon
        PlayerTopBarItem.SHARE -> TopBarShareIcon
        PlayerTopBarItem.HOME -> TopBarHomeIcon
        PlayerTopBarItem.AUDIO_ONLY -> TopBarAudioOnlyIcon
        PlayerTopBarItem.PIP -> TopBarPipIcon
        PlayerTopBarItem.STATS -> TopBarStatsIcon
        PlayerTopBarItem.SPONSOR -> TopBarSponsorIcon
        PlayerTopBarItem.SLEEP_TIMER -> TopBarSleepTimerIcon
        PlayerTopBarItem.DETAIL_AUTO_PLAY -> TopBarAutoPlayIcon
        PlayerTopBarItem.MORE -> TopBarMoreIcon
    }
    if (vector != null) {
        Icon(vector, null, modifier.size(size), tint)
    } else {
        val glyph = if (item == PlayerTopBarItem.DANMAKU_SETTINGS) PiliGlyph.DmSettings else PiliGlyph.DmOn
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            PiliIcon(glyph, null, size = size * 0.80f, tint = tint)
        }
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
    val onOpenSponsor: (() -> Unit)?,
    val onOpenSleepTimer: (() -> Unit)? = null,
    val onToggleDetailAutoPlay: (() -> Unit)? = null,
    val onHome: (() -> Unit)?,
    val onToggleAudioOnly: (() -> Unit)?,
    val onEnterPip: (() -> Unit)?,
    val onOpenMoreMenu: () -> Unit,
    val portraitMoreMenu: @Composable () -> Unit,
)

/**
 * 播放器顶栏。横屏全屏按 [items] 单行排布；竖屏播放与竖屏全屏屏幕宽度有限，
 * 拆为「返回 + 标题」首行与可自动换行的右对齐操作区，避免按钮被挤出屏幕。
 */
@OptIn(ExperimentalLayoutApi::class)
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
    sleepTimerState: SleepTimerState = SleepTimerState(),
    detailAutoPlay: Boolean = true,
) {
    val twoRow = !fullscreen || portraitFullscreen
    val hasTitle = PlayerTopBarItem.TITLE in items
    val split = when {
        hasTitle -> items.indexOf(PlayerTopBarItem.TITLE) + 1
        PlayerTopBarItem.BACK in items -> items.indexOf(PlayerTopBarItem.BACK) + 1
        else -> 0
    }
    val buttonWidth = if (twoRow) 34.dp else 40.dp
    val buttonHeight = if (twoRow) 36.dp else 40.dp

    CompositionLocalProvider(
        LocalTopBarButtonWidth provides buttonWidth,
        LocalTopBarButtonHeight provides buttonHeight,
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    // 两行顶栏比单行更高，多给一段渐变留出淡出余量，让白色图标始终压在暗底上。
                    when {
                        portraitFullscreen -> Brush.verticalGradient(
                            listOf(Color(0xE6000000), Color(0xB3000000), Color.Transparent),
                        )
                        twoRow -> Brush.verticalGradient(
                            listOf(Color(0xCC000000), Color(0x80000000), Color.Transparent),
                        )
                        else -> Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
                    },
                )
                .padding(
                    PaddingValues(
                        start = horizontalBarPad,
                        end = horizontalBarPad,
                        top = (if (twoRow && !portraitFullscreen) 6.dp else 8.dp) + portraitTopPad,
                        bottom = if (portraitFullscreen) 20.dp else 10.dp,
                    ),
                ),
        ) {
            if (twoRow) {
                val leading = items.take(split)
                val trailing = items.drop(split)
                Column(verticalArrangement = Arrangement.spacedBy(if (portraitFullscreen) 2.dp else 0.dp)) {
                    if (leading.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            leading.forEach {
                                Item(it, title, fullscreen, danmakuEnabled, audioOnly, sleepTimerState, detailAutoPlay, actions)
                            }
                            if (!hasTitle) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (trailing.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            trailing.forEach {
                                Item(it, title, fullscreen, danmakuEnabled, audioOnly, sleepTimerState, detailAutoPlay, actions)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        if (!hasTitle && index == split) Spacer(Modifier.weight(1f))
                        Item(item, title, fullscreen, danmakuEnabled, audioOnly, sleepTimerState, detailAutoPlay, actions)
                    }
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
    sleepTimerState: SleepTimerState,
    detailAutoPlay: Boolean,
    actions: PlayerTopBarActions,
) {
    when (item) {
        PlayerTopBarItem.BACK -> PlayerTopBarIconButton(TopBarBackIcon, "返回", actions.onBack)
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
            PlayerTopBarIconButton(TopBarWatchLaterIcon, "稍后再看", it)
        }
        // 弹幕的两个按钮只在全屏出现：竖屏的弹幕设置和开关在下方「发弹幕」输入框里。
        PlayerTopBarItem.DANMAKU_SETTINGS -> if (fullscreen) actions.onDanmakuSettings?.let {
            PlayerTopBarGlyphButton(PiliGlyph.DmSettings, "弹幕设置", it)
        }
        PlayerTopBarItem.DANMAKU_TOGGLE -> if (fullscreen) {
            PlayerTopBarGlyphButton(
                glyph = if (danmakuEnabled) PiliGlyph.DmOn else PiliGlyph.DmOff,
                label = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                onClick = actions.onToggleDanmaku,
                tint = if (danmakuEnabled) Color.White else Color.White.copy(alpha = 0.45f),
            )
        }
        PlayerTopBarItem.CAST -> actions.onCast?.let {
            PlayerTopBarIconButton(TopBarCastIcon, "投屏", it)
        }
        PlayerTopBarItem.SHARE -> actions.onShare?.let {
            PlayerTopBarIconButton(TopBarShareIcon, "分享", it)
        }
        PlayerTopBarItem.HOME -> actions.onHome?.let {
            PlayerTopBarIconButton(TopBarHomeIcon, "首页", it)
        }
        PlayerTopBarItem.AUDIO_ONLY -> actions.onToggleAudioOnly?.let {
            PlayerTopBarIconButton(
                icon = TopBarAudioOnlyIcon,
                label = if (audioOnly) "退出黑听" else "黑听（仅听音频）",
                onClick = it,
                tint = if (audioOnly) COUITheme.colorScheme.primary else Color.White,
            )
        }
        PlayerTopBarItem.PIP -> actions.onEnterPip?.let {
            PlayerTopBarIconButton(TopBarPipIcon, "小窗", it)
        }
        PlayerTopBarItem.STATS -> actions.onShowStats?.let {
            PlayerTopBarIconButton(TopBarStatsIcon, "视频信息统计", it)
        }
        PlayerTopBarItem.SPONSOR -> actions.onOpenSponsor?.let {
            PlayerTopBarIconButton(TopBarSponsorIcon, "空降助手", it)
        }
        PlayerTopBarItem.SLEEP_TIMER -> actions.onOpenSleepTimer?.let {
            PlayerTopBarSleepTimerButton(sleepTimerState, it)
        }
        PlayerTopBarItem.DETAIL_AUTO_PLAY -> actions.onToggleDetailAutoPlay?.let {
            PlayerTopBarAutoPlayButton(detailAutoPlay, it)
        }
        // 「顶栏设置」和「视频信息统计」挂在更多菜单下面。
        PlayerTopBarItem.MORE -> if (fullscreen) {
            PlayerTopBarIconButton(TopBarMoreIcon, "更多", actions.onOpenMoreMenu)
        } else {
            actions.portraitMoreMenu()
        }
    }
}
