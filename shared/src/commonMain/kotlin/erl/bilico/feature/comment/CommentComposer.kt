package erl.bilico.feature.comment

import erl.bilico.platform.PickedPhotoData
import erl.bilico.platform.rememberPhotoPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import erl.bilico.platform.LocalPlatformContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.CommentEmotePackage
import erl.bilico.data.model.DEFAULT_EMOTE_MANAGE_URL
import erl.bilico.data.model.FollowingUser
import erl.bilico.data.model.UploadedPicture
import erl.bilico.di.AppContainer
import erl.bilico.ui.LocalNavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.EmojiIcon
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.layout.SheetCloseCapsule
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ComposerPanel { None, Emote, At }

/** 选了但还没上传的图。 */
private typealias PickedImage = PickedPhotoData

/**
 * 发表评论 / 回复的底部输入面板：遵循 ColorOS 17 面板规范，左右 12.dp 缩进与全项目卡片对齐。
 */
@Composable
fun CommentComposerSheet(
    container: AppContainer,
    hint: String,
    initial: String = "",
    allowPictures: Boolean = true,
    submitLabel: String = "发送",
    allowBlankWithPictures: Boolean = false,
    onSubmit: (text: String, pictures: List<UploadedPicture>, atMids: Map<String, Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val nav = LocalNavState.current
    val focusRequester = remember { FocusRequester() }

    var show by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var panel by remember { mutableStateOf(ComposerPanel.None) }
    var packages by remember { mutableStateOf<List<CommentEmotePackage>>(emptyList()) }
    var manageUrl by remember { mutableStateOf(DEFAULT_EMOTE_MANAGE_URL) }
    var emotesFailed by remember { mutableStateOf(false) }
    var followings by remember { mutableStateOf<List<FollowingUser>>(emptyList()) }
    var followingsPage by remember { mutableIntStateOf(0) }
    var followingsHasMore by remember { mutableStateOf(true) }
    var followingsLoading by remember { mutableStateOf(false) }
    var atFailed by remember { mutableStateOf(false) }
    var atMids by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var pictures by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var openedEmoteManager by remember { mutableStateOf(false) }

    val loggedIn = container.session.isLoggedIn()
    val managingEmotes = nav?.stack?.any { it.route is Route.EmoteManager } == true
    val editorInteractionSource = remember { MutableInteractionSource() }
    val editorFocused by editorInteractionSource.collectIsFocusedAsState()
    var wasEditorFocused by remember { mutableStateOf(false) }

    val emoteLookup = remember(packages) { buildEmoteLookup(packages) }

    LaunchedEffect(editorFocused) {
        if (editorFocused && !wasEditorFocused && panel == ComposerPanel.At) {
            panel = ComposerPanel.None
        }
        wasEditorFocused = editorFocused
    }

    fun toast(text: String) {
        message = text
        scope.launch {
            delay(2000)
            if (message == text) message = null
        }
    }

    suspend fun loadEmotes(force: Boolean = false) {
        if (!force && (packages.isNotEmpty() || emotesFailed)) return
        val result = container.comments.emotePanel().getOrElse {
            emotesFailed = true
            return
        }
        packages = result.packages
        manageUrl = result.manageUrl
        emotesFailed = result.packages.isEmpty()
    }

    fun loadMoreFollowings() {
        if (followingsLoading || !followingsHasMore) return
        val mid = container.session.mid()
        if (mid <= 0L) {
            followingsHasMore = false
            atFailed = true
            return
        }
        followingsLoading = true
        scope.launch {
            val nextPage = followingsPage + 1
            container.relations.followings(mid, nextPage, pageSize = 50)
                .onSuccess { (list, hasMore) ->
                    followings = (followings + list).distinctBy { it.mid }
                    followingsPage = nextPage
                    followingsHasMore = hasMore && list.isNotEmpty()
                    atFailed = false
                }
                .onFailure {
                    if (followings.isEmpty()) atFailed = true
                    followingsHasMore = false
                }
            followingsLoading = false
        }
    }

    LaunchedEffect(Unit) {
        show = true
        launch { loadEmotes(force = false) }
        delay(80)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    LaunchedEffect(managingEmotes) {
        if (!managingEmotes && openedEmoteManager) {
            openedEmoteManager = false
            loadEmotes(force = true)
        }
    }

    fun insert(text: String) {
        val value = field
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(start, value.text.length)
        val merged = value.text.replaceRange(start, end, text)
        if (merged.length > MAX_COMMENT_LENGTH) {
            toast("最多 $MAX_COMMENT_LENGTH 字")
            return
        }
        field = TextFieldValue(merged, TextRange(start + text.length))
    }

    fun togglePanel(target: ComposerPanel) {
        if (panel == target) {
            panel = ComposerPanel.None
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            return
        }
        panel = target
        keyboard?.hide()
    }

    fun pickAt(user: FollowingUser) {
        insert("@${user.name} ")
        atMids = atMids + (user.name to user.mid)
        panel = ComposerPanel.None
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    fun openEmoteManager(url: String) {
        if (nav == null) {
            toast("当前页面暂不支持打开表情管理")
            return
        }
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        openedEmoteManager = true
        nav.push(Route.EmoteManager(url))
    }

    val pickImages = rememberPhotoPicker(
        maxSelection = MAX_PICTURES,
    ) { picked ->
        if (picked.isEmpty()) return@rememberPhotoPicker
        val room = (MAX_PICTURES - pictures.size).coerceAtLeast(0)
        if (room == 0) {
            toast("最多 $MAX_PICTURES 张图片")
            return@rememberPhotoPicker
        }
        pictures = pictures + picked.take(room)
    }

    // 面板只在第一次打开时拉数据；未登录服务端不给表情包/关注列表，直接提示。
    LaunchedEffect(panel) {
        when (panel) {
            ComposerPanel.Emote -> loadEmotes(force = false)

            ComposerPanel.At -> {
                if (followings.isEmpty() && !followingsLoading && !atFailed) {
                    loadMoreFollowings()
                }
            }

            ComposerPanel.None -> Unit
        }
    }

    fun submit() {
        val text = field.text.trim()
        if ((text.isEmpty() && !(allowBlankWithPictures && pictures.isNotEmpty())) || uploading) return
        if (pictures.isEmpty()) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            onSubmit(text, emptyList(), atMids)
            return
        }
        uploading = true
        scope.launch {
            val uploaded = uploadCommentPictures(
                context = context,
                comments = container.comments,
                pictures = pictures,
            ).getOrElse { err ->
                uploading = false
                toast(err.message ?: "图片上传失败")
                return@launch
            }
            uploading = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            onSubmit(text, uploaded, atMids)
        }
    }

    val density = LocalDensity.current
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val keyboardGapProgress = (imeBottom / KEYBOARD_GAP_RANGE).coerceIn(0f, 1f)
    val bottomGap = PANEL_BOTTOM_INSET + (KEYBOARD_BOTTOM_INSET - PANEL_BOTTOM_INSET) * keyboardGapProgress

    fun dismiss() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        show = false
    }

    OverlayBottomSheet(
        show = show && !managingEmotes,
        showDragHandle = false,
        insideMargin = DpSize(PANEL_SIDE_INSET, bottomGap),
        panelPaddingBottom = 0.dp,
        defaultWindowInsetsPadding = false,
        onDismissRequest = { dismiss() },
        onDismissFinished = { if (!managingEmotes) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HEADER_ROW_HEIGHT),
            ) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    SheetCloseCapsule(onClick = { dismiss() })
                }
                Text(
                    text = hint,
                    style = COUITheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    color = COUITheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = HEADER_TITLE_SAFE_INSET),
                )
            }
            Spacer(Modifier.height(HEADER_TO_BODY_GAP))

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val activePanelHeight = when (panel) {
                    ComposerPanel.Emote -> {
                        val hasEmotes = packages.any { it.emotes.isNotEmpty() }
                        (if (hasEmotes) EMOTE_PANEL_HEIGHT else EMPTY_PANEL_HEIGHT) + 8.dp
                    }
                    ComposerPanel.At -> {
                        (if (loggedIn) EMOTE_PANEL_HEIGHT else EMPTY_PANEL_HEIGHT) + 8.dp
                    }
                    ComposerPanel.None -> 0.dp
                }
                val belowField = (if (pictures.isNotEmpty()) IMAGES_ROW_HEIGHT else 0.dp) +
                    activePanelHeight +
                    TOOLS_GAP + TOOLS_HEIGHT +
                    ACTION_GAP + ACTION_HEIGHT
                val editorMax = (maxHeight - belowField).coerceAtLeast(MIN_EDITOR_HEIGHT)
                val editorBoxMax = (editorMax - 20.dp).coerceIn(MIN_EDITOR_HEIGHT, 112.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = editorMax)
                        .squircleSurface(
                            color = COUITheme.colorScheme.surfaceContainer,
                            cornerRadius = EDITOR_RADIUS,
                        ),
                ) {
                    ComposerEmoteTextField(
                        value = field,
                        onValueChange = {
                            field = if (it.text.length <= MAX_COMMENT_LENGTH) {
                                it
                            } else {
                                it.copy(text = it.text.take(MAX_COMMENT_LENGTH))
                            }
                            if (panel != ComposerPanel.None) panel = ComposerPanel.None
                        },
                        emotes = emoteLookup,
                        maxHeight = editorBoxMax,
                        focusRequester = focusRequester,
                        interactionSource = editorInteractionSource,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = "${field.text.length}/$MAX_COMMENT_LENGTH",
                            style = trimmedLineHeight,
                            fontSize = 11.sp,
                            lineHeight = 11.sp,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }

            if (pictures.isNotEmpty()) {
                PickedPicturesRow(
                    pictures = pictures,
                    onRemove = { index ->
                        pictures = pictures.filterIndexed { i, _ -> i != index }
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            if (panel == ComposerPanel.Emote) {
                EmoteGrid(
                    packages = packages,
                    failed = emotesFailed,
                    manageUrl = manageUrl,
                    onOpenManage = { openEmoteManager(it) },
                    onPick = { insert(it) },
                )
            } else if (panel == ComposerPanel.At) {
                AtUserPanel(
                    container = container,
                    loggedIn = loggedIn,
                    followings = followings,
                    followingsLoading = followingsLoading,
                    followingsHasMore = followingsHasMore,
                    followingsFailed = atFailed,
                    onLoadMoreFollowings = { loadMoreFollowings() },
                    onPick = { pickAt(it) },
                )
            }

            Spacer(Modifier.height(TOOLS_GAP))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOOLS_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (allowPictures) {
                    ComposerToolButton(
                        active = false,
                        onClick = {
                            if (pictures.size >= MAX_PICTURES) {
                                toast("最多 $MAX_PICTURES 张图片")
                            } else {
                                pickImages()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = COUIIcons.Photos,
                            contentDescription = "图片",
                            modifier = Modifier.size(22.dp),
                            tint = COUITheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                ComposerToolButton(
                    active = panel == ComposerPanel.Emote,
                    onClick = { togglePanel(ComposerPanel.Emote) },
                ) {
                    Icon(
                        imageVector = EmojiIcon,
                        contentDescription = "表情",
                        modifier = Modifier.size(22.dp),
                        tint = if (panel == ComposerPanel.Emote) {
                            COUITheme.colorScheme.primary
                        } else {
                            COUITheme.colorScheme.onSurface
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                ComposerToolButton(
                    active = panel == ComposerPanel.At,
                    onClick = { togglePanel(ComposerPanel.At) },
                ) {
                    Text(
                        text = "@",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (panel == ComposerPanel.At) {
                            COUITheme.colorScheme.primary
                        } else {
                            COUITheme.colorScheme.onSurface
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                message?.let { text ->
                    Text(
                        text = text,
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(ACTION_GAP))
            ActionCapsule(
                text = if (uploading) "上传中…" else submitLabel,
                enabled = (field.text.isNotBlank() || (allowBlankWithPictures && pictures.isNotEmpty())) && !uploading,
                onClick = { submit() },
            )
        }
    }
}

private const val MAX_COMMENT_LENGTH = 1000
private const val MAX_PICTURES = 9
private val PANEL_SIDE_INSET = 12.dp
private val PANEL_BOTTOM_INSET = 30.dp
private val KEYBOARD_BOTTOM_INSET = 4.dp
private val KEYBOARD_GAP_RANGE = 80.dp
private val HEADER_ROW_HEIGHT = 44.dp
private val HEADER_TITLE_SAFE_INSET = 76.dp
private val HEADER_TO_BODY_GAP = 8.dp
private val EDITOR_RADIUS = 20.dp
private val MIN_EDITOR_HEIGHT = 72.dp
private val IMAGES_ROW_HEIGHT = 74.dp
private val TOOLS_HEIGHT = 36.dp
private val TOOLS_GAP = 8.dp
private val ACTION_GAP = 8.dp
