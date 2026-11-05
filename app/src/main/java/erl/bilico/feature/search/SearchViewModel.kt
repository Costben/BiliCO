package erl.bilico.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.SearchUser
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.repository.SearchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val submitted: String = "",
    val tab: Int = 0,
    val hot: List<String> = emptyList(),
    val hotLoading: Boolean = false,
    val videos: List<SearchVideo> = emptyList(),
    val users: List<SearchUser> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(
    private val repository: SearchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var videoPage = 1
    private var userPage = 1
    private var inFlight = false

    init {
        loadHot()
    }

    fun loadHot() {
        if (_state.value.hot.isNotEmpty()) return
        _state.update { it.copy(hotLoading = true) }
        viewModelScope.launch {
            val list = repository.hotWords().getOrDefault(emptyList())
            _state.update { it.copy(hot = list, hotLoading = false) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setTab(tab: Int) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab) }
        if (_state.value.submitted.isNotBlank()) {
            val hasItems = if (tab == 0) {
                _state.value.videos.isNotEmpty()
            } else {
                _state.value.users.isNotEmpty()
            }
            if (!hasItems) submit(_state.value.submitted)
        }
    }

    fun submit(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        videoPage = 1
        userPage = 1
        _state.update {
            it.copy(
                query = trimmed,
                submitted = trimmed,
                videos = emptyList(),
                users = emptyList(),
                isLoading = true,
                isEnd = false,
                error = null,
            )
        }
        load(firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.submitted.isBlank()) return
        if (current.isLoading) return
        _state.update { it.copy(isLoadingMore = true) }
        load(firstPage = false)
    }

    fun retry() {
        if (_state.value.submitted.isBlank()) return
        submit(_state.value.submitted)
    }

    private fun load(firstPage: Boolean) {
        inFlight = true
        val keyword = _state.value.submitted
        val tab = _state.value.tab
        val page = if (tab == 0) videoPage else userPage
        viewModelScope.launch {
            if (tab == 0) {
                repository.searchVideos(keyword, page)
                    .onSuccess { list ->
                        videoPage = page + 1
                        _state.update {
                            it.copy(
                                videos = if (firstPage) list else it.videos + list,
                                isLoading = false,
                                isLoadingMore = false,
                                isEnd = list.isEmpty(),
                                error = null,
                            )
                        }
                    }
                    .onFailure { error -> fail(firstPage, error) }
            } else {
                repository.searchUsers(keyword, page)
                    .onSuccess { list ->
                        userPage = page + 1
                        _state.update {
                            it.copy(
                                users = if (firstPage) list else it.users + list,
                                isLoading = false,
                                isLoadingMore = false,
                                isEnd = list.isEmpty(),
                                error = null,
                            )
                        }
                    }
                    .onFailure { error -> fail(firstPage, error) }
            }
            inFlight = false
        }
    }

    private fun fail(firstPage: Boolean, error: Throwable) {
        _state.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                error = if (firstPage) error.message ?: "搜索失败" else it.error,
            )
        }
    }
}
