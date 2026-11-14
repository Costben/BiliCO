package erl.bilico.feature.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.SponsorActionType
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 播放器进度条：在 3dp 细条上叠加空降助手（SponsorBlock）彩色片段标尺。
 */
@Composable
internal fun PlayerProgress(
    positionMs: Long,
    durationMs: Long,
    sponsorSegments: List<SponsorSegment> = emptyList(),
    onSeek: (Long) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    val max = durationMs.coerceAtLeast(1L).toFloat()
    val value = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(0f, max)
    val fraction = if (durationMs > 0L) (value / max).coerceIn(0f, 1f) else 0f
    val enabled = durationMs > 0L

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(enabled, max) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek(((offset.x / widthPx.coerceAtLeast(1)) * max).toLong())
                }
            }
            .pointerInput(enabled, max) {
                if (!enabled) return@pointerInput
                fun fractionAt(x: Float) = (x / widthPx.coerceAtLeast(1)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragValue = fractionAt(offset.x) * max
                    },
                    onHorizontalDrag = { change, _ ->
                        dragValue = fractionAt(change.position.x) * max
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek(dragValue.toLong())
                    },
                    onDragCancel = { dragging = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White.copy(alpha = 0.24f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White),
        )
        if (durationMs > 0L && sponsorSegments.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
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
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(3.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(if (dragging) 11.dp else 9.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
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
            .clip(RoundedCornerShape(18.dp))
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
