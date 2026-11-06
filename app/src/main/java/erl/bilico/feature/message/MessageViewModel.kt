package erl.bilico.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.MessageCursor
import erl.bilico.data.model.MessageEntry
import erl.bilico.data.model.MessageKind
import erl.bilico.data.repository.MessageRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MessageUiState(
    val kind: MessageKind = MessageKind.REPLY,
    val items: List<MessageEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class MessageViewModel(
    private val repository: MessageRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MessageUiState())
    val state: StateFlow<MessageUiState> = _state.asStateFlow()

    private var cursor: MessageCursor? = null
    private var inFlight = false
    private var job: Job? = null

    init {
        refresh()
    }

    fun select(kind: MessageKind) {
        if (kind == _state.value.kind) return
        _state.value = MessageUiState(kind = kind)
        refresh()
    }

    fun refresh() {
        job?.cancel()
        if (!session.isLoggedIn()) {
            _state.update { it.copy(isLoading = false, items = emptyList(), isEnd = true) }
            return
        }
        cursor = null
        inFlight = false
        _state.update { it.copy(isLoading = true, error = null) }
        load(firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.items.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        load(firstPage = false)
    }

    private fun load(firstPage: Boolean) {
        if (inFlight) return
        inFlight = true
        val kind = _state.value.kind
        val pageCursor = if (firstPage) null else cursor
        job = viewModelScope.launch {
            repository.page(kind, pageCursor)
                .onSuccess { page ->
                    if (kind != _state.value.kind) return@onSuccess
                    cursor = page.cursor
                    _state.update {
                        it.copy(
                            items = if (firstPage) page.items else it.items + page.items,
                            isLoading = false,
                            isLoadingMore = false,
                            isEnd = page.isEnd || page.items.isEmpty(),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (kind != _state.value.kind) return@onFailure
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = if (firstPage) error.message ?: "消息加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}
