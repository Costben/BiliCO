package erl.bilico.feature.video

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.R
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 播放页标题栏下方的一条：左边「简介 / 评论」分段，右边弹幕输入胶囊。
 * 胶囊右端那个半圆里放弹幕开关，图标取自 PiliPlus 的 dm_on / dm_off 字形。
 */
@Composable
internal fun VideoDetailBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    composing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onComposingChange: (Boolean) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(composing) {
        if (composing) focusRequester.requestFocus()
    }

    BackHandler(enabled = composing) {
        keyboard?.hide()
        onComposingChange(false)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!composing) {
            DETAIL_TABS.forEachIndexed { index, label ->
                DetailTab(
                    label = label,
                    active = selected == index,
                    onClick = { onSelect(index) },
                )
            }
            Spacer(Modifier.weight(1f))
        }

        DanmakuCapsule(
            composing = composing,
            draft = draft,
            onDraftChange = onDraftChange,
            danmakuEnabled = danmakuEnabled,
            onToggleDanmaku = onToggleDanmaku,
            onStartComposing = { onComposingChange(true) },
            onSend = {
                keyboard?.hide()
                onSend()
            },
            focusRequester = focusRequester,
            modifier = if (composing) Modifier.weight(1f) else Modifier,
        )

        if (composing) {
            val enabled = draft.isNotBlank()
            Text(
                text = "发送",
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) {
                        keyboard?.hide()
                        onSend()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = COUITheme.textStyles.body1,
                color = if (enabled) {
                    COUITheme.colorScheme.primary
                } else {
                    COUITheme.colorScheme.onSurfaceSecondary
                },
            )
        }
    }
}

@Composable
private fun DetailTab(label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.body1,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) {
                COUITheme.colorScheme.onSurface
            } else {
                COUITheme.colorScheme.onSurfaceSecondary
            },
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (active) COUITheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun DanmakuCapsule(
    composing: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    onStartComposing: () -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val placeholderStyle = COUITheme.textStyles.footnote1.copy(
        color = scheme.onSurfaceSecondary,
    )

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainer)
            .then(
                if (composing) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStartComposing,
                    )
                },
            )
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (composing) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = COUITheme.textStyles.footnote1.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (draft.isEmpty()) {
                            Text(
                                text = "发个友善的弹幕",
                                style = placeholderStyle,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
        } else {
            Text(
                text = "发个友善的弹幕",
                style = placeholderStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .width(1.dp)
                .height(14.dp)
                .background(scheme.dividerLine),
        )

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleDanmaku,
                )
                .semantics {
                    contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (danmakuEnabled) "\uE803" else "\uE802",
                fontFamily = DanmakuIconFamily,
                fontSize = 18.sp,
                lineHeight = 18.sp,
                color = if (danmakuEnabled) scheme.primary else scheme.onSurfaceSecondary,
            )
        }
    }
}

private val DanmakuIconFamily = FontFamily(Font(R.font.piliplus_custom_icons))

private val DETAIL_TABS = listOf("简介", "评论")
