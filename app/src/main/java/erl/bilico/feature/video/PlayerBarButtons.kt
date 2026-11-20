package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.model.QualityOption
import erl.bilico.data.model.SubtitleTrack
import erl.bilico.feature.live.PlayerSuperChatButton
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.CouiStagePopupMenu
import erl.bilico.ui.components.FullscreenExitIcon
import erl.bilico.ui.components.FullscreenIcon
import erl.bilico.ui.components.PiliIcon
import erl.bilico.ui.components.PlayerScrim
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 播放器顶栏的图标按钮：40dp 触摸区，字形四周留 9dp。 */
@Composable
internal fun PlayerIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        tint = tint,
    )
}

/** 字体图标按钮：字形占满 40dp 的触摸区，按下感与图标按钮一致。 */
@Composable
internal fun PlayerGlyphButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PiliIcon(
            glyph = glyph,
            contentDescription = label,
            size = 20.dp,
            tint = tint,
        )
    }
}

/**
 * 组装播放器「更多」菜单项，含「自动联播」二级菜单，并缓存列表实例以防播放进度刷新时闪烁。
 */
@Composable
internal fun rememberPlayerMoreMenuItems(
    playbackEndMode: PlaybackEndMode,
    onSelectPlaybackEndMode: ((PlaybackEndMode) -> Unit)?,
    onShare: (() -> Unit)?,
    onCast: (() -> Unit)? = null,
    onSelectLine: (() -> Unit)? = null,
    audioOnly: Boolean = false,
    onToggleAudioOnly: (() -> Unit)? = null,
    onReportVideo: (() -> Unit)? = null,
    onSubmitSponsor: (() -> Unit)? = null,
    onShowStats: (() -> Unit)? = null,
    onOpenTopBarSettings: (() -> Unit)? = null,
): List<CouiMenuItem> {
    val selectModeState by rememberUpdatedState(onSelectPlaybackEndMode)
    val shareState by rememberUpdatedState(onShare)
    val castState by rememberUpdatedState(onCast)
    val selectLineState by rememberUpdatedState(onSelectLine)
    val toggleAudioState by rememberUpdatedState(onToggleAudioOnly)
    val reportVideoState by rememberUpdatedState(onReportVideo)
    val submitSponsorState by rememberUpdatedState(onSubmitSponsor)
    val statsState by rememberUpdatedState(onShowStats)
    val topBarSettingsState by rememberUpdatedState(onOpenTopBarSettings)
    val hasPlaybackEnd = onSelectPlaybackEndMode != null
    val hasShare = onShare != null
    val hasCast = onCast != null
    val hasSelectLine = onSelectLine != null
    val hasToggleAudio = onToggleAudioOnly != null
    val hasReportVideo = onReportVideo != null
    val hasSubmitSponsor = onSubmitSponsor != null
    val hasStats = onShowStats != null
    val hasTopBarSettings = onOpenTopBarSettings != null
    return remember(
        playbackEndMode, hasPlaybackEnd, hasShare, hasCast,
        hasSelectLine, audioOnly, hasToggleAudio, hasReportVideo, hasSubmitSponsor, hasStats,
        hasTopBarSettings,
    ) {
        buildList {
            if (hasSelectLine) {
                add(CouiMenuItem(label = "切换线路", onClick = { selectLineState?.invoke() }, group = 0))
            }
            if (hasToggleAudio) {
                add(
                    CouiMenuItem(
                        label = if (audioOnly) "退出黑听（恢复画面）" else "黑听（仅听音频）",
                        selected = audioOnly,
                        onClick = { toggleAudioState?.invoke() },
                        group = 0,
                    ),
                )
            }
            if (hasCast) add(CouiMenuItem(label = "投屏", onClick = { castState?.invoke() }, group = 0))
            if (hasPlaybackEnd) {
                add(
                    CouiMenuItem(
                        label = "自动联播", summary = playbackEndMode.label, onClick = {}, group = 0,
                        children = PlaybackEndMode.entries.map { option ->
                            CouiMenuItem(
                                label = option.label,
                                selected = option == playbackEndMode,
                                onClick = { selectModeState?.invoke(option) },
                            )
                        },
                    ),
                )
            }
            if (hasShare) add(CouiMenuItem(label = "分享", onClick = { shareState?.invoke() }, group = 1))
            if (hasSubmitSponsor) add(CouiMenuItem(label = "提交空降片段", onClick = { submitSponsorState?.invoke() }, group = 1))
            if (hasReportVideo) add(CouiMenuItem(label = "举报视频", onClick = { reportVideoState?.invoke() }, group = 1))
            if (hasTopBarSettings) {
                add(CouiMenuItem(label = "顶栏设置", onClick = { topBarSettingsState?.invoke() }, group = 2))
            }
            if (hasStats) add(CouiMenuItem(label = "视频信息统计", onClick = { statsState?.invoke() }, group = 2))
        }
    }
}

/** 顶栏右端的三个点：投屏、切换线路、黑听、自动联播、分享、空降提交、举报、顶栏设置与视频信息统计。 */
@Composable
internal fun PlayerMoreMenu(
    onShare: (() -> Unit)?,
    playbackEndMode: PlaybackEndMode = PlaybackEndMode.AUTO_NEXT,
    onSelectPlaybackEndMode: ((PlaybackEndMode) -> Unit)? = null,
    onCast: (() -> Unit)? = null,
    onSelectLine: (() -> Unit)? = null,
    audioOnly: Boolean = false,
    onToggleAudioOnly: (() -> Unit)? = null,
    onReportVideo: (() -> Unit)? = null,
    onSubmitSponsor: (() -> Unit)? = null,
    onShowStats: (() -> Unit)? = null,
    onOpenTopBarSettings: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = rememberPlayerMoreMenuItems(
        playbackEndMode, onSelectPlaybackEndMode, onShare, onCast,
        onSelectLine, audioOnly, onToggleAudioOnly, onReportVideo, onSubmitSponsor, onShowStats,
        onOpenTopBarSettings,
    )
    Box {
        PlayerIconButton(COUIIcons.More, "更多", onClick = { expanded = true })
        CouiPopupMenu(show = expanded, onDismiss = { expanded = false }, items = items)
    }
}

@Composable
internal fun ControlLabel(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(SquircleShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = COUITheme.textStyles.footnote1,
        color = Color.White,
    )
}

internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
internal fun SpeedLockMenuRow(
    locked: Boolean,
    onToggleLock: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onToggleLock()
            }
            .heightIn(min = DropdownDefaults.PopupItemMinHeight)
            .padding(
                start = DropdownDefaults.InsideHorizontalPadding,
                end = DropdownDefaults.InsideHorizontalPadding,
                top = 10.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "锁定倍速",
            style = COUITheme.textStyles.body1,
            fontSize = DropdownDefaults.PopupItemTitleFontSize,
            fontWeight = FontWeight.Normal,
            color = COUITheme.colorScheme.onSurfaceContainer,
        )
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = locked,
            onCheckedChange = { onToggleLock() },
        )
    }
}

@Composable
internal fun rememberSpeedMenuItems(
    activeSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
): List<CouiMenuItem> {
    val onSelectState by rememberUpdatedState(onSelectSpeed)
    return remember(activeSpeed) {
        PLAYBACK_SPEEDS.map { option ->
            CouiMenuItem(
                label = speedLabel(option),
                selected = option == activeSpeed,
                onClick = { onSelectState(option) },
            )
        }
    }
}

@Composable
internal fun rememberQualityMenuItems(
    qualities: List<QualityOption>,
    activeQuality: Int,
    onSelectQuality: (Int) -> Unit,
): List<CouiMenuItem> {
    val onSelectState by rememberUpdatedState(onSelectQuality)
    return remember(qualities, activeQuality) {
        qualities.distinctBy { it.quality }.map { option ->
            CouiMenuItem(
                label = option.label.ifBlank { "${option.quality}P" },
                selected = option.quality == activeQuality,
                hint = when {
                    option.needVip -> {
                        {
                            Text(
                                text = "大会员",
                                style = COUITheme.textStyles.footnote2,
                                color = COUITheme.colorScheme.primary,
                            )
                        }
                    }
                    option.needLogin -> {
                        {
                            Text(
                                text = "登录",
                                style = COUITheme.textStyles.footnote2,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                    else -> null
                },
                onClick = { onSelectState(option.quality) },
            )
        }
    }
}

@Composable
internal fun rememberSubtitleMenuItems(
    tracks: List<SubtitleTrack>,
    selectedKey: String?,
    onSelectSubtitle: (String?) -> Unit,
): List<CouiMenuItem> {
    val onSelectState by rememberUpdatedState(onSelectSubtitle)
    return remember(tracks, selectedKey) {
        buildList {
            add(
                CouiMenuItem(
                    label = "关闭字幕",
                    selected = selectedKey == null,
                    onClick = { onSelectState(null) },
                ),
            )
            tracks.forEach { track ->
                add(
                    CouiMenuItem(
                        label = track.label,
                        selected = track.key == selectedKey,
                        onClick = { onSelectState(track.key) },
                    ),
                )
            }
        }
    }
}

@Composable
internal fun PlayerBottomMenuControl(
    text: String,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    items: List<CouiMenuItem>,
    headerContent: (@Composable () -> Unit)? = null,
    maxHeight: Dp? = null,
    stageCoords: LayoutCoordinates?,
    onAnchorEndChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            if (stageCoords != null && stageCoords.isAttached && coords.isAttached) {
                val box = stageCoords.localBoundingBoxOf(coords)
                onAnchorEndChange(with(density) { (stageCoords.size.width - box.right).coerceAtLeast(0f).toDp() })
            }
        },
    ) {
        ControlLabel(text = text, onClick = onOpen)
        if (!fullscreen) {
            CouiPopupMenu(
                show = menuOpen,
                onDismiss = onDismiss,
                items = items,
                alignment = PopupPositionProvider.Align.End,
                maxHeight = maxHeight,
                headerContent = headerContent,
            )
        }
    }
}

internal enum class PlayerPopupMenu { NONE, MORE, EPISODES, SPEED, QUALITY, SUBTITLE }

@Composable
internal fun RowScope.PlayerBottomTrailingControls(
    subtitleLabel: String?,
    speed: Float,
    qualityLabel: String?,
    fullscreen: Boolean,
    liveMode: Boolean = false,
    onSelectLine: (() -> Unit)? = null,
    onSuperChatsClick: (() -> Unit)? = null,
    superChatCount: Int = 0,
    activeMenu: PlayerPopupMenu,
    onOpenMenu: (PlayerPopupMenu) -> Unit,
    onDismissMenu: () -> Unit,
    speedLocked: Boolean,
    onToggleSpeedLock: () -> Unit,
    episodesMenuItems: List<CouiMenuItem>,
    speedMenuItems: List<CouiMenuItem>,
    qualityMenuItems: List<CouiMenuItem>,
    subtitleMenuItems: List<CouiMenuItem>,
    stageCoords: LayoutCoordinates?,
    onEpisodesEndChange: (Dp) -> Unit,
    onSubtitleEndChange: (Dp) -> Unit,
    onSpeedEndChange: (Dp) -> Unit,
    onQualityEndChange: (Dp) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    if (!liveMode && episodesMenuItems.isNotEmpty()) {
        PlayerBottomMenuControl(
            text = "选集",
            menuOpen = activeMenu == PlayerPopupMenu.EPISODES,
            onOpen = { onOpenMenu(PlayerPopupMenu.EPISODES) },
            onDismiss = onDismissMenu,
            fullscreen = fullscreen,
            items = episodesMenuItems,
            maxHeight = 350.dp,
            stageCoords = stageCoords,
            onAnchorEndChange = onEpisodesEndChange,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    if (subtitleLabel != null) {
        PlayerBottomMenuControl(
            text = subtitleLabel,
            menuOpen = activeMenu == PlayerPopupMenu.SUBTITLE,
            onOpen = { onOpenMenu(PlayerPopupMenu.SUBTITLE) },
            onDismiss = onDismissMenu,
            fullscreen = fullscreen,
            items = subtitleMenuItems,
            stageCoords = stageCoords,
            onAnchorEndChange = onSubtitleEndChange,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    if (!liveMode) {
        PlayerBottomMenuControl(
            text = speedLabel(speed),
            menuOpen = activeMenu == PlayerPopupMenu.SPEED,
            onOpen = { onOpenMenu(PlayerPopupMenu.SPEED) },
            onDismiss = onDismissMenu,
            fullscreen = fullscreen,
            items = speedMenuItems,
            headerContent = { SpeedLockMenuRow(locked = speedLocked, onToggleLock = onToggleSpeedLock) },
            stageCoords = stageCoords,
            onAnchorEndChange = onSpeedEndChange,
            modifier = Modifier.padding(start = 6.dp),
        )
    } else {
        if (onSelectLine != null) {
            ControlLabel(text = "线路", onClick = onSelectLine, modifier = Modifier.padding(start = 4.dp))
        }
        if (onSuperChatsClick != null) {
            PlayerSuperChatButton(count = superChatCount, onClick = onSuperChatsClick)
        }
    }
    PlayerBottomMenuControl(
        text = qualityLabel ?: "清晰度",
        menuOpen = activeMenu == PlayerPopupMenu.QUALITY && qualityMenuItems.isNotEmpty(),
        onOpen = { if (qualityMenuItems.isNotEmpty()) onOpenMenu(PlayerPopupMenu.QUALITY) },
        onDismiss = onDismissMenu,
        fullscreen = fullscreen,
        items = qualityMenuItems,
        stageCoords = stageCoords,
        onAnchorEndChange = onQualityEndChange,
        modifier = Modifier.padding(start = 6.dp),
    )
    PlayerIconButton(
        icon = if (fullscreen) FullscreenExitIcon else FullscreenIcon,
        label = if (fullscreen) "退出全屏" else "全屏",
        onClick = onToggleFullscreen,
    )
}

@Composable
internal fun PlayerStagePopupMenus(
    activeMenu: PlayerPopupMenu,
    lastBottomMenu: PlayerPopupMenu,
    onDismiss: () -> Unit,
    hdrVideo: Boolean,
    menuBackdrop: LayerBackdrop,
    horizontalBarPad: Dp,
    onHideControls: () -> Unit,
    playbackEndMode: PlaybackEndMode,
    onSelectPlaybackEndMode: ((PlaybackEndMode) -> Unit)?,
    onShare: (() -> Unit)?,
    onCast: (() -> Unit)? = null,
    onSelectLine: (() -> Unit)? = null,
    audioOnly: Boolean = false,
    onToggleAudioOnly: (() -> Unit)? = null,
    onReportVideo: (() -> Unit)? = null,
    onSubmitSponsor: (() -> Unit)? = null,
    onShowStats: () -> Unit,
    onOpenTopBarSettings: (() -> Unit)? = null,
    episodesMenuItems: List<CouiMenuItem>,
    speedMenuItems: List<CouiMenuItem>,
    qualityMenuItems: List<CouiMenuItem>,
    subtitleMenuItems: List<CouiMenuItem>,
    episodesEndMargin: Dp,
    speedEndMargin: Dp,
    qualityEndMargin: Dp,
    subtitleEndMargin: Dp,
    speedLocked: Boolean,
    onToggleSpeedLock: () -> Unit,
) {
    CompositionLocalProvider(
        LocalBackdrop provides if (BlurEffectsEnabled && !hdrVideo) menuBackdrop else null,
    ) {
        val stageMoreItems = rememberPlayerMoreMenuItems(
            playbackEndMode = playbackEndMode,
            onSelectPlaybackEndMode = onSelectPlaybackEndMode,
            onShare = onShare,
            onCast = onCast?.let { open -> { onHideControls(); open() } },
            onSelectLine = onSelectLine?.let { open -> { onHideControls(); open() } },
            audioOnly = audioOnly,
            onToggleAudioOnly = onToggleAudioOnly,
            onReportVideo = onReportVideo?.let { open -> { onHideControls(); open() } },
            onSubmitSponsor = onSubmitSponsor?.let { open -> { onHideControls(); open() } },
            onShowStats = onShowStats,
            onOpenTopBarSettings = onOpenTopBarSettings?.let { open -> { onHideControls(); open() } },
        )
        CouiStagePopupMenu(
            show = activeMenu == PlayerPopupMenu.MORE,
            onDismiss = onDismiss,
            topMargin = 54.dp,
            endMargin = horizontalBarPad,
            items = stageMoreItems,
        )
        val bottomMenuShown = activeMenu == PlayerPopupMenu.EPISODES ||
            activeMenu == PlayerPopupMenu.SPEED ||
            activeMenu == PlayerPopupMenu.QUALITY ||
            activeMenu == PlayerPopupMenu.SUBTITLE
        val shownMenu = if (bottomMenuShown) activeMenu else lastBottomMenu
        CouiStagePopupMenu(
            show = bottomMenuShown,
            onDismiss = onDismiss,
            items = when (shownMenu) {
                PlayerPopupMenu.EPISODES -> episodesMenuItems
                PlayerPopupMenu.SPEED -> speedMenuItems
                PlayerPopupMenu.QUALITY -> qualityMenuItems
                else -> subtitleMenuItems
            },
            alignment = Alignment.BottomEnd,
            bottomMargin = 48.dp,
            maxHeight = 350.dp,
            endMargin = when (shownMenu) {
                PlayerPopupMenu.EPISODES -> episodesEndMargin
                PlayerPopupMenu.SPEED -> speedEndMargin
                PlayerPopupMenu.QUALITY -> qualityEndMargin
                else -> subtitleEndMargin
            },
            headerContent = if (shownMenu == PlayerPopupMenu.SPEED) {
                { SpeedLockMenuRow(locked = speedLocked, onToggleLock = onToggleSpeedLock) }
            } else {
                null
            },
        )
    }
}

internal fun speedLabel(speed: Float): String {
    val quarter = kotlin.math.round(speed * 4f).toInt()
    if (kotlin.math.abs(speed - quarter / 4f) >= 0.01f) return "${speed}x"
    return when (quarter % 4) {
        0 -> "${quarter / 4}.0x"
        2 -> "${quarter / 4}.5x"
        else -> "${quarter / 4}.${(quarter % 4) * 25}x"
    }
}
