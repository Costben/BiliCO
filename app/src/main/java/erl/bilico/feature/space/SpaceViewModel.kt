package erl.bilico.feature.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.model.SpaceProfile
import erl.bilico.data.repository.DynamicRepository
import erl.bilico.data.repository.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpaceTab(val title: String) {
    VIDEOS("投稿"),
    DYNAMICS("动态"),
}

data class SpaceUiState(
    val profile: SpaceProfile? = null,
    val selectedTab: SpaceTab = SpaceTab.VIDEOS,
    // 投稿
    val videos: List<LibraryVideo> = emptyList(),
    val isVideosLoading: Boolean = true,
    val isVideosLoadingMore: Boolean = false,
    val isVideosEnd: Boolean = false,
    val videosError: String? = null,
    // 动态
    val dynamics: List<DynamicItemUi> = emptyList(),
    val isDynamicsLoading: Boolean = false,
    val isDynamicsLoadingMore: Boolean = false,
    val isDynamicsEnd: Boolean = false,
    val dynamicsOffset: String = "",
    val dynamicsError: String? = null,
    // 搜索
    val isSearchActive: Boolean = false,
    val searchKeyword: String = "",
    val error: String? = null,
) {
    /** 搜索过滤后的动态（若有关键词则本地匹配正文、标题、UP主名称）。 */
    val filteredDynamics: List<DynamicItemUi>
        get() = if (searchKeyword.isBlank()) {
            dynamics
        } else {
            val kw = searchKeyword.trim().lowercase()
            dynamics.filter { item ->
                item.content.lowercase().contains(kw) ||
                    item.title.lowercase().contains(kw) ||
                    item.authorName.lowercase().contains(kw) ||
                    item.forward?.content.orEmpty().lowercase().contains(kw)
            }
        }
}

class SpaceViewModel(
    private val spaceRepository: SpaceRepository,
    private val dynamicRepository: DynamicRepository,
    private val mid: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(SpaceUiState())
    val state: StateFlow<SpaceUiState> = _state.asStateFlow()

    private var nextVideoPage = 1
    private var inFlightVideos = false
    private var inFlightDynamics = false

    init {
        loadProfile()
        loadVideos(firstPage = true)
    }

    fun selectTab(tab: SpaceTab) {
        if (_state.value.selectedTab == tab) return
        _state.update { it.copy(selectedTab = tab) }
        if (tab == SpaceTab.DYNAMICS && _state.value.dynamics.isEmpty() && !_state.value.isDynamicsLoading) {
            loadDynamics(firstPage = true)
        }
    }

    fun toggleSearch(active: Boolean? = null) {
        _state.update {
            val next = active ?: !it.isSearchActive
            if (!next && it.searchKeyword.isNotBlank()) {
                // 关闭搜索并清空关键词，恢复全部
                it.copy(isSearchActive = false, searchKeyword = "")
            } else {
                it.copy(isSearchActive = next)
            }
        }
        if (!_state.value.isSearchActive && _state.value.selectedTab == SpaceTab.VIDEOS) {
            loadVideos(firstPage = true)
        }
    }

    fun updateKeyword(kw: String) {
        _state.update { it.copy(searchKeyword = kw) }
    }

    fun submitSearch(keyword: String = _state.value.searchKeyword) {
        val kw = keyword.trim()
        _state.update { it.copy(searchKeyword = kw) }
        if (_state.value.selectedTab == SpaceTab.VIDEOS) {
            loadVideos(firstPage = true)
        }
    }

    fun retry() {
        if (_state.value.profile == null) loadProfile()
        when (_state.value.selectedTab) {
            SpaceTab.VIDEOS -> loadVideos(firstPage = true)
            SpaceTab.DYNAMICS -> loadDynamics(firstPage = true)
        }
    }

    fun loadMore() {
        when (_state.value.selectedTab) {
            SpaceTab.VIDEOS -> {
                val current = _state.value
                if (inFlightVideos || current.isVideosEnd || current.videos.isEmpty()) return
                _state.update { it.copy(isVideosLoadingMore = true) }
                loadVideos(firstPage = false)
            }
            SpaceTab.DYNAMICS -> {
                val current = _state.value
                if (inFlightDynamics || current.isDynamicsEnd || current.dynamics.isEmpty()) return
                _state.update { it.copy(isDynamicsLoadingMore = true) }
                loadDynamics(firstPage = false)
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            spaceRepository.profile(mid)
                .onSuccess { profile ->
                    _state.update { it.copy(profile = profile, error = null) }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.videos.isEmpty() && it.dynamics.isEmpty()) {
                            it.copy(error = error.message ?: "空间加载失败")
                        } else {
                            it
                        }
                    }
                }
        }
    }

    private fun loadVideos(firstPage: Boolean) {
        inFlightVideos = true
        val page = if (firstPage) 1 else nextVideoPage
        val keyword = _state.value.searchKeyword
        if (firstPage) {
            _state.update { it.copy(isVideosLoading = true, videosError = null) }
        }
        viewModelScope.launch {
            spaceRepository.videos(mid, page, keyword)
                .onSuccess { (list, hasMore) ->
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
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isVideosLoading = false,
                            isVideosLoadingMore = false,
                            videosError = if (firstPage) error.message ?: "投稿加载失败" else it.videosError,
                        )
                    }
                }
            inFlightVideos = false
        }
    }

    private fun loadDynamics(firstPage: Boolean) {
        inFlightDynamics = true
        val offset = if (firstPage) "" else _state.value.dynamicsOffset
        if (firstPage) {
            _state.update { it.copy(isDynamicsLoading = true, dynamicsError = null) }
        }
        viewModelScope.launch {
            dynamicRepository.spaceFeed(mid, offset)
                .onSuccess { page ->
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
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isDynamicsLoading = false,
                            isDynamicsLoadingMore = false,
                            dynamicsError = if (firstPage) error.message ?: "动态加载失败" else it.dynamicsError,
                        )
                    }
                }
            inFlightDynamics = false
        }
    }
}
