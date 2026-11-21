package erl.bilico.feature.comment

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import erl.bilico.ui.ExpandEasing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * 评论详情页的预测性返回跟手状态。
 *
 * 侧滑返回时整页顺着手指往下退，松手够远就提交关闭，没够远则滑回原位。
 * 返回值 1 表示停在原位，0 表示已经退到屏幕外；页面把它映射成位移即可。
 */
@Composable
internal fun rememberReplyDetailBackFollow(
    enabled: Boolean,
    onClose: () -> Unit,
): Animatable<Float, AnimationVector1D> {
    val follow = remember { Animatable(1f) }
    val close by rememberUpdatedState(onClose)
    PredictiveBackHandler(enabled = enabled) { events ->
        var last = 1f
        var moved = false
        try {
            events.collect { event ->
                val value = (1f - event.progress).coerceIn(0f, 1f)
                if (value < 0.98f) moved = true
                last = value
                follow.snapTo(value)
            }
            // 手势已提交：先把剩余位移补到底再真正关页，避免接上退场动画时跳一帧。
            if (last > 0.02f) follow.animateTo(0f, tween(durationMillis = 160, easing = ExpandEasing))
            close()
        } catch (cancelled: CancellationException) {
            // 已经拉到快见底才被系统取消，按提交处理，别把页面又推回来。
            if (last <= 0.2f && moved) {
                withContext(NonCancellable) { follow.snapTo(0f) }
                close()
                return@PredictiveBackHandler
            }
            withContext(NonCancellable) {
                follow.animateTo(1f, tween(durationMillis = 220, easing = ExpandEasing))
            }
            throw cancelled
        }
    }
    return follow
}
