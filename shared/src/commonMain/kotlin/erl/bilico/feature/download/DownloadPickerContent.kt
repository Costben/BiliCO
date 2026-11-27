package erl.bilico.feature.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.DownloadStatus
import erl.bilico.data.model.QualityOption
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoPart
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.video.PlayerModalHost
import erl.bilico.ui.components.COUISmoothCapsuleShape
import erl.bilico.ui.nestedBlockColor
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

internal val DownloadMorphEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

internal data class DownloadPickEntry(
    val id: Long,
    val title: String,
    val durationSec: Long,
    val status: DownloadStatus?,
) {
    val selectable: Boolean
        get() = status == null || status == DownloadStatus.FAILED
}

internal fun buildVideoPickEntries(
    container: AppContainer,
    detail: VideoDetail,
): List<DownloadPickEntry> {
    val pages = detail.pages.ifEmpty {
        listOf(VideoPart(detail.cid, 1, detail.title, detail.durationSec.toLong()))
    }
    return pages.map { part ->
        DownloadPickEntry(
            id = part.cid,
            title = if (pages.size > 1) {
                "P${part.index} ${part.title.ifBlank { "第 ${part.index} P" }}"
            } else {
                detail.title
            },
            durationSec = part.durationSec,
            status = container.downloads.statusForVideo(detail.bvid, part.cid),
        )
    }
}

@Composable
internal fun DownloadPickerHeader(
    title: String,
    subtitle: String,
    onOpenDownloads: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = COUITheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            AnimatedVisibility(
                visible = subtitle.isNotBlank(),
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(240, easing = DownloadMorphEasing)),
            ) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    style = COUITheme.textStyles.footnote2,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onOpenDownloads != null) {
            Text(
                text = "缓存管理",
                modifier = Modifier
                    .clip(COUISmoothCapsuleShape)
                    .clickable(onClick = onOpenDownloads)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = COUITheme.textStyles.footnote1,
                color = scheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(COUISmoothCapsuleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(COUIIcons.Close, "关闭", Modifier.size(20.dp), tint = scheme.onSurfaceSecondary)
        }
    }
}

@Composable
internal fun DownloadPickerBody(
    title: String,
    subtitle: String,
    qualities: List<QualityOption>,
    initialQuality: Int,
    entries: List<DownloadPickEntry>,
    initialSelectedId: Long,
    inlineInStage: Boolean,
    onOpenDownloads: (() -> Unit)?,
    onConfirm: (quality: Int, qualityLabel: String, selectedIds: Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingOpenDownloads by remember { mutableStateOf(false) }
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = {
            val openDownloads = pendingOpenDownloads
            pendingOpenDownloads = false
            onDismiss()
            if (openDownloads) onOpenDownloads?.invoke()
        },
        maxWidth = 440.dp,
    ) {
        val dismiss = LocalDismissState.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            DownloadPickerHeader(
                title = title,
                subtitle = subtitle,
                onOpenDownloads = onOpenDownloads?.let {
                    {
                        pendingOpenDownloads = true
                        dismiss()
                    }
                },
                onDismiss = dismiss,
            )
            DownloadPickerContent(
                qualities = qualities,
                initialQuality = initialQuality,
                entries = entries,
                initialSelectedId = initialSelectedId,
                inlineInStage = inlineInStage,
                onConfirm = onConfirm,
                onDismiss = dismiss,
            )
        }
    }
}

@Composable
internal fun DownloadPickerContent(
    qualities: List<QualityOption>,
    initialQuality: Int,
    entries: List<DownloadPickEntry>,
    initialSelectedId: Long,
    inlineInStage: Boolean,
    onConfirm: (quality: Int, qualityLabel: String, selectedIds: Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val defaultPickedQn = remember(qualities, initialQuality) {
        qualities.firstOrNull { it.quality == initialQuality }?.quality
            ?: qualities.firstOrNull { it.quality <= initialQuality }?.quality
            ?: qualities.firstOrNull()?.quality
            ?: DefaultQuality.P1080.id
    }
    var pickedQuality by remember(defaultPickedQn) { mutableIntStateOf(defaultPickedQn) }
    var selectedIds by remember(entries, initialSelectedId) {
        val initial = entries.firstOrNull { it.id == initialSelectedId && it.selectable }?.id
            ?: entries.firstOrNull { it.selectable }?.id
        mutableStateOf(if (initial != null) setOf(initial) else emptySet())
    }
    val selectableIds = remember(entries) { entries.filter { it.selectable }.map { it.id }.toSet() }
    val allSelected = selectableIds.isNotEmpty() && selectedIds.containsAll(selectableIds)

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "清晰度",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = COUITheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            qualities.forEach { option ->
                val selected = option.quality == pickedQuality
                val label = option.label.ifBlank { "${option.quality}P" }
                Text(
                    text = label,
                    modifier = Modifier
                        .clip(COUISmoothCapsuleShape)
                        .background(if (selected) scheme.primary.copy(alpha = 0.14f) else scheme.surfaceContainer)
                        .then(
                            if (selected) Modifier.border(1.dp, scheme.primary.copy(alpha = 0.55f), COUISmoothCapsuleShape)
                            else Modifier,
                        )
                        .clickable { pickedQuality = option.quality }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) scheme.primary else scheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (entries.size > 1) "选择选集（${entries.size}）" else "缓存内容",
                modifier = Modifier.weight(1f),
                style = COUITheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceSecondary,
            )
            if (entries.size > 1 && selectableIds.isNotEmpty()) {
                Text(
                    text = if (allSelected) "取消全选" else "全选",
                    modifier = Modifier
                        .clip(COUISmoothCapsuleShape)
                        .clickable {
                            selectedIds = if (allSelected) emptySet() else selectableIds
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = scheme.primary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (inlineInStage) 180.dp else 280.dp)
                .scrollEndHaptic()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEach { entry ->
                val checked = entry.id in selectedIds
                val rowShape = SquircleShape(10.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(if (checked) scheme.primary.copy(alpha = 0.10f) else nestedBlockColor())
                        .clickable(enabled = entry.selectable) {
                            selectedIds = if (checked) selectedIds - entry.id else selectedIds + entry.id
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entries.size > 1) {
                        Checkbox(
                            state = if (checked || !entry.selectable && entry.status == DownloadStatus.COMPLETED) {
                                ToggleableState.On
                            } else {
                                ToggleableState.Off
                            },
                            onClick = if (entry.selectable) {
                                { selectedIds = if (checked) selectedIds - entry.id else selectedIds + entry.id }
                            } else {
                                null
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = entry.title,
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.body2,
                        color = if (entry.selectable) scheme.onSurface else scheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val badge = entry.status?.statusLabel()
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = badge,
                            style = COUITheme.textStyles.footnote2,
                            color = if (entry.status == DownloadStatus.COMPLETED) scheme.primary else scheme.onSurfaceSecondary,
                        )
                    } else if (entry.durationSec > 0L) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Formatters.formatDuration(entry.durationSec),
                            style = COUITheme.textStyles.footnote2,
                            color = scheme.onSurfaceSecondary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        val confirmLabel = when {
            selectableIds.isEmpty() -> "已全部缓存"
            selectedIds.size > 1 -> "缓存选中 (${selectedIds.size})"
            else -> "开始缓存"
        }
        DialogButtonBar(
            negative = DialogButtonBarAction(text = "取消", onClick = onDismiss),
            positive = DialogButtonBarAction(
                text = confirmLabel,
                enabled = selectedIds.isNotEmpty(),
                recommended = true,
                onClick = {
                    val qLabel = qualities.firstOrNull { it.quality == pickedQuality }?.label
                        ?: DefaultQuality.fromId(pickedQuality).label
                    onConfirm(pickedQuality, qLabel, selectedIds)
                    onDismiss()
                },
            ),
        )
    }
}

internal fun mergeQualityOptions(fetched: List<QualityOption>): List<QualityOption> {
    val defaults = DefaultQuality.entries.map {
        QualityOption(quality = it.id, label = it.label, needVip = it.needVip)
    }
    return (fetched + defaults)
        .distinctBy { it.quality }
        .sortedByDescending { it.quality }
}

private fun DownloadStatus.statusLabel(): String = when (this) {
    DownloadStatus.QUEUED -> "排队中"
    DownloadStatus.DOWNLOADING -> "下载中"
    DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.COMPLETED -> "已缓存"
    DownloadStatus.FAILED -> "失败(可重试)"
}
