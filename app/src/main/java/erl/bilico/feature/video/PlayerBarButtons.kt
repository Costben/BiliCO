package erl.bilico.feature.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.PiliIcon
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More

/** 播放器顶栏的图标按钮：40dp 触摸区，字形四周留 9dp。 */
@Composable
internal fun PlayerIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        tint = tint,
    )
}

/** 字体图标按钮：字形占满 40dp 的触摸区，按下感与图标按钮一致。 */
@Composable
internal fun PlayerGlyphButton(
    glyph: String,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PiliIcon(
            glyph = glyph,
            contentDescription = label,
            size = 20.dp,
            tint = tint,
        )
    }
}

/**
 * 「自动连播」这一行：右侧一个复选框表示开没开，菜单不收起，勾完立刻能看见状态。
 * 放在顶栏菜单里「稍后再看」的上面。
 */
@Composable
internal fun autoPlayNextMenuItem(checked: Boolean, onToggle: () -> Unit): CouiMenuItem = CouiMenuItem(
    label = "自动连播",
    onClick = onToggle,
    keepOpen = true,
    hint = {
        // 菜单在 hint 后面还留着一个透明的选中图标位（4dp + 24dp），把它让出来的距离补掉，
        // 复选框才会贴着菜单右边缘，而不是停在中间。
        Checkbox(
            state = ToggleableState(checked),
            onClick = { onToggle() },
            modifier = Modifier.offset(x = 28.dp),
        )
    },
)

/** 顶栏右端的三个点：弹幕设置、自动连播、稍后再看与分享。 */
@Composable
internal fun PlayerMoreMenu(
    onDanmakuSettings: (() -> Unit)?,
    onWatchLater: (() -> Unit)?,
    onShare: (() -> Unit)?,
    autoPlayNext: Boolean = true,
    onToggleAutoPlayNext: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        PlayerIconButton(COUIIcons.More, "更多", onClick = { expanded = true })
        CouiPopupMenu(
            show = expanded,
            onDismiss = { expanded = false },
            items = buildList {
                onDanmakuSettings?.let { add(CouiMenuItem("弹幕设置", it)) }
                onToggleAutoPlayNext?.let { add(autoPlayNextMenuItem(autoPlayNext, it)) }
                onWatchLater?.let { add(CouiMenuItem("稍后再看", it)) }
                onShare?.let { add(CouiMenuItem("分享", it)) }
            },
        )
    }
}
