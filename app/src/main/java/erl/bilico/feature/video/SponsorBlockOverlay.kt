package erl.bilico.feature.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import erl.bilico.ui.components.COUISmoothCapsuleShape
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.SponsorActionType
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.IntentSlider
import io.github.suqi8.coui.kmp.basic.IntentSliderPlaybackState
import io.github.suqi8.coui.kmp.basic.IntentSliderSize
import io.github.suqi8.coui.kmp.basic.SliderDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToLong

/**
 * 播放器进度条：基于 COUI 17 [IntentSlider]（可选锥形光感 / 标准模式），
 * 并按需叠加空降助手（SponsorBlock）彩色片段与章节刻度。
 */
@Composable
internal fun PlayerProgress(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long = 0L,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    conicLightEnabled: Boolean = true,
    holdToSpeedUp: Boolean = false,
    hapticEnabled: Boolean = true,
    sponsorSegments: List<SponsorSegment> = emptyList(),
    chapters: List<VideoChapter> = emptyList(),
    onScrubPositionChange: (Long?) -> Unit = {},
    onSeek: (Long) -> Unit,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var dragHorizontalOffsetPx by remember { mutableFloatStateOf(0f) }

    val enabled = durationMs > 0L
    val effectiveDuration = durationMs.coerceAtLeast(1L)
    val displayPos = (if (isScrubbing) scrubPositionMs else positionMs).coerceIn(0L, effectiveDuration)
    val playbackState = when {
        durationMs <= 0L -> IntentSliderPlaybackState.LoadingInitial
        isBuffering -> IntentSliderPlaybackState.LoadingBuffering
        isPlaying -> IntentSliderPlaybackState.Playing
        else -> IntentSliderPlaybackState.Paused
    }
    val trackPadding = 6.dp

    LaunchedEffect(enabled) {
        if (!enabled && isScrubbing) {
            isScrubbing = false
            onScrubPositionChange(null)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        IntentSlider(
            value = displayPos.toFloat(),
            onValueChange = { next ->
                val target = next.roundToLong().coerceIn(0L, effectiveDuration)
                isScrubbing = true
                scrubPositionMs = target
                onScrubPositionChange(target)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            showThumb = true,
            followSystemTheme = false,
            conicLight = conicLightEnabled,
            holdToSpeedUp = holdToSpeedUp,
            playbackState = playbackState,
            valueRange = 0f..effectiveDuration.toFloat(),
            secondaryValue = bufferedMs.coerceIn(0L, effectiveDuration).toFloat(),
            onValueChangeFinished = {
                if (isScrubbing) {
                    onSeek(scrubPositionMs)
                    isScrubbing = false
                }
                onScrubPositionChange(null)
            },
            onDragHorizontalOffsetChange = { dragHorizontalOffsetPx = it },
            size = IntentSliderSize.Default,
            horizontalPadding = trackPadding,
            hapticEffect = if (hapticEnabled) {
                SliderDefaults.SliderHapticEffect.Step
            } else {
                SliderDefaults.SliderHapticEffect.None
            },
        )
        if (durationMs > 0L && (sponsorSegments.isNotEmpty() || chapters.size > 1)) {
            val max = effectiveDuration.toFloat()
            val trackHeight = IntentSliderSize.Default.trackHeight
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = trackPadding)
                    .height(trackHeight)
                    .graphicsLayer { translationX = dragHorizontalOffsetPx }
                    .clip(SquircleShape(trackHeight / 2)),
            ) {
                val trackW = size.width
                val trackH = size.height
                val minPointW = 3.dp.toPx()
                val radius = CornerRadius(trackH / 2f, trackH / 2f)
                sponsorSegments.forEach { seg ->
                    val startRatio = (seg.startMs.toFloat() / max).coerceIn(0f, 1f)
                    val endRatio = if (seg.actionType == SponsorActionType.Poi) {
                        startRatio
                    } else {
                        (seg.endMs.toFloat() / max).coerceIn(startRatio, 1f)
                    }
                    val left = startRatio * trackW
                    val rawW = (endRatio - startRatio) * trackW
                    val segW = rawW.coerceAtLeast(minPointW).coerceAtMost((trackW - left).coerceAtLeast(0f))
                    if (segW > 0f) {
                        drawRoundRect(
                            color = Color(seg.category.colorArgb).copy(alpha = 0.92f),
                            topLeft = Offset(left, 0f),
                            size = Size(segW, trackH),
                            cornerRadius = radius,
                        )
                    }
                }
                if (chapters.size > 1) {
                    val dividerW = 2.dp.toPx()
                    chapters.forEach { chapter ->
                        if (chapter.fromMs in 1L until durationMs) {
                            val x = ((chapter.fromMs.toFloat() / max).coerceIn(0f, 1f) * trackW - dividerW / 2f)
                                .coerceIn(0f, (trackW - dividerW).coerceAtLeast(0f))
                            drawRect(
                                color = Color(0xE6000000),
                                topLeft = Offset(x, 0f),
                                size = Size(dividerW, trackH),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 空降助手浮层胶囊：自动跳过后提供「撤销」，手动模式提示「跳过」，精彩时刻提示「空降」。
 */
@Composable
internal fun BoxScope.SponsorBlockPromptOverlay(
    undoNotice: UndoSkipNotice?,
    promptSegment: SponsorSegment?,
    poiSegment: SponsorSegment?,
    bottomPad: Dp,
    startPad: Dp,
    onUndoSkip: () -> Unit,
    onSkipPrompt: () -> Unit,
    onDismissPrompt: () -> Unit,
    onJumpPoi: () -> Unit,
    onDismissPoi: () -> Unit,
) {
    when {
        undoNotice != null -> SponsorCapsule(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = startPad, bottom = bottomPad),
            dotColor = Color(undoNotice.segment.category.colorArgb),
            text = "已空降「${undoNotice.segment.category.label}」",
            actionText = "撤销",
            onAction = onUndoSkip,
            onDismiss = null,
        )

        promptSegment != null -> SponsorCapsule(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = startPad, bottom = bottomPad),
            dotColor = Color(promptSegment.category.colorArgb),
            text = "跳过「${promptSegment.category.label}」",
            actionText = "空降",
            onAction = onSkipPrompt,
            onDismiss = onDismissPrompt,
        )

        poiSegment != null -> SponsorCapsule(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = startPad, bottom = bottomPad),
            dotColor = Color(poiSegment.category.colorArgb),
            text = "精彩时刻 ${Formatters.formatDuration(poiSegment.startMs / 1000)}",
            actionText = "空降",
            onAction = onJumpPoi,
            onDismiss = onDismissPoi,
        )
    }
}

@Composable
private fun SponsorCapsule(
    modifier: Modifier,
    dotColor: Color,
    text: String,
    actionText: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    Row(
        modifier = modifier
            .clip(COUISmoothCapsuleShape)
            .background(Color(0xB8000000))
            .clickable(onClick = onAction)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = text,
            style = COUITheme.textStyles.footnote2,
            color = Color.White,
        )
        Text(
            text = actionText,
            style = COUITheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
            color = COUITheme.colorScheme.primary,
        )
        if (onDismiss != null) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = "✕",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 4.dp),
                style = COUITheme.textStyles.footnote2,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}
