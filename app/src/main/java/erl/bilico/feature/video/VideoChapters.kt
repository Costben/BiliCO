package erl.bilico.feature.video

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CoverImage
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

internal fun activeChapterIndex(chapters: List<VideoChapter>, positionMs: Long): Int {
    if (chapters.isEmpty() || positionMs < chapters.first().fromMs) return -1
    val inside = chapters.indexOfFirst { positionMs >= it.fromMs && positionMs < it.toMs }
    if (inside >= 0) return inside
    return chapters.indexOfLast { positionMs >= it.fromMs }
}

/**
 * 播放器内对齐进度条的分段条：
 * - 不显示时间点，不横向滚动，严格按各分段在总时长中的起止比例与进度条对齐；
 * - 所有分段标题保持统一字号，不随分段长度压缩字体。
 */
@Composable
internal fun PlayerChapterStrip(
    chapters: List<VideoChapter>,
    positionMs: Long,
    durationMs: Long,
    onSelectChapter: (VideoChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalMs = durationMs.takeIf { it > 0L }
        ?: chapters.maxOfOrNull { it.toMs }?.takeIf { it > 0L }
        ?: return
    if (chapters.isEmpty()) return
    val activeIndex = activeChapterIndex(chapters, positionMs)
    val density = LocalDensity.current
    val gapPx = with(density) { 1.5.dp.roundToPx() }
    val barHeightPx = with(density) { 24.dp.roundToPx() }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(4.dp)),
        content = {
            chapters.forEachIndexed { index, chapter ->
                val active = index == activeIndex
                val bg = if (active) {
                    Color.White.copy(alpha = 0.28f)
                } else {
                    Color.White.copy(alpha = 0.14f)
                }
                Box(
                    modifier = Modifier
                        .background(bg)
                        .clickable { onSelectChapter(chapter) }
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = chapter.title,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = Color.White.copy(alpha = if (active) 1f else 0.9f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val totalW = constraints.maxWidth.coerceAtLeast(1)
        val halfLeft = gapPx / 2
        val halfRight = gapPx - halfLeft
        val ranges = chapters.mapIndexed { index, chapter ->
            val startRatio = (chapter.fromMs.toFloat() / totalMs).coerceIn(0f, 1f)
            val endRatio = (chapter.toMs.toFloat() / totalMs).coerceIn(startRatio, 1f)
            val rawLeft = (startRatio * totalW).roundToInt()
            val rawRight = (endRatio * totalW).roundToInt()
            val left = if (index > 0 && rawLeft > 0) (rawLeft + halfLeft).coerceAtMost(totalW) else rawLeft
            val right = if (index < chapters.lastIndex && rawRight < totalW) {
                (rawRight - halfRight).coerceAtLeast(left)
            } else {
                rawRight.coerceAtLeast(left)
            }
            left to (right - left).coerceAtLeast(0)
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            val w = ranges[index].second
            measurable.measure(Constraints.fixed(width = w, height = barHeightPx))
        }
        layout(totalW, barHeightPx) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = ranges[index].first, y = 0)
            }
        }
    }
}

/**
 * 竖屏简介页的视频分段横向卡片列表：统一字号与卡片尺寸，点击直接跳转播放。
 */
@Composable
internal fun VideoChaptersSection(
    chapters: List<VideoChapter>,
    positionMs: Long,
    onSelectChapter: (VideoChapter) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chapters.isEmpty()) return
    val activeIndex = activeChapterIndex(chapters, positionMs)
    val listState = rememberLazyListState()
    val hasThumbnails = remember(chapters) { chapters.any { it.imgUrl.isNotBlank() } }
    val density = LocalDensity.current
    val thumbW = with(density) { 148.dp.roundToPx() }
    val thumbH = with(density) { 68.dp.roundToPx() }

    LaunchedEffect(activeIndex, chapters.size) {
        if (activeIndex in chapters.indices) {
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
            if (!visible) {
                listState.animateScrollToItem(activeIndex)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "视频分段（${chapters.size}）",
                style = COUITheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (activeIndex in chapters.indices) {
                Text(
                    text = "${activeIndex + 1}/${chapters.size}",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = COUITheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Text(
                text = "全部",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier
                    .clip(SquircleShape(6.dp))
                    .clickable(onClick = onOpenAll)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = chapters,
                key = { _, item -> "info-${item.fromMs}-${item.title}" },
            ) { index, chapter ->
                val active = index == activeIndex
                val cardShape = SquircleShape(10.dp)
                val border = if (active) {
                    BorderStroke(1.dp, COUITheme.colorScheme.primary)
                } else {
                    null
                }
                val bg = if (active) {
                    COUITheme.colorScheme.pressBackground
                } else {
                    COUITheme.colorScheme.surfaceContainer
                }
                Column(
                    modifier = Modifier
                        .width(148.dp)
                        .clip(cardShape)
                        .background(bg)
                        .then(if (border != null) Modifier.border(border, cardShape) else Modifier)
                        .clickable { onSelectChapter(chapter) },
                ) {
                    if (hasThumbnails) {
                        CoverImage(
                            url = Formatters.sizedImageUrl(chapter.imgUrl, thumbW, thumbH),
                            contentDescription = chapter.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = Formatters.formatDuration(chapter.fromMs / 1000),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (active) {
                                    COUITheme.colorScheme.primary
                                } else {
                                    COUITheme.colorScheme.onSurfaceSecondary
                                },
                                maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "#${index + 1}",
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = chapter.title,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color = if (active) {
                                COUITheme.colorScheme.primary
                            } else {
                                COUITheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.heightIn(min = 34.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 完整章节选择弹窗（竖屏/全屏通用）：统一字号完整展示超长章节标题。
 */
@Composable
internal fun ChapterDialog(
    chapters: List<VideoChapter>,
    positionMs: Long,
    onSelectChapter: (VideoChapter) -> Unit,
    onDismiss: () -> Unit,
    inlineInStage: Boolean = false,
) {
    val activeIndex = activeChapterIndex(chapters, positionMs)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = activeIndex.coerceAtLeast(0),
    )
    val density = LocalDensity.current
    val thumbW = with(density) { 72.dp.roundToPx() }
    val thumbH = with(density) { 40.dp.roundToPx() }

    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "视频分段（${chapters.size}）",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .scrollEndHaptic(),
            ) {
                itemsIndexed(
                    items = chapters,
                    key = { _, item -> "dialog-${item.fromMs}-${item.title}" },
                ) { index, chapter ->
                    val active = index == activeIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (active) COUITheme.colorScheme.pressBackground else Color.Transparent,
                            )
                            .clickable { onSelectChapter(chapter) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (chapter.imgUrl.isNotBlank()) {
                            CoverImage(
                                url = Formatters.sizedImageUrl(chapter.imgUrl, thumbW, thumbH),
                                contentDescription = chapter.title,
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(40.dp)
                                    .clip(SquircleShape(6.dp)),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = chapter.title,
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                                color = if (active) {
                                    COUITheme.colorScheme.primary
                                } else {
                                    COUITheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = "${Formatters.formatDuration(chapter.fromMs / 1000)} - ${
                                    Formatters.formatDuration(chapter.toMs / 1000)
                                }",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = if (active) {
                                    COUITheme.colorScheme.primary
                                } else {
                                    COUITheme.colorScheme.onSurfaceSecondary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
