package erl.bilico.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.anim.folmeSpring
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 弹幕输入行与评论详情共用的胶囊材质：
 * 0.5dp 微光描边，深色模式压到 8% 白、浅色模式 45% 白，与顶栏按键同一套。
 */
internal fun Modifier.capsuleMaterial(isDark: Boolean): Modifier = border(
    width = 0.5.dp,
    color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f),
    shape = COUISmoothCapsuleShape,
)

/**
 * 紧凑胶囊「发送」按钮：高度由调用方传入，用来与同一行的输入框严格对齐。
 * 按下缩到 0.89，连续曲率胶囊配微光材质，和发弹幕那一行是同一套观感。
 */
@Composable
internal fun CompactSendButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    height: Dp,
    containerColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (enabled && isPressed) 1f else 0f,
        animationSpec = remember {
            folmeSpring(damping = 1f, response = 0.3f, visibilityThreshold = 0.001f)
        },
        label = "compactSendPress",
    )
    val scale = 1f - 0.11f * pressProgress
    val contentColor = if (enabled) {
        COUITheme.colorScheme.onSurface
    } else {
        COUITheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .capsuleMaterial(isDark)
            .background(color = containerColor, shape = COUISmoothCapsuleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}
