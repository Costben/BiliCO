package erl.bilico.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.di.AppContainer
import erl.bilico.platform.formatFixedDecimal
import erl.bilico.update.UpdateInfo
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

@Composable
fun UpdateDialog(
    container: AppContainer,
    info: UpdateInfo,
    preview: Boolean = false,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var browserDownload by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { show = true }
    val haptic = LocalHapticFeedback.current
    val textMargin = DialogDefaults.insideMargin.width
    val scrollState = rememberScrollState()

    OverlayDialog(
        show = show,
        title = "发现新版本 v${info.version}",
        summary = if (info.prerelease) "预发布版本" else "正式版本",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                when {
                    browserDownload -> onDownload()
                    skip -> onSkip()
                    else -> onDismiss()
                }
            }
        },
    ) {
        Column {
            if (info.notes.isNotBlank()) {
                MarkdownText(
                    markdown = info.notes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            if (scrollState.canScrollForward) {
                                val fadePx = 28.dp.toPx().coerceAtMost(size.height)
                                val topY = size.height - fadePx
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = topY,
                                        endY = size.height,
                                    ),
                                    topLeft = Offset(0f, topY),
                                    size = Size(size.width, fadePx),
                                    blendMode = BlendMode.DstOut,
                                )
                            }
                        }
                        .scrollEndHaptic()
                        .verticalScroll(scrollState)
                        .padding(horizontal = textMargin),
                )
            }
            if (!preview) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = textMargin, top = 12.dp)
                        .toggleable(
                            value = skip,
                            interactionSource = null,
                            indication = null,
                            role = Role.Checkbox,
                            onValueChange = {
                                skip = it
                                haptic.performHapticFeedback(
                                    if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                                )
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(state = ToggleableState(skip), onClick = null)
                    Text(
                        "跳过本次更新",
                        fontSize = 14.sp,
                        color = COUITheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(
                    text = "稍后",
                    onClick = { show = false },
                ),
                positive = DialogButtonBarAction(
                    text = "浏览器查看",
                    recommended = true,
                    onClick = {
                        browserDownload = true
                        show = false
                    },
                ),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes < 1024L) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${formatFixedDecimal(kb, 1)}KB"
    val mb = kb / 1024.0
    return "${formatFixedDecimal(mb, 2)}MB"
}
