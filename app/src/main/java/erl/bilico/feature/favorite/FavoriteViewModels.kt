package erl.bilico.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.repository.FavoriteRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class FavoriteFoldersUiState(
    val items: List<FavoriteFolder> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class FavoriteFoldersViewModel(
    private val repository: FavoriteRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoriteFoldersUiState())
    val state: StateFlow<FavoriteFoldersUiState> = _state.asStateFlow()

    private var coverJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val mid = session.account.value?.mid
        if (mid == null || mid <= 0L) {
            _state.value = FavoriteFoldersUiState(isLoading = false)
            return
        }
        _state.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            repository.folders(mid)
                .onSuccess { list ->
                    _state.value = FavoriteFoldersUiState(items = list, isLoading = false)
                    coverJob?.cancel()
                    coverJob = launch { fillCovers(list) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "收藏夹加载失败")
                    }
                }
        }
    }

    /** 列表接口只给收藏夹自己的封面，这里按夹子逐个补前四个视频的封面，拼成四宫格。 */
    private suspend fun fillCovers(folders: List<FavoriteFolder>) {
        val gate = Semaphore(4)
        coroutineScope {
            folders.forEach { folder ->
                launch {
                    gate.withPermit {
                        repository.covers(folder.mediaId).onSuccess { urls ->
                            if (urls.isEmpty()) return@withPermit
                            _state.update { state ->
                                state.copy(
                                    items = state.items.map {
                                        if (it.mediaId == folder.mediaId) it.copy(coverUrls = urls) else it
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class FolderContentUiState(
    val items: List<LibraryVideo> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class FavoriteFolderContentViewModel(
    private val repository: FavoriteRepository,
    private val mediaId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(FolderContentUiState())
    val state: StateFlow<FolderContentUiState> = _state.asStateFlow()

    private var nextPage = 1
    private var inFlight = false

    init {
        load(firstPage = true)
    }

    fun retry() = load(firstPage = true)

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.items.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        load(firstPage = false)
    }

    /** 左滑删除单条收藏：先乐观移除，失败回滚到原位。 */
    fun remove(video: LibraryVideo, onFailure: (String) -> Unit = {}) {
        if (video.aid <= 0L) return
        val before = _state.value.items
        val index = before.indexOfFirst { it.bvid == video.bvid }
        if (index < 0) return
        _state.update { state ->
            state.copy(items = state.items.filterNot { it.bvid == video.bvid })
        }
        viewModelScope.launch {
            repository.removeVideos(mediaId, listOf(video))
                .onSuccess {
                    if (_state.value.items.size < 10 && !_state.value.isEnd) loadMore()
                }
                .onFailure { error ->
                    _state.update { state ->
                        if (state.items.any { it.bvid == video.bvid }) {
                            state
                        } else {
                            val restored = state.items.toMutableList().apply {
                                add(index.coerceAtMost(size), video)
                            }
                            state.copy(items = restored)
                        }
                    }
                    onFailure(error.message ?: "删除失败")
                }
        }
    }

    /** 多选批量删除收藏的视频：先乐观移除，失败则整体回滚。 */
    fun removeBatch(
        bvids: Set<String>,
        onFailure: (String) -> Unit = {},
    ) {
        if (bvids.isEmpty()) return
        val snapshot = _state.value.items
        val targets = snapshot.filter { it.bvid in bvids && it.aid > 0L }
        if (targets.isEmpty()) return
        val targetSet = targets.map { it.bvid }.toSet()
        _state.update { state ->
            state.copy(items = state.items.filterNot { it.bvid in targetSet })
        }
        viewModelScope.launch {
            repository.removeVideos(mediaId, targets)
                .onSuccess {
                    if (_state.value.items.size < 10 && !_state.value.isEnd) loadMore()
                }
                .onFailure { error ->
                    _state.update { state ->
                        val currentIds = state.items.map { it.bvid }.toSet()
                        val merged = snapshot.filter { it.bvid in targetSet || it.bvid in currentIds }
                        val extras = state.items.filter { it.bvid !in snapshot.mapTo(HashSet()) { s -> s.bvid } }
                        state.copy(items = merged + extras)
                    }
                    onFailure(error.message ?: "删除失败")
                }
        }
    }

    private fun load(firstPage: Boolean) {
        inFlight = true
        val page = if (firstPage) 1 else nextPage
        viewModelScope.launch {
            repository.videos(mediaId, page)
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
                            error = if (firstPage) error.message ?: "收藏内容加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}
