package erl.bilico.feature.video

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.showToast
import erl.bilico.player.SleepTimerController
import erl.bilico.player.SleepTimerMode
import erl.bilico.player.SleepTimerState
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Timer
import io.github.suqi8.coui.kmp.layout.SheetCloseCapsule
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/**
 * 定时关闭设置面板：支持关闭、播完当前视频后停止、预设分钟（15/30/60/90）及自定义 1–480 分钟。
 *
 * 竖屏走 COUI 底部面板；全屏横屏走 [PlayerModalHost] 贴在播放器舞台内。
 */
@Composable
internal fun SleepTimerDialog(
    controller: SleepTimerController,
    onDismiss: () -> Unit,
    inlineInStage: Boolean = false,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    if (!inlineInStage) {
        SleepTimerSheet(controller = controller, state = state, onDismiss = onDismiss)
        return
    }
    PlayerModalHost(
        inlineInStage = true,
        onDismiss = onDismiss,
        horizontalPadding = 12.dp,
        cornerRadius = 24.dp,
    ) {
        val requestDismiss = LocalDismissState.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(vertical = 12.dp)
                .testTag("sleep_timer_dialog"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "定时关闭",
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = COUITheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SleepTimerContent(
                    controller = controller,
                    state = state,
                    onRequestDismiss = requestDismiss,
                )
            }
        }
    }
}

@Composable
private fun SleepTimerSheet(
    controller: SleepTimerController,
    state: SleepTimerState,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayBottomSheet(
        show = show,
        title = null,
        insideMargin = DpSize(12.dp, 0.dp),
        startAction = {
            Box(Modifier.fillMaxWidth()) {
                SheetCloseCapsule(
                    onClick = { show = false },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    text = "定时关闭",
                    style = COUITheme.textStyles.main.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = COUITheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        },
        onDismissRequest = { show = false },
        onDismissFinished = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .imePadding()
                .scrollEndHaptic()
                .testTag("sleep_timer_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "content") {
                SleepTimerContent(
                    controller = controller,
                    state = state,
                    onRequestDismiss = { show = false },
                )
            }
            item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SleepTimerContent(
    controller: SleepTimerController,
    state: SleepTimerState,
    onRequestDismiss: () -> Unit,
) {
    val context = LocalPlatformContext.current
    val scheme = COUITheme.colorScheme
    var customInput by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    val applyCustom: () -> Unit = {
        val minutes = customInput.trim().toIntOrNull()
        if (minutes == null ||
            minutes !in SleepTimerController.MIN_CUSTOM_MINUTES..SleepTimerController.MAX_CUSTOM_MINUTES
        ) {
            customError = "请输入 ${SleepTimerController.MIN_CUSTOM_MINUTES} 到 ${SleepTimerController.MAX_CUSTOM_MINUTES} 之间的分钟数"
        } else {
            customError = null
            controller.startMinutes(minutes)
            showToast(context, "已设置 $minutes 分钟后暂停播放")
            onRequestDismiss()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.isActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(14.dp))
                    .background(scheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = COUIIcons.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.primary,
                )
                Text(
                    text = state.summary,
                    style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    color = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "关闭",
                    style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(SquircleShape(8.dp))
                        .clickable {
                            controller.cancel()
                            showToast(context, "已关闭定时关闭")
                            onRequestDismiss()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(0.dp),
            weight = CardDefaults.GroupWeight,
        ) {
            val activeTimedMinutes = (state.mode as? SleepTimerMode.Timed)?.totalMinutes
            SleepTimerOptionRow(
                title = "关闭定时",
                selected = !state.isActive,
                showDivider = true,
                onClick = {
                    controller.cancel()
                    showToast(context, "已关闭定时关闭")
                    onRequestDismiss()
                },
            )
            SleepTimerOptionRow(
                title = "播完当前视频后停止",
                selected = state.mode == SleepTimerMode.AfterCurrentVideo,
                showDivider = true,
                onClick = {
                    controller.setAfterCurrentVideo()
                    showToast(context, "已设置播完当前视频后停止")
                    onRequestDismiss()
                },
            )
            SleepTimerController.PRESET_MINUTES.forEachIndexed { index, minutes ->
                SleepTimerOptionRow(
                    title = "${minutes}分钟",
                    selected = activeTimedMinutes == minutes,
                    showDivider = index < SleepTimerController.PRESET_MINUTES.lastIndex,
                    onClick = {
                        controller.startMinutes(minutes)
                        showToast(context, "已设置 $minutes 分钟后暂停播放")
                        onRequestDismiss()
                    },
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = CardDefaults.GroupCornerRadius,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            weight = CardDefaults.GroupWeight,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "自定义时长",
                        style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${SleepTimerController.MIN_CUSTOM_MINUTES}–${SleepTimerController.MAX_CUSTOM_MINUTES} 分钟",
                        style = COUITheme.textStyles.footnote2,
                        color = scheme.onSurfaceSecondary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(SquircleShape(12.dp))
                            .background(scheme.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (customInput.isEmpty()) {
                            Text(
                                text = "输入分钟数",
                                style = COUITheme.textStyles.body2,
                                color = scheme.onSurfaceSecondary.copy(alpha = scheme.onSurfaceSecondary.alpha * 0.65f),
                            )
                        }
                        BasicTextField(
                            value = customInput,
                            onValueChange = { raw ->
                                customInput = raw.filter { it.isDigit() }.take(3)
                                customError = null
                            },
                            singleLine = true,
                            textStyle = COUITheme.textStyles.body2.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { applyCustom() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = "分钟",
                        style = COUITheme.textStyles.body2,
                        color = scheme.onSurfaceSecondary,
                    )
                    TextButton(
                        text = "开始",
                        onClick = applyCustom,
                        enabled = customInput.isNotBlank(),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.width(72.dp),
                    )
                }
                if (!customError.isNullOrBlank()) {
                    Text(
                        text = customError.orEmpty(),
                        style = COUITheme.textStyles.footnote2,
                        color = scheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimerOptionRow(
    title: String,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = COUITheme.textStyles.body1,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) scheme.primary else scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = COUIIcons.Ok,
                    contentDescription = "已选中",
                    modifier = Modifier.size(18.dp),
                    tint = scheme.primary,
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(0.5.dp)
                    .background(scheme.onSurface.copy(alpha = 0.08f)),
            )
        }
    }
}
