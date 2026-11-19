package erl.bilico.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    val colors = COUITheme.colorScheme
    val textStyles = COUITheme.textStyles
    val dimens = COUITheme.dimens
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
        COUITheme(colors = colors, textStyles = textStyles, dimens = dimens) {
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
}

/**
 * 评论与回复输入弹窗：沿用 Cobo 的 [OverlayDialog] + [TextFieldMode.Rectangle]。
 */
@Composable
fun InputDialog(
    title: String,
    hint: String,
    initial: String = "",
    confirmText: String = "发送",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    OverlayDialog(
        show = show,
        title = title,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val value = result
                if (value != null) onConfirm(value) else onDismiss()
            }
        },
    ) {
        Column {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
                label = hint,
                backgroundMode = TextFieldMode.Rectangle,
                maxLines = 6,
                minLines = 3,
            )
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = confirmText,
                    enabled = text.isNotBlank(),
                    onClick = {
                        if (text.isNotBlank()) {
                            result = text.trim()
                            show = false
                        }
                    },
                ),
            )
        }
    }
}

/**
 * COUI 标准单行输入对话框：对齐 ColorOS / COUI `SingleInputDialog` 形态
 * （`OverlayDialog` + `TextFieldMode.Line` 单行带清除按钮 + `DialogButtonBar`）。
 */
@Composable
fun SingleInputDialog(
    title: String,
    hint: String,
    summary: String? = null,
    initial: String = "",
    confirmText: String = "确定",
    topContent: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var settled by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { show = true }
    LaunchedEffect(show) {
        if (show) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        messageBottomPadding = DialogDefaults.MessageBottomPaddingWithCustomContent,
        defaultWindowInsetsPadding = true,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val value = result
                if (value != null) onConfirm(value) else onDismiss()
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            topContent?.invoke()
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                    .focusRequester(focusRequester),
                label = hint,
                backgroundMode = TextFieldMode.Line,
                singleLine = true,
                showClearButton = true,
            )
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = DialogButtonBarAction(
                    text = confirmText,
                    enabled = text.isNotBlank(),
                    recommended = true,
                    onClick = {
                        if (text.isNotBlank()) {
                            result = text.trim()
                            show = false
                        }
                    },
                ),
            )
        }
    }
}
