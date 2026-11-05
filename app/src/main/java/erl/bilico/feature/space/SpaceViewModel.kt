package erl.bilico.feature.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.model.SpaceProfile
import erl.bilico.data.repository.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpaceUiState(
    val profile: SpaceProfile? = null,
    val videos: List<LibraryVideo> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class SpaceViewModel(
    private val repository: SpaceRepository,
    private val mid: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(SpaceUiState())
    val state: StateFlow<SpaceUiState> = _state.asStateFlow()

    private var nextPage = 1
    private var inFlight = false

    init {
        loadProfile()
        loadVideos(firstPage = true)
    }

    fun retry() {
        if (_state.value.profile == null) loadProfile()
        if (_state.value.videos.isEmpty()) loadVideos(firstPage = true)
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.videos.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        loadVideos(firstPage = false)
    }

    private fun loadProfile() {
        viewModelScope.launch {
            repository.profile(mid)
                .onSuccess { profile ->
                    _state.update { it.copy(profile = profile, error = null) }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.videos.isEmpty()) {
                            it.copy(isLoading = false, error = error.message ?: "空间加载失败")
                        } else {
                            it
                        }
                    }
                }
        }
    }

    private fun loadVideos(firstPage: Boolean) {
        inFlight = true
        val page = if (firstPage) 1 else nextPage
        viewModelScope.launch {
            repository.videos(mid, page)
                .onSuccess { (list, hasMore) ->
                    nextPage = page + 1
                    _state.update {
                        it.copy(
                            videos = if (firstPage) list else it.videos + list,
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
                            error = if (firstPage) error.message ?: "投稿加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}
