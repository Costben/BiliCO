package erl.bilico.feature.download

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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.BangumiDetail
import erl.bilico.data.model.BangumiEpisode
import erl.bilico.data.model.DownloadStatus
import erl.bilico.data.model.QualityOption
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoPart
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.settings.DefaultQuality
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.video.PlayerModalHost
import erl.bilico.ui.components.COUISmoothCapsuleShape
import erl.bilico.ui.nestedBlockColor
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
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

internal data class DownloadPickEntry(
    val id: Long,
    val title: String,
    val durationSec: Long,
    val status: DownloadStatus?,
) {
    val selectable: Boolean
        get() = status == null || status == DownloadStatus.FAILED
}

@Composable
internal fun rememberVideoDownloadDialog(
    visible: Boolean,
    container: AppContainer,
    detail: VideoDetail?,
    selectedCid: Long,
    activeQuality: Int,
    qualities: List<QualityOption>,
    onEnqueued: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
    onDismiss: () -> Unit,
): (@Composable (Boolean) -> Unit)? = if (visible && detail != null) {
    { inlineInStage ->
        VideoDownloadDialog(
            container = container,
            detail = detail,
            initialCid = selectedCid,
            activeQuality = activeQuality,
            initialQualities = qualities,
            inlineInStage = inlineInStage,
            onEnqueued = onEnqueued,
            onOpenDownloads = onOpenDownloads,
            onDismiss = onDismiss,
        )
    }
} else {
    null
}

/** 从普通视频播放页打开离线缓存选择面板。 */
@Composable
internal fun VideoDownloadDialog(
    container: AppContainer,
    detail: VideoDetail,
    initialCid: Long,
    activeQuality: Int,
    initialQualities: List<QualityOption>,
    inlineInStage: Boolean = false,
    onEnqueued: (Int) -> Unit = {},
    onOpenDownloads: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val defaultQuality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val cdn by container.settings.cdnOption.collectAsStateWithLifecycle()
    var qualities by remember(detail.bvid, initialQualities) {
        mutableStateOf(mergeQualityOptions(initialQualities))
    }

    LaunchedEffect(detail.bvid, detail.cid) {
        if (initialQualities.isEmpty() && detail.cid > 0L) {
            container.video.playback(detail.bvid, detail.cid, defaultQuality, CdnOption.fromId(cdn))
                .onSuccess { source ->
                    if (source.qualities.isNotEmpty()) {
                        qualities = mergeQualityOptions(source.qualities)
                    }
                }
        }
    }

    val entries = remember(detail.pages, detail.cid, detail.durationSec, tasks) {
        val pages = detail.pages.ifEmpty {
            listOf(VideoPart(detail.cid, 1, detail.title, detail.durationSec.toLong()))
        }
        pages.map { part ->
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

    DownloadPickerBody(
        title = "离线缓存",
        subtitle = detail.title,
        qualities = qualities,
        initialQuality = activeQuality.takeIf { it > 0 } ?: defaultQuality,
        entries = entries,
        initialSelectedId = initialCid.takeIf { it > 0L } ?: detail.cid,
        inlineInStage = inlineInStage,
        onOpenDownloads = onOpenDownloads,
        onConfirm = { qn, qLabel, selectedIds ->
            val pages = detail.pages.ifEmpty {
                listOf(VideoPart(detail.cid, 1, detail.title, detail.durationSec.toLong(), detail.aspect))
            }
            val chosen = pages.filter { it.cid in selectedIds }
            val count = container.downloads.enqueueVideo(detail, chosen, qn, qLabel)
            onEnqueued(count)
        },
        onDismiss = onDismiss,
    )
}

/** 从番剧/影视播放页打开离线缓存选择面板。 */
@Composable
internal fun PgcDownloadDialog(
    container: AppContainer,
    detail: BangumiDetail,
    currentEpisode: BangumiEpisode?,
    activeQuality: Int,
    initialQualities: List<QualityOption>,
    inlineInStage: Boolean = false,
    onEnqueued: (Int) -> Unit = {},
    onOpenDownloads: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val tasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val defaultQuality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val qualities = remember(initialQualities) { mergeQualityOptions(initialQualities) }

    val entries = remember(detail.episodes, detail.seasonId, tasks) {
        detail.episodes.mapIndexed { idx, ep ->
            DownloadPickEntry(
                id = ep.epId,
                title = ep.title.ifBlank { "第 ${idx + 1} 话" },
                durationSec = ep.durationSec,
                status = container.downloads.statusForBangumi(detail.seasonId, ep.epId, ep.cid),
            )
        }
    }

    DownloadPickerBody(
        title = "离线缓存",
        subtitle = detail.title,
        qualities = qualities,
        initialQuality = activeQuality.takeIf { it > 0 } ?: defaultQuality,
        entries = entries,
        initialSelectedId = currentEpisode?.epId ?: detail.episodes.firstOrNull()?.epId ?: 0L,
        inlineInStage = inlineInStage,
        onOpenDownloads = onOpenDownloads,
        onConfirm = { qn, qLabel, selectedIds ->
            val chosen = detail.episodes.filter { it.epId in selectedIds && it.cid > 0L }
            val count = container.downloads.enqueueBangumi(detail, chosen, qn, qLabel)
            onEnqueued(count)
        },
        onDismiss = onDismiss,
    )
}

/** 从推荐流 / 列表三点菜单快捷打开离线缓存。 */
@Composable
internal fun FeedVideoDownloadDialog(
    container: AppContainer,
    bvid: String,
    onEnqueued: (Int) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var detail by remember(bvid) { mutableStateOf<VideoDetail?>(null) }
    var qualities by remember(bvid) { mutableStateOf<List<QualityOption>>(emptyList()) }
    var error by remember(bvid) { mutableStateOf<String?>(null) }
    val defaultQuality by container.settings.defaultQuality.collectAsStateWithLifecycle()
    val cdn by container.settings.cdnOption.collectAsStateWithLifecycle()

    LaunchedEffect(bvid) {
        error = null
        container.video.detail(bvid)
            .onSuccess { loaded ->
                detail = loaded
                if (loaded.cid > 0L) {
                    container.video.playback(bvid, loaded.cid, defaultQuality, CdnOption.fromId(cdn))
                        .onSuccess { qualities = it.qualities }
                }
            }
            .onFailure { err -> error = err.message ?: "视频信息加载失败" }
    }

    val loaded = detail
    if (loaded != null) {
        VideoDownloadDialog(
            container = container,
            detail = loaded,
            initialCid = loaded.cid,
            activeQuality = defaultQuality,
            initialQualities = qualities,
            inlineInStage = false,
            onEnqueued = onEnqueued,
            onDismiss = onDismiss,
        )
        return
    }

    PlayerModalHost(inlineInStage = false, onDismiss = onDismiss) {
        val dismiss = LocalDismissState.current ?: onDismiss
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "离线缓存",
                style = COUITheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(18.dp))
            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            } else {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "正在获取视频清晰度…",
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "关闭", onClick = dismiss),
                positive = null,
            )
        }
    }
}

@Composable
private fun DownloadPickerBody(
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

    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
        maxWidth = 440.dp,
    ) {
        val scheme = COUITheme.colorScheme
        val dismiss = LocalDismissState.current ?: onDismiss
        val selectableIds = remember(entries) { entries.filter { it.selectable }.map { it.id }.toSet() }
        val allSelected = selectableIds.isNotEmpty() && selectedIds.containsAll(selectableIds)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
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
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
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
                            .clickable {
                                dismiss()
                                onOpenDownloads()
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        style = COUITheme.textStyles.footnote1,
                        color = scheme.primary,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(COUISmoothCapsuleShape)
                        .clickable { dismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(COUIIcons.Close, "关闭", Modifier.size(20.dp), tint = scheme.onSurfaceSecondary)
                }
            }

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
                negative = DialogButtonBarAction(text = "取消", onClick = dismiss),
                positive = DialogButtonBarAction(
                    text = confirmLabel,
                    enabled = selectedIds.isNotEmpty(),
                    recommended = true,
                    onClick = {
                        val qLabel = qualities.firstOrNull { it.quality == pickedQuality }?.label
                            ?: DefaultQuality.fromId(pickedQuality).label
                        onConfirm(pickedQuality, qLabel, selectedIds)
                        dismiss()
                    },
                ),
            )
        }
    }
}

private fun mergeQualityOptions(fetched: List<QualityOption>): List<QualityOption> {
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
