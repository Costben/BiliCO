@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.space

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.ui.AppBarSurface
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.Route
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.PageTabRow
import erl.bilico.ui.components.SearchClearButton
import erl.bilico.ui.components.UserMoreMenu
import erl.bilico.ui.glassBarContentTop
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Sort
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SpaceScreen(
    nav: NavState,
    container: AppContainer,
    mid: Long,
) {
    val viewModel: SpaceViewModel = viewModel(key = "space-$mid") {
        SpaceViewModel(container.space, container.dynamic, container.interactions, container.session, mid)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()
    val opusColumns by container.settings.feed.columns.collectAsStateWithLifecycle()
    val currentMid = account?.mid ?: container.session.mid()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var sortMenuVisible by remember { mutableStateOf(false) }

    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)
    val contentTop = glassBarContentTop(topBarHeight, 10.dp)
    val gridSpacing = spaceOpusGridSpacing(opusColumns)
    val opusColWidthPx = rememberOpusColWidthPx(opusColumns, gridSpacing)

    val visibleTabs = state.visibleTabs
    val selectedIndex = visibleTabs.indexOf(state.selectedTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { visibleTabs.size }

    val videosListState = rememberLazyListState()
    val collectionsListState = rememberLazyListState()
    val dynamicsListState = rememberLazyListState()
    val opusGridState = rememberLazyStaggeredGridState()
    val likesListState = rememberLazyListState()

    var profileHeightPx by remember { mutableIntStateOf(0) }
    var tabRowHeightPx by remember { mutableIntStateOf(0) }
    val contentTopPx = with(density) { contentTop.toPx() }
    val tabDockedTopPx = with(density) { (topBarHeight + 8.dp).toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val tabExpandedTopPx = contentTopPx + profileHeightPx + (if (profileHeightPx > 0) gapPx else 0f)
    val maxCollapsePx = (tabExpandedTopPx - tabDockedTopPx).coerceAtLeast(0f)
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(maxCollapsePx) {
        headerOffsetPx = headerOffsetPx.coerceIn(-maxCollapsePx, 0f)
    }

    val consumeHeaderScroll = remember(maxCollapsePx) {
        { deltaY: Float ->
            if (maxCollapsePx <= 0f) {
                0f
            } else {
                val prev = headerOffsetPx
                val next = (prev + deltaY).coerceIn(-maxCollapsePx, 0f)
                headerOffsetPx = next
                next - prev
            }
        }
    }

    val nestedScrollConnection = remember(consumeHeaderScroll, maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && headerOffsetPx > -maxCollapsePx) {
                    return Offset(0f, consumeHeaderScroll(available.y))
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && headerOffsetPx < 0f) {
                    return Offset(0f, consumeHeaderScroll(available.y))
                }
                return Offset.Zero
            }
        }
    }

    // 点标签翻页；滑完一页再把选中态写回去，避免动画中途回写打断翻页。
    LaunchedEffect(selectedIndex, visibleTabs.size) {
        val target = selectedIndex.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
        if (pagerState.settledPage != target) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState, visibleTabs) {
        snapshotFlow { pagerState.targetPage.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0)) }
            .distinctUntilChanged()
            .collect { index -> visibleTabs.getOrNull(index)?.let(viewModel::ensureTabLoaded) }
    }
    LaunchedEffect(pagerState, visibleTabs) {
        snapshotFlow { pagerState.settledPage.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0)) }
            .distinctUntilChanged()
            .collect { index -> visibleTabs.getOrNull(index)?.let(viewModel::selectTab) }
    }
    LaunchedEffect(state.sortOrder) {
        if (videosListState.firstVisibleItemIndex > 0) videosListState.scrollToItem(0)
    }

    val tabTopPx = tabExpandedTopPx + headerOffsetPx
    val listContentTop = with(density) { (tabTopPx + tabRowHeightPx + gapPx).toDp() }
    val collapseFraction = if (maxCollapsePx > 0f) (-headerOffsetPx / maxCollapsePx).coerceIn(0f, 1f) else 0f
    val headerAlpha = 1f - ((collapseFraction - 0.72f) / 0.28f).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, backdrop)
                .background(pageColor),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                beyondViewportPageCount = 1,
                key = { visibleTabs[it].name },
            ) { page ->
                val tab = visibleTabs.getOrNull(page) ?: return@HorizontalPager
                SpaceTabPage(
                    tab = tab,
                    active = pagerState.settledPage == page,
                    mid = mid,
                    currentMid = currentMid,
                    state = state,
                    profile = state.profile,
                    opusColumns = opusColumns,
                    gridSpacing = gridSpacing,
                    opusColWidthPx = opusColWidthPx,
                    contentTop = listContentTop,
                    videosListState = videosListState,
                    collectionsListState = collectionsListState,
                    dynamicsListState = dynamicsListState,
                    opusGridState = opusGridState,
                    likesListState = likesListState,
                    onHeaderDrag = { consumeHeaderScroll(it) },
                    viewModel = viewModel,
                    nav = nav,
                    container = container,
                )
            }

            if (headerAlpha > 0.01f) {
                val profile = state.profile
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
                        .graphicsLayer { alpha = headerAlpha }
                        .background(pageColor)
                        .padding(top = contentTop, start = 12.dp, end = 12.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = collapseFraction < 1f,
                            state = rememberDraggableState { consumeHeaderScroll(it) },
                        ),
                ) {
                    Box(Modifier.fillMaxWidth().onSizeChanged { profileHeightPx = it.height }) {
                        if (profile == null) {
                            if (state.error != null) ErrorState(state.error.orEmpty(), onRetry = viewModel::retry)
                            else LoadingState()
                        } else {
                            val isSelf = profile.mid > 0L && profile.mid == currentMid
                            ProfileCard(
                                profile = profile,
                                showFollow = !isSelf,
                                following = state.following,
                                followBusy = state.isFollowBusy,
                                onFollow = {
                                    viewModel.toggleFollow { _, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                },
                                onOpenLiveRoom = if (profile.liveRoomId > 0L) {
                                    { nav.push(Route.LiveRoom(profile.liveRoomId)) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, tabTopPx.roundToInt()) }
                    .onSizeChanged { tabRowHeightPx = it.height }
                    .padding(horizontal = 12.dp),
            ) {
                val labels = remember(visibleTabs) { visibleTabs.map { it.title } }
                PageTabRow(
                    tabs = labels,
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0)),
                    onTabSelected = { index ->
                        visibleTabs.getOrNull(index)?.let { tab ->
                            viewModel.selectTab(tab)
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                    backdrop = if (BlurEffectsEnabled) backdrop else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PinnedBarDensity {
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val pageBarHeight = statusBarTop + 44.dp
                Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    AppBarSurface(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = pageColor,
                        scrollFraction = { 1f },
                    ) {
                        Box(Modifier.fillMaxWidth().height(pageBarHeight))
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(top = statusBarTop).height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TopAppBarSearchBar(
                            query = state.searchKeyword,
                            onQueryChange = viewModel::updateKeyword,
                            expanded = state.isSearchActive,
                            onExpandedChange = { viewModel.toggleSearch(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = when (state.selectedTab) {
                                SpaceTab.VIDEOS -> "搜索他的投稿"
                                SpaceTab.SEASONS -> "搜索他的合集"
                                SpaceTab.DYNAMICS -> "搜索他的动态"
                                SpaceTab.OPUS -> "搜索他的图文"
                                SpaceTab.LIKES -> "搜索最近点赞"
                            },
                            cancelText = "取消",
                            onSearch = { viewModel.submitSearch(it) },
                            onCancel = { viewModel.toggleSearch(false) },
                            gradientColor = Color.Transparent,
                            titleContent = {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    BackIconButton(onClick = nav::pop)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = state.profile?.name?.ifBlank { "个人空间" } ?: "个人空间",
                                        fontSize = with(LocalDensity.current) { TopAppBarDefaults.ToolbarTitleTextSize.toSp() },
                                        fontWeight = FontWeight(550),
                                        color = COUITheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            },
                            trailingIcon = {
                                if (state.searchKeyword.isNotEmpty()) {
                                    SearchClearButton(onClick = { viewModel.updateKeyword(""); viewModel.submitSearch("") })
                                }
                            },
                            extraActions = {
                                Box {
                                    HighlightedIconButton(
                                        onClick = { sortMenuVisible = true },
                                        modifier = Modifier.couiTopAppBarActionItem().testTag("space_sort"),
                                    ) {
                                        Icon(COUIIcons.Sort, "排序方式", Modifier.size(24.dp))
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
                                val profile = state.profile
                                if (profile != null && profile.mid > 0L && profile.mid != currentMid) {
                                    UserMoreMenu(mid = profile.mid, name = profile.name, face = profile.face, highlighted = true)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
