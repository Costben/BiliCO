package erl.bilico.feature.comment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentSort
import erl.bilico.data.model.DynamicImage
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicImageViewer
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Send
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.flow.distinctUntilChanged

private sealed interface ComposeTarget {
    data object Main : ComposeTarget
    data class Reply(val root: Comment) : ComposeTarget
}

@Composable
fun CommentTab(
    container: AppContainer,
    oid: Long,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
    type: Int = 1,
    listState: LazyListState = rememberLazyListState(),
    topInset: Dp = 0.dp,
    topGap: Dp = 12.dp,
    bottomInset: Dp = navBottomPadding(),
    targetRootId: Long = 0L,
    targetReplyId: Long = 0L,
    replyThreadController: ReplyThreadController? = null,
) {
    BindScrollToTop(listState)
    val viewModel: CommentViewModel = viewModel(key = "comment-$type-$oid-$targetRootId-$targetReplyId") {
        CommentViewModel(container.comments, oid, type, targetRootId, targetReplyId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()
    val blocks by container.blocks.snapshot.collectAsStateWithLifecycle()
    val visibleComments = remember(state.items, blocks) {
        state.items.mapNotNull { blocks.filterCommentTree(it) }
    }
    val currentMid = account?.mid ?: container.session.mid()
    val localThreadController = remember { ReplyThreadController() }
    val threadController = replyThreadController ?: localThreadController
    var composeTarget by remember { mutableStateOf<ComposeTarget?>(null) }
    var imageViewer by remember { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }

    LaunchedEffect(viewModel, threadController) {
        threadController.onSubReplyAdded = viewModel::onSubReplyAdded
        threadController.onSubReplyDeleted = viewModel::onSubReplyDeleted
        threadController.onRootDeleted = viewModel::removeCommentLocally
    }

    LaunchedEffect(state.items, threadController) {
        threadController.itemsSnapshot = state.items
    }

    LaunchedEffect(state.focusRootId, state.items.size) {
        val focusId = state.focusRootId
        if (focusId > 0L && state.items.isNotEmpty()) {
            val idx = state.items.indexOfFirst { it.id == focusId }
            if (idx >= 0) {
                listState.animateScrollToItem(idx + 1)
            }
            viewModel.consumeFocusTarget()
        }
    }

    LaunchedEffect(state.pendingReplyDialog) {
        val pending = state.pendingReplyDialog ?: return@LaunchedEffect
        threadController.open(pending.first, pending.second)
        viewModel.consumeReplyDialog()
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

    androidx.compose.runtime.CompositionLocalProvider(LocalCommentTarget provides (oid to type)) {
    Box(modifier) {
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                state.isLoading && state.items.isEmpty() ->
                    LoadingState(Modifier.padding(top = topInset))

                state.items.isEmpty() -> if (state.error != null) {
                    ErrorState(
                        message = state.error.orEmpty(),
                        modifier = Modifier.padding(top = topInset),
                        onRetry = viewModel::refresh,
                    )
                } else {
                    EmptyState("还没有评论", Modifier.padding(top = topInset))
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().scrollEndHaptic(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = topGap + topInset,
                        end = 12.dp,
                        bottom = 88.dp + bottomInset,
                    ),
                ) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (state.sort == CommentSort.New) "最新评论" else "最热评论",
                                style = COUITheme.textStyles.body1,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            CommentSortTabRow(
                                sort = state.sort,
                                onSelectSort = viewModel::selectSort,
                            )
                        }
                    }
                    items(visibleComments, key = { it.id }) { comment ->
                        // 灰底页面 → 白卡片 → 浅灰楼中楼，三层配色照微博评论那套来。
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            onClick = { threadController.open(comment) },
                        ) {
                            BiliCommentItem(
                                comment = comment,
                                onLike = {
                                    if (!container.session.isLoggedIn()) {
                                        viewModel.showMessage("请先登录")
                                    } else {
                                        viewModel.toggleLike(comment)
                                    }
                                },
                                onDislike = {
                                    if (!container.session.isLoggedIn()) {
                                        viewModel.showMessage("请先登录")
                                    } else {
                                        viewModel.toggleDislike(comment)
                                    }
                                },
                                onReply = {
                                    if (!container.session.isLoggedIn()) {
                                        viewModel.showMessage("请先登录")
                                    } else {
                                        composeTarget = ComposeTarget.Reply(comment)
                                    }
                                },
                                onOpenReplies = {
                                    threadController.open(it)
                                },
                                onOpenUser = onOpenUser,
                                onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
                                currentMid = currentMid,
                                onDelete = { viewModel.deleteComment(comment) },
                                onClick = { threadController.open(comment) },
                            )
                        }
                    }
                    when {
                        state.isLoadingMore -> item(key = "more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    size = 18.dp,
                                )
                            }
                        }

                        state.isEnd -> item(key = "end") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "没有更多评论了",
                                    style = COUITheme.textStyles.footnote2,
                                    color = COUITheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }
                }
            }

            state.message?.let { toast ->
                Text(
                    text = toast,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp + bottomInset)
                        .clip(SquircleShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = Color.White,
                )
            }

            // 评论入口改成右下角悬浮球：按下缩一圈，松手带一点回弹。
            val ballInteraction = remember { MutableInteractionSource() }
            val ballPressed by ballInteraction.collectIsPressedAsState()
            val ballScale = remember { Animatable(1f) }
            LaunchedEffect(ballPressed) {
                ballScale.animateTo(
                    targetValue = if (ballPressed) 0.94f else 1f,
                    animationSpec = if (ballPressed) {
                        spring(dampingRatio = 0.55f, stiffness = 1400f)
                    } else {
                        spring(dampingRatio = 0.34f, stiffness = 420f)
                    },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomInset),
            ) {
                PinnedBarDensity {
                    Box(Modifier.fillMaxSize()) {
                        FloatingActionButton(
                            onClick = {
                                if (!container.session.isLoggedIn()) {
                                    viewModel.showMessage("请先登录")
                                } else {
                                    composeTarget = ComposeTarget.Main
                                }
                            },
                            interactionSource = ballInteraction,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = BottomFloatingAccessoryNavigationBarDefaults.PaddingHorizontal,
                                    bottom = (BottomFloatingNavigationBarDefaults.MinHeightWithoutPaddingVertical -
                                        BottomFloatingAccessoryNavigationBarDefaults.AccessorySize) / 2,
                                )
                                .size(BottomFloatingAccessoryNavigationBarDefaults.AccessorySize)
                                .graphicsLayer {
                                    scaleX = ballScale.value
                                    scaleY = ballScale.value
                                },
                        ) {
                            Icon(
                                imageVector = COUIIcons.Send,
                                contentDescription = "发送评论",
                                modifier = Modifier.size(24.dp),
                                tint = COUITheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }

    when (val target = composeTarget) {
        ComposeTarget.Main -> CommentComposerSheet(
            container = container,
            hint = "发一条友善的评论",
            onSubmit = { text, pictures, atMids ->
                composeTarget = null
                viewModel.postComment(text, pictures, atMids)
            },
            onDismiss = { composeTarget = null },
        )

        is ComposeTarget.Reply -> CommentComposerSheet(
            container = container,
            hint = "回复 @${target.root.userName}",
            onSubmit = { text, pictures, atMids ->
                composeTarget = null
                viewModel.postReply(target.root, text, pictures, atMids)
            },
            onDismiss = { composeTarget = null },
        )

        null -> Unit
    }

    if (replyThreadController == null) {
        ReplyDetailOverlay(
            controller = localThreadController,
            container = container,
            oid = oid,
            type = type,
            latestItems = state.items,
            topInset = topInset,
            bottomInset = bottomInset,
            onOpenUser = onOpenUser,
            modifier = Modifier.fillMaxSize(),
        )
    }

    imageViewer?.let { (images, index) ->
        DynamicImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { imageViewer = null },
        )
    }
    }
    }
}
