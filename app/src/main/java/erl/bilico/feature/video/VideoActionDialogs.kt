package erl.bilico.feature.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoEpisode
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CouiMenuItem
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/** 投币数量：1 或 2。 */
@Composable
internal fun CoinDialog(
    remaining: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerModalHost(
        inlineInStage = false,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "投币",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            listOf(1, 2).forEach { count ->
                val enabled = count <= remaining
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onSelect(count) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$count 个硬币",
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.body1,
                        color = if (enabled) {
                            COUITheme.colorScheme.onSurface
                        } else {
                            COUITheme.colorScheme.onSurfaceSecondary
                        },
                    )
                }
            }
        }
    }
}

/** 收藏夹选择：只列当前账号的收藏夹。 */
@Composable
internal fun FavoriteFolderDialog(
    folders: List<FavoriteFolder>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerModalHost(
        inlineInStage = false,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "收藏到",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState()),
            ) {
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(folder.mediaId) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = folder.title,
                            modifier = Modifier.weight(1f),
                            style = COUITheme.textStyles.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = folder.countText,
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
            }
        }
    }
}

/** 组装播放器底部「选集」更多菜单项（分P / 合集，仅两项及以上时返回非空列表）。 */
@Composable
internal fun rememberEpisodesMenuItems(
    detail: VideoDetail?,
    selectedCid: Long,
    onSelectPart: (Int) -> Unit,
    onSelectEpisode: (VideoEpisode) -> Unit,
): List<CouiMenuItem> {
    val partClickState by rememberUpdatedState(onSelectPart)
    val episodeClickState by rememberUpdatedState(onSelectEpisode)
    return remember(detail, selectedCid) {
        if (detail == null) return@remember emptyList()
        val pages = detail.pages.takeIf { it.size > 1 }.orEmpty()
        val episodes = detail.collection?.episodes?.takeIf { it.size > 1 }.orEmpty()
        if (pages.isEmpty() && episodes.isEmpty()) return@remember emptyList()
        val partItems = pages.mapIndexed { index, part ->
            CouiMenuItem(
                label = "P${part.index} ${part.title.ifBlank { "第 ${part.index} P" }}",
                summary = part.durationSec.takeIf { it > 0 }?.let(Formatters::formatDuration),
                selected = part.cid == selectedCid,
                onClick = { partClickState(index) },
                group = 0,
            )
        }
        val episodeItems = episodes.mapIndexed { index, episode ->
            CouiMenuItem(
                label = "${index + 1}. ${episode.title.ifBlank { "第 ${index + 1} 集" }}",
                summary = episode.durationSec.takeIf { it > 0 }?.let(Formatters::formatDuration),
                selected = episode.bvid == detail.bvid && (episode.cid <= 0L || episode.cid == selectedCid),
                onClick = { episodeClickState(episode) },
                group = 1,
            )
        }
        when {
            partItems.isNotEmpty() && episodeItems.isNotEmpty() -> {
                val colTitle = detail.collection?.title?.ifBlank { "合集" } ?: "合集"
                listOf(
                    CouiMenuItem(label = "分P（${partItems.size}）", children = partItems, group = 0),
                    CouiMenuItem(label = "$colTitle（${episodeItems.size}）", children = episodeItems, group = 0),
                )
            }
            partItems.isNotEmpty() -> partItems
            else -> episodeItems
        }
    }
}
