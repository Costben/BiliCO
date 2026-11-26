package erl.bilico.feature.chat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.CommentEmote
import erl.bilico.feature.comment.CapsuleComposerBar
import io.github.suqi8.coui.kmp.blur.Backdrop

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
    CapsuleComposerBar(
        value = value,
        onValueChange = onValueChange,
        emotes = emotes,
        placeholder = "发送私信...",
        onSend = onSend,
        sendEnabled = value.text.isNotBlank(),
        isSending = isSending,
        emotePanelOpen = emotePanelOpen,
        onToggleEmote = onToggleEmote,
        onPickImage = onPickImage,
        focusRequester = focusRequester,
        interactionSource = interactionSource,
        backdrop = backdrop,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
    )
}
