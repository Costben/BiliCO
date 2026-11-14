package erl.bilico.feature.following

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.FollowingUser
import erl.bilico.data.repository.RelationRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowingUiState(
    val items: List<FollowingUser> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class FollowingViewModel(
    private val repository: RelationRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FollowingUiState())
    val state: StateFlow<FollowingUiState> = _state.asStateFlow()

    private var nextPage = 1
    private var inFlight = false

    init {
        refresh()
    }

    fun refresh() {
        val mid = session.account.value?.mid
        if (mid == null || mid <= 0L) {
            _state.value = FollowingUiState(isLoading = false)
            return
        }
        nextPage = 1
        inFlight = false
        _state.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
        load(firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.items.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        load(firstPage = false)
    }

    /** 左滑取消关注：先乐观移除，失败回滚到原位。 */
    fun unfollow(user: FollowingUser, onFailure: (String) -> Unit = {}) {
        if (user.mid <= 0L) return
        val before = _state.value.items
        val index = before.indexOfFirst { it.mid == user.mid }
        if (index < 0) return
        _state.update { state ->
            state.copy(items = state.items.filterNot { it.mid == user.mid })
        }
        viewModelScope.launch {
            repository.unfollow(user.mid)
                .onSuccess {
                    if (_state.value.items.size < 10 && !_state.value.isEnd) loadMore()
                }
                .onFailure { error ->
                    _state.update { state ->
                        if (state.items.any { it.mid == user.mid }) {
                            state
                        } else {
                            val restored = state.items.toMutableList().apply {
                                add(index.coerceAtMost(size), user)
                            }
                            state.copy(items = restored)
                        }
                    }
                    onFailure(error.message ?: "取消关注失败")
                }
        }
    }

    /** 多选批量取消关注：先乐观移除，对未成功取消的条目回滚。 */
    fun unfollowBatch(
        mids: Set<Long>,
        onFailure: (String) -> Unit = {},
    ) {
        if (mids.isEmpty()) return
        val snapshot = _state.value.items
        val targets = snapshot.filter { it.mid in mids && it.mid > 0L }.map { it.mid }
        if (targets.isEmpty()) return
        val targetSet = targets.toSet()
        _state.update { state ->
            state.copy(items = state.items.filterNot { it.mid in targetSet })
        }
        viewModelScope.launch {
            repository.unfollowBatch(targets)
                .onSuccess { succeeded ->
                    val failedSet = targetSet - succeeded
                    if (failedSet.isNotEmpty()) {
                        val snapshotMap = snapshot.associateBy { it.mid }
                        _state.update { state ->
                            val currentIds = state.items.map { it.mid }.toSet()
                            val keepIds = currentIds + failedSet
                            val restored = snapshot.filter { it.mid in keepIds }
                            val extras = state.items.filter { it.mid !in snapshotMap }
                            state.copy(items = restored + extras)
                        }
                        onFailure("部分取消关注失败（${failedSet.size} 位未取消）")
                    } else if (_state.value.items.size < 10 && !_state.value.isEnd) {
                        loadMore()
                    }
                }
                .onFailure { error ->
                    val snapshotMap = snapshot.associateBy { it.mid }
                    _state.update { state ->
                        val currentIds = state.items.map { it.mid }.toSet()
                        val restored = snapshot.filter { it.mid in targetSet || it.mid in currentIds }
                        val extras = state.items.filter { it.mid !in snapshotMap }
                        state.copy(items = restored + extras)
                    }
                    onFailure(error.message ?: "取消关注失败")
                }
        }
    }

    private fun load(firstPage: Boolean) {
        val mid = session.account.value?.mid ?: return
        inFlight = true
        val page = if (firstPage) 1 else nextPage
        viewModelScope.launch {
            repository.followings(mid, page)
                .onSuccess { (list, hasMore) ->
                    nextPage = page + 1
                    _state.update {
                        it.copy(
                            items = if (firstPage) list else it.items + list,
                            isLoading = false,
                            isLoadingMore = false,
                            isEnd = !hasMore,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = if (firstPage) error.message ?: "关注列表加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}
