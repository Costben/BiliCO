package erl.bilico.feature.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.Comment
import erl.bilico.data.model.UploadedPicture
import erl.bilico.data.repository.CommentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentUiState(
    val items: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val total: Int = 0,
    val message: String? = null,
)

/** 评论主楼：按热度分页、发表评论与楼层回复、点赞主楼。 */
class CommentViewModel(
    private val repository: CommentRepository,
    private val oid: Long,
    private val type: Int = 1,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentUiState())
    val state: StateFlow<CommentUiState> = _state.asStateFlow()

    private var cursor: String? = null
    private var messageJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            cursor = null
            repository.mainComments(oid, null, type)
                .onSuccess { page ->
                    cursor = page.nextOffset
                    _state.update {
                        it.copy(
                            items = page.comments,
                            isLoading = false,
                            isLoadingMore = false,
                            isEnd = page.isEnd || page.nextOffset == null,
                            total = if (page.total > 0) page.total else page.comments.size,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "评论加载失败")
                    }
                }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.isEnd) return
        val offset = cursor ?: return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            repository.mainComments(oid, offset, type)
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
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            isEnd = true,
                            error = error.message ?: "评论加载失败",
                        )
                    }
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
            repository.addComment(
                oid = oid,
                message = content,
                type = type,
                pictures = pictures,
                atMids = atMids,
            )
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
                    if (item.id == rootId) {
                        val merged = (item.replies + created).distinctBy { it.id }
                        item.copy(
                            replyCount = item.replyCount + 1,
                            replies = merged,
                        )
                    } else {
                        item
                    }
                },
            )
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

    fun showMessage(text: String) {
        messageJob?.cancel()
        _state.update { it.copy(message = text) }
        messageJob = viewModelScope.launch {
            delay(2200)
            _state.update { it.copy(message = null) }
        }
    }
}
