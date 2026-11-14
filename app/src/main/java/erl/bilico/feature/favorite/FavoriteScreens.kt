@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.favorite

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LibraryVideoBody
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.MultiSelectSlideRow
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.TopBarTextButton
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.NotificationSwipeBox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.rememberNotificationSwipeState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun FavoriteFoldersScreen(
    container: AppContainer,
    nav: NavState,
) {
    val loggedIn by container.session.account.collectAsStateWithLifecycle()
    val viewModel: FavoriteFoldersViewModel = viewModel {
        FavoriteFoldersViewModel(container.favorites, container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    SecondaryPage(title = "收藏夹", onBack = nav::pop) {
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
                    state.error != null -> ErrorState(state.error.orEmpty(), onRetry = viewModel::refresh)
                    else -> EmptyState("还没有收藏夹")
                }
            }
        }
        items(
            count = state.items.size,
            key = { state.items[it].mediaId },
            contentType = { "fav-folder" },
        ) { index ->
            val folder = state.items[index]
            FolderRow(
                folder = folder,
                onClick = {
                    nav.push(Route.FavoriteFolder(folder.mediaId, folder.title))
                },
            )
        }
    }
}

@Composable
private fun FolderRow(folder: FavoriteFolder, onClick: () -> Unit) {
    // 收藏夹自己的封面常常是空的，优先用夹子里前几个视频的封面拼出来。
    val covers = remember(folder.coverUrls, folder.cover) {
        folder.coverUrls.ifEmpty { listOf(folder.cover) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(10.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FolderCover(urls = covers, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = folder.title.ifBlank { "未命名收藏夹" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = folder.countText,
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
            Icon(
                imageVector = COUIIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

/**
 * 收藏夹封面：只有一个视频就直接铺满，2~4 个拼成四宫格、能填几格填几格。
 * 每一格都是中心裁出来的正方形（ContentScale.Crop），不留黑边。
 */
@Composable
private fun FolderCover(urls: List<String>, size: Dp) {
    val density = LocalDensity.current
    val sizePx = remember(density, size) { with(density) { size.roundToPx() } }
    val cellPx = sizePx / 2
    val covers = remember(urls) { urls.filter { it.isNotBlank() }.take(4) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        when (covers.size) {
            0 -> CoverImage(url = null, modifier = Modifier.fillMaxSize())
            1 -> CoverImage(
                url = Formatters.sizedImageUrl(covers[0], sizePx, sizePx),
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    CoverCell(covers.getOrNull(0), cellPx)
                    CoverCell(covers.getOrNull(1), cellPx)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    CoverCell(covers.getOrNull(2), cellPx)
                    CoverCell(covers.getOrNull(3), cellPx)
                }
            }
        }
    }
}

@Composable
private fun RowScope.CoverCell(url: String?, cellPx: Int) {
    CoverImage(
        url = url?.let { Formatters.sizedImageUrl(it, cellPx, cellPx) },
        modifier = Modifier.weight(1f).fillMaxHeight(),
    )
}

@Composable
fun FavoriteFolderScreen(
    container: AppContainer,
    nav: NavState,
    mediaId: Long,
    title: String,
) {
    val context = LocalContext.current
    val viewModel: FavoriteFolderContentViewModel = viewModel(key = "fav-$mediaId") {
        FavoriteFolderContentViewModel(container.favorites, mediaId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var openKey by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedBvids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedBvids = emptySet()
    }

    fun toggleSelect(bvid: String) {
        selectedBvids = if (bvid in selectedBvids) selectedBvids - bvid else selectedBvids + bvid
    }

    BackHandler(enabled = selectionMode) { exitSelectionMode() }

    LaunchedEffect(state.items) {
        if (state.items.isEmpty()) {
            if (selectionMode) exitSelectionMode()
        } else if (selectedBvids.isNotEmpty()) {
            val valid = state.items.mapTo(HashSet()) { it.bvid }
            val filtered = selectedBvids.intersect(valid)
            if (filtered != selectedBvids) selectedBvids = filtered
        }
    }

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

    val barTitle = when {
        selectionMode && selectedBvids.isNotEmpty() -> "已选 ${selectedBvids.size} 项"
        selectionMode -> "选择视频"
        else -> title
    }

    SecondaryPage(
        title = barTitle,
        onBack = { if (selectionMode) exitSelectionMode() else nav.pop() },
        listState = listState,
        platformBarMaterial = false,
        actions = {
            if (selectionMode) {
                AnimatedVisibility(
                    visible = selectedBvids.isNotEmpty(),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    HighlightedIconButton(
                        onClick = { confirmBatchDelete = true },
                        modifier = Modifier
                            .couiTopAppBarActionItem()
                            .testTag("fav_batch_delete"),
                    ) {
                        Icon(
                            imageVector = COUIIcons.Delete,
                            contentDescription = "删除收藏的视频",
                            modifier = Modifier.size(24.dp),
                            tint = COUITheme.colorScheme.error,
                        )
                    }
                }
                TopBarTextButton(
                    text = "取消",
                    onClick = ::exitSelectionMode,
                    modifier = Modifier.testTag("fav_cancel_select"),
                )
            } else if (state.items.isNotEmpty()) {
                HighlightedIconButton(
                    onClick = {
                        openKey = null
                        selectionMode = true
                    },
                    modifier = Modifier
                        .couiTopAppBarActionItem()
                        .testTag("fav_multi_select"),
                ) {
                    Icon(
                        imageVector = COUIIcons.SelectAll,
                        contentDescription = "多选",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    ) {
        if (state.items.isEmpty()) {
            item(key = "state") {
                when {
                    state.isLoading -> LoadingState()
                    state.error != null -> ErrorState(state.error.orEmpty(), onRetry = viewModel::retry)
                    else -> EmptyState("这个收藏夹是空的")
                }
            }
        }
        items(
            count = state.items.size,
            key = { state.items[it].bvid },
            contentType = { "fav-video" },
        ) { index ->
            val video = state.items[index]
            val selected = video.bvid in selectedBvids
            val swipe = rememberNotificationSwipeState()
            LaunchedEffect(swipe.isRevealed) {
                if (swipe.isRevealed) openKey = video.bvid
            }
            LaunchedEffect(openKey, selectionMode) {
                if ((selectionMode || openKey != video.bvid) && swipe.isRevealed) swipe.close()
            }
            NotificationSwipeBox(
                onSettings = null,
                onDismiss = {
                    viewModel.remove(video) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                settingsIcon = null,
                dismissIcon = null,
                state = swipe,
                enabled = !selectionMode,
                minHeight = 0.dp,
                containerColor = CardDefaults.defaultColors().color,
                dismissOnFullSwipe = true,
                dismissDescription = "删除收藏的视频",
                modifier = Modifier.animateItem(),
            ) {
                MultiSelectSlideRow(
                    selectionMode = selectionMode,
                    selected = selected,
                    onToggleSelect = { toggleSelect(video.bvid) },
                    checkboxTag = "fav_check_${video.bvid}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                when {
                                    selectionMode -> toggleSelect(video.bvid)
                                    swipe.isRevealed -> scope.launch { swipe.close() }
                                    else -> nav.push(Route.Video(video.bvid))
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    openKey = null
                                    selectionMode = true
                                    selectedBvids = setOf(video.bvid)
                                }
                            },
                        )
                        .padding(8.dp),
                ) {
                    LibraryVideoBody(
                        video = video,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") { LoadingState() }
        }
    }

    if (confirmBatchDelete) {
        val targets = selectedBvids
        ConfirmDialog(
            title = "删除收藏的视频",
            message = "确定删除选中的 ${targets.size} 个收藏视频吗？",
            confirmText = "删除",
            onDismiss = { confirmBatchDelete = false },
            onConfirm = {
                confirmBatchDelete = false
                exitSelectionMode()
                viewModel.removeBatch(targets) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
