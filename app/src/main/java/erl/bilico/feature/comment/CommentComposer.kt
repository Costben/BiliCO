package erl.bilico.feature.comment

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.CommentEmotePackage
import erl.bilico.data.model.FollowingUser
import erl.bilico.data.model.UploadedPicture
import erl.bilico.di.AppContainer
import erl.bilico.ui.components.EmojiIcon
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonSize
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Image
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ComposerPanel { None, Emote, At }

/** 选了但还没上传的图：真正读字节放到发送那一刻，免得一堆原图一直占内存。 */
private data class PickedImage(val uri: Uri)

/**
 * 发表评论 / 回复的输入面板：底部升起，顶栏只有一个拖动条，不再占一整块大标题。
 * 表情、配图、字数、发送都在同一行工具栏里，键盘和表情面板互斥。
 */
@Composable
internal fun CommentComposerSheet(
    container: AppContainer,
    hint: String,
    initial: String = "",
    allowPictures: Boolean = true,
    onSubmit: (text: String, pictures: List<UploadedPicture>, atMids: Map<String, Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var show by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var panel by remember { mutableStateOf(ComposerPanel.None) }
    var packages by remember { mutableStateOf<List<CommentEmotePackage>>(emptyList()) }
    var emotesFailed by remember { mutableStateOf(false) }
    var followings by remember { mutableStateOf<List<FollowingUser>>(emptyList()) }
    var atFailed by remember { mutableStateOf(false) }
    var atMids by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var pictures by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { show = true }

    fun toast(text: String) {
        message = text
        scope.launch {
            delay(2000)
            if (message == text) message = null
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
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICTURES),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val room = (MAX_PICTURES - pictures.size).coerceAtLeast(0)
        if (room == 0) {
            toast("最多 $MAX_PICTURES 张图片")
            return@rememberLauncherForActivityResult
        }
        pictures = pictures + uris.take(room).map { PickedImage(it) }
    }

    // 面板只在第一次打开时拉数据；未登录服务端不给表情包/关注列表，直接提示。
    LaunchedEffect(panel) {
        when (panel) {
            ComposerPanel.Emote -> {
                if (packages.isNotEmpty() || emotesFailed) return@LaunchedEffect
                packages = container.comments.emotePanel().getOrElse {
                    emotesFailed = true
                    emptyList()
                }
                if (packages.isEmpty()) emotesFailed = true
            }

            ComposerPanel.At -> {
                if (followings.isNotEmpty() || atFailed) return@LaunchedEffect
                followings = container.relations.followings(container.session.mid(), 1)
                    .getOrElse {
                        atFailed = true
                        null
                    }
                    ?.first
                    .orEmpty()
                if (followings.isEmpty()) atFailed = true
            }

            ComposerPanel.None -> Unit
        }
    }

    fun submit() {
        val text = field.text.trim()
        if (text.isEmpty() || uploading) return
        if (pictures.isEmpty()) {
            onSubmit(text, emptyList(), atMids)
            return
        }
        uploading = true
        scope.launch {
            val uploaded = ArrayList<UploadedPicture>(pictures.size)
            for ((index, picture) in pictures.withIndex()) {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(picture.uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null) {
                    uploading = false
                    toast("读取第 ${index + 1} 张图片失败")
                    return@launch
                }
                val mime = context.contentResolver.getType(picture.uri) ?: "image/jpeg"
                val name = "comment_${System.currentTimeMillis()}_$index." + mime.substringAfterLast('/', "jpg")
                val result = container.comments.uploadPicture(bytes, name, mime)
                val pictureUrl = result.getOrElse {
                    uploading = false
                    toast(it.message ?: "图片上传失败")
                    return@launch
                }
                uploaded += pictureUrl
            }
            uploading = false
            onSubmit(text, uploaded, atMids)
        }
    }

    OverlayBottomSheet(
        show = show,
        title = null,
        showDragHandle = true,
        onDismissRequest = { show = false },
        onDismissFinished = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            TextField(
                value = field,
                onValueChange = {
                    field = if (it.text.length <= MAX_COMMENT_LENGTH) {
                        it
                    } else {
                        it.copy(text = it.text.take(MAX_COMMENT_LENGTH))
                    }
                    if (panel != ComposerPanel.None) panel = ComposerPanel.None
                },
                modifier = Modifier.fillMaxWidth(),
                label = hint,
                backgroundMode = TextFieldMode.Rectangle,
                minLines = 3,
                maxLines = 6,
            )

            if (pictures.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pictures.size) { index ->
                        Box {
                            AsyncImage(
                                model = pictures[index].uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(COUITheme.colorScheme.surface.copy(alpha = 0.8f))
                                    .clickable {
                                        pictures = pictures.filterIndexed { i, _ -> i != index }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = COUIIcons.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(14.dp),
                                    tint = COUITheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerToolButton(
                    active = panel == ComposerPanel.Emote,
                    onClick = { togglePanel(ComposerPanel.Emote) },
                ) {
                    Icon(
                        imageVector = EmojiIcon,
                        contentDescription = "表情",
                        modifier = Modifier.size(22.dp),
                        tint = COUITheme.colorScheme.onSurface,
                    )
                }
                ComposerToolButton(
                    active = panel == ComposerPanel.At,
                    onClick = { togglePanel(ComposerPanel.At) },
                ) {
                    Text(
                        text = "@",
                        fontSize = 20.sp,
                        color = COUITheme.colorScheme.onSurface,
                    )
                }
                if (allowPictures) {
                    ComposerToolButton(
                        onClick = {
                            if (pictures.size >= MAX_PICTURES) {
                                toast("最多 $MAX_PICTURES 张图片")
                            } else {
                                pickImages.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                        },
                    ) {
                        Icon(
                            imageVector = COUIIcons.Image,
                            contentDescription = "图片",
                            modifier = Modifier.size(22.dp),
                            tint = COUITheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${field.text.length}/$MAX_COMMENT_LENGTH",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Button(
                    onClick = { submit() },
                    enabled = field.text.isNotBlank() && !uploading,
                    size = ButtonSize.Small,
                ) {
                    if (uploading) {
                        CircularProgressIndicator(size = 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("上传中", modifier = Modifier.padding(end = 2.dp))
                    } else {
                        Text("发送", modifier = Modifier.padding(end = 2.dp))
                    }
                }
            }

            if (panel == ComposerPanel.Emote) {
                EmoteGrid(
                    packages = packages,
                    failed = emotesFailed,
                    onPick = { insert(it) },
                )
            } else if (panel == ComposerPanel.At) {
                AtList(
                    users = followings,
                    failed = atFailed,
                    onPick = { pickAt(it) },
                )
            }

            message?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmoteGrid(
    packages: List<CommentEmotePackage>,
    failed: Boolean,
    onPick: (String) -> Unit,
) {
    val emotes = remember(packages) { packages.flatMap { it.emotes } }
    // 没拿到表情时不要撑满整块高度，否则面板里会留一大片空白。
    val showGrid = emotes.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showGrid) EMOTE_PANEL_HEIGHT else EMPTY_PANEL_HEIGHT)
            .padding(top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            showGrid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(44.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(emotes) { emote ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(emote.insert) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (emote.url.isBlank()) {
                            Text(
                                text = emote.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = COUITheme.colorScheme.onSurface,
                            )
                        } else {
                            AsyncImage(
                                model = emote.url,
                                contentDescription = emote.text,
                                modifier = Modifier.size(if (emote.large) 38.dp else 26.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }

            failed -> Text(
                text = "登录后才能使用表情",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )

            else -> CircularProgressIndicator(size = 18.dp)
        }
    }
}

@Composable
private fun AtList(
    users: List<FollowingUser>,
    failed: Boolean,
    onPick: (FollowingUser) -> Unit,
) {
    // 和表情面板一样：没有数据就收起高度，别在面板里留一大片空白。
    val showList = users.isNotEmpty()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showList) EMOTE_PANEL_HEIGHT else EMPTY_PANEL_HEIGHT)
            .padding(top = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            showList -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(users.size) { index ->
                    val user = users[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(user) }
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = user.face,
                            contentDescription = null,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = user.name,
                            style = COUITheme.textStyles.body2,
                            maxLines = 1,
                            color = COUITheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            failed -> Text(
                text = "登录后才能 @ 关注的人",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )

            else -> CircularProgressIndicator(size = 18.dp)
        }
    }
}

@Composable
private fun ComposerToolButton(
    active: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (active) COUITheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val MAX_COMMENT_LENGTH = 1000
private const val MAX_PICTURES = 9
private val EMOTE_PANEL_HEIGHT = 212.dp
private val EMPTY_PANEL_HEIGHT = 56.dp
