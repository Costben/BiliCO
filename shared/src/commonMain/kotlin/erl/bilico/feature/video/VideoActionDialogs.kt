package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoEpisode
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.CouiMenuItem
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

/** 投币数量：1 或 2。 */
@Composable
internal fun CoinDialog(
    remaining: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedCount by remember { mutableStateOf<Int?>(null) }
    PlayerModalHost(
        inlineInStage = false,
        onDismiss = {
            val count = pickedCount
            if (count != null) onSelect(count) else onDismiss()
        },
    ) {
        val dismiss = LocalDismissState.current ?: onDismiss
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
                        .clickable(enabled = enabled) {
                            pickedCount = count
                            dismiss()
                        }
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

/** 收藏夹选择：勾选后可加入或取消收藏到指定收藏夹，支持临时新建收藏夹后直接勾选并提交。 */
@Composable
internal fun FavoriteFolderDialog(
    folders: List<FavoriteFolder>,
    onConfirm: (addMediaIds: List<Long>, delMediaIds: List<Long>, nowFavorited: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCreateFolder: (suspend (title: String, intro: String, isPrivate: Boolean) -> Result<FavoriteFolder>)? = null,
) {
    val initialIds = remember(folders) {
        folders.filter { it.favorited }.map { it.mediaId }.toSet()
    }
    var folderList by remember(folders) { mutableStateOf(folders) }
    var selectedIds by remember(folders) { mutableStateOf(initialIds) }
    var creating by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newIntro by remember { mutableStateOf("") }
    var newPublic by remember { mutableStateOf(true) }
    var createError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val addIds = (selectedIds - initialIds).toList()
    val delIds = (initialIds - selectedIds).toList()
    val hasChanges = addIds.isNotEmpty() || delIds.isNotEmpty()

    var pendingConfirm by remember { mutableStateOf<Triple<List<Long>, List<Long>, Boolean>?>(null) }
    PlayerModalHost(
        inlineInStage = false,
        onDismiss = {
            val result = pendingConfirm
            if (result != null) onConfirm(result.first, result.second, result.third) else onDismiss()
        },
        canDismiss = { !submitting },
    ) {
        val dismiss = LocalDismissState.current ?: onDismiss
        if (creating && onCreateFolder != null) {
            CreateFavoriteFolderContent(
                title = newTitle,
                onTitleChange = {
                    if (it.length <= MAX_FAV_TITLE_LENGTH) newTitle = it
                    createError = null
                },
                intro = newIntro,
                onIntroChange = {
                    if (it.length <= MAX_FAV_INTRO_LENGTH) newIntro = it
                    createError = null
                },
                isPublic = newPublic,
                onPublicChange = { newPublic = it },
                error = createError,
                submitting = submitting,
                onCancel = {
                    if (!submitting) {
                        creating = false
                        createError = null
                    }
                },
                onSubmit = {
                    val trimmed = newTitle.trim()
                    if (trimmed.isEmpty()) {
                        createError = "请输入收藏夹名称"
                        return@CreateFavoriteFolderContent
                    }
                    if (submitting) return@CreateFavoriteFolderContent
                    submitting = true
                    createError = null
                    scope.launch {
                        onCreateFolder(trimmed, newIntro.trim(), !newPublic)
                            .onSuccess { created ->
                                val insertAt = if (folderList.isNotEmpty()) 1 else 0
                                folderList = folderList.toMutableList().apply { add(insertAt, created) }
                                selectedIds = selectedIds + created.mediaId
                                newTitle = ""
                                newIntro = ""
                                newPublic = true
                                creating = false
                            }
                            .onFailure { err ->
                                createError = err.message ?: "创建收藏夹失败"
                            }
                        submitting = false
                    }
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "添加到收藏夹",
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = COUITheme.colorScheme.onSurface,
                    )
                    if (onCreateFolder != null) {
                        Row(
                            modifier = Modifier
                                .clip(SquircleShape(8.dp))
                                .clickable {
                                    createError = null
                                    creating = true
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = COUIIcons.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = COUITheme.colorScheme.primary,
                            )
                            Text(
                                text = "新建收藏夹",
                                style = COUITheme.textStyles.footnote1,
                                fontWeight = FontWeight.Medium,
                                color = COUITheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .scrollEndHaptic()
                        .verticalScroll(rememberScrollState()),
                ) {
                    folderList.forEach { folder ->
                        val checked = folder.mediaId in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) {
                                    selectedIds = if (checked) {
                                        selectedIds - folder.mediaId
                                    } else {
                                        selectedIds + folder.mediaId
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                state = ToggleableState(checked),
                                onClick = null,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = folder.title,
                                modifier = Modifier.weight(1f),
                                style = COUITheme.textStyles.body1,
                                color = COUITheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = folder.countText,
                                style = COUITheme.textStyles.footnote2,
                                color = COUITheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = dismiss,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "完成",
                        onClick = {
                            if (hasChanges) {
                                pendingConfirm = Triple(addIds, delIds, selectedIds.isNotEmpty())
                            }
                            dismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateFavoriteFolderContent(
    title: String,
    onTitleChange: (String) -> Unit,
    intro: String,
    onIntroChange: (String) -> Unit,
    isPublic: Boolean,
    onPublicChange: (Boolean) -> Unit,
    error: String?,
    submitting: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val scheme = COUITheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(top = 14.dp, bottom = 10.dp),
    ) {
        Text(
            text = "新建收藏夹",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = COUITheme.textStyles.title4,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FavoriteInputBox(
                label = "名称",
                value = title,
                onValueChange = onTitleChange,
                placeholder = "输入收藏夹名称（必填）",
                maxLength = MAX_FAV_TITLE_LENGTH,
                singleLine = true,
                enabled = !submitting,
            )
            FavoriteInputBox(
                label = "简介",
                value = intro,
                onValueChange = onIntroChange,
                placeholder = "补充说明（选填）",
                maxLength = MAX_FAV_INTRO_LENGTH,
                singleLine = false,
                enabled = !submitting,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(10.dp))
                    .clickable(enabled = !submitting) { onPublicChange(!isPublic) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "公开收藏夹",
                        style = COUITheme.textStyles.body1,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = if (isPublic) "其他用户可以在你的主页查看" else "仅自己可见",
                        style = COUITheme.textStyles.footnote2,
                        color = scheme.onSurfaceSecondary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = isPublic,
                    onCheckedChange = { if (!submitting) onPublicChange(it) },
                    enabled = !submitting,
                )
            }
            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    style = COUITheme.textStyles.footnote2,
                    color = scheme.error,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                text = "返回",
                onClick = onCancel,
                enabled = !submitting,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = if (submitting) "创建中…" else "创建",
                onClick = onSubmit,
                enabled = !submitting && title.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun FavoriteInputBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int,
    singleLine: Boolean,
    enabled: Boolean,
) {
    val scheme = COUITheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = COUITheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${value.length}/$maxLength",
                style = COUITheme.textStyles.footnote2,
                color = scheme.onSurfaceSecondary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(12.dp))
                .background(scheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = COUITheme.textStyles.body2,
                    color = scheme.onSurfaceSecondary.copy(alpha = scheme.onSurfaceSecondary.alpha * 0.65f),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 3,
                textStyle = COUITheme.textStyles.body2.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val MAX_FAV_TITLE_LENGTH = 20
private const val MAX_FAV_INTRO_LENGTH = 200

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
