package erl.bilico.feature.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import io.github.suqi8.coui.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.player.BiliPlayer
import erl.bilico.data.model.PlaybackSource
import erl.bilico.di.LocalAppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * 播放器画面上的无边框统计浮窗：只铺一层带阴影的纯文字，不加边框或底色。
 * 在设置中开启（或点击统计弹窗左上角「浮窗」）后显示，支持直接在播放画面内四向拖动并自动保存位置，
 * 长按浮窗文字本身可直接关闭浮窗。
 */
@Composable
internal fun PlayerStatsFloatingOverlay(
    player: BiliPlayer,
    source: PlaybackSource?,
    fullscreen: Boolean,
    inPipMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val statsOverlay = LocalAppContainer.current?.settings?.statsOverlay ?: return
    val enabled by statsOverlay.enabled.collectAsStateWithLifecycle()
    if (!enabled || inPipMode) return

    val savedX by statsOverlay.xFraction.collectAsStateWithLifecycle()
    val savedY by statsOverlay.yFraction.collectAsStateWithLifecycle()
    val visibleFields by statsOverlay.visibleFields.collectAsStateWithLifecycle()
    if (visibleFields.isEmpty()) return

    val stats = rememberPlayerStats(player)
    var snapshot by remember(player, source) { mutableStateOf(readSnapshot(player, source, stats, null)) }
    LaunchedEffect(player, source) {
        while (true) {
            snapshot = readSnapshot(player, source, stats, snapshot)
            delay(500)
        }
    }

    val rows = remember(snapshot, visibleFields) {
        snapshot.toRows().filter { (field, _) -> field.id in visibleFields }
    }
    if (rows.isEmpty()) return

    var dragging by remember { mutableStateOf(false) }
    var dragX by remember(savedX) { mutableFloatStateOf(savedX) }
    var dragY by remember(savedY) { mutableFloatStateOf(savedY) }
    val activeX = if (dragging) dragX else savedX
    val activeY = if (dragging) dragY else savedY
    val currentSavedX by rememberUpdatedState(savedX)
    val currentSavedY by rememberUpdatedState(savedY)
    val haptic = LocalHapticFeedback.current

    val density = LocalDensity.current
    val hPadPx = with(density) { (if (fullscreen) 20.dp else 10.dp).roundToPx() }
    val topPadPx = with(density) { (if (fullscreen) 14.dp else 8.dp).roundToPx() }
    val bottomPadPx = with(density) { (if (fullscreen) 16.dp else 8.dp).roundToPx() }

    BoxWithConstraints(modifier = modifier) {
        var boxWidthPx by remember { mutableIntStateOf(0) }
        var boxHeightPx by remember { mutableIntStateOf(0) }
        val maxX = (constraints.maxWidth - boxWidthPx - hPadPx * 2).coerceAtLeast(0)
        val maxY = (constraints.maxHeight - boxHeightPx - topPadPx - bottomPadPx).coerceAtLeast(0)
        val xPx = hPadPx + (activeX * maxX).roundToInt()
        val yPx = topPadPx + (activeY * maxY).roundToInt()

        Column(
            horizontalAlignment = if (activeX > 0.6f) Alignment.End else Alignment.Start,
            modifier = Modifier
                .offset { IntOffset(xPx, yPx) }
                .onSizeChanged {
                    boxWidthPx = it.width
                    boxHeightPx = it.height
                }
                .pointerInput(maxX, maxY) {
                    val timeoutMs = viewConfiguration.longPressTimeoutMillis
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var timedOut = true
                        val slopChange = withTimeoutOrNull(timeoutMs) {
                            awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }.also {
                                timedOut = false
                            }
                        }
                        if (timedOut) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            statsOverlay.setEnabled(false)
                            return@awaitEachGesture
                        }
                        val firstDrag = slopChange ?: return@awaitEachGesture
                        dragX = currentSavedX
                        dragY = currentSavedY
                        dragging = true
                        drag(firstDrag.id) { change ->
                            val delta = change.positionChange()
                            change.consume()
                            if (maxX > 0) dragX = (dragX + delta.x / maxX).coerceIn(0f, 1f)
                            if (maxY > 0) dragY = (dragY + delta.y / maxY).coerceIn(0f, 1f)
                        }
                        dragging = false
                        statsOverlay.setPosition(dragX, dragY)
                    }
                }
                .padding(4.dp)
                .testTag("player_stats_floating_overlay"),
        ) {
            rows.forEach { (field, value) ->
                Text(
                    text = "${field.label}: $value",
                    style = STATS_TEXT_STYLE,
                    maxLines = 1,
                )
            }
        }
    }
}

private val STATS_TEXT_STYLE = TextStyle(
    color = Color.White,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.Medium,
    fontFamily = FontFamily.Monospace,
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.9f),
        offset = Offset(1f, 1f),
        blurRadius = 3f,
    ),
)
