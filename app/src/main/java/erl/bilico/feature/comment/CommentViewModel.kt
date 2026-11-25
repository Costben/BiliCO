package erl.bilico.feature.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentSort
import erl.bilico.data.model.UploadedPicture
import erl.bilico.data.repository.CommentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 评论主楼：按热度分页、发表评论与楼层回复、点赞主楼。 */
class CommentViewModel(
    private val repository: CommentRepository,
    private val oid: Long,
    private val type: Int = 1,
    private val targetRootId: Long = 0L,
    private val targetReplyId: Long = 0L,
    private val upMid: Long = 0L,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentUiState())
    val state: StateFlow<CommentUiState> = _state.asStateFlow()

    private var cursor: String? = null
    private var newestPage = 0
    private var refreshGeneration = 0
    private var messageJob: Job? = null
    private var targetHandled = false

    init {
        refresh()
    }

    fun refresh() {
        val reqGen = ++refreshGeneration
        val preserveExisting = _state.value.items.isNotEmpty()
        val sort = _state.value.sort
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = !preserveExisting,
                    isSwitchingSort = preserveExisting,
                    error = null,
                )
            }
            if (!preserveExisting) {
                cursor = null
                newestPage = 0
            }
            repository.mainComments(oid, null, type, sort, fallbackUpMid = upMid)
                .onSuccess { page ->
                    if (reqGen != refreshGeneration) return@onSuccess
                    cursor = page.nextOffset
                    newestPage = if (sort == CommentSort.New) 1 else 0
                    var mergedItems = page.comments
                    var focusRootId = 0L
                    var pendingReplyDialog: Pair<Comment, Long>? = null
                    if (!targetHandled && (targetRootId > 0L || targetReplyId > 0L) && sort == CommentSort.Hot) {
                        targetHandled = true
                        val lookupRootId = if (targetRootId > 0L) targetRootId else targetReplyId
                        val existing = page.comments.firstOrNull { it.id == lookupRootId }
                        val resolvedRoot = existing
                            ?: repository.rootComment(oid, lookupRootId, type, fallbackUpMid = upMid)
                            ?: if (targetReplyId > 0L && targetReplyId != lookupRootId) {
                                repository.rootComment(oid, targetReplyId, type, fallbackUpMid = upMid)
                            } else {
                                null
                            }
                        if (resolvedRoot != null) {
                            mergedItems = listOf(resolvedRoot) + page.comments.filterNot { it.id == resolvedRoot.id }
                            focusRootId = resolvedRoot.id
                            if (targetReplyId > 0L && targetReplyId != resolvedRoot.id) {
                                pendingReplyDialog = resolvedRoot to targetReplyId
                            }
                        }
                    }
                    _state.update {
                        it.copy(
                            items = mergedItems,
                            isLoading = false,
                            isSwitchingSort = false,
                            isLoadingMore = false,
                            renderedSort = sort,
                            // 按时间那一页的 is_end 不可信，按总条数判断还有没有下一页。
                            isEnd = if (sort == CommentSort.New) {
                                page.comments.size >= page.total
                            } else {
                                page.isEnd || page.nextOffset == null
                            },
                            total = maxOf(page.total, mergedItems.size),
                            focusRootId = focusRootId,
                            pendingReplyDialog = pendingReplyDialog,
                        )
                    }
                }
                .onFailure { error ->
                    if (reqGen != refreshGeneration) return@onFailure
                    _state.update {
                        if (preserveExisting) {
                            it.copy(
                                isLoading = false,
                                isSwitchingSort = false,
                                sort = it.renderedSort,
                            )
                        } else {
                            it.copy(
                                isLoading = false,
                                isSwitchingSort = false,
                                error = error.message ?: "评论加载失败",
                            )
                        }
                    }
                    if (preserveExisting) showMessage(error.message ?: "评论加载失败")
                }
        }
    }

    /** 热度 / 时间来回切：保留当前已加载列表直到新排序加载完成，再淡出淡入切换。 */
    fun toggleSort() {
        val next = if (_state.value.sort == CommentSort.Hot) CommentSort.New else CommentSort.Hot
        selectSort(next)
    }

    fun selectSort(next: CommentSort) {
        if (_state.value.sort == next) return
        _state.update { it.copy(sort = next, isLoadingMore = false) }
        refresh()
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isSwitchingSort || current.isLoadingMore || current.isEnd) return
        if (current.sort == CommentSort.New) {
            loadNewestMore()
            return
        }
        val offset = cursor ?: return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            repository.mainComments(oid, offset, type, current.sort, fallbackUpMid = upMid)
                .onSuccess { page ->
                    cursor = page.nextOffset
                    _state.update { state ->
                        val known = state.items.mapTo(HashSet()) { it.id }
                        val fresh = page.comments.filter { it.id !in known }
                        state.copy(
                            items = state.items + fresh,
                            isLoadingMore = false,
                            isEnd = page.isEnd || page.nextOffset == null || fresh.isEmpty(),
                            total = if (page.total > 0) page.total else state.total,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoadingMore = false, isEnd = true, error = error.message ?: "评论加载失败") }
                }
        }
    }

    private fun loadNewestMore() {
        val nextPage = newestPage + 1
        _state.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            repository.newestPage(oid, nextPage, type, fallbackUpMid = upMid)
                .onSuccess { page ->
                    newestPage = nextPage
                    _state.update { state ->
                        val known = state.items.mapTo(HashSet()) { it.id }
                        val fresh = page.comments.filter { it.id !in known }
                        state.copy(
                            items = state.items + fresh,
                            isLoadingMore = false,
                            isEnd = page.isEnd || fresh.isEmpty(),
                            total = if (page.total > 0) page.total else state.total,
                        )
                    }
                }
                .onFailure { error ->
                    // 失败别把 isEnd 钉死，滑到底再试还能继续拉。
                    _state.update { it.copy(isLoadingMore = false, error = error.message ?: "评论加载失败") }
                }
        }
    }

    fun postComment(
        content: String,
        pictures: List<UploadedPicture> = emptyList(),
        atMids: Map<String, Long> = emptyMap(),
    ) {
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            repository.addComment(oid = oid, message = content, type = type, pictures = pictures, atMids = atMids, upMid = upMid)
                .onSuccess { created ->
                    _state.update { s ->
                        s.copy(
                            isSending = false,
                            total = s.total + 1,
                            items = listOf(created) + s.items.filterNot { it.id == created.id },
                        )
                    }
                    showMessage("评论已发送")
                }
                .onFailure { err ->
                    _state.update { it.copy(isSending = false) }
                    showMessage(err.message ?: "发送失败")
                }
        }
    }

    fun postReply(
        rootComment: Comment,
        content: String,
        pictures: List<UploadedPicture> = emptyList(),
        atMids: Map<String, Long> = emptyMap(),
    ) {
        if (_state.value.isSending) return
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            repository.addComment(
                oid = oid,
                message = content,
                root = rootComment.id,
                parent = rootComment.id,
                type = type,
                pictures = pictures,
                atMids = atMids,
                upMid = upMid,
            )
                .onSuccess { created ->
                    _state.update { it.copy(isSending = false) }
                    onSubReplyAdded(rootComment.id, created)
                    showMessage("回复已发送")
                }
                .onFailure { err ->
                    _state.update { it.copy(isSending = false) }
                    showMessage(err.message ?: "回复失败")
                }
        }
    }

    fun onSubReplyAdded(rootId: Long, created: Comment) {
        _state.update { s ->
            s.copy(
                total = s.total + 1,
                items = s.items.map { item ->
                    if (item.id == rootId) item.copy(replyCount = item.replyCount + 1, replies = (item.replies + created).distinctBy { it.id })
                    else item
                },
            )
        }
    }

    fun onSubReplyDeleted(rootId: Long, replyId: Long) {
        _state.update { s ->
            var removed = false
            val nextItems = s.items.map { item ->
                if (item.id == rootId) {
                    removed = true
                    item.copy(replyCount = (item.replyCount - 1).coerceAtLeast(0), replies = item.replies.filterNot { it.id == replyId })
                } else {
                    item
                }
            }
            if (!removed) s else s.copy(total = (s.total - 1).coerceAtLeast(0), items = nextItems)
        }
    }

    fun removeCommentLocally(commentId: Long) {
        _state.update { s ->
            val next = s.items.filterNot { it.id == commentId }
            if (next.size == s.items.size) s else s.copy(items = next, total = (s.total - 1).coerceAtLeast(0))
        }
    }

    fun deleteComment(target: Comment) {
        viewModelScope.launch {
            repository.deleteComment(oid = oid, rpid = target.id, type = type)
                .onSuccess {
                    removeCommentLocally(target.id)
                    showMessage("已删除评论")
                }
                .onFailure { err ->
                    showMessage(err.message ?: "删除评论失败")
                }
        }
    }

    fun toggleLike(target: Comment) {
        val nextLiked = !target.liked
        val nextDisliked = if (nextLiked) false else target.disliked
        val nextCount = (target.like + if (nextLiked) 1 else -1).coerceAtLeast(0)
        _state.update { s ->
            s.copy(
                items = s.items.map {
                    if (it.id == target.id) {
                        it.copy(liked = nextLiked, disliked = nextDisliked, like = nextCount)
                    } else {
                        it
                    }
                },
            )
        }
        viewModelScope.launch {
            repository.likeComment(oid = oid, rpid = target.id, liked = nextLiked, type = type)
                .onFailure { err ->
                    _state.update { s ->
                        s.copy(
                            items = s.items.map {
                                if (it.id == target.id) target else it
                            },
                        )
                    }
                    showMessage(err.message ?: "点赞失败")
                }
        }
    }

    fun toggleDislike(target: Comment) {
        val nextDisliked = !target.disliked
        val nextLiked = if (nextDisliked) false else target.liked
        val nextCount = if (nextDisliked && target.liked) {
            (target.like - 1).coerceAtLeast(0)
        } else {
            target.like
        }
        _state.update { s ->
            s.copy(
                items = s.items.map {
                    if (it.id == target.id) {
                        it.copy(disliked = nextDisliked, liked = nextLiked, like = nextCount)
                    } else {
                        it
                    }
                },
            )
        }
        viewModelScope.launch {
            repository.hateComment(oid = oid, rpid = target.id, hated = nextDisliked, type = type)
                .onFailure { err ->
                    _state.update { s ->
                        s.copy(
                            items = s.items.map {
                                if (it.id == target.id) target else it
                            },
                        )
                    }
                    showMessage(err.message ?: "操作失败")
                }
        }
    }

    fun consumeFocusTarget() {
        _state.update { it.copy(focusRootId = 0L) }
    }

    fun consumeReplyDialog() {
        _state.update { it.copy(pendingReplyDialog = null) }
    }

    fun showMessage(text: String) {
        messageJob?.cancel()
        _state.update { it.copy(message = text) }
        messageJob = viewModelScope.launch {
            delay(2200)
            _state.update { it.copy(message = null) }
        }
    }
}
