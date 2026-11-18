package erl.bilico.feature.video

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 横屏全屏播放时嵌在底部控制行里的弹幕输入框。
 *
 * 与详情栏展开的弹幕输入框同为 32dp 胶囊造型，但去掉磨砂、投影、焦散光与展开动画，
 * 只保留简单扁平的 1dp 细线框，放在时间与右侧字幕/倍速按钮之间。
 */
@Composable
internal fun FullscreenDanmakuComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // 输入框被控件条收起时直接销毁，这时也要把「正在输入」告诉外面，否则控件条会一直不自动隐藏。
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)
    DisposableEffect(Unit) {
        onDispose { currentOnFocusChange(false) }
    }

    val canSend = draft.isNotBlank()
    val submit = {
        if (canSend) {
            keyboard?.hide()
            focusManager.clearFocus()
            onSend()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(STAGE_COMPOSER_HEIGHT)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { onDraftChange(it.take(MAX_DANMAKU_LENGTH)) },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = COUITheme.textStyles.footnote1.copy(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "发弹幕",
                            style = COUITheme.textStyles.footnote1,
                            color = Color.White.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Text(
            text = "发送",
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = canSend, onClick = { submit() })
                .padding(horizontal = 10.dp, vertical = 5.dp),
            style = COUITheme.textStyles.footnote1,
            color = if (canSend) Color.White else Color.White.copy(alpha = 0.35f),
        )
    }
}

/** 与外部详情栏弹幕胶囊同高，可居中嵌进 40dp 高的底部控制行。 */
private val STAGE_COMPOSER_HEIGHT = 32.dp

private const val MAX_DANMAKU_LENGTH = 100
