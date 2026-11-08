package erl.bilico.feature.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.PiliIcon
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

/** 顶栏右端的三个点：弹幕设置、稍后再看与分享。 */
@Composable
internal fun PlayerMoreMenu(
    onDanmakuSettings: (() -> Unit)?,
    onWatchLater: (() -> Unit)?,
    onShare: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        PlayerIconButton(COUIIcons.More, "更多", onClick = { expanded = true })
        CouiPopupMenu(
            show = expanded,
            onDismiss = { expanded = false },
            items = buildList {
                onDanmakuSettings?.let { add(CouiMenuItem("弹幕设置", it)) }
                onWatchLater?.let { add(CouiMenuItem("稍后再看", it)) }
                onShare?.let { add(CouiMenuItem("分享", it)) }
            },
        )
    }
}
