package erl.bilico.feature.comment

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentSort
import erl.bilico.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 控制评论详情内嵌页面的打开、关闭及与主评论区的数据同步。 */
class ReplyThreadController {
    var root by mutableStateOf<Comment?>(null)
        private set
    var targetReplyId by mutableLongStateOf(0L)
        private set
    internal var itemsSnapshot by mutableStateOf<List<Comment>>(emptyList())
    var onSubReplyAdded: ((Long, Comment) -> Unit)? = null
    var onSubReplyDeleted: ((Long, Long) -> Unit)? = null
    var onRootDeleted: ((Long) -> Unit)? = null

    fun open(comment: Comment, targetId: Long = 0L) {
        root = comment
        targetReplyId = targetId
    }

    fun close() {
        root = null
        targetReplyId = 0L
    }
}

@Stable
internal class ReplyDetailState(
    private val container: AppContainer,
    private val oid: Long,
    private val type: Int,
    initialRoot: Comment,
    private val scope: CoroutineScope,
    private val onSubReplyAdded: (Comment) -> Unit,
    private val onSubReplyDeleted: (Long) -> Unit,
    private val onRootDeleted: () -> Unit,
) {
    var rootComment by mutableStateOf(initialRoot)
    var sort by mutableStateOf(CommentSort.Hot)
        private set
    var items by mutableStateOf<List<Comment>>(emptyList())
        private set
    var totalCount by mutableIntStateOf(initialRoot.replyCount)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isEnd by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var isSending by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)
        private set

    private var hotPage = 0
    private var nextNewPage = 0
    private var knownTotal = -1
    private var generation = 0

    fun showToast(message: String) {
        toast = message
        scope.launch {
            delay(2000)
            if (toast == message) toast = null
        }
    }

    fun selectSort(nextSort: CommentSort) {
        if (sort == nextSort) return
        sort = nextSort
        loadInitial(nextSort)
    }

    fun loadInitial(targetSort: CommentSort = sort) {
        generation += 1
        val reqGen = generation
        val rootId = rootComment.id
        items = emptyList()
        hotPage = 0
        nextNewPage = 0
        isEnd = false
        error = null
        isLoading = true
        scope.launch {
            if (targetSort == CommentSort.Hot) {
                container.comments.replies(oid, rootId, page = 1, pageSize = SUB_REPLY_PAGE_SIZE, type = type)
                    .onSuccess { page ->
                        if (reqGen != generation) return@onSuccess
                        knownTotal = page.total
                        totalCount = maxOf(totalCount, page.total, page.replies.size)
                        hotPage = 1
                        items = sortHotSubReplies(page.replies).distinctBy { it.id }
                        isEnd = page.replies.isEmpty() || page.replies.size >= page.total
                    }
                    .onFailure { failure ->
                        if (reqGen == generation) error = failure.message ?: "回复加载失败"
                    }
                if (reqGen == generation) isLoading = false
                return@launch
            }

            // 最新排序：x/v2/reply/reply 按时间正序分页，先确认总条数再从末页倒序拉取最新回复。
            var firstPageReplies: List<Comment>? = null
            if (knownTotal < 0) {
                val probe = container.comments.replies(oid, rootId, page = 1, pageSize = SUB_REPLY_PAGE_SIZE, type = type)
                if (reqGen != generation) return@launch
                probe.onSuccess { page ->
                    knownTotal = page.total
                    totalCount = maxOf(totalCount, page.total, page.replies.size)
                    firstPageReplies = page.replies
                }.onFailure { failure ->
                    error = failure.message ?: "回复加载失败"
                    isLoading = false
                    return@launch
                }
            }

            val total = knownTotal.coerceAtLeast(0)
            val lastPage = ((total + SUB_REPLY_PAGE_SIZE - 1) / SUB_REPLY_PAGE_SIZE).coerceAtLeast(1)
            if (lastPage == 1 && firstPageReplies != null) {
                items = firstPageReplies.sortedByDescending { it.timeSec }.distinctBy { it.id }
                nextNewPage = 0
                isEnd = true
                isLoading = false
                return@launch
            }

            container.comments.replies(oid, rootId, page = lastPage, pageSize = SUB_REPLY_PAGE_SIZE, type = type)
                .onSuccess { tailPage ->
                    if (reqGen != generation) return@onSuccess
                    val tailSorted = tailPage.replies.sortedByDescending { it.timeSec }
                    if (lastPage > 1 && tailSorted.size < 10) {
                        val prev = container.comments.replies(oid, rootId, page = lastPage - 1, pageSize = SUB_REPLY_PAGE_SIZE, type = type)
                            .getOrNull()
                        if (reqGen != generation) return@onSuccess
                        val prevSorted = prev?.replies.orEmpty().sortedByDescending { it.timeSec }
                        items = (tailSorted + prevSorted).distinctBy { it.id }
                        nextNewPage = lastPage - 2
                    } else {
                        items = tailSorted.distinctBy { it.id }
                        nextNewPage = lastPage - 1
                    }
                    isEnd = nextNewPage < 1
                }
                .onFailure { failure ->
                    if (reqGen == generation) error = failure.message ?: "回复加载失败"
                }
            if (reqGen == generation) isLoading = false
        }
    }

    fun loadMore() {
        if (isLoading || isEnd) return
        val reqGen = generation
        val activeSort = sort
        val rootId = rootComment.id
        isLoading = true
        error = null
        scope.launch {
            if (activeSort == CommentSort.Hot) {
                val next = hotPage + 1
                container.comments.replies(oid, rootId, page = next, pageSize = SUB_REPLY_PAGE_SIZE, type = type)
                    .onSuccess { page ->
                        if (reqGen != generation) return@onSuccess
                        hotPage = next
                        knownTotal = maxOf(knownTotal, page.total)
                        totalCount = maxOf(totalCount, page.total)
                        val knownIds = items.mapTo(HashSet()) { it.id }
                        val fresh = sortHotSubReplies(page.replies.filter { it.id !in knownIds })
                        items = items + fresh
                        isEnd = page.replies.isEmpty() || (knownTotal > 0 && items.size >= knownTotal)
                    }
                    .onFailure { failure ->
                        if (reqGen == generation) error = failure.message ?: "回复加载失败"
                    }
            } else {
                val targetPage = nextNewPage
                if (targetPage < 1) {
                    isEnd = true
                    isLoading = false
                    return@launch
                }
                container.comments.replies(oid, rootId, page = targetPage, pageSize = SUB_REPLY_PAGE_SIZE, type = type)
                    .onSuccess { page ->
                        if (reqGen != generation) return@onSuccess
                        nextNewPage = targetPage - 1
                        val knownIds = items.mapTo(HashSet()) { it.id }
                        val fresh = page.replies.filter { it.id !in knownIds }.sortedByDescending { it.timeSec }
                        items = items + fresh
                        isEnd = nextNewPage < 1 || page.replies.isEmpty()
                    }
                    .onFailure { failure ->
                        if (reqGen == generation) error = failure.message ?: "回复加载失败"
                    }
            }
            if (reqGen == generation) isLoading = false
        }
    }

    fun toggleRootLike() = toggleVote(rootComment, isLike = true) { rootComment = it }
    fun toggleRootDislike() = toggleVote(rootComment, isLike = false) { rootComment = it }
    fun toggleLike(target: Comment) = toggleVote(target, isLike = true) { u -> items = items.map { if (it.id == target.id) u else it } }
    fun toggleDislike(target: Comment) = toggleVote(target, isLike = false) { u -> items = items.map { if (it.id == target.id) u else it } }

    private fun toggleVote(target: Comment, isLike: Boolean, update: (Comment) -> Unit) {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val next = if (isLike) {
            val liked = !target.liked
            target.copy(liked = liked, disliked = if (liked) false else target.disliked, like = (target.like + if (liked) 1 else -1).coerceAtLeast(0))
        } else {
            val disliked = !target.disliked
            val like = if (disliked && target.liked) (target.like - 1).coerceAtLeast(0) else target.like
            target.copy(disliked = disliked, liked = if (disliked) false else target.liked, like = like)
        }
        update(next)
        scope.launch {
            val req = if (isLike) {
                container.comments.likeComment(oid = oid, rpid = target.id, liked = next.liked, type = type)
            } else {
                container.comments.hateComment(oid = oid, rpid = target.id, hated = next.disliked, type = type)
            }
            req.onFailure { err ->
                update(target)
                showToast(err.message ?: if (isLike) "点赞失败" else "操作失败")
            }
        }
    }

    fun sendReply(
        draft: String,
        pictures: List<Uri>,
        context: Context,
        replyTarget: Comment?,
        onSent: () -> Unit,
    ) {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val text = draft.trim()
        if (text.isEmpty() || isSending) return
        val parentTarget = replyTarget ?: rootComment
        isSending = true
        scope.launch {
            val uploaded = if (pictures.isNotEmpty()) {
                uploadCommentPictures(context = context, comments = container.comments, uris = pictures)
                    .getOrElse { err ->
                        isSending = false
                        showToast(err.message ?: "图片上传失败")
                        return@launch
                    }
            } else {
                emptyList()
            }
            container.comments.addComment(
                oid = oid,
                message = text,
                root = rootComment.id,
                parent = parentTarget.id,
                type = type,
                pictures = uploaded,
            )
                .onSuccess { created ->
                    isSending = false
                    totalCount += 1
                    if (knownTotal >= 0) knownTotal += 1
                    items = (listOf(created) + items).distinctBy { it.id }
                    onSubReplyAdded(created)
                    onSent()
                    showToast("回复已发送")
                }
                .onFailure { err ->
                    isSending = false
                    showToast(err.message ?: "回复失败")
                }
        }
    }

    fun deleteRootComment() {
        scope.launch {
            container.comments.deleteComment(oid = oid, rpid = rootComment.id, type = type)
                .onSuccess { onRootDeleted() }
                .onFailure { err -> showToast(err.message ?: "删除失败") }
        }
    }

    fun deleteSubReply(target: Comment) {
        scope.launch {
            container.comments.deleteComment(oid = oid, rpid = target.id, type = type)
                .onSuccess {
                    items = items.filterNot { it.id == target.id }
                    totalCount = (totalCount - 1).coerceAtLeast(0)
                    if (knownTotal > 0) knownTotal -= 1
                    onSubReplyDeleted(target.id)
                    showToast("已删除评论")
                }
                .onFailure { err -> showToast(err.message ?: "删除失败") }
        }
    }
}

@Composable
internal fun rememberReplyDetailState(
    container: AppContainer,
    oid: Long,
    type: Int,
    comment: Comment,
    onSubReplyAdded: (Comment) -> Unit,
    onSubReplyDeleted: (Long) -> Unit,
    onRootDeleted: () -> Unit,
): ReplyDetailState {
    val scope = rememberCoroutineScope()
    return remember(comment.id, oid, type) {
        ReplyDetailState(
            container = container,
            oid = oid,
            type = type,
            initialRoot = comment,
            scope = scope,
            onSubReplyAdded = onSubReplyAdded,
            onSubReplyDeleted = onSubReplyDeleted,
            onRootDeleted = onRootDeleted,
        )
    }
}

private fun sortHotSubReplies(list: List<Comment>): List<Comment> =
    list.sortedWith(compareByDescending<Comment> { it.like }.thenBy { it.timeSec })

private const val SUB_REPLY_PAGE_SIZE = 20
