package erl.bilico.feature.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import erl.bilico.data.model.Comment
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.RichCommentText
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 楼中楼从底部弹出：支持分页查看、点赞子评论与直接回复。 */
@Composable
internal fun ReplyDialog(
    container: AppContainer,
    oid: Long,
    comment: Comment,
    type: Int = 1,
    onSubReplyAdded: (Comment) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var items by remember(comment.id) { mutableStateOf<List<Comment>>(emptyList()) }
    var page by remember(comment.id) { mutableIntStateOf(0) }
    var totalCount by remember(comment.id) { mutableIntStateOf(comment.replyCount) }
    var isLoading by remember(comment.id) { mutableStateOf(true) }
    var isEnd by remember(comment.id) { mutableStateOf(false) }
    var error by remember(comment.id) { mutableStateOf<String?>(null) }
    var replyTarget by remember(comment.id) { mutableStateOf<Comment?>(null) }
    var draft by remember(comment.id) { mutableStateOf("") }
    var isSending by remember(comment.id) { mutableStateOf(false) }
    var toast by remember(comment.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun showToast(message: String) {
        toast = message
        scope.launch {
            delay(2000)
            if (toast == message) toast = null
        }
    }

    fun loadNext() {
        if (isLoading && page > 0) return
        if (isEnd) return
        isLoading = true
        error = null
        scope.launch {
            val next = page + 1
            container.comments.replies(oid, comment.id, next, type = type)
                .onSuccess { list ->
                    page = next
                    items = (items + list).distinctBy { it.id }
                    isEnd = list.isEmpty()
                }
                .onFailure { failure ->
                    error = failure.message ?: "回复加载失败"
                }
            isLoading = false
        }
    }

    fun toggleLike(target: Comment) {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val nextLiked = !target.liked
        val nextLike = (target.like + if (nextLiked) 1 else -1).coerceAtLeast(0)
        items = items.map {
            if (it.id == target.id) it.copy(liked = nextLiked, like = nextLike) else it
        }
        scope.launch {
            container.comments.likeComment(oid = oid, rpid = target.id, liked = nextLiked, type = type)
                .onFailure { err ->
                    items = items.map { if (it.id == target.id) target else it }
                    showToast(err.message ?: "点赞失败")
                }
        }
    }

    fun sendReply() {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val text = draft.trim()
        if (text.isEmpty() || isSending) return
        val parentTarget = replyTarget ?: comment
        isSending = true
        scope.launch {
            container.comments.addComment(
                oid = oid,
                message = text,
                root = comment.id,
                parent = parentTarget.id,
                type = type,
            )
                .onSuccess { created ->
                    isSending = false
                    draft = ""
                    replyTarget = null
                    totalCount += 1
                    items = (items + created).distinctBy { it.id }
                    onSubReplyAdded(created)
                    showToast("回复已发送")
                }
                .onFailure { err ->
                    isSending = false
                    showToast(err.message ?: "回复失败")
                }
        }
    }

    LaunchedEffect(comment.id) {
        if (items.isEmpty()) loadNext()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible != null && items.isNotEmpty() && lastVisible >= items.size - 2) {
                    loadNext()
                }
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(COUITheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "回复（${maxOf(totalCount, items.size)}）",
                        style = COUITheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = COUIIcons.Close,
                        contentDescription = "关闭",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss)
                            .padding(6.dp),
                        tint = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
                HorizontalDivider(color = COUITheme.colorScheme.dividerLine)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        items.isEmpty() && isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(size = 22.dp)
                        }

                        items.isEmpty() && error != null -> ErrorState(
                            message = error.orEmpty(),
                            modifier = Modifier.fillMaxSize(),
                            onRetry = { loadNext() },
                        )

                        items.isEmpty() -> EmptyState("还没有回复")

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(items, key = { it.id }) { reply ->
                                ReplyRow(
                                    reply = reply,
                                    onLike = { toggleLike(reply) },
                                    onReply = { replyTarget = reply },
                                )
                            }
                            if (isLoading) {
                                item(key = "loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(size = 16.dp)
                                    }
                                }
                            }
                        }
                    }

                    toast?.let { msg ->
                        Text(
                            text = msg,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC000000))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = COUITheme.textStyles.footnote2,
                            color = Color.White,
                        )
                    }
                }

                HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    replyTarget?.let { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
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
                                    .clickable { replyTarget = null }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            label = "回复 @${(replyTarget ?: comment).userName}",
                            backgroundMode = TextFieldMode.Rectangle,
                            maxLines = 3,
                            minLines = 1,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            text = if (isSending) "发送中" else "发送",
                            enabled = draft.isNotBlank() && !isSending,
                            onClick = { sendReply() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(
    reply: Comment,
    onLike: () -> Unit,
    onReply: () -> Unit,
) {
    val density = LocalDensity.current
    val avatarPx = with(density) { 30.dp.roundToPx() }

    Row(modifier = Modifier.fillMaxWidth()) {
        CoverImage(
            url = Formatters.sizedImageUrl(reply.avatar, avatarPx, avatarPx),
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.userName,
                    modifier = Modifier.weight(1f, fill = false),
                    style = COUITheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reply.level > 0) {
                    Text(
                        text = "Lv${reply.level}",
                        modifier = Modifier.padding(start = 6.dp),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = Formatters.formatTimeAgo(reply.timeSec),
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.height(3.dp))
            RichCommentText(
                content = reply.content,
                emotes = reply.emotes,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val likeLabel = when {
                    reply.liked && reply.like > 0 ->
                        "已赞 ${Formatters.formatStat(reply.like.toLong())}"
                    reply.liked -> "已赞"
                    reply.like > 0 -> "赞 ${Formatters.formatStat(reply.like.toLong())}"
                    else -> "赞"
                }
                Text(
                    text = likeLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onLike)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    style = COUITheme.textStyles.footnote2,
                    color = if (reply.liked) {
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
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}
