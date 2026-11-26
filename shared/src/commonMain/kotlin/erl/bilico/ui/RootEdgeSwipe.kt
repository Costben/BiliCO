package erl.bilico.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import erl.bilico.platform.BackDispatcher
import io.github.suqi8.coui.kmp.utils.PredictiveBackDragTracker
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * iOS 左侧 28dp 边缘交互式跟手返回浮层。
 * 仅在 `useEdgeSwipeBackOverlay == true` 的平台挂载，避免在 Android 上与系统预测性返回冲突。
 */
@Composable
internal fun EdgeSwipeBackOverlay(
    widthPx: Float,
    nav: NavState,
    backDispatcher: BackDispatcher,
    scope: CoroutineScope,
) {
    val density = LocalDensity.current
    val maxFlingSpeedPx = with(density) { PredictiveBackMotionDefaults.MaxFlingSpeed.toPx() }
    val flingCommitVelocityPx = with(density) { 450.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .zIndex(4.5f)
            .pointerInput(nav, widthPx, maxFlingSpeedPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val startPos = down.position
                    var started = false
                    var lastChange = down

                    while (!started) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        lastChange = change
                        val dx = change.position.x - startPos.x
                        val dy = change.position.y - startPos.y
                        if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.15f) {
                            return@awaitEachGesture
                        }
                        if (dx > touchSlop && dx >= abs(dy) * 0.7f) {
                            started = true
                            change.consume()
                        }
                    }

                    if (backDispatcher.dispatch()) return@awaitEachGesture
                    val entry = nav.stack.lastOrNull { !it.exiting } ?: return@awaitEachGesture
                    if (nav.stack.count { !it.exiting } <= 1) return@awaitEachGesture

                    var lastProgress = 1f
                    var moved = false
                    entry.previewing = true
                    nav.preparePredictivePop(entry)
                    val cardCollapse = entry.expand && !entry.minimizingToOrb
                    val trackFinger = entry.expand && nav.predictiveBackFollow()
                    val dragTracker = if (trackFinger) PredictiveBackDragTracker() else null
                    dragTracker?.record(startPos.x, startPos.y, down.uptimeMillis)

                    var prevX = lastChange.position.x
                    var prevT = lastChange.uptimeMillis
                    var lastVx = 0f

                    fun applyPointer(pos: Offset, uptimeMs: Long) {
                        val dt = (uptimeMs - prevT).coerceAtLeast(1L)
                        val vx = (pos.x - prevX) * 1000f / dt
                        lastVx = lastVx * 0.4f + vx * 0.6f
                        prevX = pos.x
                        prevT = uptimeMs

                        val raw = ((pos.x - startPos.x) / (widthPx * 0.65f)).coerceIn(0f, 1f)
                        val value = if (cardCollapse) {
                            (1f - raw / (1f - LandedProgress)).coerceIn(0f, 1f)
                        } else {
                            (1f - raw).coerceIn(0f, 1f)
                        }
                        if (value < 0.98f) moved = true
                        lastProgress = value
                        val followOffset = dragTracker?.record(pos.x, pos.y, uptimeMs)
                        scope.launch {
                            entry.progress.snapTo(value)
                            if (entry.expand) entry.fade.snapTo(value)
                            if (followOffset != null) entry.offset.snapTo(followOffset)
                        }
                    }

                    applyPointer(lastChange.position, lastChange.uptimeMillis)

                    var canceled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null) {
                            canceled = true
                            break
                        }
                        if (!change.pressed) {
                            if (change.positionChanged()) {
                                applyPointer(change.position, change.uptimeMillis)
                                change.consume()
                            }
                            break
                        }
                        if (change.positionChanged()) {
                            applyPointer(change.position, change.uptimeMillis)
                            change.consume()
                        }
                    }

                    val commitVelocity = dragTracker?.velocityPxPerSec(maxFlingSpeedPx) ?: Offset.Zero
                    val shouldCommit = !canceled && (
                        lastProgress <= 0.68f ||
                            (moved && lastVx >= flingCommitVelocityPx)
                        )
                    val landed = if (cardCollapse) lastProgress <= 0.05f else lastProgress <= LandedProgress
                    if (shouldCommit || (landed && moved)) {
                        finishPredictivePop(nav, entry, lastProgress, moved, commitVelocity)
                    } else {
                        scope.launch {
                            coroutineScope {
                                launch { entry.progress.animateTo(1f, ExpandCancelSpringSpec) }
                                if (entry.expand) {
                                    launch { entry.fade.animateTo(1f, tween(200, easing = LinearEasing)) }
                                }
                                if (entry.offset.value != Offset.Zero) {
                                    launch { entry.returnFlingOffset() }
                                }
                            }
                            nav.cancelPredictivePop(entry)
                        }
                    }
                }
            },
    )
}
