package erl.bilico.feature.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import erl.bilico.platform.LocalScreenConfig
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.BangumiCard
import erl.bilico.data.repository.PgcRepository
import erl.bilico.di.AppContainer
import erl.bilico.feature.feed.FeedSegmentRowHeight
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.components.COUISmoothCapsuleShape
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.FullPageRefreshHoldMillis
import erl.bilico.ui.components.FullPageRefreshTexts
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.FullPagePullToRefresh
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.fullPagePullToRefreshColors
import io.github.suqi8.coui.kmp.basic.rememberFullPagePullToRefreshState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CinemaSection(val seasonType: Int, val label: String)

private val CINEMA_SECTIONS = listOf(
    CinemaSection(2, "电影"),
    CinemaSection(5, "电视剧"),
    CinemaSection(3, "纪录片"),
    CinemaSection(7, "综艺"),
)

data class CinemaRankUiState(
    val section: Int = 0,
    val items: List<BangumiCard> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class CinemaRankViewModel(
    private val repository: PgcRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CinemaRankUiState())
    val state: StateFlow<CinemaRankUiState> = _state.asStateFlow()

    private val cache = mutableMapOf<Int, List<BangumiCard>>()
    private var job: Job? = null

    init {
        load(sectionIndex = 0, refreshing = false)
    }

    fun select(index: Int) {
        if (index !in CINEMA_SECTIONS.indices || index == _state.value.section) return
        val cached = cache[CINEMA_SECTIONS[index].seasonType]
        if (cached != null) {
            job?.cancel()
            _state.update {
                it.copy(section = index, items = cached, isLoading = false, isRefreshing = false, error = null)
            }
            return
        }
        load(sectionIndex = index, refreshing = false)
    }

    fun refresh() = load(sectionIndex = _state.value.section, refreshing = true)

    private fun load(sectionIndex: Int, refreshing: Boolean) {
        job?.cancel()
        val entry = CINEMA_SECTIONS[sectionIndex]
        _state.update {
            it.copy(
                section = sectionIndex,
                items = if (refreshing) it.items else emptyList(),
                isLoading = !refreshing,
                isRefreshing = refreshing,
                error = null,
            )
        }
        job = viewModelScope.launch {
            repository.rank(entry.seasonType)
                .onSuccess { items ->
                    cache[entry.seasonType] = items
                    _state.update {
                        it.copy(items = items, isLoading = false, isRefreshing = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "影视排行加载失败",
                        )
                    }
                }
        }
    }
}

/** 影视分段：支持电影、电视剧、纪录片、综艺排行切换，点封面进详情。 */
@Composable
fun CinemaRankSection(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    gridState: LazyGridState,
    active: Boolean,
    onCollapse: (Float) -> Unit,
    refreshTick: Int,
) {
    BindScrollToTop(gridState, enabled = active)
    val viewModel: CinemaRankViewModel = viewModel { CinemaRankViewModel(container.pgc) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pullState = rememberFullPagePullToRefreshState()
    val density = LocalDensity.current
    val screenWidthDp = LocalScreenConfig.current.screenWidthDp
    val coverWidthPx = remember(screenWidthDp, density) {
        with(density) { (screenWidthDp.dp / 2 - 40.dp).roundToPx() }.coerceAtMost(600)
    }
    val coverHeightPx = coverWidthPx * 4 / 3
    val titleHeightPx = with(density) { TitleRowHeight.toPx() }

    var lastSection by remember { mutableIntStateOf(state.section) }
    LaunchedEffect(state.section) {
        if (state.section != lastSection) {
            lastSection = state.section
            gridState.scrollToItem(0)
        }
    }

    LaunchedEffect(gridState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val first = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first == null || first.index > 0) titleHeightPx
            else (-first.offset.y).toFloat().coerceIn(0f, titleHeightPx)
        }
            .distinctUntilChanged()
            .collect(onCollapse)
    }

    val refreshBaseline = remember { mutableIntStateOf(refreshTick) }
    LaunchedEffect(refreshTick) {
        if (refreshTick == refreshBaseline.intValue || !active) return@LaunchedEffect
        pullState.snapToRefreshing { viewModel.refresh() }
        if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
            if (gridState.firstVisibleItemIndex > 6) gridState.scrollToItem(6)
            gridState.animateScrollToItem(0)
        }
    }

    FullPagePullToRefresh(
        isRefreshing = state.isRefreshing,
        onRefresh = { scope.launch { viewModel.refresh() } },
        modifier = Modifier.fillMaxSize(),
        state = pullState,
        contentPadding = PaddingValues(top = topInset + FeedSegmentRowHeight),
        colors = fullPagePullToRefreshColors(backgroundColor = pageContainerColor()),
        refreshTexts = FullPageRefreshTexts,
        completeHoldDurationMillis = FullPageRefreshHoldMillis,
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = topInset + FeedSegmentRowHeight,
                end = 12.dp,
                bottom = 66.dp + navBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "sections", span = { GridItemSpan(2) }) {
                CinemaTabs(selected = state.section, onSelect = viewModel::select)
            }

            if (state.items.isEmpty()) {
                item(key = "state", span = { GridItemSpan(2) }) {
                    Box(Modifier.fillMaxWidth()) {
                        when {
                            state.isLoading -> LoadingState()
                            state.error != null -> ErrorState(state.error.orEmpty()) { viewModel.refresh() }
                            else -> EmptyState("暂时没有影视排行")
                        }
                    }
                }
            }

            items(
                items = state.items,
                key = { it.seasonId },
                contentType = { "cinema-card" },
            ) { card ->
                BangumiRankCard(
                    card = card,
                    coverWidthPx = coverWidthPx,
                    coverHeightPx = coverHeightPx,
                    onClick = { nav.push(Route.Pgc(card.seasonId, 0L)) },
                )
            }
        }
    }
}

@Composable
private fun CinemaTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CINEMA_SECTIONS.forEachIndexed { index, section ->
            val active = index == selected
            Text(
                text = section.label,
                modifier = Modifier
                    .clip(COUISmoothCapsuleShape)
                    .background(
                        if (active) {
                            COUITheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            COUITheme.colorScheme.surfaceContainer
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                fontSize = 13.sp,
                color = if (active) {
                    COUITheme.colorScheme.primary
                } else {
                    COUITheme.colorScheme.onSurfaceSecondary
                },
            )
        }
    }
}
