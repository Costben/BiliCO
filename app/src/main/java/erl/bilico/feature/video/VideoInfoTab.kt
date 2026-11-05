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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoPart
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.LoadingBox
import erl.bilico.ui.components.MessageState
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun VideoInfoTab(
    detail: VideoDetail?,
    isLoading: Boolean,
    error: String?,
    selectedCid: Long,
    onRetry: () -> Unit,
    onSelectPart: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        detail == null && isLoading -> LoadingBox(modifier)

        detail == null -> MessageState(
            message = error ?: "视频加载失败",
            modifier = modifier,
            action = {
                Button(onClick = onRetry) { Text("重试") }
            },
        )

        else -> InfoContent(
            detail = detail,
            selectedCid = selectedCid,
            onSelectPart = onSelectPart,
            modifier = modifier,
        )
    }
}

@Composable
private fun InfoContent(
    detail: VideoDetail,
    selectedCid: Long,
    onSelectPart: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var descExpanded by rememberSaveable(detail.bvid) { mutableStateOf(false) }
    val density = LocalDensity.current
    val avatarPx = with(density) { 40.dp.roundToPx() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Text(
                text = detail.title,
                style = COUITheme.textStyles.title3,
                fontWeight = FontWeight.Medium,
            )
        }

        item(key = "stats") {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("播放", Formatters.formatStat(detail.view), Modifier.weight(1f))
                StatCell("点赞", Formatters.formatStat(detail.like), Modifier.weight(1f))
                StatCell("投币", Formatters.formatStat(detail.coin), Modifier.weight(1f))
                StatCell("收藏", Formatters.formatStat(detail.favorite), Modifier.weight(1f))
            }
        }

        item(key = "up") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(
                    url = Formatters.sizedImageUrl(detail.upFace, avatarPx, avatarPx),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = detail.upName.ifBlank { "未知 UP" },
                        style = COUITheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = Formatters.formatTimeAgo(detail.pubdate),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }

        val desc = detail.desc.trim()
        if (desc.isNotEmpty()) {
            item(key = "desc") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(COUITheme.colorScheme.surfaceVariant)
                        .clickable { descExpanded = !descExpanded }
                        .padding(12.dp),
                ) {
                    Text(
                        text = desc,
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = if (descExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!descExpanded && desc.length > 80) {
                        Text(
                            text = "展开",
                            modifier = Modifier.padding(top = 6.dp),
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (detail.pages.size > 1) {
            item(key = "parts-title") {
                Text(
                    text = "分P（${detail.pages.size}）",
                    style = COUITheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                )
            }
            itemsIndexed(detail.pages, key = { _, part -> part.cid }) { index, part ->
                PartRow(
                    part = part,
                    selected = part.cid == selectedCid,
                    onClick = { onSelectPart(index) },
                )
            }
        }

        item(key = "divider") {
            HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = COUITheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
        )
        Box(Modifier.height(2.dp))
        Text(
            text = label,
            style = COUITheme.textStyles.footnote2,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
}

@Composable
private fun PartRow(part: VideoPart, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) COUITheme.colorScheme.pressBackground else COUITheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "P${part.index}",
            modifier = Modifier.width(36.dp),
            style = COUITheme.textStyles.footnote1,
            color = if (selected) {
                COUITheme.colorScheme.primary
            } else {
                COUITheme.colorScheme.onSurfaceSecondary
            },
        )
        Text(
            text = part.title.ifBlank { "第 ${part.index} P" },
            modifier = Modifier.weight(1f),
            style = COUITheme.textStyles.body2,
            color = if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (part.durationSec > 0) {
            Text(
                text = Formatters.formatDuration(part.durationSec),
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}
