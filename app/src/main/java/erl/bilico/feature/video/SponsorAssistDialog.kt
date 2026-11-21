package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.COUISmoothCapsuleShape
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/**
 * 空降助手面板：上面是社区已标记片段的快捷跳转，点一条直接落到它的起点；
 * 下面保留「提交空降片段」入口，跳转与提交在同一个面板里完成。
 */
@Composable
internal fun SponsorAssistDialog(
    segments: List<SponsorSegment>,
    inlineInStage: Boolean,
    onJump: (SponsorSegment) -> Unit,
    onSubmit: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sorted = remember(segments) {
        segments.sortedWith(compareBy({ it.startMs }, { it.endMs }))
    }
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
        horizontalPadding = if (inlineInStage) 12.dp else 24.dp,
        cornerRadius = 24.dp,
        maxWidth = 440.dp,
    ) {
        val scheme = COUITheme.colorScheme
        val dismiss = LocalDismissState.current ?: onDismiss
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
                    text = "空降助手",
                    modifier = Modifier.align(Alignment.Center),
                    style = COUITheme.textStyles.main.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
                HighlightedIconButton(
                    onClick = dismiss,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(COUIIcons.Close, "关闭", Modifier.size(24.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "快捷跳转",
                    style = COUITheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
                Text(
                    text = if (sorted.isEmpty()) "暂无片段" else "${sorted.size} 条 · 点一下跳到起点",
                    style = COUITheme.textStyles.footnote2,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (sorted.isEmpty()) {
                Text(
                    text = "这个视频还没有社区标记的片段，可以先提交一条。",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = COUITheme.textStyles.footnote2,
                    color = scheme.onSurfaceSecondary,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (inlineInStage) 220.dp else 420.dp)
                        .scrollEndHaptic()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sorted.forEach { segment ->
                        SegmentRow(segment = segment, onClick = { onJump(segment) })
                    }
                }
            }

            if (onSubmit != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(COUISmoothCapsuleShape)
                            .background(scheme.surfaceContainer)
                            .clickable(onClick = onSubmit)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "提交空降片段",
                            modifier = Modifier.weight(1f),
                            style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = "标记现在的片段",
                            style = COUITheme.textStyles.footnote2,
                            color = scheme.onSurfaceSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(segment: SponsorSegment, onClick: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(COUISmoothCapsuleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(segment.category.colorArgb)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = segment.category.label,
                style = COUITheme.textStyles.body2,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = segmentMeta(segment),
                style = COUITheme.textStyles.footnote2,
                color = scheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "跳转",
            style = COUITheme.textStyles.footnote2,
            color = scheme.primary,
            maxLines = 1,
        )
    }
}

private fun segmentMeta(segment: SponsorSegment): String = buildString {
    append(Formatters.formatDuration(segment.startMs / 1000L))
    append(" – ")
    append(Formatters.formatDuration(segment.endMs / 1000L))
    if (segment.actionType.label.isNotBlank()) {
        append(" · ")
        append(segment.actionType.label)
    }
    if (segment.votes > 0) {
        append(" · ")
        append(segment.votes)
        append(" 票")
    }
}
