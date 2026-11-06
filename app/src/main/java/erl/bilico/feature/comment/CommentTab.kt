package erl.bilico.feature.comment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.Comment
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.InputDialog
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.RichCommentText
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Send
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

private sealed interface ComposeTarget {
    data object Main : ComposeTarget
    data class Reply(val root: Comment) : ComposeTarget
}

@Composable
fun CommentTab(
    container: AppContainer,
    oid: Long,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
    type: Int = 1,
) {
    val viewModel: CommentViewModel = viewModel(key = "comment-$type-$oid") {
        CommentViewModel(container.comments, oid, type)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var dialogRoot by remember { mutableStateOf<Comment?>(null) }
    var composeTarget by remember { mutableStateOf<ComposeTarget?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    Column(modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> LoadingState()

                state.items.isEmpty() -> if (state.error != null) {
                    ErrorState(state.error.orEmpty(), onRetry = viewModel::refresh)
                } else {
                    EmptyState("还没有评论")
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "header") {
                        Text(
                            text = "评论 ${Formatters.formatStat(state.total.toLong())}",
                            style = COUITheme.textStyles.body1,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    items(state.items, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            onLike = {
                                if (!container.session.isLoggedIn()) {
                                    viewModel.showMessage("请先登录")
                                } else {
                                    viewModel.toggleLike(comment)
                                }
                            },
                            onReply = {
                                if (!container.session.isLoggedIn()) {
                                    viewModel.showMessage("请先登录")
                                } else {
                                    composeTarget = ComposeTarget.Reply(comment)
                                }
                            },
                            onOpenReplies = { dialogRoot = it },
                            onOpenUser = onOpenUser,
                        )
                    }
                    when {
                        state.isLoadingMore -> item(key = "more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    size = 18.dp,
                                )
                            }
                        }

                        state.isEnd -> item(key = "end") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "没有更多评论了",
                                    style = COUITheme.textStyles.footnote2,
                                    color = COUITheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
            }

            state.message?.let { toast ->
                Text(
                    text = toast,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = Color.White,
                )
            }

            // 评论入口改成右下角悬浮球：按下缩一圈，松手带一点回弹。
            val ballInteraction = remember { MutableInteractionSource() }
            val ballPressed by ballInteraction.collectIsPressedAsState()
            val ballScale = remember { Animatable(1f) }
            LaunchedEffect(ballPressed) {
                ballScale.animateTo(
                    targetValue = if (ballPressed) 0.94f else 1f,
                    animationSpec = if (ballPressed) {
                        spring(dampingRatio = 0.55f, stiffness = 1400f)
                    } else {
                        spring(dampingRatio = 0.34f, stiffness = 420f)
                    },
                )
            }
            FloatingActionButton(
                onClick = {
                    if (!container.session.isLoggedIn()) {
                        viewModel.showMessage("请先登录")
                    } else {
                        composeTarget = ComposeTarget.Main
                    }
                },
                interactionSource = ballInteraction,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .graphicsLayer {
                        scaleX = ballScale.value
                        scaleY = ballScale.value
                    },
            ) {
                Icon(
                    imageVector = COUIIcons.Send,
                    contentDescription = "发送评论",
                    modifier = Modifier.size(24.dp),
                    tint = COUITheme.colorScheme.onPrimary,
                )
            }
        }
    }

    when (val target = composeTarget) {
        ComposeTarget.Main -> InputDialog(
            title = "发表评论",
            hint = "发一条友善的评论",
            confirmText = "发送",
            onDismiss = { composeTarget = null },
            onConfirm = { text ->
                composeTarget = null
                viewModel.postComment(text)
            },
        )

        is ComposeTarget.Reply -> InputDialog(
            title = "回复 @${target.root.userName}",
            hint = "写回复",
            confirmText = "发送",
            onDismiss = { composeTarget = null },
            onConfirm = { text ->
                composeTarget = null
                viewModel.postReply(target.root, text)
            },
        )

        null -> Unit
    }

    val root = dialogRoot
    if (root != null) {
        ReplyDialog(
            container = container,
            oid = oid,
            comment = state.items.firstOrNull { it.id == root.id } ?: root,
            type = type,
            onSubReplyAdded = { created -> viewModel.onSubReplyAdded(root.id, created) },
            onDismiss = { dialogRoot = null },
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onOpenReplies: (Comment) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    var expanded by remember(comment.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val avatarPx = with(density) { 36.dp.roundToPx() }
    val openUser = remember(comment.mid, onOpenUser) {
        { if (comment.mid > 0L) onOpenUser(comment.mid) }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        CoverImage(
            url = Formatters.sizedImageUrl(comment.avatar, avatarPx, avatarPx),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = openUser),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(onClick = openUser),
                    style = COUITheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comment.level > 0) {
                    Text(
                        text = "Lv${comment.level}",
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(COUITheme.colorScheme.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 4.dp),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = Formatters.formatTimeAgo(comment.timeSec),
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }

            Spacer(Modifier.height(4.dp))

            RichCommentText(
                content = comment.content,
                emotes = comment.emotes,
                modifier = Modifier.clickable { expanded = !expanded },
                style = COUITheme.textStyles.body2,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
            )

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val likeLabel = when {
                    comment.liked && comment.like > 0 ->
                        "已赞 ${Formatters.formatStat(comment.like.toLong())}"
                    comment.liked -> "已赞"
                    comment.like > 0 -> "赞 ${Formatters.formatStat(comment.like.toLong())}"
                    else -> "赞"
                }
                Text(
                    text = likeLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (comment.liked) {
                                COUITheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(onClick = onLike)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = COUITheme.textStyles.footnote2,
                    fontWeight = if (comment.liked) FontWeight.Medium else FontWeight.Normal,
                    color = if (comment.liked) {
                        COUITheme.colorScheme.primary
                    } else {
                        COUITheme.colorScheme.onSurfaceSecondary
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "回复",
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onReply)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
                if (comment.replyCount > 0) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "查看 ${comment.replyCount} 条回复",
                        modifier = Modifier.clickable { onOpenReplies(comment) },
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.primary,
                    )
                }
            }

            if (comment.replies.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(COUITheme.colorScheme.surfaceVariant)
                        .clickable { onOpenReplies(comment) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    comment.replies.take(2).forEach { reply ->
                        RichCommentText(
                            content = reply.content,
                            emotes = reply.emotes,
                            prefixUserName = reply.userName,
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 3,
                        )
                    }
                    if (comment.replyCount > comment.replies.take(2).size) {
                        Text(
                            text = "展开更多回复",
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
