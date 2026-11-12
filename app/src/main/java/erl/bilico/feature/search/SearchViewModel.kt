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
    /** B 站 `order` 参数：totalrank / pubdate / click / dm / stow。 */
    val order: String = "totalrank",
    /** 视频分区 id，0 表示不限。只对视频结果有效。 */
    val tid: Int = 0,
    val hot: List<String> = emptyList(),
    val hotLoading: Boolean = false,
    val suggestions: List<SearchSuggestion> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val videos: List<SearchVideo> = emptyList(),
    val users: List<SearchUser> = emptyList(),
    val articles: List<SearchArticle> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
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

    /** 已经翻到底的分类。按分类记，滑到别的一类再滑回来不会重复请求空页。 */
    private val endedTabs = mutableSetOf<Int>()
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
        endedTabs.clear()
        _state.update {
            SearchUiState(hot = it.hot, hotLoading = it.hotLoading)
        }
    }

    fun setTab(tab: Int) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, error = null) }
        if (_state.value.submitted.isNotBlank()) {
            val hasItems = when (tab) {
                0 -> _state.value.videos.isNotEmpty()
                1 -> _state.value.users.isNotEmpty()
                else -> _state.value.articles.isNotEmpty()
            }
            if (!hasItems) {
                // 只补这一类，不重跑整次搜索。pager 左右滑动会反复经过空分类，
                // 走 submit 每次都会把另外两类已经拿到的结果清掉。
                _state.update { it.copy(isLoading = true) }
                load(firstPage = true)
            }
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
        endedTabs.clear()
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
                error = null,
            )
        }
        load(firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.tab in endedTabs || current.submitted.isBlank()) return
        if (current.query.trim() != current.submitted) return
        if (current.isLoading) return
        _state.update { it.copy(isLoadingMore = true) }
        load(firstPage = false)
    }

    fun retry() {
        if (_state.value.submitted.isBlank()) return
        submit(_state.value.submitted)
    }

    /** 换排序方式：三类结果都作废，按新顺序从第一页重来。 */
    fun setOrder(order: String) {
        if (_state.value.order == order) return
        reload(_state.value.copy(order = order))
    }

    /** 换视频分区，只影响视频这一类；目录里的另外两类不受牵连。 */
    fun setTid(tid: Int) {
        if (_state.value.tid == tid) return
        reload(_state.value.copy(tid = tid))
    }

    private fun reload(next: SearchUiState) {
        videoPage = 1
        userPage = 1
        articlePage = 1
        endedTabs.clear()
        _state.update {
            next.copy(
                videos = emptyList(),
                users = emptyList(),
                articles = emptyList(),
                isLoading = true,
                error = null,
            )
        }
        if (next.submitted.isNotBlank()) load(firstPage = true)
    }

    private fun load(firstPage: Boolean) {
        inFlight = true
        val keyword = _state.value.submitted
        val tab = _state.value.tab
        val order = _state.value.order
        val tid = _state.value.tid
        val page = when (tab) {
            0 -> videoPage
            1 -> userPage
            else -> articlePage
        }
        viewModelScope.launch {
            when (tab) {
                0 -> repository.searchVideos(keyword, page, order, tid)
                    .onSuccess { list ->
                        videoPage = page + 1
                        if (list.isEmpty()) endedTabs.add(tab) else endedTabs.remove(tab)
                        _state.update {
                            val onTab = it.tab == tab
                            it.copy(
                                videos = if (firstPage) list.distinctBy { v -> v.bvid } else (it.videos + list).distinctBy { v -> v.bvid },
                                isLoading = if (onTab) false else it.isLoading,
                                isLoadingMore = if (onTab) false else it.isLoadingMore,
                                error = if (onTab) null else it.error,
                            )
                        }
                    }
                    .onFailure { error -> fail(tab, firstPage, error) }

                1 -> repository.searchUsers(keyword, page)
                    .onSuccess { list ->
                        userPage = page + 1
                        if (list.isEmpty()) endedTabs.add(tab) else endedTabs.remove(tab)
                        _state.update {
                            val onTab = it.tab == tab
                            it.copy(
                                users = if (firstPage) list.distinctBy { u -> u.mid } else (it.users + list).distinctBy { u -> u.mid },
                                isLoading = if (onTab) false else it.isLoading,
                                isLoadingMore = if (onTab) false else it.isLoadingMore,
                                error = if (onTab) null else it.error,
                            )
                        }
                    }
                    .onFailure { error -> fail(tab, firstPage, error) }

                else -> repository.searchArticles(keyword, page, order)
                    .onSuccess { list ->
                        articlePage = page + 1
                        if (list.isEmpty()) endedTabs.add(tab) else endedTabs.remove(tab)
                        _state.update {
                            val onTab = it.tab == tab
                            it.copy(
                                articles = if (firstPage) list.distinctBy { a -> a.id } else (it.articles + list).distinctBy { a -> a.id },
                                isLoading = if (onTab) false else it.isLoading,
                                isLoadingMore = if (onTab) false else it.isLoadingMore,
                                error = if (onTab) null else it.error,
                            )
                        }
                    }
                    .onFailure { error -> fail(tab, firstPage, error) }
            }
            inFlight = false
        }
    }

    private fun fail(tab: Int, firstPage: Boolean, error: Throwable) {
        _state.update {
            val onTab = it.tab == tab
            it.copy(
                isLoading = if (onTab) false else it.isLoading,
                isLoadingMore = if (onTab) false else it.isLoadingMore,
                error = if (onTab && firstPage) error.message ?: "搜索失败" else it.error,
            )
        }
    }
}
