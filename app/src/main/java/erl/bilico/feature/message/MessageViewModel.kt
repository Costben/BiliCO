package erl.bilico.feature.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.ImSessionUi
import erl.bilico.data.model.MessageCursor
import erl.bilico.data.model.MessageEntry
import erl.bilico.data.model.MessageKind
import erl.bilico.data.repository.ImRepository
import erl.bilico.data.repository.MessageRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 单个通知消息分类的一页内容；各分类各自独立，左右滑动互不打断。 */
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
    // 私信会话
    val sessions: List<ImSessionUi> = emptyList(),
    val isSessionsLoading: Boolean = false,
    val isSessionsLoadingMore: Boolean = false,
    val isSessionsEnd: Boolean = false,
    val sessionsLoaded: Boolean = false,
    val sessionsEndTs: Long? = null,
    val sessionsError: String? = null,
) {
    fun of(kind: MessageKind): MessageKindState = kinds[kind] ?: MessageKindState()
}

class MessageViewModel(
    private val repository: MessageRepository,
    private val imRepository: ImRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MessageUiState())
    val state: StateFlow<MessageUiState> = _state.asStateFlow()

    private val cursors = mutableMapOf<MessageKind, MessageCursor?>()
    private val loading = mutableSetOf<MessageKind>()

    init {
        ensureLoaded(MessageKind.SESSION)
    }

    /** 第一次滑到某一类时拉它的第一页。 */
    fun ensureLoaded(kind: MessageKind) {
        if (kind == MessageKind.SESSION) {
            val cur = _state.value
            if (cur.sessionsLoaded || cur.isSessionsLoading || loading.contains(kind)) return
            loadSessions(firstPage = true)
            return
        }
        val current = _state.value.of(kind)
        if (current.loaded || current.isLoading || loading.contains(kind)) return
        load(kind, firstPage = true)
    }

    /** 失败重试：丢掉这一类的游标和已有内容重来。 */
    fun reload(kind: MessageKind) {
        if (loading.contains(kind)) return
        if (kind == MessageKind.SESSION) {
            loadSessions(firstPage = true)
        } else {
            load(kind, firstPage = true)
        }
    }

    fun loadMore(kind: MessageKind) {
        if (kind == MessageKind.SESSION) {
            val cur = _state.value
            if (loading.contains(kind) || cur.isSessionsEnd || cur.sessions.isEmpty()) return
            loadSessions(firstPage = false)
            return
        }
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

    private fun loadSessions(firstPage: Boolean) {
        val kind = MessageKind.SESSION
        if (!loading.add(kind)) return
        if (!session.isLoggedIn()) {
            loading -= kind
            _state.update { it.copy(sessions = emptyList(), isSessionsLoading = false, sessionsLoaded = true) }
            return
        }
        if (firstPage) {
            _state.update { it.copy(isSessionsLoading = true, sessionsError = null) }
        } else {
            _state.update { it.copy(isSessionsLoadingMore = true) }
        }
        val endTs = if (firstPage) null else _state.value.sessionsEndTs
        viewModelScope.launch {
            imRepository.sessions(endTs)
                .onSuccess { (list, nextTs) ->
                    _state.update {
                        it.copy(
                            sessions = if (firstPage) list else it.sessions + list,
                            isSessionsLoading = false,
                            isSessionsLoadingMore = false,
                            sessionsLoaded = true,
                            sessionsEndTs = nextTs,
                            isSessionsEnd = nextTs == null,
                            sessionsError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSessionsLoading = false,
                            isSessionsLoadingMore = false,
                            sessionsError = if (firstPage) error.message ?: "私信加载失败" else it.sessionsError,
                        )
                    }
                }
            loading -= kind
        }
    }

    private fun set(kind: MessageKind, state: MessageKindState) {
        _state.update { it.copy(kinds = it.kinds + (kind to state)) }
    }

    private fun update(kind: MessageKind, transform: (MessageKindState) -> MessageKindState) {
        _state.update {
            val current = it.of(kind)
            it.copy(kinds = it.kinds + (kind to transform(current)))
        }
    }
}
