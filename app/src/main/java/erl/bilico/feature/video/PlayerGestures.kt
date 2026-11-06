package erl.bilico.feature.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.unit.sp
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

/** 滑动超过这个距离（dp）判定进入全屏意图。 */
private val FullscreenThresholdDp = 32.dp

@Composable
internal fun PlayerGestureLayer(
    positionMs: Long,
    durationMs: Long,
    longPressSpeedActive: Boolean,
    longPressSpeedValue: Float,
    fullscreen: Boolean,
    hapticEnabled: Boolean,
    volumeBrightnessEnabled: Boolean,
    fullscreenHintEnabled: Boolean,
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
    val currentVolBrightEnabled by rememberUpdatedState(volumeBrightnessEnabled)
    val currentHintEnabled by rememberUpdatedState(fullscreenHintEnabled)
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
    /** 触发切换后短暂淡入淡出的提示：true = 进入全屏，false = 退出全屏，null = 不显示。 */
    var triggeredFullscreenHint by remember { mutableStateOf<Boolean?>(null) }

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
                            val dx = abs(totalDx)
                            val dy = abs(totalDy)
                            if (dx > slop || dy > slop) {
                                val isHorizontal = dx > dy * 1.25f
                                val isVertical = dy > dx * 1.25f
                                val leftBoundary = width / 3f
                                val rightBoundary = width * 2f / 3f

                                activeMode = when {
                                    isHorizontal -> {
                                        if (currentDuration > 0L) {
                                            seekBaseMs = currentPosition.coerceIn(0L, currentDuration)
                                            seekPreviewMs = seekBaseMs
                                            DragMode.SEEK
                                        } else {
                                            break
                                        }
                                    }

                                    isVertical -> {
                                        when {
                                            down.position.x < leftBoundary -> {
                                                if (!currentVolBrightEnabled) {
                                                    DragMode.NONE
                                                } else {
                                                    startBrightness = readWindowBrightness(activity, context)
                                                    levelFraction = startBrightness
                                                    lastStep = -1
                                                    DragMode.BRIGHTNESS
                                                }
                                            }

                                            down.position.x > rightBoundary -> {
                                                if (!currentVolBrightEnabled) {
                                                    DragMode.NONE
                                                } else {
                                                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                    startVolume = (curVol.toFloat() / maxVol).coerceIn(0f, 1f)
                                                    levelFraction = startVolume
                                                    lastStep = -1
                                                    DragMode.VOLUME
                                                }
                                            }

                                            else -> {
                                                fullscreenIntent = 0
                                                DragMode.FULLSCREEN
                                            }
                                        }
                                    }

                                    else -> DragMode.NONE
                                }

                                if (activeMode != DragMode.NONE) {
                                    dragMode = activeMode
                                    // 记录基准位移，后续调节只算判定后的相对增量
                                    baseDx = totalDx
                                    baseDy = totalDy
                                    change.consume()
                                }
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
                                    val travelY = totalDy - baseDy
                                    val thresholdPx = with(density) { FullscreenThresholdDp.toPx() }
                                    val nextIntent = when {
                                        travelY < -thresholdPx -> 1   // 上滑：进全屏
                                        travelY > thresholdPx -> -1  // 下滑：退全屏
                                        else -> 0
                                    }
                                    if (nextIntent != fullscreenIntent) {
                                        fullscreenIntent = nextIntent
                                        if (nextIntent != 0 && currentHapticEnabled) {
                                            haptic(CouiHapticEffect.Short)
                                        }
                                    }
                                }

                                DragMode.NONE -> Unit
                            }
                        }
                    }

                    when (activeMode) {
                        DragMode.SEEK -> if (currentDuration > 0L) currentOnSeek(seekPreviewMs)
                        DragMode.FULLSCREEN -> when (fullscreenIntent) {
                            1 -> if (!currentFullscreen) {
                                currentOnFullscreen(true)
                                if (currentHintEnabled) triggeredFullscreenHint = true
                            }
                            -1 -> if (currentFullscreen) {
                                currentOnFullscreen(false)
                                if (currentHintEnabled) triggeredFullscreenHint = false
                            }
                            else -> Unit
                        }

                        else -> Unit
                    }
                    dragMode = DragMode.NONE
                    fullscreenIntent = 0
                }
            },
    ) {
        androidx.compose.runtime.LaunchedEffect(triggeredFullscreenHint) {
            if (triggeredFullscreenHint != null) {
                kotlinx.coroutines.delay(1200)
                triggeredFullscreenHint = null
            }
        }

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

            // 亮度贴左 1/3，音量贴右 1/3，各自居中显示
            DragMode.BRIGHTNESS, DragMode.VOLUME -> {
                val isBrightness = dragMode == DragMode.BRIGHTNESS
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.333f)
                        .align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    LevelIndicator(
                        isBrightness = isBrightness,
                        fraction = levelFraction,
                        isCompact = !currentFullscreen,
                    )
                }
            }

            DragMode.FULLSCREEN, DragMode.NONE -> Unit
        }

        // 仅在成功触发进入/退出全屏后短暂淡入淡出，拖动过程中保持画面干净无遮挡
        AnimatedVisibility(
            visible = triggeredFullscreenHint != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val isEnter = triggeredFullscreenHint == true
            val icon = if (isEnter) FullscreenIcon else FullscreenExitIcon
            val text = if (isEnter) "已进入全屏" else "已退出全屏"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = Color.White,
                )
                Text(
                    text = text,
                    style = COUITheme.textStyles.footnote1,
                    color = Color.White,
                )
            }
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
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    val percent = (fraction * 100).roundToInt().coerceIn(0, 100)
    val cardWidth = if (isCompact) 40.dp else 54.dp
    val cardPaddingH = if (isCompact) 8.dp else 10.dp
    val cardPaddingV = if (isCompact) 8.dp else 14.dp
    val itemSpacing = if (isCompact) 4.dp else 8.dp
    val iconSize = if (isCompact) 16.dp else 20.dp
    val trackWidth = if (isCompact) 4.dp else 5.dp
    val trackHeight = if (isCompact) 44.dp else 96.dp
    val cornerRadius = if (isCompact) 12.dp else 18.dp

    Column(
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xCC000000))
            .padding(horizontal = cardPaddingH, vertical = cardPaddingV),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        Icon(
            imageVector = if (isBrightness) BrightnessIcon else VolumeIcon,
            contentDescription = if (isBrightness) "亮度" else "音量",
            modifier = Modifier.size(iconSize),
            tint = Color.White,
        )
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(RoundedCornerShape(trackWidth / 2))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(trackWidth / 2))
                    .background(COUITheme.colorScheme.primary),
            )
        }
        Text(
            text = "$percent%",
            style = if (isCompact) {
                COUITheme.textStyles.footnote2.copy(fontSize = 10.sp, lineHeight = 12.sp)
            } else {
                COUITheme.textStyles.footnote2
            },
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
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
