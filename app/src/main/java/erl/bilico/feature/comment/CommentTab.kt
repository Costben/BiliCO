package erl.bilico.feature.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import erl.bilico.ui.components.LoadingBox
import erl.bilico.ui.components.MessageState
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun CommentTab(
    container: AppContainer,
    oid: Long,
    modifier: Modifier = Modifier,
) {
    val viewModel: CommentViewModel = viewModel(key = "comment-$oid") {
        CommentViewModel(container.comments, oid)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var replyTarget by remember { mutableStateOf<Comment?>(null) }

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

    Box(modifier) {
        when {
            state.isLoading && state.items.isEmpty() -> LoadingBox()

            state.items.isEmpty() -> MessageState(
                message = state.error ?: "还没有评论",
                modifier = Modifier.fillMaxSize(),
                action = if (state.error != null) {
                    { Button(onClick = viewModel::refresh) { Text("重试") } }
                } else {
                    null
                },
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                        onOpenReplies = { replyTarget = it },
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
    }

    val target = replyTarget
    if (target != null) {
        ReplyDialog(
            container = container,
            oid = oid,
            comment = target,
            onDismiss = { replyTarget = null },
        )
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    onOpenReplies: (Comment) -> Unit,
) {
    var expanded by remember(comment.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val avatarPx = with(density) { 36.dp.roundToPx() }

    Row(modifier = Modifier.fillMaxWidth()) {
        CoverImage(
            url = Formatters.sizedImageUrl(comment.avatar, avatarPx, avatarPx),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName,
                    modifier = Modifier.weight(1f, fill = false),
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

            Text(
                text = comment.content.ifBlank { "[图片评论]" },
                modifier = Modifier.clickable { expanded = !expanded },
                style = COUITheme.textStyles.body2,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.like > 0) {
                    Text(
                        text = "赞 ${Formatters.formatStat(comment.like.toLong())}",
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                    Spacer(Modifier.width(16.dp))
                }
                if (comment.replyCount > 0) {
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
                        Text(
                            text = "${reply.userName}：${reply.content}",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
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
