package erl.bilico.feature.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.Comment
import erl.bilico.data.repository.CommentRepository
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
    val error: String? = null,
    val total: Int = 0,
)

/** 只读评论主楼，按热度分页；楼中楼由详情弹层自己拉。 */
class CommentViewModel(
    private val repository: CommentRepository,
    private val oid: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentUiState())
    val state: StateFlow<CommentUiState> = _state.asStateFlow()

    private var cursor: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            cursor = null
            repository.mainComments(oid, null)
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
            repository.mainComments(oid, offset)
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
                    // 分页失败不推翻已有列表，停在当前页。
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
}
