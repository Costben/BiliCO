package erl.bilico.feature.comment

import erl.bilico.platform.PickedPhotoData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentEmote
import erl.bilico.data.model.CommentEmotePackage
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.components.COUISmoothCapsuleShape
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.inputCapsuleGlass
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.LocalToolbarBackdropBlurFraction
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.ProgressiveBlur
import io.github.suqi8.coui.kmp.blur.isBlurEnabled
import io.github.suqi8.coui.kmp.blur.progressiveTextureBlur
import io.github.suqi8.coui.kmp.theme.COUITheme

private const val TOP_BAR_ENGINE_SIGMA_SCALE = 0.45f

/** 评论详情底部的输入区：回复提示 + 配图预览 + 与私信同款的长胶囊输入框 + 表情面板。 */
@Composable
internal fun ReplyComposer(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    emotes: Map<String, CommentEmote>,
    pictures: List<PickedPhotoData>,
    onRemovePicture: (Int) -> Unit,
    replyTarget: Comment?,
    onClearReplyTarget: () -> Unit,
    isSending: Boolean,
    isDark: Boolean,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    focusTick: Int,
    emotePanelOpen: Boolean,
    onToggleEmote: () -> Unit,
    onPickImage: () -> Unit,
    emotePackages: List<CommentEmotePackage>,
    emotesFailed: Boolean,
    emoteManageUrl: String,
    onOpenEmoteManage: (String) -> Unit,
    onPickEmote: (String) -> Unit,
    onSend: () -> Unit,
    panelBackground: Color,
    bottomPadding: Dp = 10.dp,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalBackdrop.current,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        replyTarget?.let { target ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .inputCapsuleGlass(backdrop = backdrop, isDark = isDark, cornerRadius = null)
                    .clip(COUISmoothCapsuleShape(null))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在回复 @${target.userName}",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "取消",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .clickable(onClick = onClearReplyTarget)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        if (pictures.isNotEmpty()) {
            PickedPicturesRow(
                pictures = pictures,
                onRemove = onRemovePicture,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
        }

        CapsuleComposerBar(
            value = draft,
            onValueChange = onDraftChange,
            emotes = emotes,
            placeholder = if (replyTarget != null) {
                "回复 @${replyTarget.userName} :"
            } else {
                "发一条友善的评论"
            },
            onSend = {
                keyboard?.hide()
                onSend()
            },
            sendEnabled = draft.text.isNotBlank(),
            isSending = isSending,
            emotePanelOpen = emotePanelOpen,
            onToggleEmote = onToggleEmote,
            onPickImage = onPickImage,
            focusRequester = focusRequester,
            interactionSource = interactionSource,
            backdrop = backdrop,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (emotePanelOpen) {
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(bottomPadding))
        }

        InlineEmoteDrawer(
            visible = emotePanelOpen,
            panelBackground = panelBackground,
            packages = emotePackages,
            failed = emotesFailed,
            manageUrl = emoteManageUrl,
            onOpenManage = onOpenEmoteManage,
            onPick = onPickEmote,
            bottomPadding = bottomPadding,
        )
    }
}

/** 评论详情顶栏：静止时不模糊，向上滑动时随滚动进度渐入均匀渐变毛玻璃与顶栏底色遮罩。 */
@Composable
internal fun ReplyDetailTopBar(
    scrollFraction: () -> Float,
    containerColor: Color,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    val isDark = containerColor.luminance() < 0.5f
    val blurEnabled = BlurEffectsEnabled && isBlurEnabled() && backdrop != null
    val density = LocalDensity.current
    val extraHeightPx = with(density) { 14.dp.roundToPx() }
    val engineBlurRadiusDp = remember(density) {
        with(density) { (30f * 0.57735f / TOP_BAR_ENGINE_SIGMA_SCALE).toDp().value }
    }
    val gradientBrush = remember(containerColor, isDark) {
        Brush.verticalGradient(
            0.00f to containerColor.copy(alpha = if (isDark) 0.92f else 0.96f),
            0.52f to containerColor.copy(alpha = if (isDark) 0.80f else 0.88f),
            0.82f to containerColor.copy(alpha = if (isDark) 0.36f else 0.48f),
            1.00f to containerColor.copy(alpha = 0f),
        )
    }
    val materialModifier = Modifier
        .layoutId("material")
        .fillMaxSize()
        .graphicsLayer {
            alpha = scrollFraction().coerceIn(0f, 1f)
            compositingStrategy = CompositingStrategy.Auto
        }
        .then(
            if (blurEnabled) {
                val targetBackdrop = backdrop
                Modifier.progressiveTextureBlur(
                    backdrop = targetBackdrop,
                    shape = RectangleShape,
                    blurRadius = engineBlurRadiusDp,
                    gradient = ProgressiveBlur(
                        angle = 90f,
                        startFraction = 0.52f,
                        endFraction = 1f,
                        curve = 2.0f,
                    ),
                )
            } else {
                Modifier
            },
        )
        .background(gradientBrush)

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box(modifier = materialModifier)
            CompositionLocalProvider(LocalToolbarBackdropBlurFraction provides scrollFraction) {
                Row(
                    modifier = Modifier
                        .layoutId("content")
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = topInset, end = 20.dp)
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "评论详情",
                        style = COUITheme.textStyles.main.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = COUITheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val contentPlaceable = measurables
            .first { it.layoutId == "content" }
            .measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = maxOf(contentPlaceable.width, constraints.minWidth)
        val height = maxOf(contentPlaceable.height, constraints.minHeight)
        val materialPlaceable = measurables
            .firstOrNull { it.layoutId == "material" }
            ?.measure(Constraints.fixed(width = width, height = height + extraHeightPx))
        layout(width, height) {
            materialPlaceable?.placeRelative(0, 0)
            contentPlaceable.placeRelative(0, 0)
        }
    }
}

/** 子评论列表首屏加载/错误/空态占位框。 */
@Composable
internal fun SubReplyPlaceholderBox(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    when {
        isLoading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(size = 22.dp)
        }
        error != null -> ErrorState(
            message = error,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            onRetry = onRetry,
        )
        else -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState("暂时还没有相关回复")
        }
    }
}
