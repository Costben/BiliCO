package erl.bilico.feature.comment

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import erl.bilico.data.model.CommentEmote
import erl.bilico.data.model.CommentEmotePackage
import erl.bilico.data.model.UploadedPicture
import erl.bilico.data.repository.CommentRepository
import erl.bilico.ui.components.CompactSendButton
import erl.bilico.ui.components.ComposerCapsuleShape
import erl.bilico.ui.components.EmojiIcon
import erl.bilico.ui.components.inputCapsuleGlass
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.isSquircleEnabled
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val CAPSULE_COMPOSER_HEIGHT = 36.dp
internal val CAPSULE_COMPOSER_MAX_HEIGHT = 108.dp
private val CAPSULE_COMPOSER_MULTI_RADIUS = 18.dp

/**
 * 底部胶囊输入栏（私信与评论回复共用）：
 * 左侧为长胶囊（单行自动使用全圆角真胶囊，多行固定 18dp 圆角），内含支持表情图标内联渲染的输入框及表情/图片按钮；
 * 右侧为同高度胶囊「发送」按钮。
 */
@Composable
internal fun CapsuleComposerBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    emotes: Map<String, CommentEmote>,
    placeholder: String,
    onSend: () -> Unit,
    sendEnabled: Boolean,
    isSending: Boolean,
    emotePanelOpen: Boolean,
    onToggleEmote: () -> Unit,
    onPickImage: () -> Unit,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val squircleEnabled = isSquircleEnabled()
    val capsuleShape = remember(squircleEnabled) {
        ComposerCapsuleShape(
            maxCapsuleHeight = 44.dp,
            multiLineRadius = CAPSULE_COMPOSER_MULTI_RADIUS,
            squircleEnabled = squircleEnabled,
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = CAPSULE_COMPOSER_HEIGHT)
                .inputCapsuleGlass(
                    backdrop = backdrop,
                    isDark = isDark,
                    shape = capsuleShape,
                )
                .clip(capsuleShape),
        ) {
            ComposerEmoteTextField(
                value = value,
                onValueChange = onValueChange,
                emotes = emotes,
                maxHeight = CAPSULE_COMPOSER_MAX_HEIGHT,
                focusRequester = focusRequester,
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                label = placeholder,
                minLines = 1,
                insideMargin = DpSize(14.dp, 6.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
                compact = true,
            )

            Row(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(bottom = 3.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerToolButton(
                    active = emotePanelOpen,
                    size = 30.dp,
                    cornerRadius = 15.dp,
                    onClick = onToggleEmote,
                ) {
                    Icon(
                        imageVector = EmojiIcon,
                        contentDescription = "表情",
                        modifier = Modifier.size(20.dp),
                        tint = if (emotePanelOpen) scheme.primary else scheme.onSurface,
                    )
                }

                Spacer(Modifier.width(2.dp))

                ComposerToolButton(
                    active = false,
                    size = 30.dp,
                    cornerRadius = 15.dp,
                    onClick = onPickImage,
                ) {
                    Icon(
                        imageVector = COUIIcons.Photos,
                        contentDescription = "图片",
                        modifier = Modifier.size(20.dp),
                        tint = scheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        CompactSendButton(
            text = if (isSending) "发送中" else "发送",
            onClick = onSend,
            enabled = sendEnabled && !isSending,
            height = CAPSULE_COMPOSER_HEIGHT,
            containerColor = scheme.surfaceContainer,
            isDark = isDark,
            backdrop = backdrop,
        )
    }
}

/** 从相册 Uri 逐张读出字节并上传到 B 站评论图床；任一张失败立即中止并返回错误。 */
internal suspend fun uploadCommentPictures(
    context: Context,
    comments: CommentRepository,
    uris: List<Uri>,
    prefix: String = "comment",
): Result<List<UploadedPicture>> = runCatching {
    val uploaded = ArrayList<UploadedPicture>(uris.size)
    for ((index, uri) in uris.withIndex()) {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        } ?: error("读取第 ${index + 1} 张图片失败")
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val name = "${prefix}_${System.currentTimeMillis()}_$index." + mime.substringAfterLast('/', "jpg")
        uploaded += comments.uploadPicture(bytes, name, mime).getOrThrow()
    }
    uploaded
}

/** 待发送配图的横向预览条，每张图右上角带移除按钮。 */
@Composable
internal fun PickedPicturesRow(
    uris: List<Uri>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uris.size) { index ->
            Box {
                AsyncImage(
                    model = uris[index],
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(SquircleShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(COUITheme.colorScheme.surface.copy(alpha = 0.8f))
                        .clickable { onRemove(index) },
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

/** 收集可渲染成图标的表情索引表（颜文字 `type == 4` 无图片，保持纯文本）。 */
internal fun buildEmoteLookup(packages: List<CommentEmotePackage>): Map<String, CommentEmote> =
    buildMap {
        packages.forEach { pkg ->
            if (pkg.type != 4) {
                pkg.emotes.forEach { emote ->
                    if (emote.url.isNotBlank() && emote.insert.isNotBlank()) {
                        putIfAbsent(emote.insert, emote)
                    }
                }
            }
        }
    }

/** 内嵌在页面底部的表情抽屉容器：补全不透明页面底色与顶部分隔线，避免透出下方列表。 */
@Composable
internal fun InlineEmoteDrawer(
    visible: Boolean,
    panelBackground: Color,
    packages: List<CommentEmotePackage>,
    failed: Boolean,
    manageUrl: String,
    onOpenManage: (String) -> Unit,
    onPick: (String) -> Unit,
    bottomPadding: Dp = 8.dp,
) {
    AnimatedVisibility(visible = visible) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(panelBackground)
                // 吞掉面板空白处点击，避免穿透到底层列表项。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = COUITheme.colorScheme.dividerLine,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = bottomPadding),
            ) {
                EmoteGrid(
                    packages = packages,
                    failed = failed,
                    manageUrl = manageUrl,
                    onOpenManage = onOpenManage,
                    onPick = onPick,
                )
            }
        }
    }
}

/** 在当前选区插入文本/表情，超出上限时返回 null。 */
internal fun insertTokenInDraft(
    current: TextFieldValue,
    token: String,
    maxLength: Int = Int.MAX_VALUE,
): TextFieldValue? {
    val start = current.selection.min.coerceIn(0, current.text.length)
    val end = current.selection.max.coerceIn(start, current.text.length)
    val merged = current.text.replaceRange(start, end, token)
    if (merged.length > maxLength) return null
    return TextFieldValue(merged, TextRange(start + token.length))
}
