package erl.bilico.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.CommentEmote
import erl.bilico.feature.comment.ComposerEmoteTextField
import erl.bilico.feature.comment.ComposerToolButton
import erl.bilico.ui.components.CompactSendButton
import erl.bilico.ui.components.EmojiIcon
import erl.bilico.ui.components.inputCapsuleGlass
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.theme.COUITheme


@Composable
internal fun ChatBottomBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    emotes: Map<String, CommentEmote>,
    onSend: () -> Unit,
    isSending: Boolean,
    emotePanelOpen: Boolean,
    onToggleEmote: () -> Unit,
    onPickImage: () -> Unit,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    backdrop: Backdrop?,
) {
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = CHAT_COMPOSER_HEIGHT)
                // 和发送按钮同一块 COUI 磨砂：反采样背后滚过去的消息，只漏色不漏形。
                .inputCapsuleGlass(
                    backdrop = backdrop,
                    isDark = isDark,
                    cornerRadius = CHAT_COMPOSER_HEIGHT / 2,
                )
                .clip(CHAT_COMPOSER_SHAPE),
        ) {
            ComposerEmoteTextField(
                value = value,
                onValueChange = onValueChange,
                emotes = emotes,
                maxHeight = CHAT_COMPOSER_MAX_HEIGHT,
                focusRequester = focusRequester,
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                label = "发送私信...",
                minLines = 1,
                insideMargin = DpSize(14.dp, 6.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                compact = true,
            )

            Row(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(bottom = 3.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerToolButton(
                    active = emotePanelOpen,
                    size = 30.dp,
                    cornerRadius = 15.dp,
                    onClick = onToggleEmote,
                ) {
                    Icon(
                        imageVector = EmojiIcon,
                        contentDescription = "表情",
                        modifier = Modifier.size(20.dp),
                        tint = if (emotePanelOpen) scheme.primary else scheme.onSurface,
                    )
                }

                Spacer(Modifier.width(2.dp))

                ComposerToolButton(
                    active = false,
                    size = 30.dp,
                    cornerRadius = 15.dp,
                    onClick = onPickImage,
                ) {
                    Icon(
                        imageVector = COUIIcons.Photos,
                        contentDescription = "图片",
                        modifier = Modifier.size(20.dp),
                        tint = scheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        CompactSendButton(
            text = if (isSending) "发送中" else "发送",
            onClick = onSend,
            enabled = value.text.isNotBlank() && !isSending,
            height = CHAT_COMPOSER_HEIGHT,
            containerColor = scheme.surfaceContainer,
            isDark = isDark,
            backdrop = backdrop,
        )
    }
}
