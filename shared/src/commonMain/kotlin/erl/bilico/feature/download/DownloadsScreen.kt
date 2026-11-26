@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.download

import erl.bilico.platform.PlatformBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.MultiSelectSlideRow
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import erl.bilico.ui.components.TopBarTextButton
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.NotificationSwipeBox
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.rememberNotificationSwipeState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.FileDownloads
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

private val SwipeCardCornerRadius = 16.dp
private const val SwipeCardCornerWeight = 1.1f

@Composable
fun DownloadsScreen(
    container: AppContainer,
    nav: NavState,
) {
    val viewModel: DownloadsViewModel = viewModel {
        DownloadsViewModel(container.downloads)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var openKey by remember { mutableStateOf<String?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    PlatformBackHandler(enabled = state.selectionMode) { viewModel.exitSelection() }

    val barTitle = when {
        state.selectionMode && state.selectedIds.isNotEmpty() -> "已选 ${state.selectedIds.size} 项"
        state.selectionMode -> "选择缓存"
        else -> "离线缓存"
    }

    SecondaryPage(
        title = barTitle,
        onBack = { if (state.selectionMode) viewModel.exitSelection() else nav.pop() },
        listState = listState,
        platformBarMaterial = false,
        actions = {
            if (state.selectionMode) {
                TopBarTextButton(
                    text = if (state.selectedIds.containsAll(state.allIds) && state.allIds.isNotEmpty()) {
                        "全不选"
                    } else {
                        "全选"
                    },
                    onClick = viewModel::toggleSelectAll,
                )
                AnimatedVisibility(
                    visible = state.selectedIds.isNotEmpty(),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    HighlightedIconButton(
                        onClick = { confirmBatchDelete = true },
                        modifier = Modifier
                            .couiTopAppBarActionItem()
                            .testTag("downloads_batch_delete"),
                    ) {
                        Icon(
                            imageVector = COUIIcons.Delete,
                            contentDescription = "删除选中缓存",
                            modifier = Modifier.size(24.dp),
                            tint = COUITheme.colorScheme.error,
                        )
                    }
                }
                TopBarTextButton(
                    text = "取消",
                    onClick = viewModel::exitSelection,
                )
            } else if (state.hasAnyTask) {
                if (state.hasRunning) {
                    HighlightedIconButton(
                        onClick = viewModel::pauseAll,
                        modifier = Modifier.couiTopAppBarActionItem(),
                    ) {
                        Icon(COUIIcons.Pause, "全部暂停", Modifier.size(24.dp))
                    }
                } else if (state.hasPausedOrFailed) {
                    HighlightedIconButton(
                        onClick = viewModel::resumeAll,
                        modifier = Modifier.couiTopAppBarActionItem(),
                    ) {
                        Icon(COUIIcons.Play, "全部继续", Modifier.size(24.dp))
                    }
                }
                HighlightedIconButton(
                    onClick = {
                        openKey = null
                        viewModel.enterSelection()
                    },
                    modifier = Modifier
                        .couiTopAppBarActionItem()
                        .testTag("downloads_multi_select"),
                ) {
                    Icon(COUIIcons.SelectAll, "多选", Modifier.size(24.dp))
                }
            }
        },
    ) {
        if (!state.hasAnyTask) {
            item(key = "empty") {
                EmptyState(
                    text = "还没有离线缓存的视频\n在视频或番剧详情页点击「缓存」即可离线观看",
                    icon = COUIIcons.FileDownloads,
                )
            }
            return@SecondaryPage
        }

        item(key = "summary") {
            StorageSummaryCard(
                completedCount = state.completedTasks.size,
                activeCount = state.activeTasks.size,
                totalBytes = state.totalCachedBytes,
            )
        }

        if (state.activeTasks.isNotEmpty()) {
            item(key = "active-title") {
                SectionTitle(
                    text = "正在下载（${state.activeTasks.size}）",
                    topPadding = 8.dp,
                )
            }
            items(
                items = state.activeTasks,
                key = { "active-${it.id}" },
                contentType = { "active-download" },
            ) { task ->
                MultiSelectSlideRow(
                    selectionMode = state.selectionMode,
                    selected = task.id in state.selectedIds,
                    onToggleSelect = { viewModel.toggleSelect(task.id) },
                    checkboxTag = "download_check_${task.id}",
                ) {
                    ActiveDownloadCard(
                        task = task,
                        coverUrl = container.downloads.localCoverUri(task) ?: task.coverUrl,
                        onClick = {
                            if (state.selectionMode) {
                                viewModel.toggleSelect(task.id)
                            } else if (task.isActive) {
                                viewModel.pause(task.id)
                            } else {
                                viewModel.resume(task.id)
                            }
                        },
                        onLongClick = {
                            if (!state.selectionMode) {
                                openKey = null
                                viewModel.enterSelection(task.id)
                            }
                        },
                        onTogglePauseResume = {
                            if (task.isActive) viewModel.pause(task.id) else viewModel.resume(task.id)
                        },
                        onDelete = { viewModel.deleteTask(task.id) },
                    )
                }
            }
        }

        if (state.completedTasks.isNotEmpty()) {
            item(key = "completed-title") {
                SectionTitle(
                    text = "已缓存（${state.completedTasks.size}）",
                    topPadding = if (state.activeTasks.isNotEmpty()) 12.dp else 8.dp,
                )
            }
            items(
                items = state.completedTasks,
                key = { "done-${it.id}" },
                contentType = { "completed-download" },
            ) { task ->
                val swipe = rememberNotificationSwipeState()
                LaunchedEffect(swipe.isRevealed) {
                    if (swipe.isRevealed) openKey = task.id
                }
                LaunchedEffect(openKey) {
                    if (openKey != task.id && swipe.isRevealed) swipe.close()
                }
                LaunchedEffect(state.selectionMode) {
                    if (state.selectionMode && swipe.isRevealed) swipe.close()
                }
                MultiSelectSlideRow(
                    selectionMode = state.selectionMode,
                    selected = task.id in state.selectedIds,
                    onToggleSelect = { viewModel.toggleSelect(task.id) },
                    checkboxTag = "download_check_${task.id}",
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                ) {
                    NotificationSwipeBox(
                        onSettings = null,
                        onDismiss = { viewModel.deleteTask(task.id) },
                        settingsIcon = null,
                        dismissIcon = null,
                        state = swipe,
                        minHeight = 0.dp,
                        containerColor = CardDefaults.defaultColors().color,
                        dismissOnFullSwipe = true,
                        dismissDescription = "删除",
                    ) {
                        CompletedDownloadRow(
                            task = task,
                            coverUrl = container.downloads.localCoverUri(task) ?: task.coverUrl,
                            coverCornerRadius = SwipeCardCornerRadius,
                            coverCornerWeight = SwipeCardCornerWeight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            state.selectionMode -> viewModel.toggleSelect(task.id)
                                            swipe.isRevealed -> scope.launch { swipe.close() }
                                            task.isPgc -> nav.push(
                                                Route.Pgc(
                                                    seasonId = task.seasonId,
                                                    epId = task.epId,
                                                    offline = true,
                                                ),
                                            )
                                            else -> nav.push(
                                                Route.Video(
                                                    bvid = task.bvid,
                                                    cid = task.cid,
                                                    offline = true,
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!state.selectionMode) {
                                            openKey = null
                                            viewModel.enterSelection(task.id)
                                        }
                                    },
                                )
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmBatchDelete) {
        ConfirmDialog(
            title = "删除离线缓存",
            message = "确定要删除选中的 ${state.selectedIds.size} 个缓存视频吗？本地文件将被永久清理。",
            confirmText = "删除",
            onDismiss = { confirmBatchDelete = false },
            onConfirm = {
                confirmBatchDelete = false
                viewModel.deleteSelected()
            },
        )
    }
}
