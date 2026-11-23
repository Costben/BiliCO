package erl.bilico.feature.comment

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.Comment
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.theme.COUITheme

internal val LocalCommentTarget = compositionLocalOf<Pair<Long, Int>?> { null }

@Composable
internal fun rememberCommentMenuItems(
    comment: Comment,
    enabled: Boolean,
    onRequestDelete: () -> Unit,
): List<CouiMenuItem> {
    val context = LocalContext.current
    val container = erl.bilico.di.LocalAppContainer.current
    val commentTarget = LocalCommentTarget.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var showReport by remember(comment.id) { mutableStateOf(false) }
    var showKeyword by remember(comment.id) { mutableStateOf(false) }

    if (showReport && commentTarget != null && commentTarget.first > 0L && comment.id > 0L) {
        erl.bilico.ui.components.ReportDialog(
            target = erl.bilico.ui.components.ReportTarget.Comment(
                oid = commentTarget.first,
                rpid = comment.id,
                mid = comment.mid,
                userName = comment.userName,
                avatar = comment.avatar,
                type = commentTarget.second,
            ),
            onDismiss = { showReport = false },
        )
    }
    if (showKeyword) {
        erl.bilico.ui.components.KeywordBlockDialog(
            allowedScopes = listOf(
                erl.bilico.data.repository.BlockKeywordScope.COMMENT,
                erl.bilico.data.repository.BlockKeywordScope.UP_NAME,
            ),
            initialScope = erl.bilico.data.repository.BlockKeywordScope.COMMENT,
            scopeInitialKeywords = mapOf(
                erl.bilico.data.repository.BlockKeywordScope.COMMENT to comment.content,
                erl.bilico.data.repository.BlockKeywordScope.UP_NAME to comment.userName,
            ),
            onDismiss = { showKeyword = false },
        )
    }

    return remember(comment, enabled, container, commentTarget, context, clipboard) {
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
            if (container != null) {
                if (comment.mid > 0L) {
                    add(
                        CouiMenuItem(
                            label = "屏蔽该用户",
                            group = 1,
                            onClick = {
                                container.blocks.blockUserLocally(
                                    comment.mid,
                                    comment.userName,
                                    comment.avatar,
                                )
                                Toast.makeText(context, "已屏蔽 ${comment.userName}", Toast.LENGTH_SHORT).show()
                            },
                        ),
                    )
                }
                add(CouiMenuItem("按关键词屏蔽", onClick = { showKeyword = true }, group = 1))
                if (commentTarget != null && commentTarget.first > 0L && comment.id > 0L) {
                    add(CouiMenuItem("举报评论", onClick = { showReport = true }, group = 1))
                }
            }
            if (enabled) {
                add(CouiMenuItem("删除评论", onRequestDelete, group = 2))
            }
        }
    }
}

@Composable
internal fun CommentMoreButton(
    menuItems: List<CouiMenuItem>,
    modifier: Modifier = Modifier,
    tint: Color = COUITheme.colorScheme.onSurfaceSecondary,
    boxSize: Dp = 24.dp,
    iconSize: Dp = 18.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(boxSize)
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
                modifier = Modifier.size(iconSize),
                tint = tint,
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
