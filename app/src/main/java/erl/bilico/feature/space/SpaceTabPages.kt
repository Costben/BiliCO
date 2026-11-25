@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.space

import android.widget.Toast
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.SpaceProfile
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicCard
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.asRowScrollState
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LibraryVideoRow
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun SpaceTabPage(
    tab: SpaceTab,
    active: Boolean,
    mid: Long,
    currentMid: Long,
    state: SpaceUiState,
    profile: SpaceProfile?,
    opusColumns: Int,
    gridSpacing: Dp,
    opusColWidthPx: Int,
    contentTop: Dp,
    videosListState: LazyListState,
    collectionsListState: LazyListState,
    dynamicsListState: LazyListState,
    opusGridState: LazyStaggeredGridState,
    likesListState: LazyListState,
    onHeaderDrag: (Float) -> Unit,
    viewModel: SpaceViewModel,
    nav: NavState,
    container: AppContainer,
) {
    when (tab) {
        SpaceTab.VIDEOS -> SpaceListPage(
            listState = videosListState,
            active = active,
            contentTop = contentTop,
            onHeaderDrag = onHeaderDrag,
            onLoadMore = { if (active) viewModel.loadMore() },
        ) {
            val visibleVideos = state.videos
            if (visibleVideos.isEmpty()) {
                item(key = "videos-state") {
                    when {
                        state.isVideosLoading -> LoadingState()
                        state.videosError != null -> ErrorState(state.videosError.orEmpty(), onRetry = viewModel::retry)
                        else -> EmptyState(if (state.searchKeyword.isNotBlank()) "未找到相关投稿" else "还没有投稿")
                    }
                }
            }
            items(
                count = visibleVideos.size,
                key = { "v-${visibleVideos[it].bvid}" },
                contentType = { "space-video" },
            ) { index ->
                val video = visibleVideos[index]
                LibraryVideoRow(
                    video = video,
                    onClick = { nav.push(Route.Video(video.bvid)) },
                    longPressItems = rememberSpaceVideoMenuItems(video, profile, container),
                )
            }
            if (state.isVideosLoadingMore) {
                item(key = "videos-loading-more") { LoadingState() }
            }
        }

        SpaceTab.SEASONS -> SpaceListPage(
            listState = collectionsListState,
            active = active,
            contentTop = contentTop,
            onHeaderDrag = onHeaderDrag,
            onLoadMore = { if (active) viewModel.loadMore() },
        ) {
            val list = state.filteredCollections
            if (list.isEmpty()) {
                item(key = "seasons-state") {
                    when {
                        state.isCollectionsLoading -> LoadingState()
                        state.collectionsError != null -> ErrorState(state.collectionsError.orEmpty(), onRetry = viewModel::retry)
                        else -> EmptyState(if (state.searchKeyword.isNotBlank()) "未找到相关合集" else "还没有创建合集")
                    }
                }
            }
            items(
                count = list.size,
                key = { "c-${if (list[it].isSeason) "s" else "r"}-${list[it].id}" },
                contentType = { "space-collection" },
            ) { index ->
                val col = list[index]
                SpaceCollectionRow(
                    collection = col,
                    onClick = { nav.push(Route.SpaceCollection(mid, col.id, col.isSeason, col.name, col.total)) },
                )
            }
            if (state.isCollectionsLoadingMore) {
                item(key = "seasons-loading-more") { LoadingState() }
            }
        }

        SpaceTab.DYNAMICS -> {
            val context = LocalContext.current
            val rowScrollState = remember(dynamicsListState) { dynamicsListState.asRowScrollState() }
            SpaceListPage(
                listState = dynamicsListState,
                active = active,
                contentTop = contentTop,
                onHeaderDrag = onHeaderDrag,
                onLoadMore = { if (active) viewModel.loadMore() },
            ) {
                val dynamicsList = state.filteredDynamics
                if (dynamicsList.isEmpty()) {
                    item(key = "dynamics-state") {
                        when {
                            state.isDynamicsLoading -> LoadingState()
                            state.dynamicsError != null -> ErrorState(state.dynamicsError.orEmpty(), onRetry = viewModel::retry)
                            else -> EmptyState(if (state.searchKeyword.isNotBlank()) "未找到相关动态" else "还没有动态")
                        }
                    }
                }
                items(
                    count = dynamicsList.size,
                    key = { "d-${dynamicsList[it].dynamicId}" },
                    contentType = { "space-dynamic" },
                ) { index ->
                    val dyn = dynamicsList[index]
                    DynamicCard(
                        item = dyn,
                        onOpenUser = { userMid -> if (userMid != mid) nav.push(Route.Space(userMid)) },
                        onOpenVideo = { bvid, origin -> nav.push(Route.Video(bvid), origin) },
                        onOpenImage = { _, _ -> },
                        onOpenDynamic = { target, origin ->
                            val articleId = target.article?.articleId ?: 0L
                            val dynId = target.dynamicId.ifBlank { target.article?.dynamicId.orEmpty() }
                            val headerTitle = target.title.ifBlank { target.authorName }
                            if (articleId > 0L) nav.push(Route.Article(articleId, headerTitle))
                            else if (dynId.isNotBlank()) nav.push(Route.Opus(dynId, headerTitle), origin)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onOpenLive = { roomId -> nav.push(Route.LiveRoom(roomId)) },
                        currentMid = currentMid,
                        onDelete = { target ->
                            viewModel.deleteDynamic(target.dynamicId) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLike = { target ->
                            viewModel.toggleLike(target) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenHotReply = { target, reply, origin ->
                            val video = target.video
                            val articleId = target.article?.articleId ?: 0L
                            val dynId = target.dynamicId.ifBlank { target.article?.dynamicId.orEmpty() }
                            val headerTitle = target.title.ifBlank { target.authorName }
                            when {
                                video != null -> nav.push(
                                    Route.Video(
                                        bvid = video.bvid,
                                        commentRootId = reply.rpid,
                                        commentMatchAuthor = reply.authorName,
                                        commentMatchText = reply.contentText,
                                    ),
                                    origin,
                                )

                                articleId > 0L -> nav.push(
                                    Route.Article(
                                        cvId = articleId,
                                        title = headerTitle,
                                        commentRootId = reply.rpid,
                                        commentMatchAuthor = reply.authorName,
                                        commentMatchText = reply.contentText,
                                    ),
                                )

                                dynId.isNotBlank() -> nav.push(
                                    Route.Opus(
                                        dynamicId = dynId,
                                        title = headerTitle,
                                        commentRootId = reply.rpid,
                                        commentMatchAuthor = reply.authorName,
                                        commentMatchText = reply.contentText,
                                    ),
                                    origin,
                                )
                            }
                        },
                        rowScrollState = rowScrollState,
                        rowKey = "d-${dyn.dynamicId}",
                    )
                }
                if (state.isDynamicsLoadingMore) {
                    item(key = "dynamics-loading-more") { LoadingState() }
                }
            }
        }

        SpaceTab.OPUS -> SpaceOpusPage(
            gridState = opusGridState,
            active = active,
            opusColumns = opusColumns,
            gridSpacing = gridSpacing,
            opusColWidthPx = opusColWidthPx,
            contentTop = contentTop,
            state = state,
            profile = profile,
            onHeaderDrag = onHeaderDrag,
            onLoadMore = { if (active) viewModel.loadMore() },
            onRetry = viewModel::retry,
            onOpenOpus = { opusId ->
                nav.push(Route.Opus(opusId, profile?.name?.ifBlank { "图文详情" } ?: "图文详情"))
            },
        )

        SpaceTab.LIKES -> SpaceListPage(
            listState = likesListState,
            active = active,
            contentTop = contentTop,
            onHeaderDrag = onHeaderDrag,
            onLoadMore = {},
        ) {
            val list = state.filteredLikedVideos
            if (list.isEmpty()) {
                item(key = "likes-state") {
                    when {
                        state.isLikesLoading -> LoadingState()
                        state.likesError != null -> ErrorState(state.likesError.orEmpty(), onRetry = viewModel::retry)
                        else -> EmptyState(if (state.searchKeyword.isNotBlank()) "未找到相关点赞视频" else "还没有最近点赞")
                    }
                }
            }
            items(
                count = list.size,
                key = { "l-${list[it].bvid}" },
                contentType = { "space-like-video" },
            ) { index ->
                val video = list[index]
                LibraryVideoRow(
                    video = video,
                    onClick = { nav.push(Route.Video(video.bvid)) },
                    longPressItems = rememberSpaceVideoMenuItems(video, profile, container),
                )
            }
        }
    }
}

@Composable
private fun SpaceListPage(
    listState: LazyListState,
    active: Boolean,
    contentTop: Dp,
    onHeaderDrag: (Float) -> Unit,
    onLoadMore: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    BindScrollToTop(listState, enabled = active)
    LaunchedEffect(listState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 3) onLoadMore()
            }
    }
    val canScroll = listState.canScrollForward || listState.canScrollBackward
    Box(
        Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !canScroll,
                state = rememberDraggableState(onHeaderDrag),
            ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = contentTop,
                end = 12.dp,
                bottom = 24.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SpaceOpusPage(
    gridState: LazyStaggeredGridState,
    active: Boolean,
    opusColumns: Int,
    gridSpacing: Dp,
    opusColWidthPx: Int,
    contentTop: Dp,
    state: SpaceUiState,
    profile: SpaceProfile?,
    onHeaderDrag: (Float) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenOpus: (String) -> Unit,
) {
    BindScrollToTop(gridState, enabled = active)
    LaunchedEffect(gridState, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (total > 0 && lastVisible >= total - 3) onLoadMore()
            }
    }
    val canScroll = gridState.canScrollForward || gridState.canScrollBackward
    Box(
        Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !canScroll,
                state = rememberDraggableState(onHeaderDrag),
            ),
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(opusColumns.coerceAtLeast(1)),
            state = gridState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = contentTop,
                end = 12.dp,
                bottom = 24.dp + navBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            verticalItemSpacing = 8.dp,
        ) {
            val list = state.filteredOpus
            if (list.isEmpty()) {
                item(key = "opus-state", span = StaggeredGridItemSpan.FullLine) {
                    when {
                        state.isOpusLoading -> LoadingState()
                        state.opusError != null -> ErrorState(state.opusError.orEmpty(), onRetry = onRetry)
                        else -> EmptyState(if (state.searchKeyword.isNotBlank()) "未找到相关图文" else "还没有图文")
                    }
                }
            }
            items(
                count = list.size,
                key = { "o-${list[it].opusId}" },
                contentType = { "space-opus" },
            ) { index ->
                val opus = list[index]
                SpaceOpusWaterfallCard(
                    opus = opus,
                    colWidthPx = opusColWidthPx,
                    onClick = { onOpenOpus(opus.opusId) },
                )
            }
            if (state.isOpusLoadingMore) {
                item(key = "opus-loading-more", span = StaggeredGridItemSpan.FullLine) { LoadingState() }
            }
        }
    }
}
