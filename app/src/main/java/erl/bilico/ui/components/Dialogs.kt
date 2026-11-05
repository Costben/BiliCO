package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog

/**
 * 统一确认弹窗。点击按钮先收窗，收完再回调，避免弹窗关闭动画还没走就把页面拆了。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确定",
) {
    var show by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    OverlayDialog(
        show = show,
        title = title,
        summary = message,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                if (confirmed) onConfirm() else onDismiss()
            }
        },
    ) {
        DialogButtonBar(
            negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
            positive = DialogButtonBarAction(
                text = confirmText,
                onClick = {
                    confirmed = true
                    show = false
                },
            ),
        )
    }
}
