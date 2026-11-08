package erl.bilico.feature.dynamic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.BiliCommentItem
import erl.bilico.feature.comment.CommentComposerSheet
import erl.bilico.feature.comment.CommentViewModel
import erl.bilico.feature.comment.ReplyDialog
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.Route
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.glassBarContentTop
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Send
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

private sealed interface DynComposeTarget {
    data object Main : DynComposeTarget
    data class Reply(val root: Comment) : DynComposeTarget
}

/**
 * 动态详情页（对齐 PiliPlus / BiliPai）：
 * 顶部完整展示原动态卡片（含转发原帖整块卡片、全文表情与配图），下方在同一个滚动列表中直接展示评论区。
 */
@Composable
fun DynamicDetailScreen(
    container: AppContainer,
    nav: NavState,
    dynamicId: String,
) {
    val cleanId = remember(dynamicId) { dynamicId.trim() }
    var item by remember(cleanId) { mutableStateOf(container.dynamic.peekSeed(cleanId)) }
    var isLoading by remember(cleanId) { mutableStateOf(item == null) }
    var error by remember(cleanId) { mutableStateOf<String?>(null) }
    var reloadTrigger by remember(cleanId) { mutableStateOf(0) }
    var imageViewer by remember { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }

    LaunchedEffect(cleanId, reloadTrigger) {
        if (item == null) isLoading = true
        error = null
        container.dynamic.detail(cleanId)
            .onSuccess { fetched ->
                item = fetched
                isLoading = false
            }
            .onFailure { err ->
                isLoading = false
                if (item == null) {
                    error = err.message ?: "动态加载失败"
                }
            }
    }

    val currentItem = item
    val commentOid = currentItem?.commentOid ?: 0L
    val commentType = currentItem?.commentType?.takeIf { it > 0 } ?: 17
    val commentViewModel: CommentViewModel? = if (commentOid > 0L) {
        viewModel(key = "dyn-comment-$commentType-$commentOid") {
            CommentViewModel(container.comments, commentOid, commentType)
        }
    } else {
        null
    }
    val commentState = commentViewModel?.state?.collectAsStateWithLifecycle()?.value
    var dialogRoot by remember { mutableStateOf<Comment?>(null) }
    var composeTarget by remember { mutableStateOf<DynComposeTarget?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, commentViewModel) {
        val vm = commentViewModel ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val total = listState.layoutInfo.totalItemsCount
                if (lastVisible != null && total > 2 && lastVisible >= total - 3) {
                    vm.loadMore()
                }
            }
    }

    val pageColor = pageContainerColor()
    val backdrop = rememberLayerBackdrop()
    val topBarHeight = pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    Box(Modifier.fillMaxSize().background(pageColor)) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdropIf(BlurEffectsEnabled, backdrop)
                .background(pageColor),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = glassBarContentTop(topBarHeight, 10.dp),
                    end = 12.dp,
                    bottom = 88.dp + navBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    currentItem == null && isLoading -> item(key = "loading") {
                        LoadingState()
                    }

                    currentItem == null && error != null -> item(key = "error") {
                        ErrorState(error.orEmpty(), onRetry = { reloadTrigger++ })
                    }

                    currentItem == null -> item(key = "empty") {
                        EmptyState("动态不存在或已删除")
                    }

                    else -> {
                        item(key = "dynamic-card") {
                            DynamicCard(
                                item = currentItem,
                                isDetail = true,
                                onOpenUser = { mid -> nav.push(Route.Space(mid)) },
                                onOpenVideo = { bvid -> nav.push(Route.Video(bvid)) },
                                onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
                                onOpenDynamic = { target ->
                                    val artId = target.article?.articleId ?: 0L
                                    val targetDynId = target.dynamicId.ifBlank { target.article?.dynamicId.orEmpty() }
                                    val video = target.video
                                    when {
                                        artId > 0L -> nav.push(Route.Article(artId, target.title.ifBlank { target.authorName }))
                                        targetDynId.isNotBlank() && targetDynId != cleanId ->
                                            nav.push(Route.Opus(targetDynId, target.title.ifBlank { target.authorName }))
                                        video != null -> nav.push(Route.Video(video.bvid))
                                    }
                                },
                            )
                        }

                        if (commentViewModel != null && commentState != null) {
                            item(key = "comment-section") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                ) {
                                    val totalComments = maxOf(commentState.total, currentItem.commentCount)
                                    Text(
                                        text = "评论 ${Formatters.formatStat(totalComments.toLong())}",
                                        style = COUITheme.textStyles.body1,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )

                                    when {
                                        commentState.isLoading && commentState.items.isEmpty() ->
                                            LoadingState()

                                        commentState.items.isEmpty() -> if (commentState.error != null) {
                                            ErrorState(
                                                commentState.error.orEmpty(),
                                                onRetry = commentViewModel::refresh,
                                            )
                                        } else {
                                            EmptyState("还没有评论")
                                        }

                                        else -> Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(18.dp),
                                        ) {
                                            commentState.items.forEach { comment ->
                                                BiliCommentItem(
                                                    comment = comment,
                                                    onLike = {
                                                        if (!container.session.isLoggedIn()) {
                                                            commentViewModel.showMessage("请先登录")
                                                        } else {
                                                            commentViewModel.toggleLike(comment)
                                                        }
                                                    },
                                                    onDislike = {
                                                        if (!container.session.isLoggedIn()) {
                                                            commentViewModel.showMessage("请先登录")
                                                        } else {
                                                            commentViewModel.toggleDislike(comment)
                                                        }
                                                    },
                                                    onReply = {
                                                        if (!container.session.isLoggedIn()) {
                                                            commentViewModel.showMessage("请先登录")
                                                        } else {
                                                            composeTarget = DynComposeTarget.Reply(comment)
                                                        }
                                                    },
                                                    onOpenReplies = { dialogRoot = it },
                                                    onOpenUser = { mid -> nav.push(Route.Space(mid)) },
                                                    onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
                                                )
                                            }
                                            when {
                                                commentState.isLoadingMore -> Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(40.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        size = 18.dp,
                                                    )
                                                }

                                                commentState.isEnd -> Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(36.dp),
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
                            }
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalBackdrop provides if (BlurEffectsEnabled) backdrop else null,
            LocalEdgeToEdge provides true,
        ) {
            PinnedBarDensity {
                SmallTopAppBar(
                    title = "动态详情",
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = pageColor,
                    showDivider = false,
                    gradientGlass = BlurEffectsEnabled,
                    navigationIcon = { BackIconButton(onClick = nav::pop) },
                )
            }
        }

        if (commentViewModel != null) {
            commentState?.message?.let { toast ->
                Text(
                    text = toast,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = Color.White,
                )
            }

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
                        commentViewModel.showMessage("请先登录")
                    } else {
                        composeTarget = DynComposeTarget.Main
                    }
                },
                interactionSource = ballInteraction,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
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

    if (commentViewModel != null) {
        when (val target = composeTarget) {
            DynComposeTarget.Main -> CommentComposerSheet(
                container = container,
                hint = "发一条友善的评论",
                onSubmit = { text, pictures, atMids ->
                    composeTarget = null
                    commentViewModel.postComment(text, pictures, atMids)
                },
                onDismiss = { composeTarget = null },
            )

            is DynComposeTarget.Reply -> CommentComposerSheet(
                container = container,
                hint = "回复 @${target.root.userName}",
                onSubmit = { text, pictures, atMids ->
                    composeTarget = null
                    commentViewModel.postReply(target.root, text, pictures, atMids)
                },
                onDismiss = { composeTarget = null },
            )

            null -> Unit
        }

        val root = dialogRoot
        if (root != null) {
            ReplyDialog(
                container = container,
                oid = commentOid,
                comment = commentState?.items?.firstOrNull { it.id == root.id } ?: root,
                type = commentType,
                onSubReplyAdded = { created -> commentViewModel.onSubReplyAdded(root.id, created) },
                onOpenUser = { mid ->
                    dialogRoot = null
                    nav.push(Route.Space(mid))
                },
                onDismiss = { dialogRoot = null },
            )
        }
    }

    imageViewer?.let { (images, index) ->
        DynamicImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { imageViewer = null },
        )
    }
}
