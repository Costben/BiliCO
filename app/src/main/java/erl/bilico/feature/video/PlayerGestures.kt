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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import erl.bilico.ui.components.BrightnessIcon
import erl.bilico.ui.components.FullscreenExitIcon
import erl.bilico.ui.components.FullscreenIcon
import erl.bilico.ui.components.VolumeIcon
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode {
    NONE,
    SEEK,
    BRIGHTNESS,
    VOLUME,
    FULLSCREEN,
}

/** 竖直方向整体快过这个速度（px/ms）的，按「甩一下」算全屏手势，不再改亮度音量。 */
private const val FlingSpeedPxPerMs = 2.0f

/** 竖向手势至少先走满播放区的这个比例，才开始判速度。 */
private const val FlingDecideTravel = 0.08f

/** 或者按下超过这么久，也当慢拖处理，直接进亮度音量。 */
private const val FlingDecideTimeoutMs = 260L

/** 全屏手势要甩过播放区的这个比例才算数，避免误触。 */
private const val FullscreenSwipeTravel = 0.1f

@Composable
internal fun PlayerGestureLayer(
    positionMs: Long,
    durationMs: Long,
    longPressSpeedActive: Boolean,
    longPressSpeedValue: Float,
    fullscreen: Boolean,
    hapticEnabled: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPressSpeedChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val density = LocalDensity.current
    val edgeGuardPx = remember(density) { with(density) { 24.dp.toPx() } }
    val haptic = rememberCouiHaptic()

    val currentPosition by rememberUpdatedState(positionMs)
    val currentDuration by rememberUpdatedState(durationMs)
    val currentLongPressActive by rememberUpdatedState(longPressSpeedActive)
    val currentFullscreen by rememberUpdatedState(fullscreen)
    val currentHapticEnabled by rememberUpdatedState(hapticEnabled)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPressSpeedChange)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnFullscreen by rememberUpdatedState(onFullscreenChange)

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var seekPreviewMs by remember { mutableLongStateOf(0L) }
    var seekBaseMs by remember { mutableLongStateOf(0L) }
    var levelFraction by remember { mutableFloatStateOf(0f) }
    /** 全屏手势的方向：1 上滑进全屏，-1 下滑退全屏，0 还没甩够。 */
    var fullscreenIntent by remember { mutableIntStateOf(0) }

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
                    var baseDx = 0f
                    var baseDy = 0f
                    var activeMode = DragMode.NONE
                    var startBrightness = 0.5f
                    var startVolume = 0.5f
                    var lastStep = -1
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        .coerceAtLeast(1)
                    fullscreenIntent = 0

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
                                val elapsedMs = (change.uptimeMillis - down.uptimeMillis)
                                    .coerceAtLeast(1L)
                                // 竖向先走够一段距离再判速度：刚越过 slop 的那几毫秒
                                // 速度噪声极大，拿它判「甩」几乎必然误判成全屏。
                                val undecidedVertical = abs(totalDx) < abs(totalDy) &&
                                    abs(totalDy) < height * FlingDecideTravel &&
                                    elapsedMs < FlingDecideTimeoutMs
                                val fling = abs(totalDy) / elapsedMs > FlingSpeedPxPerMs
                                activeMode = when {
                                    undecidedVertical -> DragMode.NONE

                                    abs(totalDx) >= abs(totalDy) -> {
                                        if (currentDuration > 0L) {
                                            seekBaseMs = currentPosition.coerceIn(0L, currentDuration)
                                            seekPreviewMs = seekBaseMs
                                            DragMode.SEEK
                                        } else {
                                            break
                                        }
                                    }

                                    fling -> DragMode.FULLSCREEN

                                    down.position.x < width * 0.5f -> {
                                        startBrightness = readWindowBrightness(activity, context)
                                        levelFraction = startBrightness
                                        lastStep = -1
                                        DragMode.BRIGHTNESS
                                    }

                                    else -> {
                                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        startVolume = (curVol.toFloat() / maxVol).coerceIn(0f, 1f)
                                        levelFraction = startVolume
                                        lastStep = -1
                                        DragMode.VOLUME
                                    }
                                }
                                dragMode = activeMode
                                // 不把累计位移清零，只记基线：判定前已经走掉的那段距离
                                // 不该再作用到音量/亮度上，否则一判定就跳一截。
                                baseDx = totalDx
                                baseDy = totalDy
                                change.consume()
                            }
                        } else {
                            change.consume()
                            when (activeMode) {
                                DragMode.SEEK -> {
                                    val spanMs = minOf(currentDuration, 180_000L).coerceAtLeast(15_000L)
                                    val offsetMs = (((totalDx - baseDx) / width) * spanMs).toLong()
                                    seekPreviewMs = (seekBaseMs + offsetMs).coerceIn(0L, currentDuration)
                                }

                                DragMode.BRIGHTNESS -> {
                                    val next = (startBrightness - (totalDy - baseDy) / (height * 0.75f))
                                        .coerceIn(0.02f, 1f)
                                    levelFraction = next
                                    activity?.window?.let { win ->
                                        val attrs = win.attributes
                                        attrs.screenBrightness = next
                                        win.attributes = attrs
                                    }
                                    val step = (next * BrightnessSteps).roundToInt()
                                    if (step != lastStep) {
                                        lastStep = step
                                        if (currentHapticEnabled) haptic(CouiHapticEffect.Short)
                                    }
                                }

                                DragMode.VOLUME -> {
                                    val next = (startVolume - (totalDy - baseDy) / (height * 0.75f))
                                        .coerceIn(0f, 1f)
                                    levelFraction = next
                                    val targetVol = (next * maxVol).roundToInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    if (targetVol != lastStep) {
                                        lastStep = targetVol
                                        if (currentHapticEnabled) haptic(CouiHapticEffect.Short)
                                    }
                                }

                                DragMode.FULLSCREEN -> {
                                    fullscreenIntent = when {
                                        totalDy < -height * FullscreenSwipeTravel -> 1
                                        totalDy > height * FullscreenSwipeTravel -> -1
                                        else -> 0
                                    }
                                }

                                DragMode.NONE -> Unit
                            }
                        }
                    }

                    when (activeMode) {
                        DragMode.SEEK -> if (currentDuration > 0L) currentOnSeek(seekPreviewMs)
                        DragMode.FULLSCREEN -> when (fullscreenIntent) {
                            1 -> if (!currentFullscreen) currentOnFullscreen(true)
                            -1 -> if (currentFullscreen) currentOnFullscreen(false)
                            else -> Unit
                        }

                        else -> Unit
                    }
                    dragMode = DragMode.NONE
                    fullscreenIntent = 0
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
                        text = "${Formatters.formatDuration(seekPreviewMs / 1000)} / " +
                            "${Formatters.formatDuration(durationMs / 1000)} (${sign}${deltaSec}s)",
                        style = COUITheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }

            // 亮度贴左半边、音量贴右半边，各自落在自己那半的中间。
            DragMode.BRIGHTNESS, DragMode.VOLUME -> {
                val isBrightness = dragMode == DragMode.BRIGHTNESS
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.5f)
                        .align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    LevelIndicator(isBrightness = isBrightness, fraction = levelFraction)
                }
            }

            DragMode.FULLSCREEN -> {
                // 全屏里能做的只有下滑退出，非全屏里只有上滑进入，提示按当前状态给，不跟甩的方向走。
                val isExit = currentFullscreen
                val icon = if (isExit) FullscreenExitIcon else FullscreenIcon
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                    Text(
                        text = if (isExit) "下滑退出全屏" else "上滑进入全屏",
                        style = COUITheme.textStyles.footnote1,
                        color = Color.White,
                    )
                }
            }

            DragMode.NONE -> Unit
        }
    }
}

/** 亮度按 5% 一档震动，音量按系统音量档震动。 */
private const val BrightnessSteps = 20

/** 竖着的电平条：上面图标、中间轨道、下面百分比，轨道用主题色填充。 */
@Composable
private fun LevelIndicator(
    isBrightness: Boolean,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val percent = (fraction * 100).roundToInt().coerceIn(0, 100)
    Column(
        modifier = modifier
            .width(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (isBrightness) BrightnessIcon else VolumeIcon,
            contentDescription = if (isBrightness) "亮度" else "音量",
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(108.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(COUITheme.colorScheme.primary),
            )
        }
        Text(
            text = "$percent%",
            style = COUITheme.textStyles.footnote2,
            color = Color.White,
        )
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
