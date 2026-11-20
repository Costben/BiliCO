package erl.bilico.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import io.github.suqi8.coui.kmp.utils.PredictiveBackDragTracker
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 页面跟着手指往回收的返回动画；根页面不注册，交给系统播退回桌面的动画。 */
@Composable
fun BiliCoPredictiveBackHandler(nav: NavState, enabled: Boolean = true) {
    val maxFlingSpeedPx = with(LocalDensity.current) { PredictiveBackMotionDefaults.MaxFlingSpeed.toPx() }
    PredictiveBackHandler(enabled = enabled && nav.stack.count { !it.exiting } > 1) { events ->
        val entry = nav.stack.lastOrNull { !it.exiting }
        if (entry == null || nav.stack.count { !it.exiting } <= 1) {
            events.collect { }
            return@PredictiveBackHandler
        }

        var lastProgress = 1f
        var moved = false
        entry.previewing = true
        nav.preparePredictivePop(entry)
        val cardCollapse = entry.expand && !entry.minimizingToOrb
        // 缩回卡片或缩进融合球都跟手，统一由开关控制。
        val trackFinger = entry.expand && nav.predictiveBackFollow()
        val dragTracker = if (trackFinger) PredictiveBackDragTracker() else null
        try {
            events.collect { event ->
                val raw = event.progress
                val value = if (cardCollapse) (1f - raw / (1f - LandedProgress)).coerceIn(0f, 1f) else (1f - raw).coerceIn(0f, 1f)
                if (value < 0.98f) moved = true
                lastProgress = value
                entry.progress.snapTo(value)
                if (entry.expand) entry.fade.snapTo(value)
                if (dragTracker != null) {
                    entry.offset.snapTo(dragTracker.record(event.touchX, event.touchY, event.frameTimeMillis))
                }
            }
            val commitVelocity = dragTracker?.velocityPxPerSec(maxFlingSpeedPx) ?: Offset.Zero
            finishPredictivePop(nav, entry, lastProgress, moved, commitVelocity)
        } catch (cancelled: CancellationException) {
            val landed = if (cardCollapse) lastProgress <= 0.05f else lastProgress <= LandedProgress
            if (landed && moved) {
                val cancelVelocity = dragTracker?.velocityPxPerSec(maxFlingSpeedPx) ?: Offset.Zero
                finishPredictivePop(nav, entry, lastProgress, moved, cancelVelocity)
                return@PredictiveBackHandler
            }
            // 松手太早：让页面顺着手指弹回去。
            withContext(NonCancellable) {
                coroutineScope {
                    launch { entry.progress.animateTo(1f, ExpandCancelSpringSpec) }
                    if (entry.expand) launch { entry.fade.animateTo(1f, tween(200, easing = LinearEasing)) }
                    if (entry.offset.value != Offset.Zero) launch { entry.returnFlingOffset() }
                }
                nav.cancelPredictivePop(entry)
            }
            throw cancelled
        }
    }
}
