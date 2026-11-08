package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 播放器控件条压暗底色：视频本身可能是亮的，白字要能读清。 */
internal val PlayerScrim = Color(0x66000000)

/** 播放器底部控件条的通用内边距。 */
internal val PlayerControlsPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

/**
 * 弹幕开关，图标取自 PiliPlus 的 dm_on / dm_off 字形。
 * 传了 [onOpenSettings] 时在左边多出一个弹幕设置入口，分隔线跟着一起出现。
 */
@Composable
internal fun DanmakuToggle(
    enabled: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val scheme = COUITheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onOpenSettings != null) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(1.dp)
                    .height(14.dp)
                    .background(scheme.dividerLine),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenSettings,
                    )
                    .semantics {
                        contentDescription = "弹幕设置"
                    },
                contentAlignment = Alignment.Center,
            ) {
                PiliIcon(
                    glyph = PiliGlyph.DmSettings,
                    contentDescription = null,
                    size = 17.dp,
                    tint = scheme.onSurfaceSecondary,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .semantics {
                    contentDescription = if (enabled) "关闭弹幕" else "开启弹幕"
                },
            contentAlignment = Alignment.Center,
        ) {
            PiliIcon(
                glyph = if (enabled) PiliGlyph.DmOn else PiliGlyph.DmOff,
                contentDescription = null,
                size = 18.dp,
                tint = if (enabled) scheme.primary else scheme.onSurfaceSecondary,
            )
        }
    }
}
