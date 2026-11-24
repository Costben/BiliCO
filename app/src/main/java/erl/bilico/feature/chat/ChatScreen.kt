package erl.bilico.feature.chat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.ImMessageUi
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.ComposerEmoteTextField
import erl.bilico.feature.comment.ComposerToolButton
import erl.bilico.feature.comment.CAPSULE_COMPOSER_HEIGHT
import erl.bilico.feature.comment.InlineEmoteDrawer
import erl.bilico.feature.comment.buildEmoteLookup
import erl.bilico.feature.dynamic.DynamicImageViewer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.COUISmoothCapsuleShape
import erl.bilico.ui.components.CompactSendButton
import erl.bilico.ui.components.EmojiIcon
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.RichCommentText
import erl.bilico.ui.hyperlinkColor
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.inputCapsuleGlass
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MAX_CHAT_IMAGE_PICK = 9


@Composable
fun ChatScreen(
    container: AppContainer,
    nav: NavState,
    talkerId: Long,
    talkerName: String,
    talkerFace: String,
) {
    val viewModel: ChatViewModel = viewModel(key = "chat-$talkerId") {
        ChatViewModel(container.im, container.comments, container.session, container.messages, talkerId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // ViewModel 挂在 Activity 上，退出再进来不会重建；这里补一次静默刷新，新消息才看得到。
    LaunchedEffect(Unit) { viewModel.refreshOnEnter() }
    val listState = rememberLazyListState()
    val pageColor = pageContainerColor()
    // 消息列表录进这个图层，底栏的胶囊和发送按钮拿它做磨砂。
    val backdrop = rememberLayerBackdrop()
    // 底栏浮在列表上，列表底部要按它实测的高度让位，草稿换行把底栏顶高时消息才跟着上移。
    var bottomStackPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val listBottomPadding = with(density) {
        val measured = bottomStackPx.toDp()
        if (measured > 0.dp) measured + 8.dp else CAPSULE_COMPOSER_HEIGHT + 32.dp
    }
    var inputField by remember { mutableStateOf(TextFieldValue("")) }
    var emotePanelOpen by remember { mutableStateOf(false) }
    var openedEmoteManager by remember { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<DynamicImage?>(null) }

    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val editorInteractionSource = remember { MutableInteractionSource() }
    val editorFocused by editorInteractionSource.collectIsFocusedAsState()
    var wasEditorFocused by remember { mutableStateOf(false) }

    val managingEmotes = nav.stack.any { it.route is Route.EmoteManager }
    LaunchedEffect(managingEmotes) {
        if (!managingEmotes && openedEmoteManager) {
            openedEmoteManager = false
            viewModel.loadEmotes(force = true)
        }
    }

    LaunchedEffect(editorFocused) {
        if (editorFocused && !wasEditorFocused && emotePanelOpen) {
            emotePanelOpen = false
        }
        wasEditorFocused = editorFocused
    }

    val emoteLookup = remember(state.emotePackages) { buildEmoteLookup(state.emotePackages) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_CHAT_IMAGE_PICK),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (!container.session.isLoggedIn()) {
            viewModel.showNotice("请先登录发送图片")
            return@rememberLauncherForActivityResult
        }
        val items = uris.mapIndexed { index, uri ->
            ChatImagePayload(
                previewUri = uri.toString(),
                readBytes = {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: return@runCatching null
                            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                            val ext = mime.substringAfterLast('/', "jpg")
                            val name = "chat_${System.currentTimeMillis()}_$index.$ext"
                            Triple(bytes, name, mime)
                        }.getOrNull()
                    }
                },
            )
        }
        viewModel.sendImages(items)
    }

    // 当收到新消息、发送消息或展开表情面板时，自动滚动到最底部
    LaunchedEffect(state.messages.size, emotePanelOpen) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // 草稿换行或表情面板把底栏顶高时，列表底部让位的空间跟着变大；最后一条消息还露在屏上的话就把它重新
    // 钉到新的底部，免得被胶囊压住。逐帧累加滚动会被最大滚动位置夹断，所以每帧直接钉到最底。
    var prevBottomPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(bottomStackPx) {
        val delta = bottomStackPx - prevBottomPx
        prevBottomPx = bottomStackPx
        if (delta == 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return@LaunchedEffect
        if (last.index < info.totalItemsCount - 1) return@LaunchedEffect
        runCatching { listState.scrollToItem(last.index, 100_000) }
    }

    val notice = state.notice
    if (notice != null) {
        LaunchedEffect(notice) {
            delay(2400)
            viewModel.consumeNotice()
        }
    }

    BackHandler(enabled = emotePanelOpen) {
        emotePanelOpen = false
    }

    fun insertEmote(token: String) {
        val current = inputField
        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(start, current.text.length)
        val merged = current.text.replaceRange(start, end, token)
        inputField = TextFieldValue(merged, TextRange(start + token.length))
    }

    fun toggleEmotePanel() {
        if (emotePanelOpen) {
            emotePanelOpen = false
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
            emotePanelOpen = true
            viewModel.loadEmotes(force = false)
        }
    }

    fun submitText() {
        keyboard?.hide()
        viewModel.send(inputField.text, emoteLookup) {
            inputField = TextFieldValue("")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))),
        ) {
            SecondaryPage(
                title = talkerName.ifBlank { "UID: $talkerId" },
                onBack = nav::pop,
                listState = listState,
                backdrop = backdrop,
                actions = {
                    HighlightedIconButton(
                        onClick = { nav.push(Route.Space(talkerId)) },
                        modifier = Modifier.couiTopAppBarActionItem(),
                    ) {
                        Icon(
                            imageVector = COUIIcons.Contacts,
                            contentDescription = "个人空间",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                contentBottomPadding = listBottomPadding,
                // 私信对话页的弹层（表情、发送失败重试、个人空间）一开一关就会让 backdrop 停止录制，
                // 窗口级材质随即丢几帧模糊。改走 COUI 引擎，采样本页列表的 layerBackdrop。
                platformBarMaterial = false,
            ) {
                if (state.isLoading) {
                    item(key = "loading") { LoadingState() }
                } else if (state.error != null && state.messages.isEmpty()) {
                    item(key = "error") {
                        ErrorState(message = state.error.orEmpty(), onRetry = viewModel::loadMessages)
                    }
                } else if (state.messages.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(text = "暂无聊天记录，发条私信打个招呼吧")
                    }
                } else {
                    items(
                        items = state.messages,
                        key = { "${it.msgKey}_${it.timestamp}_${it.imageUrl}" },
                    ) { msg ->
                        ChatBubbleRow(
                            message = msg,
                            talkerFace = talkerFace,
                            onAvatarClick = { nav.push(Route.Space(talkerId)) },
                            onImageClick = {
                                if (msg.imageUrl.isNotBlank()) {
                                    viewerImage = DynamicImage(
                                        url = msg.imageUrl,
                                        width = msg.imageWidth,
                                        height = msg.imageHeight,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // 底部输入栏浮在列表上：胶囊采到的是从它背后滚过去的消息。
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomStackPx = it.height },
            ) {
                ChatBottomBar(
                    value = inputField,
                    onValueChange = { inputField = it },
                    emotes = emoteLookup,
                    onSend = ::submitText,
                    isSending = state.isSending,
                    emotePanelOpen = emotePanelOpen,
                    onToggleEmote = ::toggleEmotePanel,
                    onPickImage = {
                        if (emotePanelOpen) emotePanelOpen = false
                        keyboard?.hide()
                        pickImages.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    focusRequester = focusRequester,
                    interactionSource = editorInteractionSource,
                    backdrop = if (BlurEffectsEnabled) backdrop else null,
                )

                InlineEmoteDrawer(
                    visible = emotePanelOpen,
                    panelBackground = pageColor,
                    packages = state.emotePackages,
                    failed = state.emotesFailed,
                    manageUrl = state.emoteManageUrl,
                    onOpenManage = { url ->
                        keyboard?.hide()
                        openedEmoteManager = true
                        nav.push(Route.EmoteManager(url))
                    },
                    onPick = ::insertEmote,
                    bottomPadding = 8.dp,
                )
            }
        }

        notice?.let { msg ->
            Text(
                text = msg,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 84.dp)
                    .clip(SquircleShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = Color.White,
            )
        }
    }

    viewerImage?.let { img ->
        DynamicImageViewer(
            images = listOf(img),
            initialIndex = 0,
            onDismiss = { viewerImage = null },
        )
    }
}

@Composable
private fun ChatBubbleRow(
    message: ImMessageUi,
    talkerFace: String,
    onAvatarClick: () -> Unit,
    onImageClick: () -> Unit,
) {
    val isMe = message.isMe
    val scheme = COUITheme.colorScheme
    val avatarUrl = remember(talkerFace) { Formatters.sizedImageUrl(talkerFace, 108, 108) }
    val bubbleShape = remember(isMe) {
        SquircleShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isMe) 16.dp else 4.dp,
            bottomEnd = if (isMe) 4.dp else 16.dp,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!isMe) {
                Avatar(
                    url = avatarUrl,
                    size = 36.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick),
                )
                Spacer(Modifier.width(8.dp))
            }

            if (message.imageUrl.isNotBlank()) {
                val rawW = if (message.imageWidth > 0) message.imageWidth.toFloat() else 4f
                val rawH = if (message.imageHeight > 0) message.imageHeight.toFloat() else 3f
                val ratio = (rawW / rawH).coerceIn(0.5f, 2.2f)
                val targetW = if (ratio >= 1f) 210.dp else (240.dp * ratio).coerceIn(110.dp, 210.dp)
                val targetH = (targetW / ratio).coerceIn(96.dp, 240.dp)
                Box(
                    modifier = Modifier
                        .size(width = targetW, height = targetH)
                        .clip(bubbleShape)
                        .background(scheme.surfaceContainer)
                        .clickable(onClick = onImageClick),
                ) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "图片消息",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                // 气泡主体
                Box(
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .clip(bubbleShape)
                        .background(if (isMe) scheme.primary else scheme.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    RichCommentText(
                        content = message.content,
                        emotes = message.emotes,
                        compactEmotes = true,
                        style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
                        color = if (isMe) Color.White else scheme.onSurface,
                        prefixColor = if (isMe) Color.White else hyperlinkColor(),
                    )
                }
            }

            if (isMe) {
                Spacer(Modifier.width(8.dp))
            }
        }

        if (message.timeText.isNotBlank()) {
            Text(
                text = message.timeText,
                fontSize = 10.sp,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 4.dp, start = if (!isMe) 44.dp else 0.dp, end = if (isMe) 8.dp else 0.dp),
            )
        }
    }
}
