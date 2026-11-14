@file:OptIn(io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi::class)

package erl.bilico.feature.following

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.FollowingUser
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.MultiSelectSlideRow
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.TopBarTextButton
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.NotificationSwipeBox
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.rememberNotificationSwipeState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.SelectAll
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun FollowingScreen(
    container: AppContainer,
    nav: NavState,
) {
    val context = LocalContext.current
    val account by container.session.account.collectAsStateWithLifecycle()
    val viewModel: FollowingViewModel = viewModel {
        FollowingViewModel(container.relations, container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var openKey by remember { mutableStateOf<Long?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedMids by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmBatchUnfollow by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedMids = emptySet()
    }

    fun toggleSelect(mid: Long) {
        selectedMids = if (mid in selectedMids) selectedMids - mid else selectedMids + mid
    }

    BackHandler(enabled = selectionMode) { exitSelectionMode() }

    LaunchedEffect(state.items) {
        if (state.items.isEmpty()) {
            if (selectionMode) exitSelectionMode()
        } else if (selectedMids.isNotEmpty()) {
            val valid = state.items.mapTo(HashSet()) { it.mid }
            val filtered = selectedMids.intersect(valid)
            if (filtered != selectedMids) selectedMids = filtered
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    val barTitle = when {
        selectionMode && selectedMids.isNotEmpty() -> "已选 ${selectedMids.size} 人"
        selectionMode -> "选择关注"
        else -> "关注"
    }

    SecondaryPage(
        title = barTitle,
        onBack = { if (selectionMode) exitSelectionMode() else nav.pop() },
        listState = listState,
        platformBarMaterial = false,
        actions = {
            if (selectionMode) {
                AnimatedVisibility(
                    visible = selectedMids.isNotEmpty(),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    HighlightedIconButton(
                        onClick = { confirmBatchUnfollow = true },
                        modifier = Modifier
                            .couiTopAppBarActionItem()
                            .testTag("following_batch_delete"),
                    ) {
                        Icon(
                            imageVector = COUIIcons.Delete,
                            contentDescription = "取消关注",
                            modifier = Modifier.size(24.dp),
                            tint = COUITheme.colorScheme.error,
                        )
                    }
                }
                TopBarTextButton(
                    text = "取消",
                    onClick = ::exitSelectionMode,
                    modifier = Modifier.testTag("following_cancel_select"),
                )
            } else if (account != null && state.items.isNotEmpty()) {
                HighlightedIconButton(
                    onClick = {
                        openKey = null
                        selectionMode = true
                    },
                    modifier = Modifier
                        .couiTopAppBarActionItem()
                        .testTag("following_multi_select"),
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
        if (account == null) {
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
                    else -> EmptyState("还没有关注任何人")
                }
            }
        }
        items(
            count = state.items.size,
            key = { state.items[it].mid },
            contentType = { "following-user" },
        ) { index ->
            val user = state.items[index]
            val selected = user.mid in selectedMids
            val swipe = rememberNotificationSwipeState()
            LaunchedEffect(swipe.isRevealed) {
                if (swipe.isRevealed) openKey = user.mid
            }
            LaunchedEffect(openKey, selectionMode) {
                if ((selectionMode || openKey != user.mid) && swipe.isRevealed) swipe.close()
            }
            NotificationSwipeBox(
                onSettings = null,
                onDismiss = {
                    viewModel.unfollow(user) { msg ->
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
                dismissDescription = "取消关注",
                modifier = Modifier.animateItem(),
            ) {
                MultiSelectSlideRow(
                    selectionMode = selectionMode,
                    selected = selected,
                    onToggleSelect = { toggleSelect(user.mid) },
                    checkboxTag = "following_check_${user.mid}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                when {
                                    selectionMode -> toggleSelect(user.mid)
                                    swipe.isRevealed -> scope.launch { swipe.close() }
                                    else -> nav.push(Route.Space(user.mid))
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    openKey = null
                                    selectionMode = true
                                    selectedMids = setOf(user.mid)
                                }
                            },
                        )
                        .padding(12.dp),
                ) {
                    FollowingBody(
                        user = user,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (state.isLoadingMore) {
            item(key = "loading-more") { LoadingState() }
        }
    }

    if (confirmBatchUnfollow) {
        val targets = selectedMids
        ConfirmDialog(
            title = "取消关注",
            message = "确定取消关注选中的 ${targets.size} 位 UP 主吗？",
            confirmText = "取消关注",
            onDismiss = { confirmBatchUnfollow = false },
            onConfirm = {
                confirmBatchUnfollow = false
                exitSelectionMode()
                viewModel.unfollowBatch(targets) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun FollowingBody(
    user: FollowingUser,
    modifier: Modifier = Modifier,
) {
    val avatarUrl = remember(user.face) { Formatters.sizedImageUrl(user.face, 132, 132) }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(url = avatarUrl, size = 44.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = user.name.ifBlank { "未知用户" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.sign.isNotBlank()) {
                Text(
                    text = user.sign,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
