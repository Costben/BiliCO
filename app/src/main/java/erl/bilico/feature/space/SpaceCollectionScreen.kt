@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.space

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.model.SpaceCollection
import erl.bilico.data.repository.SpaceRepository
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LibraryVideoRow
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SecondaryPage
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Sort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class SpaceCollectionScreenState(
    val videos: List<LibraryVideo> = emptyList(),
    val sortOrder: SpaceSort = SpaceSort.TIME,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
) {
    val visibleVideos: List<LibraryVideo>
        get() = when (sortOrder) {
            SpaceSort.TIME -> videos
            SpaceSort.HEAT -> videos.sortedByDescending { it.playCount }
        }
}

private class SpaceCollectionViewModel(
    private val spaceRepository: SpaceRepository,
    private val mid: Long,
    collectionId: Long,
    isSeason: Boolean,
    title: String,
) : ViewModel() {

    private val collection = SpaceCollection(
        id = collectionId,
        isSeason = isSeason,
        name = title,
        cover = "",
        description = "",
        total = 0,
    )
    private val _state = MutableStateFlow(SpaceCollectionScreenState())
    val state: StateFlow<SpaceCollectionScreenState> = _state.asStateFlow()

    private var nextPage = 1
    private var inFlight = false

    init {
        load(firstPage = true)
    }

    fun refresh() {
        if (inFlight) return
        load(firstPage = true, refreshing = true)
    }

    fun retry() = load(firstPage = true)

    fun setSortOrder(order: SpaceSort) {
        if (_state.value.sortOrder == order) return
        _state.update { it.copy(sortOrder = order) }
    }

    fun loadMore() {
        val s = _state.value
        if (inFlight || s.isEnd || s.videos.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        load(firstPage = false)
    }

    private fun load(firstPage: Boolean, refreshing: Boolean = false) {
        if (!firstPage && inFlight) return
        inFlight = true
        val page = if (firstPage) 1 else nextPage
        if (firstPage) {
            nextPage = 1
            _state.update {
                it.copy(
                    isLoading = !refreshing || it.videos.isEmpty(),
                    isRefreshing = refreshing && it.videos.isNotEmpty(),
                    isLoadingMore = false,
                    error = null,
                )
            }
        }
        viewModelScope.launch {
            spaceRepository.collectionVideos(mid, collection, page)
                .onSuccess { (list, hasMore) ->
                    nextPage = page + 1
                    _state.update {
                        it.copy(
                            videos = if (firstPage) list else it.videos + list,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            isEnd = !hasMore,
                            error = null,
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (firstPage && it.videos.isEmpty()) err.message ?: "合集内容加载失败" else it.error,
                        )
                    }
                }
            inFlight = false
        }
    }
}

@Composable
fun SpaceCollectionScreen(
    nav: NavState,
    container: AppContainer,
    mid: Long,
    collectionId: Long,
    isSeason: Boolean,
    title: String,
    total: Int = 0,
) {
    val viewModel: SpaceCollectionViewModel = viewModel(
        key = "space-col-$mid-$isSeason-$collectionId",
    ) {
        SpaceCollectionViewModel(container.space, mid, collectionId, isSeason, title)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var sortMenuVisible by remember { mutableStateOf(false) }

    LaunchedEffect(listState, state.videos.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { last ->
                val size = viewModel.state.value.visibleVideos.size
                if (last != null && size > 0 && last >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    val kindLabel = if (isSeason) "合集" else "系列"
    val subtitleText = if (total > 0) "$kindLabel · 共 $total 个视频" else kindLabel

    SecondaryPage(
        title = title.ifBlank { kindLabel },
        subtitle = subtitleText,
        onBack = nav::pop,
        listState = listState,
        platformBarMaterial = false,
        refreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        actions = {
            Box {
                HighlightedIconButton(
                    onClick = { sortMenuVisible = true },
                    modifier = Modifier.couiTopAppBarActionItem(),
                ) {
                    Icon(
                        imageVector = COUIIcons.Sort,
                        contentDescription = "排序方式",
                        modifier = Modifier.size(24.dp),
                    )
                }
                CouiPopupMenu(
                    show = sortMenuVisible,
                    onDismiss = { sortMenuVisible = false },
                    alignment = PopupPositionProvider.Align.End,
                    items = SpaceSort.entries.map { option ->
                        CouiMenuItem(
                            label = option.label,
                            onClick = { viewModel.setSortOrder(option) },
                            selected = option == state.sortOrder,
                        )
                    },
                )
            }
        },
    ) {
        val list = state.visibleVideos
        if (list.isEmpty()) {
            when {
                state.isLoading -> item(key = "col-loading") { LoadingState() }
                state.error != null -> item(key = "col-error") {
                    ErrorState(state.error.orEmpty(), onRetry = viewModel::retry)
                }
                else -> item(key = "col-empty") { EmptyState("合集里还没有视频") }
            }
        }
        items(
            items = list,
            key = { "col-v-${it.bvid}" },
            contentType = { "space-collection-video" },
        ) { video ->
            LibraryVideoRow(
                video = video,
                onClick = { nav.push(Route.Video(video.bvid)) },
                longPressItems = rememberSpaceVideoMenuItems(
                    video = video,
                    profile = null,
                    container = container,
                ),
            )
        }
        if (state.isLoadingMore) {
            item(key = "col-more") { LoadingState() }
        }
    }
}
