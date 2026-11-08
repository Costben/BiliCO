package erl.bilico.feature.watchlater

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.LibraryVideo
import erl.bilico.data.repository.WatchLaterRepository
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.QueuedVideo
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
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Sort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 稍后再看的排列方式。接口只按收藏顺序给，切换排序都在本地做。 */
enum class WatchLaterSort(val label: String) {
    DEFAULT("默认顺序"),
    DURATION_ASC("时长从短到长"),
    DURATION_DESC("时长从长到短"),
    TITLE("标题"),
}

private fun List<LibraryVideo>.orderedBy(sort: WatchLaterSort): List<LibraryVideo> = when (sort) {
    WatchLaterSort.DEFAULT -> this
    WatchLaterSort.DURATION_ASC -> sortedBy { it.durationSec }
    WatchLaterSort.DURATION_DESC -> sortedByDescending { it.durationSec }
    WatchLaterSort.TITLE -> sortedBy { it.title }
}

data class WatchLaterUiState(
    val items: List<LibraryVideo> = emptyList(),
    val sort: WatchLaterSort = WatchLaterSort.DEFAULT,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isEnd: Boolean = false,
    val error: String? = null,
)

class WatchLaterViewModel(
    private val repository: WatchLaterRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchLaterUiState())
    val state: StateFlow<WatchLaterUiState> = _state.asStateFlow()

    /** 接口给的原始顺序；排序只影响展示，翻页仍然接在原表后面。 */
    private var raw: List<LibraryVideo> = emptyList()
    private var sort = WatchLaterSort.DEFAULT
    private var nextPage = 1
    private var inFlight = false

    init {
        load()
    }

    fun retry() = load()

    fun setSort(value: WatchLaterSort) {
        if (value == sort) return
        sort = value
        _state.update { it.copy(sort = value, items = raw.orderedBy(value)) }
    }

    fun loadMore() {
        val current = _state.value
        if (inFlight || current.isEnd || current.items.isEmpty()) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { fetch(nextPage) }
    }

    /**
     * 「播放全部」用：把还没拉的页补齐，返回当前排序下的整份列表。
     * 中途失败就用手上已有的部分，不把整次播放卡死。
     */
    suspend fun loadAllForPlayback(): List<LibraryVideo> {
        while (!_state.value.isEnd && !inFlight) {
            val before = raw.size
            if (!fetch(nextPage) || raw.size == before) break
        }
        return _state.value.items
    }

    private fun load() {
        nextPage = 1
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { fetch(1) }
    }

    private suspend fun fetch(page: Int): Boolean {
        if (inFlight) return false
        inFlight = true
        val result = repository.list(page)
        inFlight = false
        return result.fold(
            onSuccess = { (list, hasMore) ->
                nextPage = page + 1
                raw = if (page == 1) list else raw + list
                _state.update {
                    it.copy(
                        items = raw.orderedBy(sort),
                        isLoading = false,
                        isLoadingMore = false,
                        isEnd = !hasMore,
                        error = null,
                    )
                }
                true
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = if (page == 1) error.message ?: "稍后再看加载失败" else it.error,
                    )
                }
                false
            },
        )
    }
}

/** 从上次的进度接着看；已经播到结尾的就从头来，免得一开播就结束。 */
private fun LibraryVideo.resumeSec(): Long {
    val progress = progressSec.toLong()
    val finished = durationSec > 0 && progress >= durationSec - 5
    return if (finished) 0L else progress
}

@Composable
fun WatchLaterScreen(
    container: AppContainer,
    nav: NavState,
) {
    val loggedIn by container.session.account.collectAsStateWithLifecycle()
    val viewModel: WatchLaterViewModel = viewModel {
        WatchLaterViewModel(container.watchLater)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var sortMenuVisible by remember { mutableStateOf(false) }
    var playAllBusy by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    // 按当前列表顺序连播：先把剩下的页拉齐，第一条从上次的进度接着看，后面几条从头开始。
    val playAll: () -> Unit = {
        if (!playAllBusy && state.items.isNotEmpty()) {
            playAllBusy = true
            scope.launch {
                val queue = viewModel.loadAllForPlayback().mapIndexed { index, video ->
                    QueuedVideo(video, if (index == 0) video.resumeSec() else 0L)
                }
                playAllBusy = false
                nav.playQueue.start(queue)?.let { first ->
                    nav.push(Route.Video(first.video.bvid, first.startSec))
                }
            }
        }
    }

    SecondaryPage(
        title = "稍后再看",
        onBack = nav::pop,
        listState = listState,
        actions = {
            Box {
                HighlightedIconButton(
                    onClick = { sortMenuVisible = true },
                    enabled = state.items.isNotEmpty(),
                    modifier = Modifier
                        .couiTopAppBarActionItem()
                        .testTag("watch_later_sort"),
                ) {
                    Icon(COUIIcons.Sort, "排序", Modifier.size(24.dp))
                }
                CouiPopupMenu(
                    show = sortMenuVisible,
                    onDismiss = { sortMenuVisible = false },
                    items = WatchLaterSort.entries.map { sort ->
                        CouiMenuItem(
                            label = sort.label,
                            onClick = { viewModel.setSort(sort) },
                            selected = sort == state.sort,
                        )
                    },
                )
            }
            HighlightedIconButton(
                onClick = playAll,
                enabled = state.items.isNotEmpty() && !playAllBusy,
                modifier = Modifier
                    .couiTopAppBarActionItem()
                    .testTag("watch_later_play_all"),
            ) {
                Icon(COUIIcons.Play, "播放全部", Modifier.size(24.dp))
            }
        },
    ) {
        if (loggedIn == null) {
            item(key = "login") {
                EmptyState(
                    text = "登录后查看",
                    action = { TextButton(text = "去登录", onClick = nav::popToMe) },
                )
            }
            return@SecondaryPage
        }
        if (state.items.isEmpty()) {
            item(key = "state") {
                when {
                    state.isLoading -> LoadingState()
                    state.error != null -> ErrorState(state.error.orEmpty(), onRetry = viewModel::retry)
                    else -> EmptyState("稍后再看是空的")
                }
            }
        }
        items(
            count = state.items.size,
            key = { state.items[it].bvid },
            contentType = { "watch-later" },
        ) { index ->
            val video = state.items[index]
            LibraryVideoRow(
                video = video,
                onClick = {
                    nav.push(Route.Video(video.bvid, video.progressSec.toLong()))
                },
            )
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") { LoadingState() }
        }
    }
}
