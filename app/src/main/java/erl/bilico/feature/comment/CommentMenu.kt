package erl.bilico.feature.comment

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.Comment
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun rememberCommentMenuItems(
    comment: Comment,
    enabled: Boolean,
    onRequestDelete: () -> Unit,
): List<CouiMenuItem> {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    return remember(comment.id, comment.content, enabled, context, clipboard) {
        if (!enabled) {
            emptyList()
        } else {
            buildList {
                if (comment.content.isNotBlank()) {
                    add(
                        CouiMenuItem(
                            label = "复制评论",
                            onClick = {
                                clipboard.setText(AnnotatedString(comment.content))
                                Toast.makeText(context, "已复制评论", Toast.LENGTH_SHORT).show()
                            },
                        ),
                    )
                }
                add(CouiMenuItem("删除评论", onRequestDelete, group = if (isEmpty()) 0 else 1))
            }
        }
    }
}

@Composable
internal fun CommentMoreButton(
    menuItems: List<CouiMenuItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = true },
                )
                .semantics { contentDescription = "更多" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = COUIIcons.More,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }

        CouiPopupMenu(
            show = expanded,
            onDismiss = { expanded = false },
            items = menuItems,
        )
    }
}

@Composable
internal fun DeleteCommentConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ConfirmDialog(
        title = "删除评论",
        message = "确定删除这条评论吗？删除后无法恢复。",
        confirmText = "删除",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
