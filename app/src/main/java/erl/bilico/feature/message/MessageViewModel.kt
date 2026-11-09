package erl.bilico.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.MessageCursor
import erl.bilico.data.model.MessageEntry
import erl.bilico.data.model.MessageKind
import erl.bilico.data.repository.MessageRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 单个消息分类的一页内容；四类各自独立，左右滑动互不打断。 */
data class MessageKindState(
    val items: List<MessageEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

data class MessageUiState(
    val kinds: Map<MessageKind, MessageKindState> = emptyMap(),
) {
    fun of(kind: MessageKind): MessageKindState = kinds[kind] ?: MessageKindState()
}

class MessageViewModel(
    private val repository: MessageRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MessageUiState())
    val state: StateFlow<MessageUiState> = _state.asStateFlow()

    private val cursors = mutableMapOf<MessageKind, MessageCursor?>()
    private val loading = mutableSetOf<MessageKind>()

    init {
        ensureLoaded(MessageKind.REPLY)
    }

    /** 第一次滑到某一类时拉它的第一页。 */
    fun ensureLoaded(kind: MessageKind) {
        val current = _state.value.of(kind)
        if (current.loaded || current.isLoading || loading.contains(kind)) return
        load(kind, firstPage = true)
    }

    /** 失败重试：丢掉这一类的游标和已有内容重来。 */
    fun reload(kind: MessageKind) {
        if (loading.contains(kind)) return
        load(kind, firstPage = true)
    }

    fun loadMore(kind: MessageKind) {
        val current = _state.value.of(kind)
        if (loading.contains(kind) || current.isEnd || current.items.isEmpty()) return
        load(kind, firstPage = false)
    }

    private fun load(kind: MessageKind, firstPage: Boolean) {
        if (!loading.add(kind)) return
        if (!session.isLoggedIn()) {
            loading -= kind
            set(kind, MessageKindState())
            return
        }
        if (firstPage) {
            cursors[kind] = null
            set(kind, MessageKindState(isLoading = true))
        } else {
            update(kind) { it.copy(isLoadingMore = true) }
        }
        val pageCursor = if (firstPage) null else cursors[kind]
        viewModelScope.launch {
            repository.page(kind, pageCursor)
                .onSuccess { page ->
                    cursors[kind] = page.cursor
                    update(kind) { state ->
                        state.copy(
                            items = if (firstPage) page.items else state.items + page.items,
                            isLoading = false,
                            isLoadingMore = false,
                            loaded = true,
                            isEnd = page.isEnd || page.items.isEmpty(),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    update(kind) {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = if (firstPage) error.message ?: "消息加载失败" else it.error,
                        )
                    }
                }
            loading -= kind
        }
    }

    private fun set(kind: MessageKind, value: MessageKindState) {
        _state.update { it.copy(kinds = it.kinds + (kind to value)) }
    }

    private fun update(kind: MessageKind, transform: (MessageKindState) -> MessageKindState) {
        _state.update { current ->
            current.copy(kinds = current.kinds + (kind to transform(current.of(kind))))
        }
    }
}
