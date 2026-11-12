package erl.bilico.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.SearchArticle
import erl.bilico.data.model.SearchSuggestion
import erl.bilico.data.model.SearchUser
import erl.bilico.data.model.SearchVideo
import erl.bilico.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val suggestions: List<SearchSuggestion> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val videos: List<SearchVideo> = emptyList(),
    val users: List<SearchUser> = emptyList(),
    val articles: List<SearchArticle> = emptyList(),
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
    private var articlePage = 1
    private var inFlight = false
    private var suggestJob: Job? = null
    private var lastSuggestTerm: String = ""

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
        val trimmed = query.trim()

        if (trimmed.isEmpty()) {
            suggestJob?.cancel()
            suggestJob = null
            lastSuggestTerm = ""
            _state.update {
                it.copy(
                    suggestions = emptyList(),
                    suggestionsLoading = false,
                    submitted = "",
                )
            }
            return
        }

        // 如果与当前已提交的关键词完全一致，说明用户正处于该关键词的结果页，不弹建议
        if (trimmed == _state.value.submitted) {
            suggestJob?.cancel()
            suggestJob = null
            lastSuggestTerm = ""
            _state.update { it.copy(suggestions = emptyList(), suggestionsLoading = false) }
            return
        }

        if (trimmed == lastSuggestTerm && (_state.value.suggestions.isNotEmpty() || suggestJob?.isActive == true)) {
            return
        }

        // 正在输入且未提交：防抖 160ms 请求 B 站搜索建议，保留已有建议防止列表闪烁
        lastSuggestTerm = trimmed
        suggestJob?.cancel()
        _state.update { it.copy(suggestionsLoading = true) }
        suggestJob = viewModelScope.launch {
            delay(160)
            val list = repository.suggest(trimmed).getOrDefault(emptyList())
            val current = _state.value
            if (current.query.trim() == trimmed && current.submitted != trimmed) {
                _state.update {
                    it.copy(
                        suggestions = list,
                        suggestionsLoading = false,
                    )
                }
            }
        }
    }

    /** 重新打开面板时回到搜索首页，不带着上次的结果；热搜缓存留着。 */
    fun reset() {
        suggestJob?.cancel()
        suggestJob = null
        lastSuggestTerm = ""
        videoPage = 1
        userPage = 1
        articlePage = 1
        inFlight = false
        _state.update {
            SearchUiState(hot = it.hot, hotLoading = it.hotLoading)
        }
    }

    fun setTab(tab: Int) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab) }
        if (_state.value.submitted.isNotBlank()) {
            val hasItems = when (tab) {
                0 -> _state.value.videos.isNotEmpty()
                1 -> _state.value.users.isNotEmpty()
                else -> _state.value.articles.isNotEmpty()
            }
            if (!hasItems) submit(_state.value.submitted)
        }
    }

    fun submit(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        suggestJob?.cancel()
        suggestJob = null
        lastSuggestTerm = ""
        videoPage = 1
        userPage = 1
        articlePage = 1
        _state.update {
            it.copy(
                query = trimmed,
                submitted = trimmed,
                suggestions = emptyList(),
                suggestionsLoading = false,
                videos = emptyList(),
                users = emptyList(),
                articles = emptyList(),
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
        if (current.query.trim() != current.submitted) return
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
        val page = when (tab) {
            0 -> videoPage
            1 -> userPage
            else -> articlePage
        }
        viewModelScope.launch {
            when (tab) {
                0 -> repository.searchVideos(keyword, page)
                    .onSuccess { list ->
                        videoPage = page + 1
                        _state.update {
                            it.copy(
                                videos = if (firstPage) list.distinctBy { v -> v.bvid } else (it.videos + list).distinctBy { v -> v.bvid },
                                isLoading = false,
                                isLoadingMore = false,
                                isEnd = list.isEmpty(),
                                error = null,
                            )
                        }
                    }
                    .onFailure { error -> fail(firstPage, error) }

                1 -> repository.searchUsers(keyword, page)
                    .onSuccess { list ->
                        userPage = page + 1
                        _state.update {
                            it.copy(
                                users = if (firstPage) list.distinctBy { u -> u.mid } else (it.users + list).distinctBy { u -> u.mid },
                                isLoading = false,
                                isLoadingMore = false,
                                isEnd = list.isEmpty(),
                                error = null,
                            )
                        }
                    }
                    .onFailure { error -> fail(firstPage, error) }

                else -> repository.searchArticles(keyword, page)
                    .onSuccess { list ->
                        articlePage = page + 1
                        _state.update {
                            it.copy(
                                articles = if (firstPage) list.distinctBy { a -> a.id } else (it.articles + list).distinctBy { a -> a.id },
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
