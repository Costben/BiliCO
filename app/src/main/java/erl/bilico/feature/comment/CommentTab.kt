package erl.bilico.feature.comment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentSort
import erl.bilico.data.model.DynamicImage
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicImageViewer
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Send
import io.github.suqi8.coui.kmp.icon.extended.Sort
import io.github.suqi8.coui.kmp.theme.COUITheme
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
) {
    val viewModel: CommentViewModel = viewModel(key = "comment-$type-$oid") {
        CommentViewModel(container.comments, oid, type)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialogRoot by remember { mutableStateOf<Comment?>(null) }
    var composeTarget by remember { mutableStateOf<ComposeTarget?>(null) }
    var imageViewer by remember { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }

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

    Column(modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> LoadingState()

                state.items.isEmpty() -> if (state.error != null) {
                    ErrorState(state.error.orEmpty(), onRetry = viewModel::refresh)
                } else {
                    EmptyState("还没有评论")
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp),
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
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.toggleSort() }
                                    .padding(start = 8.dp, top = 4.dp, end = 2.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = COUIIcons.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = COUITheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (state.sort == CommentSort.New) "最新" else "最热",
                                    style = COUITheme.textStyles.footnote1,
                                    color = COUITheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    items(state.items, key = { it.id }) { comment ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 6.dp),
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
                                onOpenReplies = { dialogRoot = it },
                                onOpenUser = onOpenUser,
                                onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
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
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                    .padding(16.dp)
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

    val root = dialogRoot
    if (root != null) {
        ReplyDialog(
            container = container,
            oid = oid,
            comment = state.items.firstOrNull { it.id == root.id } ?: root,
            type = type,
            onSubReplyAdded = { created -> viewModel.onSubReplyAdded(root.id, created) },
            onOpenUser = { mid ->
                dialogRoot = null
                onOpenUser(mid)
            },
            onDismiss = { dialogRoot = null },
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
