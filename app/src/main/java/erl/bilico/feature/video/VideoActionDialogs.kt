package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.QualityOption
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 投币数量：1 或 2。 */
@Composable
internal fun CoinDialog(
    remaining: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
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

@Composable
internal fun QualityDialog(
    qualities: List<QualityOption>,
    activeQuality: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    inlineInStage: Boolean = false,
) {
    val options = remember(qualities) { qualities.distinctBy { it.quality } }
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "清晰度",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { option ->
                    val active = option.quality == activeQuality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.quality) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label.ifBlank { "${option.quality}P" },
                            modifier = Modifier.weight(1f),
                            style = COUITheme.textStyles.body1,
                            color = if (active) {
                                COUITheme.colorScheme.primary
                            } else {
                                COUITheme.colorScheme.onSurface
                            },
                        )
                        if (option.needVip) {
                            Text(
                                text = "大会员",
                                style = COUITheme.textStyles.footnote2,
                                color = COUITheme.colorScheme.primary,
                            )
                        } else if (option.needLogin) {
                            Text(
                                text = "登录",
                                style = COUITheme.textStyles.footnote2,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
internal fun SpeedDialog(
    active: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
    inlineInStage: Boolean = false,
) {
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(COUITheme.colorScheme.surfaceContainer)
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = "播放倍速",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                PLAYBACK_SPEEDS.forEach { option ->
                    val selected = option == active
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (option == 1f) "正常" else speedLabel(option),
                            modifier = Modifier.weight(1f),
                            style = COUITheme.textStyles.body1,
                            color = if (selected) {
                                COUITheme.colorScheme.primary
                            } else {
                                COUITheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}
