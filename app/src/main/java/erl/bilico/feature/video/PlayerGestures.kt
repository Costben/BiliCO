package erl.bilico.feature.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode {
    NONE,
    SEEK,
    BRIGHTNESS,
    VOLUME,
}

@Composable
internal fun PlayerGestureLayer(
    positionMs: Long,
    durationMs: Long,
    longPressSpeedActive: Boolean,
    longPressSpeedValue: Float,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPressSpeedChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val density = LocalDensity.current
    val edgeGuardPx = remember(density) { with(density) { 24.dp.toPx() } }

    val currentPosition by rememberUpdatedState(positionMs)
    val currentDuration by rememberUpdatedState(durationMs)
    val currentLongPressActive by rememberUpdatedState(longPressSpeedActive)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPressSpeedChange)
    val currentOnSeek by rememberUpdatedState(onSeek)

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var seekPreviewMs by remember { mutableLongStateOf(0L) }
    var seekBaseMs by remember { mutableLongStateOf(0L) }
    var levelFraction by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var longPressed = false
                detectTapGestures(
                    onPress = {
                        longPressed = false
                        tryAwaitRelease()
                        if (longPressed) {
                            longPressed = false
                            currentOnLongPress(false)
                        }
                    },
                    onTap = { currentOnTap() },
                    onDoubleTap = { currentOnDoubleTap() },
                    onLongPress = {
                        longPressed = true
                        currentOnLongPress(true)
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    if (down.position.x < edgeGuardPx ||
                        down.position.x > width - edgeGuardPx ||
                        down.position.y < edgeGuardPx ||
                        down.position.y > height - edgeGuardPx
                    ) {
                        return@awaitEachGesture
                    }

                    val slop = viewConfiguration.touchSlop
                    var totalDx = 0f
                    var totalDy = 0f
                    var activeMode = DragMode.NONE
                    var startBrightness = 0.5f
                    var startVolume = 0.5f
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed || currentLongPressActive) break
                        if (event.changes.count { it.pressed } > 1) {
                            activeMode = DragMode.NONE
                            dragMode = DragMode.NONE
                            break
                        }

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (activeMode == DragMode.NONE) {
                            if (abs(totalDx) > slop || abs(totalDy) > slop) {
                                activeMode = if (abs(totalDx) >= abs(totalDy)) {
                                    if (currentDuration > 0L) {
                                        seekBaseMs = currentPosition.coerceIn(0L, currentDuration)
                                        seekPreviewMs = seekBaseMs
                                        DragMode.SEEK
                                    } else {
                                        break
                                    }
                                } else if (down.position.x < width * 0.5f) {
                                    startBrightness = readWindowBrightness(activity, context)
                                    levelFraction = startBrightness
                                    DragMode.BRIGHTNESS
                                } else {
                                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    startVolume = (curVol.toFloat() / maxVol).coerceIn(0f, 1f)
                                    levelFraction = startVolume
                                    DragMode.VOLUME
                                }
                                dragMode = activeMode
                                totalDx = 0f
                                totalDy = 0f
                                change.consume()
                            }
                        } else {
                            change.consume()
                            when (activeMode) {
                                DragMode.SEEK -> {
                                    val spanMs = minOf(currentDuration, 180_000L).coerceAtLeast(15_000L)
                                    val offsetMs = ((totalDx / width) * spanMs).toLong()
                                    seekPreviewMs = (seekBaseMs + offsetMs).coerceIn(0L, currentDuration)
                                }

                                DragMode.BRIGHTNESS -> {
                                    val next = (startBrightness - totalDy / (height * 0.75f)).coerceIn(0.02f, 1f)
                                    levelFraction = next
                                    activity?.window?.let { win ->
                                        val attrs = win.attributes
                                        attrs.screenBrightness = next
                                        win.attributes = attrs
                                    }
                                }

                                DragMode.VOLUME -> {
                                    val next = (startVolume - totalDy / (height * 0.75f)).coerceIn(0f, 1f)
                                    levelFraction = next
                                    val targetVol = (next * maxVol).roundToInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                }

                                DragMode.NONE -> Unit
                            }
                        }
                    }

                    if (activeMode == DragMode.SEEK && currentDuration > 0L) {
                        currentOnSeek(seekPreviewMs)
                    }
                    dragMode = DragMode.NONE
                }
            },
    ) {
        if (longPressSpeedActive) {
            Text(
                text = "倍速播放中 ${longPressSpeedValue}x",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                style = COUITheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }

        when (dragMode) {
            DragMode.SEEK -> {
                val deltaSec = (seekPreviewMs - seekBaseMs) / 1000
                val sign = if (deltaSec >= 0) "+" else ""
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "${Formatters.formatDuration(seekPreviewMs / 1000)} / ${Formatters.formatDuration(durationMs / 1000)} (${sign}${deltaSec}s)",
                        style = COUITheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }

            DragMode.BRIGHTNESS, DragMode.VOLUME -> {
                val label = if (dragMode == DragMode.BRIGHTNESS) "亮度" else "音量"
                val percent = (levelFraction * 100).roundToInt().coerceIn(0, 100)
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(148.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = label, style = COUITheme.textStyles.footnote1, color = Color.White)
                        Text(text = "$percent%", style = COUITheme.textStyles.footnote1, color = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.28f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(levelFraction.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(COUITheme.colorScheme.primary),
                        )
                    }
                }
            }

            DragMode.NONE -> Unit
        }
    }
}

private fun readWindowBrightness(activity: Activity?, context: Context): Float {
    val winVal = activity?.window?.attributes?.screenBrightness ?: -1f
    if (winVal in 0.01f..1f) return winVal
    return runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f).coerceIn(0.02f, 1f)
}

private fun Context.findHostActivity(): Activity? {
    var cur: Context? = this
    while (cur is android.content.ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}
