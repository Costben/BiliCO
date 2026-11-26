package erl.bilico.feature.comment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import erl.bilico.platform.PlatformBackHandler

@Composable
internal fun rememberReplyDetailBackFollow(
    enabled: Boolean,
    onClose: () -> Unit,
): Animatable<Float, AnimationVector1D> {
    val follow = remember { Animatable(1f) }
    PlatformBackHandler(enabled = enabled, onBack = onClose)
    return follow
}
