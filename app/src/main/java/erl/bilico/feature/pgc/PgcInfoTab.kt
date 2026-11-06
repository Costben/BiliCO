package erl.bilico.feature.pgc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.BangumiDetail
import erl.bilico.data.model.BangumiEpisode
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun PgcInfoTab(
    detail: BangumiDetail?,
    isLoading: Boolean,
    error: String?,
    selectedEpIndex: Int,
    followed: Boolean,
    onToggleFollow: () -> Unit,
    onRetry: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSelectSeason: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        detail == null && isLoading -> LoadingState(modifier)

        detail == null -> ErrorState(
            message = error ?: "番剧加载失败",
            modifier = modifier,
            onRetry = onRetry,
        )

        else -> PgcInfoContent(
            detail = detail,
            selectedEpIndex = selectedEpIndex,
            followed = followed,
            onToggleFollow = onToggleFollow,
            onSelectEpisode = onSelectEpisode,
            onSelectSeason = onSelectSeason,
            modifier = modifier,
        )
    }
}

@Composable
private fun PgcInfoContent(
    detail: BangumiDetail,
    selectedEpIndex: Int,
    followed: Boolean,
    onToggleFollow: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSelectSeason: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var descExpanded by rememberSaveable(detail.seasonId) { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = detail.title,
                    modifier = Modifier.weight(1f),
                    style = COUITheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (followed) "已追番" else "追番",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (followed) {
                                COUITheme.colorScheme.surfaceContainer
                            } else {
                                COUITheme.colorScheme.primary
                            },
                        )
                        .clickable(onClick = onToggleFollow)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (followed) {
                        COUITheme.colorScheme.onSurfaceSecondary
                    } else {
                        androidx.compose.ui.graphics.Color.White
                    },
                )
            }
        }

        if (detail.score.isNotBlank() || detail.viewsText.isNotBlank()) {
            item(key = "rating") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (detail.score.isNotBlank()) {
                        Text(
                            text = detail.score,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = COUITheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "评分",
                                fontSize = 11.sp,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                            Text(
                                text = detail.rankText,
                                fontSize = 11.sp,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${detail.viewsText}播放",
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                        )
                        Text(
                            text = "${detail.followText}追番",
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            }
        }

        if (detail.metaText.isNotBlank()) {
            item(key = "meta") {
                Text(
                    text = detail.metaText,
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }

        if (detail.seasons.size > 1) {
            item(key = "seasons") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.seasons.forEach { season ->
                        val active = season.seasonId == detail.seasonId
                        Text(
                            text = season.title,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (active) {
                                        COUITheme.colorScheme.primary.copy(alpha = 0.14f)
                                    } else {
                                        COUITheme.colorScheme.surfaceContainer
                                    },
                                )
                                .clickable(enabled = !active) { onSelectSeason(season.seasonId) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            fontSize = 13.sp,
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

        if (detail.episodes.isNotEmpty()) {
            item(key = "episodes-title") {
                Text(
                    text = "选集（${detail.episodes.size}）",
                    style = COUITheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                )
            }
            items(
                items = detail.episodes.withIndex().chunked(4),
                key = { row -> row.first().index },
                contentType = { "episode-row" },
            ) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { indexed ->
                        EpisodeChip(
                            episode = detail.episodes[indexed.index],
                            selected = indexed.index == selectedEpIndex,
                            onClick = { onSelectEpisode(indexed.index) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        val desc = detail.evaluate.trim()
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
                        maxLines = if (descExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!descExpanded && desc.length > 100) {
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

        item(key = "divider") {
            HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
        }
    }
}

@Composable
private fun EpisodeChip(
    episode: BangumiEpisode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    COUITheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    COUITheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = episode.title.ifBlank { "第 ${episode.epId} 话" },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) {
                    COUITheme.colorScheme.primary
                } else {
                    COUITheme.colorScheme.onSurface
                },
            )
            if (episode.badge.isNotBlank()) {
                Text(
                    text = episode.badge,
                    fontSize = 10.sp,
                    color = COUITheme.colorScheme.primary,
                )
            }
        }
    }
}
