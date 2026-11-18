package erl.bilico.feature.comment

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentEmotePackage
import erl.bilico.data.model.DEFAULT_EMOTE_MANAGE_URL
import erl.bilico.data.model.DynamicImage
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicImageViewer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.ExpandEasing
import erl.bilico.ui.LocalNavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.LocalCommentSeek
import erl.bilico.ui.components.LocalOpenBvid
import erl.bilico.ui.components.navBottomPadding
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 评论详情底部展开覆盖层：带底部滑入/滑出动画，完整盖住下层简介、评论与发弹幕栏。 */
@Composable
internal fun ReplyDetailOverlay(
    controller: ReplyThreadController,
    container: AppContainer,
    oid: Long,
    type: Int = 1,
    latestItems: List<Comment> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    topInset: Dp = 0.dp,
    bottomInset: Dp = navBottomPadding(),
    backEnabled: Boolean = true,
    onOpenUser: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var displayedThread by remember { mutableStateOf<Pair<Comment, Long>?>(null) }
    val activeRoot = controller.root
    if (activeRoot != null) {
        val pool = latestItems.ifEmpty { controller.itemsSnapshot }
        val synced = pool.firstOrNull { it.id == activeRoot.id } ?: activeRoot
        displayedThread = synced to controller.targetReplyId
    }

    AnimatedVisibility(
        visible = activeRoot != null,
        modifier = modifier,
        enter = slideInVertically(
            animationSpec = tween(durationMillis = 280, easing = ExpandEasing),
            initialOffsetY = { it },
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = slideOutVertically(
            animationSpec = tween(durationMillis = 240, easing = ExpandEasing),
            targetOffsetY = { it },
        ) + fadeOut(animationSpec = tween(durationMillis = 160)),
    ) {
        val thread = displayedThread ?: return@AnimatedVisibility
        val root = thread.first
        ReplyDetailPage(
            container = container,
            oid = oid,
            comment = root,
            type = type,
            initialTargetReplyId = thread.second,
            listState = listState,
            topInset = topInset,
            bottomInset = bottomInset,
            backEnabled = backEnabled && activeRoot != null,
            onSubReplyAdded = { created -> controller.onSubReplyAdded?.invoke(root.id, created) },
            onSubReplyDeleted = { replyId -> controller.onSubReplyDeleted?.invoke(root.id, replyId) },
            onRootDeleted = {
                controller.onRootDeleted?.invoke(root.id)
                controller.close()
            },
            onOpenUser = onOpenUser,
            onClose = controller::close,
        )
    }
}

/** 评论详情内嵌页面：在底部区域直接展开，覆盖简介/评论/发弹幕，支持「最热」「最新」切换与回复。 */
@Composable
internal fun ReplyDetailPage(
    container: AppContainer,
    oid: Long,
    comment: Comment,
    type: Int = 1,
    initialTargetReplyId: Long = 0L,
    listState: LazyListState = rememberLazyListState(),
    topInset: Dp = 0.dp,
    bottomInset: Dp = navBottomPadding(),
    backEnabled: Boolean = true,
    onSubReplyAdded: (Comment) -> Unit = {},
    onSubReplyDeleted: (Long) -> Unit = {},
    onRootDeleted: () -> Unit = {},
    onOpenUser: (Long) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val currentMid = account?.mid ?: container.session.mid()
    val detailState = rememberReplyDetailState(
        container = container,
        oid = oid,
        type = type,
        comment = comment,
        onSubReplyAdded = onSubReplyAdded,
        onSubReplyDeleted = onSubReplyDeleted,
        onRootDeleted = onRootDeleted,
    )
    LaunchedEffect(comment) {
        if (comment.id == detailState.rootComment.id) {
            detailState.rootComment = comment
        }
    }

    var replyTarget by remember(comment.id) { mutableStateOf<Comment?>(null) }
    var draft by remember(comment.id) { mutableStateOf(TextFieldValue("")) }
    var pictures by remember(comment.id) { mutableStateOf<List<Uri>>(emptyList()) }
    var emotePanelOpen by remember(comment.id) { mutableStateOf(false) }
    var emotePackages by remember { mutableStateOf<List<CommentEmotePackage>>(emptyList()) }
    var emoteManageUrl by remember { mutableStateOf(DEFAULT_EMOTE_MANAGE_URL) }
    var emotesFailed by remember { mutableStateOf(false) }
    var openedEmoteManager by remember { mutableStateOf(false) }
    var imageViewer by remember(comment.id) { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val nav = LocalNavState.current
    val imeInsets = WindowInsets.ime
    val composerFocus = remember { FocusRequester() }
    val editorInteractionSource = remember { MutableInteractionSource() }
    val editorFocused by editorInteractionSource.collectIsFocusedAsState()
    var wasEditorFocused by remember { mutableStateOf(false) }
    var focusTick by remember(comment.id) { mutableIntStateOf(0) }
    var alignRequest by remember(comment.id) { mutableStateOf<Int?>(null) }
    var targetReplyHandled by remember(comment.id, initialTargetReplyId) { mutableStateOf(false) }
    var bottomStackPx by remember { mutableIntStateOf(0) }

    val emoteLookup = remember(emotePackages) { buildEmoteLookup(emotePackages) }

    suspend fun loadEmotes(force: Boolean = false) {
        if (!force && (emotePackages.isNotEmpty() || emotesFailed)) return
        val result = container.comments.emotePanel().getOrElse {
            emotesFailed = true
            return
        }
        emotePackages = result.packages
        emoteManageUrl = result.manageUrl
        emotesFailed = result.packages.isEmpty()
    }

    val managingEmotes = nav?.stack?.any { it.route is Route.EmoteManager } == true
    LaunchedEffect(managingEmotes) {
        if (!managingEmotes && openedEmoteManager) {
            openedEmoteManager = false
            loadEmotes(force = true)
        }
    }

    LaunchedEffect(editorFocused) {
        if (editorFocused && !wasEditorFocused && emotePanelOpen) {
            emotePanelOpen = false
        }
        wasEditorFocused = editorFocused
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_REPLY_PICTURES),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val room = (MAX_REPLY_PICTURES - pictures.size).coerceAtLeast(0)
        if (room == 0) {
            detailState.showToast("最多 $MAX_REPLY_PICTURES 张图片")
            return@rememberLauncherForActivityResult
        }
        pictures = pictures + uris.take(room)
    }

    BackHandler(enabled = backEnabled) {
        if (emotePanelOpen) {
            emotePanelOpen = false
        } else {
            onClose()
        }
    }

    LaunchedEffect(comment.id) {
        listState.scrollToItem(0)
        detailState.loadInitial()
        launch { loadEmotes(force = false) }
    }

    LaunchedEffect(initialTargetReplyId, detailState.items.size) {
        if (targetReplyHandled || initialTargetReplyId <= 0L) return@LaunchedEffect
        val idx = detailState.items.indexOfFirst { it.id == initialTargetReplyId }
        if (idx >= 0) {
            targetReplyHandled = true
            listState.animateScrollToItem(SUB_REPLY_ITEM_OFFSET + idx)
        }
    }

    LaunchedEffect(alignRequest) {
        val index = alignRequest ?: return@LaunchedEffect
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

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible != null && detailState.items.isNotEmpty() && lastVisible >= detailState.items.size - 1) {
                    detailState.loadMore()
                }
            }
    }

    fun startReply(target: Comment, itemIndex: Int) {
        replyTarget = target
        emotePanelOpen = false
        focusTick += 1
        alignRequest = itemIndex
    }

    fun insertEmote(token: String) {
        val next = insertTokenInDraft(draft, token, MAX_REPLY_LENGTH)
        if (next == null) {
            detailState.showToast("最多 $MAX_REPLY_LENGTH 字")
        } else {
            draft = next
        }
    }

    fun toggleEmotePanel() {
        if (emotePanelOpen) {
            emotePanelOpen = false
            runCatching { composerFocus.requestFocus() }
            keyboard?.show()
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
            emotePanelOpen = true
            scope.launch { loadEmotes(force = false) }
        }
    }

    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val keyboardGapProgress = (imeBottom / 80.dp).coerceIn(0f, 1f)
    val navBottom = bottomInset * (1f - keyboardGapProgress)
    val composerBottomPadding = 10.dp + navBottom
    val listBottomPadding = with(density) {
        val measured = bottomStackPx.toDp()
        if (measured > 0.dp) measured + 8.dp else 84.dp + navBottom
    }
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val pageBg = pageContainerColor()
    val parentOpenBvid = LocalOpenBvid.current
    val parentSeek = LocalCommentSeek.current
    val threadUserMids = remember(detailState.rootComment, detailState.items) {
        buildMap {
            val root = detailState.rootComment
            if (root.mid > 0L && root.userName.isNotBlank()) {
                put(root.userName, root.mid)
            }
            detailState.items.forEach { reply ->
                if (reply.mid > 0L && reply.userName.isNotBlank()) put(reply.userName, reply.mid)
            }
        }
    }

    val pageBackdrop = rememberLayerBackdrop()
    val overlayBackdrop = if (BlurEffectsEnabled) pageBackdrop else null
    val scrollRampPx = remember(density) { with(density) { 12.dp.toPx().coerceAtLeast(1f) } }

    CompositionLocalProvider(
        LocalOpenBvid provides parentOpenBvid,
        LocalCommentSeek provides parentSeek,
        LocalEdgeToEdge provides true,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(pageBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdropIf(BlurEffectsEnabled, pageBackdrop)
                    .background(pageBg)
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = topInset + REPLY_TOP_BAR_HEIGHT + 12.dp,
                    end = 16.dp,
                    bottom = listBottomPadding,
                ),
            ) {
                item(key = "root_comment") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    ) {
                        BiliCommentItem(
                            comment = detailState.rootComment,
                            onLike = detailState::toggleRootLike,
                            onDislike = detailState::toggleRootDislike,
                            onReply = { startReply(detailState.rootComment, 0) },
                            onOpenReplies = null,
                            onOpenUser = onOpenUser,
                            onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
                            isSubReply = false,
                            expandable = false,
                            currentMid = currentMid,
                            onDelete = detailState::deleteRootComment,
                            onClick = { startReply(detailState.rootComment, 0) },
                        )
                    }
                }

                item(key = "sub_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "相关回复共${maxOf(detailState.totalCount, detailState.items.size)}条",
                            style = COUITheme.textStyles.main.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = COUITheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        CommentSortTabRow(
                            sort = detailState.sort,
                            onSelectSort = detailState::selectSort,
                        )
                    }
                }

                if (detailState.items.isEmpty()) {
                    item(key = "sub_placeholder") {
                        SubReplyPlaceholderBox(
                            isLoading = detailState.isLoading,
                            error = detailState.error,
                            onRetry = { detailState.loadInitial() },
                        )
                    }
                } else {
                    itemsIndexed(detailState.items, key = { _, reply -> reply.id }) { index, reply ->
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
                            BiliCommentItem(
                                comment = displayReply,
                                onLike = { detailState.toggleLike(reply) },
                                onDislike = { detailState.toggleDislike(reply) },
                                onReply = { startReply(reply, itemIndex) },
                                onOpenUser = onOpenUser,
                                onOpenImage = { imgs, idx -> imageViewer = imgs to idx },
                                isSubReply = true,
                                expandable = false,
                                currentMid = currentMid,
                                onDelete = { detailState.deleteSubReply(reply) },
                                onClick = { startReply(reply, itemIndex) },
                            )
                        }
                    }
                    if (detailState.isLoading) {
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

            detailState.toast?.let { msg ->
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
                    if (listState.firstVisibleItemIndex > 0) 1f
                    else (listState.firstVisibleItemScrollOffset / scrollRampPx).coerceIn(0f, 1f)
                },
                containerColor = pageBg,
                backdrop = overlayBackdrop,
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter),
                topInset = topInset,
            )

            ReplyComposer(
                draft = draft,
                onDraftChange = { next ->
                    draft = if (next.text.length <= MAX_REPLY_LENGTH) {
                        next
                    } else {
                        next.copy(text = next.text.take(MAX_REPLY_LENGTH))
                    }
                },
                emotes = emoteLookup,
                pictures = pictures,
                onRemovePicture = { idx ->
                    pictures = pictures.filterIndexed { i, _ -> i != idx }
                },
                replyTarget = replyTarget,
                onClearReplyTarget = { replyTarget = null },
                isSending = detailState.isSending,
                isDark = isDark,
                focusRequester = composerFocus,
                interactionSource = editorInteractionSource,
                focusTick = focusTick,
                emotePanelOpen = emotePanelOpen,
                onToggleEmote = ::toggleEmotePanel,
                onPickImage = {
                    if (pictures.size >= MAX_REPLY_PICTURES) {
                        detailState.showToast("最多 $MAX_REPLY_PICTURES 张图片")
                    } else {
                        if (emotePanelOpen) emotePanelOpen = false
                        keyboard?.hide()
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                },
                emotePackages = emotePackages,
                emotesFailed = emotesFailed,
                emoteManageUrl = emoteManageUrl,
                onOpenEmoteManage = { url ->
                    if (nav == null) {
                        detailState.showToast("当前页面暂不支持打开表情管理")
                    } else {
                        keyboard?.hide()
                        openedEmoteManager = true
                        nav.push(Route.EmoteManager(url))
                    }
                },
                onPickEmote = ::insertEmote,
                onSend = {
                    detailState.sendReply(
                        draft = draft.text,
                        pictures = pictures,
                        context = context,
                        replyTarget = replyTarget,
                    ) {
                        draft = TextFieldValue("")
                        pictures = emptyList()
                        replyTarget = null
                        emotePanelOpen = false
                    }
                },
                panelBackground = pageBg,
                bottomPadding = composerBottomPadding,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomStackPx = it.height },
                backdrop = overlayBackdrop,
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

private val REPLY_TOP_BAR_HEIGHT = 48.dp
private const val MAX_REPLY_LENGTH = 1000
private const val MAX_REPLY_PICTURES = 9

/** 列表最前面固定两项：根评论卡片、相关回复标题，子回复从下标 2 开始。 */
private const val SUB_REPLY_ITEM_OFFSET = 2
