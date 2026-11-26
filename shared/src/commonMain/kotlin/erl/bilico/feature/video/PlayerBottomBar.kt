package erl.bilico.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CouiMenuItem
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 播放器底部控件栏（章节条、进度条、播放/时间、全屏弹幕输入框与右侧功能按钮）。 */
@Composable
internal fun BoxScope.PlayerBottomChrome(
    showChrome: Boolean,
    liveMode: Boolean,
    fullscreen: Boolean,
    horizontalBarPad: Dp,
    chapters: List<VideoChapter>,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    scrubbingPositionMs: Long?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    conicLightEnabled: Boolean,
    holdToSpeedUp: Boolean,
    hapticEnabled: Boolean,
    sponsorSegments: List<SponsorSegment>,
    statusText: String?,
    showStageComposer: Boolean,
    danmakuComposer: (@Composable (onFocusChange: (Boolean) -> Unit) -> Unit)?,
    onComposerFocusedChange: (Boolean) -> Unit,
    onScrubPositionChange: (Long?) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    subtitleLabel: String?,
    speed: Float,
    qualityLabel: String?,
    onSelectLine: (() -> Unit)?,
    onSuperChatsClick: (() -> Unit)?,
    superChatCount: Int,
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
    AnimatedVisibility(
        visible = showChrome,
        enter = fadeIn(tween(200, easing = ColorOSMoveEase)) +
            slideInVertically(tween(200, easing = ColorOSMoveEase)) { it / 3 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // 全屏发弹幕时软键盘会把底栏顶走，整条跟着键盘抬起来。
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = horizontalBarPad, vertical = 6.dp),
        ) {
            if (!liveMode && chapters.isNotEmpty()) {
                PlayerChapterStrip(
                    chapters = chapters,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSelectChapter = { chapter ->
                        onSeek(chapter.fromMs)
                        if (!isPlaying) onPlayPause()
                    },
                )
            }
            if (!liveMode) {
                PlayerProgress(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedMs = bufferedMs,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    conicLightEnabled = conicLightEnabled,
                    holdToSpeedUp = holdToSpeedUp,
                    hapticEnabled = hapticEnabled,
                    sponsorSegments = sponsorSegments,
                    chapters = chapters,
                    onScrubPositionChange = onScrubPositionChange,
                    onSeek = onSeek,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerIconButton(
                    icon = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                    label = if (isPlaying) "暂停" else "播放",
                    onClick = onPlayPause,
                    buttonSize = if (fullscreen) 40.dp else 36.dp,
                    iconPadding = if (fullscreen) 9.dp else 7.dp,
                )
                if (liveMode) {
                    Text(
                        text = statusText.orEmpty(),
                        modifier = Modifier.padding(start = 6.dp),
                        style = COUITheme.textStyles.footnote2,
                        color = Color.White,
                    )
                } else {
                    Column(modifier = Modifier.padding(start = 2.dp)) {
                        Text(
                            text = Formatters.formatDuration((scrubbingPositionMs ?: positionMs) / 1000),
                            style = COUITheme.textStyles.footnote2,
                            color = Color.White,
                            lineHeight = 15.sp,
                        )
                        Text(
                            text = Formatters.formatDuration(durationMs / 1000),
                            style = COUITheme.textStyles.footnote2,
                            color = Color.White.copy(alpha = 0.6f),
                            lineHeight = 15.sp,
                        )
                    }
                }
                if (showStageComposer && danmakuComposer != null) {
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { danmakuComposer(onComposerFocusedChange) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                PlayerBottomTrailingControls(
                    subtitleLabel = subtitleLabel,
                    speed = speed,
                    qualityLabel = qualityLabel,
                    fullscreen = fullscreen,
                    liveMode = liveMode,
                    onSelectLine = onSelectLine,
                    onSuperChatsClick = onSuperChatsClick,
                    superChatCount = superChatCount,
                    activeMenu = activeMenu,
                    onOpenMenu = onOpenMenu,
                    onDismissMenu = onDismissMenu,
                    speedLocked = speedLocked,
                    onToggleSpeedLock = onToggleSpeedLock,
                    episodesMenuItems = episodesMenuItems,
                    speedMenuItems = speedMenuItems,
                    qualityMenuItems = qualityMenuItems,
                    subtitleMenuItems = subtitleMenuItems,
                    stageCoords = stageCoords,
                    onEpisodesEndChange = onEpisodesEndChange,
                    onSubtitleEndChange = onSubtitleEndChange,
                    onSpeedEndChange = onSpeedEndChange,
                    onQualityEndChange = onQualityEndChange,
                    onToggleFullscreen = onToggleFullscreen,
                )
            }
        }
    }
}
