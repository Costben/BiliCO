package erl.bilico.feature.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.repository.DynamicRepository
import erl.bilico.data.repository.InteractionRepository
import erl.bilico.data.repository.SpaceRepository
import erl.bilico.data.session.SessionRepository
import erl.bilico.feature.dynamic.DynamicEnricher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpaceViewModel(
    private val spaceRepository: SpaceRepository,
    private val dynamicRepository: DynamicRepository,
    private val interactions: InteractionRepository,
    private val session: SessionRepository,
    private val mid: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(SpaceUiState())
    val state: StateFlow<SpaceUiState> = _state.asStateFlow()

    private var nextVideoPage = 1
    private var inFlightVideos = false
    private var videoRequestId = 0
    private var loadedVideoSort: SpaceSort? = null

    private var nextCollectionPage = 1
    private var inFlightCollections = false
    private var inFlightDynamics = false
    private var nextOpusPage = 1
    private var inFlightOpus = false
    private var inFlightLikes = false

    private val enricher = DynamicEnricher(dynamicRepository, viewModelScope) { transform ->
        _state.update { it.copy(dynamics = transform(it.dynamics)) }
    }

    init {
        viewModelScope.launch {
            dynamicRepository.deletedIds.collect { deletedId ->
                _state.update { s ->
                    val next = s.dynamics.filterNot { it.dynamicId == deletedId }
                    if (next.size == s.dynamics.size) s else s.copy(dynamics = next)
                }
            }
        }
        loadProfile()
        loadVideos(firstPage = true)
        loadCollections(firstPage = true)
        loadLikedVideos()
        loadFollowing()
    }

    private fun DynamicItemUi.withLike(dynamicId: String, liked: Boolean): DynamicItemUi {
        if (this.dynamicId != dynamicId || this.liked == liked) return this
        val delta = if (liked) 1 else -1
        return copy(liked = liked, likeCount = (likeCount + delta).coerceAtLeast(0))
    }

    private fun loadFollowing() {
        if (mid <= 0L || mid == session.mid() || !session.isLoggedIn()) return
        viewModelScope.launch { _state.update { it.copy(following = interactions.isFollowing(mid)) } }
    }

    fun toggleFollow(onResult: (ok: Boolean, message: String) -> Unit = { _, _ -> }) {
        val current = _state.value
        if (mid <= 0L || current.isFollowBusy || mid == session.mid()) return
        if (!session.isLoggedIn()) { onResult(false, "登录后可用"); return }
        val target = !current.following
        _state.update { it.copy(isFollowBusy = true) }
        viewModelScope.launch {
            interactions.follow(mid, target)
                .onSuccess {
                    _state.update { it.copy(following = target, isFollowBusy = false) }
                    onResult(true, if (target) "已关注" else "已取消关注")
                }
                .onFailure { err ->
                    _state.update { it.copy(isFollowBusy = false) }
                    onResult(false, err.message ?: "操作失败")
                }
        }
    }

    fun deleteDynamic(dynamicId: String, onResult: (ok: Boolean, message: String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            dynamicRepository.deleteDynamic(dynamicId)
                .onSuccess { onResult(true, "已删除动态") }
                .onFailure { err -> onResult(false, err.message ?: "删除动态失败") }
        }
    }

    fun toggleLike(item: DynamicItemUi, onResult: (ok: Boolean, message: String) -> Unit = { _, _ -> }) {
        val id = item.dynamicId
        if (id.isBlank()) { onResult(false, "这条动态暂不支持点赞"); return }
        val liked = !item.liked
        _state.update { s -> s.copy(dynamics = s.dynamics.map { it.withLike(id, liked) }) }
        viewModelScope.launch {
            dynamicRepository.thumbDynamic(id, liked).onFailure { err ->
                _state.update { s -> s.copy(dynamics = s.dynamics.map { it.withLike(id, !liked) }) }
                onResult(false, err.message ?: "点赞失败")
            }
        }
    }

    fun selectTab(tab: SpaceTab) {
        if (tab == SpaceTab.LIKES && !_state.value.showLikesTab) return
        if (_state.value.selectedTab != tab) {
            _state.update { it.copy(selectedTab = tab) }
        }
        ensureTabLoaded(tab)
    }

    fun ensureTabLoaded(tab: SpaceTab) {
        val s = _state.value
        when (tab) {
            SpaceTab.VIDEOS -> if (loadedVideoSort != s.sortOrder) loadVideos(true, true)
            SpaceTab.SEASONS -> if (s.collections.isEmpty() && !s.isCollectionsLoading) loadCollections(true)
            SpaceTab.DYNAMICS -> if (s.dynamics.isEmpty() && !s.isDynamicsLoading) loadDynamics(true)
            SpaceTab.OPUS -> if (s.opusItems.isEmpty() && !s.isOpusLoading) loadOpus(true)
            SpaceTab.LIKES -> if (s.likedVideos.isEmpty() && !s.isLikesLoading && s.showLikesTab) loadLikedVideos()
        }
    }

    fun setSortOrder(order: SpaceSort) {
        if (_state.value.sortOrder == order) return
        _state.update { it.copy(sortOrder = order) }
        if (_state.value.selectedTab == SpaceTab.VIDEOS) loadVideos(firstPage = true, clearExisting = true)
    }

    fun toggleSearch(active: Boolean? = null) {
        _state.update {
            val next = active ?: !it.isSearchActive
            if (!next && it.searchKeyword.isNotBlank()) it.copy(isSearchActive = false, searchKeyword = "")
            else it.copy(isSearchActive = next)
        }
        val s = _state.value
        if (!s.isSearchActive && s.selectedTab == SpaceTab.VIDEOS) loadVideos(true)
    }

    fun updateKeyword(kw: String) = _state.update { it.copy(searchKeyword = kw) }

    fun submitSearch(keyword: String = _state.value.searchKeyword) {
        _state.update { it.copy(searchKeyword = keyword.trim()) }
        if (_state.value.selectedTab == SpaceTab.VIDEOS) loadVideos(true)
    }

    fun retry() {
        if (_state.value.profile == null) loadProfile()
        when (_state.value.selectedTab) {
            SpaceTab.VIDEOS -> loadVideos(true)
            SpaceTab.SEASONS -> loadCollections(true)
            SpaceTab.DYNAMICS -> loadDynamics(true)
            SpaceTab.OPUS -> loadOpus(true)
            SpaceTab.LIKES -> loadLikedVideos()
        }
    }

    fun loadMore() {
        val s = _state.value
        when (s.selectedTab) {
            SpaceTab.VIDEOS -> if (!inFlightVideos && !s.isVideosEnd && s.videos.isNotEmpty()) {
                _state.update { it.copy(isVideosLoadingMore = true) }
                loadVideos(false)
            }
            SpaceTab.SEASONS -> if (!inFlightCollections && !s.isCollectionsEnd && s.collections.isNotEmpty()) {
                _state.update { it.copy(isCollectionsLoadingMore = true) }
                loadCollections(false)
            }
            SpaceTab.DYNAMICS -> if (!inFlightDynamics && !s.isDynamicsEnd && s.dynamics.isNotEmpty()) {
                _state.update { it.copy(isDynamicsLoadingMore = true) }
                loadDynamics(false)
            }
            SpaceTab.OPUS -> if (!inFlightOpus && !s.isOpusEnd && s.opusItems.isNotEmpty()) {
                _state.update { it.copy(isOpusLoadingMore = true) }
                loadOpus(false)
            }
            SpaceTab.LIKES -> Unit
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            spaceRepository.profile(mid)
                .onSuccess { p -> _state.update { it.copy(profile = p, error = null) } }
                .onFailure { e ->
                    _state.update { if (it.videos.isEmpty() && it.dynamics.isEmpty()) it.copy(error = e.message ?: "空间加载失败") else it }
                }
        }
    }

    private fun loadVideos(firstPage: Boolean, clearExisting: Boolean = false) {
        if (!firstPage && inFlightVideos) return
        inFlightVideos = true
        val requestId = ++videoRequestId
        val page = if (firstPage) 1 else nextVideoPage
        val s = _state.value
        val sort = s.sortOrder
        if (firstPage) {
            nextVideoPage = 1
            _state.update {
                it.copy(
                    videos = if (clearExisting) emptyList() else it.videos,
                    isVideosLoading = true,
                    isVideosLoadingMore = false,
                    isVideosEnd = false,
                    videosError = null,
                )
            }
        }
        viewModelScope.launch {
            spaceRepository.videos(mid, page, s.searchKeyword, sort.apiOrder)
                .onSuccess { (list, hasMore) ->
                    if (requestId != videoRequestId) return@launch
                    loadedVideoSort = sort
                    nextVideoPage = page + 1
                    _state.update {
                        it.copy(
                            videos = if (firstPage) list else it.videos + list,
                            isVideosLoading = false,
                            isVideosLoadingMore = false,
                            isVideosEnd = !hasMore,
                            videosError = null,
                        )
                    }
                    inFlightVideos = false
                }
                .onFailure { e ->
                    if (requestId != videoRequestId) return@launch
                    _state.update {
                        it.copy(
                            isVideosLoading = false,
                            isVideosLoadingMore = false,
                            videosError = if (firstPage) e.message ?: "投稿加载失败" else it.videosError,
                        )
                    }
                    inFlightVideos = false
                }
        }
    }

    private fun loadCollections(firstPage: Boolean) {
        if (!firstPage && inFlightCollections) return
        inFlightCollections = true
        val page = if (firstPage) 1 else nextCollectionPage
        if (firstPage) _state.update { it.copy(isCollectionsLoading = true, collectionsError = null) }
        viewModelScope.launch {
            spaceRepository.collections(mid, page)
                .onSuccess { (list, hasMore) ->
                    nextCollectionPage = page + 1
                    _state.update {
                        it.copy(
                            collections = if (firstPage) list else it.collections + list,
                            isCollectionsLoading = false,
                            isCollectionsLoadingMore = false,
                            isCollectionsEnd = !hasMore,
                            collectionsError = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isCollectionsLoading = false,
                            isCollectionsLoadingMore = false,
                            collectionsError = if (firstPage) e.message ?: "合集加载失败" else it.collectionsError,
                        )
                    }
                }
            inFlightCollections = false
        }
    }

    private fun loadDynamics(firstPage: Boolean) {
        inFlightDynamics = true
        val offset = if (firstPage) "" else _state.value.dynamicsOffset
        if (firstPage) _state.update { it.copy(isDynamicsLoading = true, dynamicsError = null) }
        viewModelScope.launch {
            dynamicRepository.spaceFeed(mid, offset)
                .onSuccess { page ->
                    if (firstPage) enricher.clearHistory()
                    _state.update {
                        it.copy(
                            dynamics = if (firstPage) page.items else it.dynamics + page.items,
                            isDynamicsLoading = false,
                            isDynamicsLoadingMore = false,
                            isDynamicsEnd = !page.hasMore,
                            dynamicsOffset = page.offset,
                            dynamicsError = null,
                        )
                    }
                    enricher.enrich(page.items)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isDynamicsLoading = false,
                            isDynamicsLoadingMore = false,
                            dynamicsError = if (firstPage) e.message ?: "动态加载失败" else it.dynamicsError,
                        )
                    }
                }
            inFlightDynamics = false
        }
    }

    private fun loadOpus(firstPage: Boolean) {
        if (!firstPage && inFlightOpus) return
        inFlightOpus = true
        val page = if (firstPage) 1 else nextOpusPage
        val offset = if (firstPage) "" else _state.value.opusOffset
        if (firstPage) { nextOpusPage = 1; _state.update { it.copy(isOpusLoading = true, opusError = null) } }
        viewModelScope.launch {
            spaceRepository.opusFeed(mid, offset, page)
                .onSuccess { res ->
                    nextOpusPage = page + 1
                    _state.update {
                        it.copy(
                            opusItems = if (firstPage) res.items else it.opusItems + res.items,
                            isOpusLoading = false,
                            isOpusLoadingMore = false,
                            isOpusEnd = !res.hasMore,
                            opusOffset = res.offset,
                            opusError = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isOpusLoading = false,
                            isOpusLoadingMore = false,
                            opusError = if (firstPage) e.message ?: "图文加载失败" else it.opusError,
                        )
                    }
                }
            inFlightOpus = false
        }
    }

    private fun loadLikedVideos() {
        if (inFlightLikes) return
        inFlightLikes = true
        _state.update { it.copy(isLikesLoading = true, likesError = null) }
        viewModelScope.launch {
            spaceRepository.likedVideos(mid)
                .onSuccess { res ->
                    _state.update { s ->
                        val show = !res.isPrivate
                        val nextTab = if (!show && s.selectedTab == SpaceTab.LIKES) SpaceTab.VIDEOS else s.selectedTab
                        s.copy(
                            likedVideos = res.videos,
                            isLikesLoading = false,
                            showLikesTab = show,
                            selectedTab = nextTab,
                            likesError = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLikesLoading = false, likesError = e.message ?: "最近点赞加载失败") }
                }
            inFlightLikes = false
        }
    }
}
