package erl.bilico.feature.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.Comment
import erl.bilico.data.model.DynamicImage
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicImageViewer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.components.LocalCommentSeek
import erl.bilico.ui.components.LocalOpenBvid
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

/** 楼中楼从底部弹出：支持分页查看、点赞子评论与直接回复。 */
@Composable
internal fun ReplyDialog(
    container: AppContainer,
    oid: Long,
    comment: Comment,
    type: Int = 1,
    initialTargetReplyId: Long = 0L,
    onSubReplyAdded: (Comment) -> Unit = {},
    onOpenUser: (Long) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var show by remember(comment.id) { mutableStateOf(false) }
    var rootComment by remember(comment.id) { mutableStateOf(comment) }
    var items by remember(comment.id) { mutableStateOf<List<Comment>>(emptyList()) }
    var page by remember(comment.id) { mutableIntStateOf(0) }
    var totalCount by remember(comment.id) { mutableIntStateOf(comment.replyCount) }
    var isLoading by remember(comment.id) { mutableStateOf(true) }
    var isEnd by remember(comment.id) { mutableStateOf(false) }
    var error by remember(comment.id) { mutableStateOf<String?>(null) }
    var replyTarget by remember(comment.id) { mutableStateOf<Comment?>(null) }
    var draft by remember(comment.id) { mutableStateOf("") }
    var isSending by remember(comment.id) { mutableStateOf(false) }
    var toast by remember(comment.id) { mutableStateOf<String?>(null) }
    var imageViewer by remember(comment.id) { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val composerFocus = remember { FocusRequester() }
    // 点评论即回复：先聚焦输入框拉起键盘，再等面板高度重新分配完，把这一条滚到面板顶部。
    var focusTick by remember(comment.id) { mutableIntStateOf(0) }
    var alignRequest by remember(comment.id) { mutableStateOf<Int?>(null) }
    var targetReplyHandled by remember(comment.id, initialTargetReplyId) { mutableStateOf(false) }

    LaunchedEffect(initialTargetReplyId, items.size) {
        if (targetReplyHandled || initialTargetReplyId <= 0L) return@LaunchedEffect
        val idx = items.indexOfFirst { it.id == initialTargetReplyId }
        if (idx >= 0) {
            targetReplyHandled = true
            listState.animateScrollToItem(SUB_REPLY_ITEM_OFFSET + idx)
        }
    }

    LaunchedEffect(comment.id) { show = true }

    LaunchedEffect(alignRequest) {
        val index = alignRequest ?: return@LaunchedEffect
        // 键盘弹出会压缩面板并挤动列表，等 IME 高度连续几帧不动再对齐。
        var ime = imeInsets.getBottom(density)
        var stableFrames = 0
        while (stableFrames < 3) {
            delay(32)
            val now = imeInsets.getBottom(density)
            if (now == ime) stableFrames++ else { ime = now; stableFrames = 0 }
        }
        listState.animateScrollToItem(index)
        alignRequest = null
    }

    fun startReply(target: Comment, itemIndex: Int) {
        replyTarget = target
        focusTick += 1
        alignRequest = itemIndex
    }

    fun showToast(message: String) {
        toast = message
        scope.launch {
            delay(2000)
            if (toast == message) toast = null
        }
    }

    fun loadNext() {
        if (isLoading && page > 0) return
        if (isEnd) return
        isLoading = true
        error = null
        scope.launch {
            val next = page + 1
            container.comments.replies(oid, comment.id, next, type = type)
                .onSuccess { list ->
                    page = next
                    items = (items + list).distinctBy { it.id }
                    isEnd = list.isEmpty()
                }
                .onFailure { failure ->
                    error = failure.message ?: "回复加载失败"
                }
            isLoading = false
        }
    }

    fun toggleRootLike() {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val nextLiked = !rootComment.liked
        val nextDisliked = if (nextLiked) false else rootComment.disliked
        val nextLike = (rootComment.like + if (nextLiked) 1 else -1).coerceAtLeast(0)
        rootComment = rootComment.copy(liked = nextLiked, disliked = nextDisliked, like = nextLike)
        scope.launch {
            container.comments.likeComment(oid = oid, rpid = rootComment.id, liked = nextLiked, type = type)
                .onFailure { err ->
                    rootComment = comment
                    showToast(err.message ?: "点赞失败")
                }
        }
    }

    fun toggleRootDislike() {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val nextDisliked = !rootComment.disliked
        val nextLiked = if (nextDisliked) false else rootComment.liked
        val nextLike = if (nextDisliked && rootComment.liked) {
            (rootComment.like - 1).coerceAtLeast(0)
        } else {
            rootComment.like
        }
        rootComment = rootComment.copy(disliked = nextDisliked, liked = nextLiked, like = nextLike)
        scope.launch {
            container.comments.hateComment(oid = oid, rpid = rootComment.id, hated = nextDisliked, type = type)
                .onFailure { err ->
                    rootComment = comment
                    showToast(err.message ?: "操作失败")
                }
        }
    }

    fun toggleLike(target: Comment) {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val nextLiked = !target.liked
        val nextDisliked = if (nextLiked) false else target.disliked
        val nextLike = (target.like + if (nextLiked) 1 else -1).coerceAtLeast(0)
        items = items.map {
            if (it.id == target.id) {
                it.copy(liked = nextLiked, disliked = nextDisliked, like = nextLike)
            } else {
                it
            }
        }
        scope.launch {
            container.comments.likeComment(oid = oid, rpid = target.id, liked = nextLiked, type = type)
                .onFailure { err ->
                    items = items.map { if (it.id == target.id) target else it }
                    showToast(err.message ?: "点赞失败")
                }
        }
    }

    fun toggleDislike(target: Comment) {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val nextDisliked = !target.disliked
        val nextLiked = if (nextDisliked) false else target.liked
        val nextLike = if (nextDisliked && target.liked) {
            (target.like - 1).coerceAtLeast(0)
        } else {
            target.like
        }
        items = items.map {
            if (it.id == target.id) {
                it.copy(disliked = nextDisliked, liked = nextLiked, like = nextLike)
            } else {
                it
            }
        }
        scope.launch {
            container.comments.hateComment(oid = oid, rpid = target.id, hated = nextDisliked, type = type)
                .onFailure { err ->
                    items = items.map { if (it.id == target.id) target else it }
                    showToast(err.message ?: "操作失败")
                }
        }
    }

    fun sendReply() {
        if (!container.session.isLoggedIn()) {
            showToast("请先登录")
            return
        }
        val text = draft.trim()
        if (text.isEmpty() || isSending) return
        val parentTarget = replyTarget ?: comment
        isSending = true
        scope.launch {
            container.comments.addComment(
                oid = oid,
                message = text,
                root = comment.id,
                parent = parentTarget.id,
                type = type,
            )
                .onSuccess { created ->
                    isSending = false
                    draft = ""
                    replyTarget = null
                    totalCount += 1
                    items = (items + created).distinctBy { it.id }
                    onSubReplyAdded(created)
                    showToast("回复已发送")
                }
                .onFailure { err ->
                    isSending = false
                    showToast(err.message ?: "回复失败")
                }
        }
    }

    LaunchedEffect(comment.id) {
        if (items.isEmpty()) loadNext()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible != null && items.isNotEmpty() && lastVisible >= items.size - 2) {
                    loadNext()
                }
            }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetMaxHeight = (screenHeight * 0.74f).coerceAtLeast(500.dp)
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val keyboardGapProgress = (imeBottom / 80.dp).coerceIn(0f, 1f)
    val navBottom = navBottomPadding() * (1f - keyboardGapProgress)
    val composerBottomPadding = 10.dp + navBottom
    val listBottomPadding = 84.dp + navBottom
    val topStripOffset = 10.dp
    val topStripPx = with(density) { topStripOffset.roundToPx() }
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val parentOpenBvid = LocalOpenBvid.current
    val parentSeek = LocalCommentSeek.current
    val openUserAndClose: (Long) -> Unit = remember(onOpenUser) {
        { mid ->
            if (mid > 0L) {
                show = false
                onOpenUser(mid)
            }
        }
    }
    val threadUserMids = remember(rootComment, items) {
        buildMap {
            if (rootComment.mid > 0L && rootComment.userName.isNotBlank()) {
                put(rootComment.userName, rootComment.mid)
            }
            items.forEach { reply ->
                if (reply.mid > 0L && reply.userName.isNotBlank()) put(reply.userName, reply.mid)
            }
        }
    }

    OverlayBottomSheet(
        show = show,
        title = null,
        startAction = null,
        endAction = null,
        showDragHandle = false,
        insideMargin = DpSize(0.dp, 0.dp),
        panelPaddingBottom = 0.dp,
        // 评论详情是接近满屏的高面板，按原有比例要拖走几乎整块才关得上；这里改成拖走 1/3 就收，
        // 配合向下轻甩随时可关。
        dismissOffsetFraction = 1f / 3f,
        onDismissRequest = { show = false },
        onDismissFinished = onDismiss,
    ) {
        val sheetBackdrop = rememberLayerBackdrop()
        val sheetOverlayBackdrop = if (BlurEffectsEnabled) sheetBackdrop else null
        val topBarColor = if (isDark) Color(0xFF1B1B1B) else AppBarBlurOverlayDefaults.containerColor()
        CompositionLocalProvider(
            LocalOpenBvid provides parentOpenBvid?.let { open -> { bvid -> show = false; open(bvid) } },
            LocalCommentSeek provides parentSeek?.let { seek -> { ms -> show = false; seek(ms) } },
            LocalEdgeToEdge provides true,
        ) {
            // OverlayBottomSheet 无拖拽条时仍会在内容上方保留 10dp 空白拖动手柄带；把内容上移这段高度，
            // 让顶栏渐进模糊与列表滚动直接贴齐面板圆角顶部，消除顶部未模糊黑带。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight + topStripOffset)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, (placeable.height - topStripPx).coerceAtLeast(0)) {
                            placeable.placeRelative(0, -topStripPx)
                        }
                    },
            ) {
                val sheetBackdropTint = if (isDark) Color(0xF21B1B1B) else Color(0xF2F4F5F6)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layerBackdropIf(BlurEffectsEnabled, sheetBackdrop)
                        .background(sheetBackdropTint),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().scrollEndHaptic(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 60.dp + topStripOffset,
                            end = 16.dp,
                            bottom = listBottomPadding,
                        ),
                    ) {
                    // 1. 根评论卡片（与主评论区一致的卡片组件）
                    item(key = "root_comment") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tapToReply { startReply(rootComment, 0) },
                            ) {
                                BiliCommentItem(
                                    comment = rootComment,
                                    onLike = { toggleRootLike() },
                                    onDislike = { toggleRootDislike() },
                                    onReply = { startReply(rootComment, 0) },
                                    onOpenReplies = null,
                                    onOpenUser = openUserAndClose,
                                    onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
                                    isSubReply = false,
                                    expandable = false,
                                )
                            }
                        }
                    }

                    // 2. 小节标题栏：相关回复共N条 + 按时间
                    item(key = "sub_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "相关回复共${maxOf(totalCount, items.size)}条",
                                style = COUITheme.textStyles.main.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = COUITheme.colorScheme.onSurface,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = SortTimeIcon,
                                    contentDescription = "按时间",
                                    modifier = Modifier.size(13.dp),
                                    tint = COUITheme.colorScheme.onSurfaceSecondary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "按时间",
                                    style = COUITheme.textStyles.footnote1.copy(fontSize = 12.sp),
                                    color = COUITheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    }

                    // 4. 子评论内容与状态
                    if (items.isEmpty()) {
                        item(key = "sub_placeholder") {
                            SubReplyPlaceholderBox(
                                isLoading = isLoading,
                                error = error,
                                onRetry = { loadNext() },
                            )
                        }
                    } else {
                        itemsIndexed(items, key = { _, reply -> reply.id }) { index, reply ->
                                val itemIndex = SUB_REPLY_ITEM_OFFSET + index
                                val displayReply = remember(reply, threadUserMids) {
                                    if (threadUserMids.isEmpty()) reply
                                    else reply.copy(mentions = threadUserMids + reply.mentions)
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .tapToReply { startReply(reply, itemIndex) },
                                    ) {
                                        BiliCommentItem(
                                            comment = displayReply,
                                            onLike = { toggleLike(reply) },
                                            onDislike = { toggleDislike(reply) },
                                            onReply = { startReply(reply, itemIndex) },
                                            onOpenUser = openUserAndClose,
                                            onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
                                            isSubReply = true,
                                            expandable = false,
                                        )
                                    }
                                }
                            }
                            if (isLoading) {
                                item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(size = 16.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                toast?.let { msg ->
                    Text(
                        text = msg,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = listBottomPadding)
                            .clip(SquircleShape(8.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = COUITheme.textStyles.footnote2,
                        color = Color.White,
                    )
                }

                ReplyDetailTopBar(
                    scrollFraction = {
                        val rawScroll = if (listState.firstVisibleItemIndex > 0) 10_000f
                        else listState.firstVisibleItemScrollOffset.toFloat()
                        AppBarBlurOverlayDefaults.scrollAlpha(rawScroll, density)
                    },
                    containerColor = topBarColor,
                    backdrop = sheetOverlayBackdrop,
                    onClose = { show = false },
                    modifier = Modifier.align(Alignment.TopCenter),
                    topInset = topStripOffset,
                )

                ReplyComposer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    replyTarget = replyTarget,
                    onClearReplyTarget = { replyTarget = null },
                    isSending = isSending,
                    isDark = isDark,
                    focusRequester = composerFocus,
                    focusTick = focusTick,
                    onSend = { sendReply() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = composerBottomPadding),
                    backdrop = sheetOverlayBackdrop,
                )
            }
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

/** 列表最前面固定两项：根评论卡片、相关回复标题，子回复从下标 2 开始。 */
private const val SUB_REPLY_ITEM_OFFSET = 2

/**
 * 点评论空白处即进入回复。评论里自己的头像、用户名、图片、回复按钮都带各自的点击，
 * 先把事件吃掉，所以这里只管其余区域；不加波纹，聚焦回复框与滚动到位本身就是反馈。
 */
@Composable
private fun Modifier.tapToReply(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}
