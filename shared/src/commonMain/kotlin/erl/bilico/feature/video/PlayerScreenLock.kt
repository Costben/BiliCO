package erl.bilico.feature.video

import erl.bilico.platform.PlatformBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Unlock
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

internal val LocalStageScreenLockObserver = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }

/**
 * 计算锁屏按钮起始边距：在横屏全屏下自动避让 Android 前置挖孔/刘海与 iOS 灵动岛安全区，
 * 无挖孔侧仍保持 [baseStartPad]，挖孔侧在安全区外额外保留 16dp 呼吸间距。
 */
@Composable
internal fun rememberScreenLockStartPad(baseStartPad: Dp): Dp {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cutoutStartPx = if (layoutDirection == LayoutDirection.Ltr) {
        WindowInsets.displayCutout.getLeft(density, layoutDirection)
    } else {
        WindowInsets.displayCutout.getRight(density, layoutDirection)
    }
    val safeStartPx = if (layoutDirection == LayoutDirection.Ltr) {
        WindowInsets.safeDrawing.getLeft(density, layoutDirection)
    } else {
        WindowInsets.safeDrawing.getRight(density, layoutDirection)
    }
    val edgeInset = with(density) { maxOf(cutoutStartPx, safeStartPx).toDp() }
    return maxOf(baseStartPad, edgeInset + 16.dp)
}

@Stable
internal class PlayerScreenLockState {
    var locked by mutableStateOf(false)
        private set
    var hintVisible by mutableStateOf(false)
        private set
    var hintTick by mutableIntStateOf(0)
        private set

    fun lock() {
        locked = true
        hintVisible = true
        hintTick++
    }

    fun unlock() {
        locked = false
        hintVisible = false
    }

    fun reset() {
        locked = false
        hintVisible = false
    }

    fun toggleHint() {
        hintVisible = !hintVisible
        if (hintVisible) hintTick++
    }

    fun revealHint() {
        hintVisible = true
        hintTick++
    }

    fun hideHint() {
        hintVisible = false
    }
}

@Composable
internal fun rememberPlayerScreenLockState(
    fullscreen: Boolean,
    inPipMode: Boolean,
): PlayerScreenLockState {
    val state = remember { PlayerScreenLockState() }
    LaunchedEffect(fullscreen, inPipMode) {
        if (!fullscreen || inPipMode) state.reset()
    }
    LaunchedEffect(state.locked, state.hintVisible, state.hintTick) {
        if (state.locked && state.hintVisible) {
            delay(3000)
            state.hideHint()
        }
    }
    PlatformBackHandler(enabled = fullscreen && !inPipMode && state.locked) {
        state.revealHint()
    }
    return state
}

/**
 * 锁屏拦截层：吞掉所有滑动/双击/长按手势，仅在单击时切换解锁键的短暂显隐。
 */
@Composable
internal fun PlayerLockedTouchShield(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var moved = false
                    var multiTouch = false
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.size > 1) multiTouch = true
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > touchSlop) {
                            moved = true
                        }
                    }
                    if (!moved && !multiTouch) {
                        currentOnTap()
                    }
                }
            },
    )
}

/**
 * 全屏防误触锁屏悬浮键：位于画面左侧居中，未锁定时随控件栏显隐，锁定时仅在单击屏幕后短暂浮现。
 */
@Composable
internal fun BoxScope.PlayerScreenLockButton(
    visible: Boolean,
    locked: Boolean,
    startPad: Dp,
    onToggleLock: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = startPad),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (locked) Color(0xA6000000) else Color(0x88000000))
                .clickable(onClick = onToggleLock),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (locked) COUIIcons.Lock else COUIIcons.Unlock,
                contentDescription = if (locked) "解锁屏幕" else "锁定屏幕",
                modifier = Modifier.size(22.dp),
                tint = if (locked) COUITheme.colorScheme.primary else Color.White,
            )
        }
    }
}
