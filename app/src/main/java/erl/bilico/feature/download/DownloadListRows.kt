package erl.bilico.feature.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.DownloadStatus
import erl.bilico.data.model.DownloadTask
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CoverImage
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

private val RowCoverWidth = 132.dp

@Composable
internal fun StorageSummaryCard(
    completedCount: Int,
    activeCount: Int,
    totalBytes: Long,
) {
    val scheme = COUITheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "已占用 ${Formatters.formatFileSize(totalBytes)}",
                    style = COUITheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                val detail = buildList {
                    add("已完成 $completedCount 项")
                    if (activeCount > 0) add("进行中 $activeCount 项")
                }.joinToString(" · ")
                Text(
                    text = detail,
                    style = COUITheme.textStyles.footnote2,
                    color = scheme.onSurfaceSecondary,
                )
            }
            Text(
                text = "支持离线弹幕与字幕",
                style = COUITheme.textStyles.footnote2,
                color = scheme.primary,
            )
        }
    }
}

@Composable
internal fun ActiveDownloadCard(
    task: DownloadTask,
    coverUrl: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    val inset = 8.dp
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { RowCoverWidth.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val resolvedCover = remember(coverUrl, coverWidthPx) {
        if (coverUrl.startsWith("file://")) coverUrl else Formatters.sizedImageUrl(coverUrl, coverWidthPx, coverHeightPx)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        insideMargin = PaddingValues(inset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(RowCoverWidth)
                    .aspectRatio(16f / 9f)
                    .clip(SquircleShape((CardDefaults.CornerRadius - inset).coerceAtLeast(0.dp))),
            ) {
                CoverImage(url = resolvedCover, modifier = Modifier.fillMaxSize())
                Text(
                    text = task.qualityLabel,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(SquircleShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = task.displayTitle,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = task.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                val statusText = when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        val sizePart = if (task.totalBytes > 0L) {
                            "${Formatters.formatFileSize(task.downloadedBytes)} / ${Formatters.formatFileSize(task.totalBytes)}"
                        } else {
                            Formatters.formatFileSize(task.downloadedBytes)
                        }
                        val speedPart = if (task.speedBytesPerSec > 0L) {
                            " · ${Formatters.formatSpeed(task.speedBytesPerSec)}"
                        } else {
                            ""
                        }
                        "$sizePart$speedPart"
                    }
                    DownloadStatus.QUEUED -> "等待下载…"
                    DownloadStatus.PAUSED -> if (task.totalBytes > 0L) {
                        "已暂停 · ${Formatters.formatFileSize(task.downloadedBytes)} / ${Formatters.formatFileSize(task.totalBytes)}"
                    } else {
                        "已暂停"
                    }
                    DownloadStatus.FAILED -> task.errorMessage ?: "下载失败"
                    DownloadStatus.COMPLETED -> "已完成"
                }
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = if (task.status == DownloadStatus.FAILED) scheme.error else scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val actionIcon = when (task.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> COUIIcons.Pause
                    DownloadStatus.FAILED -> COUIIcons.Refresh
                    else -> COUIIcons.Play
                }
                val actionDesc = when (task.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> "暂停"
                    DownloadStatus.FAILED -> "重试"
                    else -> "继续"
                }
                SmallRoundIconButton(
                    icon = actionIcon,
                    description = actionDesc,
                    onClick = onTogglePauseResume,
                )
                SmallRoundIconButton(
                    icon = COUIIcons.Close,
                    description = "取消任务",
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
internal fun CompletedDownloadRow(
    task: DownloadTask,
    coverUrl: String,
    coverCornerRadius: Dp,
    coverCornerWeight: Float,
    modifier: Modifier = Modifier,
) {
    val scheme = COUITheme.colorScheme
    val inset = 8.dp
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { RowCoverWidth.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val resolvedCover = remember(coverUrl, coverWidthPx) {
        if (coverUrl.startsWith("file://")) coverUrl else Formatters.sizedImageUrl(coverUrl, coverWidthPx, coverHeightPx)
    }

    Row(modifier) {
        Box(
            modifier = Modifier
                .width(RowCoverWidth)
                .aspectRatio(16f / 9f)
                .clip(SquircleShape((coverCornerRadius - inset).coerceAtLeast(0.dp), weight = coverCornerWeight)),
        ) {
            CoverImage(url = resolvedCover, modifier = Modifier.fillMaxSize())
            if (task.durationSec > 0L) {
                Text(
                    text = Formatters.formatDuration(task.durationSec),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(SquircleShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = task.displayTitle,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val authorOrTag = when {
                task.isPgc -> "番剧 / 影视"
                task.upName.isNotBlank() -> task.upName
                else -> "本地视频"
            }
            Text(
                text = authorOrTag,
                fontSize = 11.sp,
                color = scheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${task.qualityLabel} · ${Formatters.formatFileSize(task.totalBytes)}",
                fontSize = 11.sp,
                color = scheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SmallRoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = scheme.onSurface,
        )
    }
}
