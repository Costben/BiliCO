package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.BuildConfig
import erl.bilico.data.model.SponsorActionType
import erl.bilico.data.model.SponsorCategory
import erl.bilico.ui.components.COUISmoothCapsuleShape
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

private val SubmitCategories = SponsorCategory.entries.filter { it.isSubmitSupported }
private val SubmitActions = listOf(SponsorActionType.Skip, SponsorActionType.Mute)

/**
 * 空降助手片段提交弹窗：竖屏走 OverlayDialog，全屏横屏直接嵌入播放器舞台。
 * 草稿（起止点/分类/动作）由 [SponsorBlockController] 跨开关弹窗暂存，方便播放到片尾或广告结束再补设终点。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SponsorSubmitDialog(
    bvid: String,
    cid: Long,
    durationMs: Long,
    currentPositionMs: () -> Long,
    controller: SponsorBlockController,
    sponsorUserIdProvider: () -> String,
    inlineInStage: Boolean,
    onSuccess: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val draft = controller.submitDraft
    var startText by remember(bvid, cid) {
        mutableStateOf(draft.startMs?.let(::formatMillisPrecise).orEmpty())
    }
    var endText by remember(bvid, cid) {
        mutableStateOf(draft.endMs?.let(::formatMillisPrecise).orEmpty())
    }
    var category by remember(bvid, cid) { mutableStateOf(draft.category) }
    var actionType by remember(bvid, cid) { mutableStateOf(draft.actionType) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isPoi = category == SponsorCategory.PoiHighlight
    val effectiveAction = if (isPoi) SponsorActionType.Poi else actionType
    val syncDraft: () -> Unit = {
        controller.updateSubmitDraft(
            SponsorSubmitDraft(
                startMs = parseTimeToMs(startText),
                endMs = parseTimeToMs(endText),
                category = category,
                actionType = effectiveAction,
            ),
        )
    }

    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = {
            if (!submitting) {
                syncDraft()
                onDismiss()
            }
        },
        horizontalPadding = if (inlineInStage) 12.dp else 24.dp,
        cornerRadius = 24.dp,
        maxWidth = 440.dp,
    ) {
        val scheme = COUITheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "提交空降片段",
                    modifier = Modifier.align(Alignment.Center),
                    style = COUITheme.textStyles.main.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
                HighlightedIconButton(
                    onClick = {
                        if (!submitting) {
                            syncDraft()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(COUIIcons.Close, "关闭", Modifier.size(24.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (inlineInStage) 220.dp else 440.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TimeFieldRow(
                    label = if (isPoi) "时间点" else "开始时间",
                    value = startText,
                    onValueChange = {
                        startText = it
                        errorText = null
                        syncDraft()
                    },
                    onNudge = { deltaMs ->
                        startText = nudgeTime(startText, deltaMs, currentPositionMs(), durationMs)
                        errorText = null
                        syncDraft()
                    },
                    onSetCurrent = {
                        val pos = currentPositionMs().coerceAtLeast(0L)
                        startText = formatMillisPrecise(pos)
                        errorText = null
                        syncDraft()
                    },
                )

                if (!isPoi) {
                    TimeFieldRow(
                        label = "结束时间",
                        value = endText,
                        onValueChange = {
                            endText = it
                            errorText = null
                            syncDraft()
                        },
                        onNudge = { deltaMs ->
                            endText = nudgeTime(endText, deltaMs, currentPositionMs(), durationMs)
                            errorText = null
                            syncDraft()
                        },
                        onSetCurrent = {
                            val pos = currentPositionMs().coerceAtLeast(0L)
                            endText = formatMillisPrecise(pos)
                            errorText = null
                            syncDraft()
                        },
                    )
                }

                val parsedStart = parseTimeToMs(startText)
                val parsedEnd = parseTimeToMs(endText)
                if (!isPoi && parsedStart != null && parsedEnd != null && parsedEnd > parsedStart) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "片段时长 %.1f 秒（格式支持 MM:SS 或秒数）",
                            (parsedEnd - parsedStart) / 1000.0,
                        ),
                        style = COUITheme.textStyles.footnote2,
                        color = scheme.onSurfaceSecondary,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "片段类型",
                        style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                        color = scheme.onSurfaceSecondary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SubmitCategories.forEach { cat ->
                            val selected = cat == category
                            SelectCapsule(
                                label = cat.label,
                                dotColor = Color(cat.colorArgb),
                                selected = selected,
                                onClick = {
                                    category = cat
                                    actionType = if (cat == SponsorCategory.PoiHighlight) {
                                        SponsorActionType.Poi
                                    } else if (actionType == SponsorActionType.Poi) {
                                        SponsorActionType.Skip
                                    } else {
                                        actionType
                                    }
                                    errorText = null
                                    syncDraft()
                                },
                            )
                        }
                    }
                }

                if (!isPoi) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "处理方式",
                            style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                            color = scheme.onSurfaceSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubmitActions.forEach { act ->
                                SelectCapsule(
                                    label = act.label,
                                    dotColor = null,
                                    selected = actionType == act,
                                    onClick = {
                                        actionType = act
                                        errorText = null
                                        syncDraft()
                                    },
                                )
                            }
                        }
                    }
                }

                errorText?.let { err ->
                    Text(
                        text = err,
                        style = COUITheme.textStyles.footnote1,
                        color = scheme.error,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(COUISmoothCapsuleShape)
                        .clickable(enabled = !submitting) {
                            startText = ""
                            endText = ""
                            errorText = null
                            controller.clearSubmitDraft()
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = "清空",
                        style = COUITheme.textStyles.body2,
                        color = scheme.onSurfaceSecondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(COUISmoothCapsuleShape)
                        .background(if (submitting) scheme.primary.copy(alpha = 0.55f) else scheme.primary)
                        .clickable(enabled = !submitting) {
                            val sMs = parseTimeToMs(startText)
                            val eMs = if (isPoi) sMs else parseTimeToMs(endText)
                            val validationError = validateSubmitInput(
                                startMs = sMs,
                                endMs = eMs,
                                durationMs = durationMs,
                                isPoi = isPoi,
                            )
                            if (validationError != null) {
                                errorText = validationError
                                return@clickable
                            }
                            submitting = true
                            errorText = null
                            val resolvedStart = sMs!!
                            val resolvedEnd = eMs!!
                            scope.launch {
                                val res = controller.submitSegment(
                                    bvid = bvid,
                                    cid = cid,
                                    videoDurationMs = durationMs,
                                    startMs = resolvedStart,
                                    endMs = resolvedEnd,
                                    category = category,
                                    actionType = effectiveAction,
                                    userId = sponsorUserIdProvider(),
                                    userAgent = "BiliCO/${BuildConfig.VERSION_NAME}",
                                    currentPosMs = currentPositionMs(),
                                )
                                submitting = false
                                res.onSuccess {
                                    onSuccess("空降片段已提交，感谢贡献")
                                }.onFailure { err ->
                                    errorText = err.message ?: "提交失败"
                                }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = if (submitting) "提交中..." else "提交",
                        style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                        color = scheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onNudge: (Long) -> Unit,
    onSetCurrent: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(56.dp),
            style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
            color = scheme.onSurfaceSecondary,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(SquircleShape(10.dp))
                .background(scheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "00:00.0",
                    style = COUITheme.textStyles.body2,
                    color = scheme.onSurfaceSecondary.copy(alpha = 0.6f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = COUITheme.textStyles.body2.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SmallActionChip("-1s") { onNudge(-1_000L) }
        SmallActionChip("+1s") { onNudge(1_000L) }
        SmallActionChip("当前", highlight = true, onClick = onSetCurrent)
    }
}

@Composable
private fun SmallActionChip(
    text: String,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .clip(COUISmoothCapsuleShape)
            .background(if (highlight) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = COUITheme.textStyles.footnote2.copy(fontWeight = FontWeight.Medium),
            color = if (highlight) scheme.primary else scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectCapsule(
    label: String,
    dotColor: Color?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val bg = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceContainer
    val borderColor = if (selected) scheme.primary else Color.Transparent
    Row(
        modifier = Modifier
            .clip(COUISmoothCapsuleShape)
            .background(bg)
            .border(1.dp, borderColor, COUISmoothCapsuleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dotColor != null) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            text = label,
            style = COUITheme.textStyles.footnote1.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun nudgeTime(raw: String, deltaMs: Long, currentPosMs: Long, durationMs: Long): String {
    val base = parseTimeToMs(raw) ?: currentPosMs.coerceAtLeast(0L)
    val maxMs = if (durationMs > 0L) durationMs else Long.MAX_VALUE
    return formatMillisPrecise((base + deltaMs).coerceIn(0L, maxMs))
}

private fun validateSubmitInput(
    startMs: Long?,
    endMs: Long?,
    durationMs: Long,
    isPoi: Boolean,
): String? {
    if (startMs == null || startMs < 0L) return "请填写有效的开始时间（如 01:23.5 或 83.5）"
    if (durationMs > 0L && startMs > durationMs) return "开始时间不能超过视频总时长"
    if (isPoi) return null
    if (endMs == null || endMs <= startMs) return "结束时间必须大于开始时间"
    if (endMs - startMs < 500L) return "片段时长至少为 0.5 秒"
    if (durationMs > 0L && endMs > durationMs) return "结束时间不能超过视频总时长"
    return null
}

private fun formatMillisPrecise(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val tenths = (safe % 1000L) / 100L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) {
        String.format(Locale.US, "%d:%02d:%02d.%d", h, m, s, tenths)
    } else {
        String.format(Locale.US, "%02d:%02d.%d", m, s, tenths)
    }
}

private fun parseTimeToMs(raw: String): Long? {
    val text = raw.trim().replace('：', ':')
    if (text.isEmpty()) return null
    val parts = text.split(':')
    return when (parts.size) {
        1 -> {
            val sec = parts[0].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
            (sec * 1000.0).roundToLong()
        }
        2 -> {
            val min = parts[0].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val sec = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it < 60.0 } ?: return null
            min * 60_000L + (sec * 1000.0).roundToLong()
        }
        3 -> {
            val hour = parts[0].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val min = parts[1].toLongOrNull()?.takeIf { it in 0L..59L } ?: return null
            val sec = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 && it < 60.0 } ?: return null
            hour * 3_600_000L + min * 60_000L + (sec * 1000.0).roundToLong()
        }
        else -> null
    }
}
