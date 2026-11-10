package erl.bilico.feature.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.Comment
import erl.bilico.data.model.DynamicImage
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicImageViewer
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonSize
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.squircle.squircleSurface
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
    onOpenUser: (Long) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var show by remember(comment.id) { mutableStateOf(false) }
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
    var imageViewer by remember(comment.id) { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(comment.id) { show = true }

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
        val nextDisliked = if (nextLiked) false else target.disliked
        val nextLike = (target.like + if (nextLiked) 1 else -1).coerceAtLeast(0)
        items = items.map {
            if (it.id == target.id) {
                it.copy(liked = nextLiked, disliked = nextDisliked, like = nextLike)
            } else {
                it
            }
        }
        scope.launch {
            container.comments.likeComment(oid = oid, rpid = target.id, liked = nextLiked, type = type)
                .onFailure { err ->
                    items = items.map { if (it.id == target.id) target else it }
                    showToast(err.message ?: "点赞失败")
                }
        }
    }

    fun toggleDislike(target: Comment) {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val nextDisliked = !target.disliked
        val nextLiked = if (nextDisliked) false else target.liked
        val nextLike = if (nextDisliked && target.liked) {
            (target.like - 1).coerceAtLeast(0)
        } else {
            target.like
        }
        items = items.map {
            if (it.id == target.id) {
                it.copy(disliked = nextDisliked, liked = nextLiked, like = nextLike)
            } else {
                it
            }
        }
        scope.launch {
            container.comments.hateComment(oid = oid, rpid = target.id, hated = nextDisliked, type = type)
                .onFailure { err ->
                    items = items.map { if (it.id == target.id) target else it }
                    showToast(err.message ?: "操作失败")
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

    val density = LocalDensity.current
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val keyboardGapProgress = (imeBottom / 80.dp).coerceIn(0f, 1f)
    val bottomGap = 24.dp + (6.dp - 24.dp) * keyboardGapProgress

    OverlayBottomSheet(
        show = show,
        title = "回复（${maxOf(totalCount, items.size)}）",
        showDragHandle = false,
        insideMargin = DpSize(12.dp, bottomGap),
        panelPaddingBottom = 0.dp,
        onDismissRequest = { show = false },
        onDismissFinished = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 460.dp),
            ) {
                when {
                    items.isEmpty() && isLoading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 22.dp)
                    }

                    items.isEmpty() && error != null -> ErrorState(
                        message = error.orEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        onRetry = { loadNext() },
                    )

                    items.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState("还没有回复")
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items, key = { it.id }) { reply ->
                            BiliCommentItem(
                                comment = reply,
                                onLike = { toggleLike(reply) },
                                onDislike = { toggleDislike(reply) },
                                onReply = { replyTarget = reply },
                                onOpenUser = onOpenUser,
                                onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
                                isSubReply = true,
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
                            .clickable { replyTarget = null }
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
                        .squircleSurface(
                            color = COUITheme.colorScheme.surfaceContainer,
                            cornerRadius = 20.dp,
                        ),
                ) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = "回复 @${(replyTarget ?: comment).userName}",
                        backgroundMode = TextFieldMode.None,
                        insideMargin = DpSize(12.dp, 10.dp),
                        maxLines = 3,
                        minLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { sendReply() },
                    enabled = draft.isNotBlank() && !isSending,
                    size = ButtonSize.Small,
                ) {
                    Text(
                        text = if (isSending) "发送中" else "发送",
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
            }
        }
    }

    imageViewer?.let { (images, index) ->
        DynamicImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { imageViewer = null },
        )
    }
}
