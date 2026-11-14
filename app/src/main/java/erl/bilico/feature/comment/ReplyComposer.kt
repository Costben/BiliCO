package erl.bilico.feature.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.Comment
import erl.bilico.ui.components.CompactSendButton
import erl.bilico.ui.components.COUISmoothCapsuleShape
import erl.bilico.ui.components.capsuleMaterial
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 评论详情底部输入胶囊与发送按钮共用的高度。 */
private val COMPOSER_HEIGHT = 36.dp

/** 评论详情底部的输入行：正在回复对象提示 + 胶囊输入框 + 发送按钮。 */
@Composable
internal fun ReplyComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTarget: Comment?,
    onClearReplyTarget: () -> Unit,
    isSending: Boolean,
    isDark: Boolean,
    focusRequester: FocusRequester,
    onSend: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    replyTarget?.let { target ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "正在回复 @${target.userName}",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "取消",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier
                    .clickable(onClick = onClearReplyTarget)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(COMPOSER_HEIGHT)
                .capsuleMaterial(isDark)
                .background(
                    color = COUITheme.colorScheme.surfaceContainer,
                    shape = COUISmoothCapsuleShape,
                )
                .clip(COUISmoothCapsuleShape),
        ) {
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(COMPOSER_HEIGHT)
                    .focusRequester(focusRequester),
                label = if (replyTarget != null) {
                    "回复 @${replyTarget.userName} :"
                } else {
                    "发一条友善的评论"
                },
                backgroundMode = TextFieldMode.None,
                insideMargin = DpSize(14.dp, 4.dp),
                singleLine = true,
            )
        }
        Spacer(Modifier.width(8.dp))
        CompactSendButton(
            text = if (isSending) "发送中" else "发送",
            onClick = onSend,
            enabled = draft.isNotBlank() && !isSending,
            height = COMPOSER_HEIGHT,
            containerColor = COUITheme.colorScheme.surfaceContainer,
            isDark = isDark,
        )
    }
}
