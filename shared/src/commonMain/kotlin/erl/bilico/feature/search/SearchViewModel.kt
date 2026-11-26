package erl.bilico.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.SearchPageResult
import erl.bilico.data.repository.SearchRepository
import erl.bilico.platform.SimpleDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: SearchRepository,
    initialQuery: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val tabPages = IntArray(SearchTab.entries.size) { 1 }
    private val endedTabs = mutableSetOf<SearchTab>()
    private val tabJobs = mutableMapOf<SearchTab, Job>()
    private var suggestJob: Job? = null
    private var lastSuggestTerm: String = ""

    init {
        loadHot()
        val initial = initialQuery.trim()
        if (initial.isNotEmpty()) submit(initial)
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
            cancelSuggest()
            _state.update { it.copy(suggestions = emptyList(), suggestionsLoading = false, submitted = "") }
            return
        }
        if (trimmed == _state.value.submitted) {
            cancelSuggest()
            _state.update { it.copy(suggestions = emptyList(), suggestionsLoading = false) }
            return
        }
        if (trimmed == lastSuggestTerm && (_state.value.suggestions.isNotEmpty() || suggestJob?.isActive == true)) {
            return
        }
        lastSuggestTerm = trimmed
        suggestJob?.cancel()
        _state.update { it.copy(suggestionsLoading = true) }
        suggestJob = viewModelScope.launch {
            delay(160)
            val list = repository.suggest(trimmed).getOrDefault(emptyList())
            val current = _state.value
            if (current.query.trim() == trimmed && current.submitted != trimmed) {
                _state.update { it.copy(suggestions = list, suggestionsLoading = false) }
            }
        }
    }

    /** 重新打开面板时回到搜索首页，不带着上次的结果；热搜缓存留着。 */
    fun reset() {
        cancelSuggest()
        cancelAllLoads()
        tabPages.fill(1)
        endedTabs.clear()
        _state.update { SearchUiState(hot = it.hot, hotLoading = it.hotLoading) }
    }

    fun setTab(tab: Int) {
        val target = SearchTab.fromIndex(tab)
        if (_state.value.tab == target.ordinal) return
        val inFlight = tabJobs[target]?.isActive == true
        val needsLoad = _state.value.submitted.isNotBlank() &&
            target !in _state.value.loadedTabs &&
            !inFlight
        _state.update {
            it.copy(
                tab = target.ordinal,
                isLoading = inFlight || needsLoad,
                isLoadingMore = false,
                error = null,
            )
        }
        if (needsLoad) load(target, firstPage = true)
    }

    fun submit(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        cancelSuggest()
        cancelAllLoads()
        tabPages.fill(1)
        endedTabs.clear()
        val currentTab = _state.value.currentTab
        _state.update {
            it.copy(
                query = trimmed,
                submitted = trimmed,
                suggestions = emptyList(),
                suggestionsLoading = false,
                allUsers = emptyList(),
                allPgc = emptyList(),
                allVideos = emptyList(),
                videos = emptyList(),
                bangumis = emptyList(),
                movies = emptyList(),
                liveRooms = emptyList(),
                users = emptyList(),
                articles = emptyList(),
                tabCounts = emptyMap(),
                loadedTabs = emptySet(),
                isLoading = true,
                isLoadingMore = false,
                error = null,
            )
        }
        load(currentTab, firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        val target = current.currentTab
        if (current.submitted.isBlank() || current.query.trim() != current.submitted) return
        if (current.isLoading || target in endedTabs || tabJobs[target]?.isActive == true) return
        _state.update { it.copy(isLoadingMore = true) }
        load(target, firstPage = false)
    }

    fun retry() {
        val current = _state.value
        if (current.submitted.isBlank()) return
        reloadTabs(setOf(current.currentTab), current)
    }

    internal fun setVideoOrder(order: VideoOrderOption) {
        val current = _state.value
        if (current.videoOrder == order) return
        reloadVideoTabs(current.copy(videoOrder = order))
    }

    internal fun setVideoDuration(duration: VideoDurationOption) {
        val current = _state.value
        if (current.videoDuration == duration) return
        reloadVideoTabs(current.copy(videoDuration = duration))
    }

    internal fun setVideoZone(zone: VideoZoneOption) {
        val current = _state.value
        if (current.videoZone == zone) return
        reloadVideoTabs(current.copy(videoZone = zone))
    }

    internal fun setVideoPubTime(pubTime: VideoPubTimeOption) {
        val current = _state.value
        if (current.videoPubTime == pubTime && pubTime != VideoPubTimeOption.Custom) return
        reloadVideoTabs(current.copy(videoPubTime = pubTime))
    }

    internal fun setVideoCustomPubRange(begin: SimpleDate, end: SimpleDate) {
        val ordered = if (begin.isAfter(end)) end to begin else begin to end
        reloadVideoTabs(
            _state.value.copy(
                videoPubTime = VideoPubTimeOption.Custom,
                customPubBegin = ordered.first,
                customPubEnd = ordered.second,
            ),
        )
    }

    internal fun setUserOrder(order: UserOrderOption) {
        val current = _state.value
        if (current.userOrder == order) return
        reloadTabs(setOf(SearchTab.User), current.copy(userOrder = order))
    }

    internal fun setUserType(type: UserTypeOption) {
        val current = _state.value
        if (current.userType == type) return
        reloadTabs(setOf(SearchTab.User), current.copy(userType = type))
    }

    internal fun setArticleOrder(order: ArticleOrderOption) {
        val current = _state.value
        if (current.articleOrder == order) return
        reloadTabs(setOf(SearchTab.Article), current.copy(articleOrder = order))
    }

    internal fun setArticleZone(zone: ArticleZoneOption) {
        val current = _state.value
        if (current.articleZone == zone) return
        reloadTabs(setOf(SearchTab.Article), current.copy(articleZone = zone))
    }

    private fun reloadVideoTabs(next: SearchUiState) {
        reloadTabs(setOf(SearchTab.All, SearchTab.Video), next)
    }

    private fun reloadTabs(targets: Set<SearchTab>, next: SearchUiState) {
        targets.forEach { target ->
            tabJobs.remove(target)?.cancel()
            tabPages[target.ordinal] = 1
            endedTabs.remove(target)
        }
        val current = next.currentTab
        val resetState = next.copy(
            allUsers = if (SearchTab.All in targets) emptyList() else next.allUsers,
            allPgc = if (SearchTab.All in targets) emptyList() else next.allPgc,
            allVideos = if (SearchTab.All in targets) emptyList() else next.allVideos,
            videos = if (SearchTab.Video in targets) emptyList() else next.videos,
            bangumis = if (SearchTab.Bangumi in targets) emptyList() else next.bangumis,
            movies = if (SearchTab.Movie in targets) emptyList() else next.movies,
            liveRooms = if (SearchTab.LiveRoom in targets) emptyList() else next.liveRooms,
            users = if (SearchTab.User in targets) emptyList() else next.users,
            articles = if (SearchTab.Article in targets) emptyList() else next.articles,
            loadedTabs = next.loadedTabs - targets,
            isLoading = current in targets && next.submitted.isNotBlank(),
            isLoadingMore = false,
            error = if (current in targets) null else next.error,
        )
        _state.value = resetState
        if (current in targets && resetState.submitted.isNotBlank()) {
            load(current, firstPage = true)
        }
    }

    private fun load(target: SearchTab, firstPage: Boolean) {
        val snapshot = _state.value
        val keyword = snapshot.submitted
        if (keyword.isBlank()) return
        val page = tabPages[target.ordinal]
        val (pubBeginSec, pubEndSec) = computePubTimeSeconds(
            snapshot.videoPubTime, snapshot.customPubBegin, snapshot.customPubEnd,
        )
        tabJobs[target]?.cancel()
        tabJobs[target] = viewModelScope.launch {
            when (target) {
                SearchTab.All -> if (firstPage && !snapshot.hasVideoFilter) {
                    repository.searchAll(keyword)
                        .onSuccess { bundle ->
                            tabPages[target.ordinal] = page + 1
                            if (bundle.videos.isEmpty()) endedTabs.add(target) else endedTabs.remove(target)
                            updateSuccess(target) {
                                copy(
                                    allUsers = bundle.users,
                                    allPgc = bundle.pgc,
                                    allVideos = bundle.videos,
                                    tabCounts = tabCounts + bundle.typeCounts.toTabCountMap(bundle.numResults),
                                )
                            }
                        }
                        .onFailure { fail(target, firstPage, it) }
                } else {
                    val res = repository.searchVideos(
                        keyword, page, snapshot.videoOrder.param, snapshot.videoZone.tid,
                        snapshot.videoDuration.param, pubBeginSec, pubEndSec,
                    )
                    handleTypePage(target, page, firstPage, res) {
                        copy(allVideos = mergeDistinct(firstPage, allVideos, it) { v -> v.bvid })
                    }
                }

                SearchTab.Video -> {
                    val res = repository.searchVideos(
                        keyword, page, snapshot.videoOrder.param, snapshot.videoZone.tid,
                        snapshot.videoDuration.param, pubBeginSec, pubEndSec,
                    )
                    handleTypePage(target, page, firstPage, res) {
                        copy(videos = mergeDistinct(firstPage, videos, it) { v -> v.bvid })
                    }
                }

                SearchTab.Bangumi, SearchTab.Movie -> {
                    val searchType = if (target == SearchTab.Bangumi) "media_bangumi" else "media_ft"
                    handleTypePage(target, page, firstPage, repository.searchPgc(keyword, page, searchType)) {
                        if (target == SearchTab.Bangumi) {
                            copy(bangumis = mergeDistinct(firstPage, bangumis, it) { p -> p.seasonId })
                        } else {
                            copy(movies = mergeDistinct(firstPage, movies, it) { p -> p.seasonId })
                        }
                    }
                }

                SearchTab.LiveRoom -> handleTypePage(
                    target, page, firstPage, repository.searchLiveRooms(keyword, page),
                ) { copy(liveRooms = mergeDistinct(firstPage, liveRooms, it) { r -> r.roomId }) }

                SearchTab.User -> {
                    val res = repository.searchUsers(
                        keyword, page, snapshot.userOrder.order, snapshot.userOrder.orderSort, snapshot.userType.param,
                    )
                    handleTypePage(target, page, firstPage, res) {
                        copy(users = mergeDistinct(firstPage, users, it) { u -> u.mid })
                    }
                }

                SearchTab.Article -> {
                    val res = repository.searchArticles(
                        keyword, page, snapshot.articleOrder.param, snapshot.articleZone.categoryId,
                    )
                    handleTypePage(target, page, firstPage, res) {
                        copy(articles = mergeDistinct(firstPage, articles, it) { a -> a.id })
                    }
                }
            }
        }
    }

    private inline fun <T> handleTypePage(
        target: SearchTab,
        page: Int,
        firstPage: Boolean,
        result: Result<SearchPageResult<T>>,
        apply: SearchUiState.(List<T>) -> SearchUiState,
    ) {
        result.onSuccess { pageRes ->
            tabPages[target.ordinal] = page + 1
            if (pageRes.items.isEmpty()) endedTabs.add(target) else endedTabs.remove(target)
            updateSuccess(target) {
                val nextCounts = if (firstPage) tabCounts + (target to pageRes.numResults) else tabCounts
                apply(pageRes.items).copy(tabCounts = nextCounts)
            }
        }.onFailure { fail(target, firstPage, it) }
    }

    private inline fun updateSuccess(target: SearchTab, transform: SearchUiState.() -> SearchUiState) {
        _state.update { state ->
            val onTab = state.currentTab == target
            state.transform().copy(
                loadedTabs = state.loadedTabs + target,
                isLoading = if (onTab) false else state.isLoading,
                isLoadingMore = if (onTab) false else state.isLoadingMore,
                error = if (onTab) null else state.error,
            )
        }
    }

    private fun fail(target: SearchTab, firstPage: Boolean, error: Throwable) {
        _state.update { state ->
            val onTab = state.currentTab == target
            state.copy(
                loadedTabs = if (firstPage) state.loadedTabs + target else state.loadedTabs,
                isLoading = if (onTab) false else state.isLoading,
                isLoadingMore = if (onTab) false else state.isLoadingMore,
                error = if (onTab && firstPage) error.message ?: "搜索失败" else state.error,
            )
        }
    }

    private fun <T, K> mergeDistinct(
        firstPage: Boolean,
        existing: List<T>,
        incoming: List<T>,
        keySelector: (T) -> K,
    ): List<T> = if (firstPage) incoming.distinctBy(keySelector) else (existing + incoming).distinctBy(keySelector)

    private fun cancelSuggest() {
        suggestJob?.cancel()
        suggestJob = null
        lastSuggestTerm = ""
    }

    private fun cancelAllLoads() {
        tabJobs.values.forEach { it.cancel() }
        tabJobs.clear()
    }
}
