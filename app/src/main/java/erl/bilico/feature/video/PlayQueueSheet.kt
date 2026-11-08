package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import erl.bilico.ui.PlayQueue
import erl.bilico.ui.QueuedVideo
import erl.bilico.ui.components.LibraryVideoRow
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 播放队列面板：稍后再看「播放全部」起的那份队列在这里看。
 * 点一条直接切过去播，点右下角的叉把它移出队列。
 */
@Composable
internal fun PlayQueueSheet(
    visible: Boolean,
    queue: PlayQueue,
    onDismiss: () -> Unit,
    onPlay: (QueuedVideo) -> Unit,
) {
    val items = queue.items

    // 删到最后一条就收起面板，免得留一块空白。
    LaunchedEffect(visible, items.isEmpty()) {
        if (visible && items.isEmpty()) onDismiss()
    }

    OverlayBottomSheet(
        show = visible,
        title = "播放队列",
        showDragHandle = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.video.bvid },
                contentType = { _, _ -> "queue-row" },
            ) { index, item ->
                LibraryVideoRow(
                    video = item.video,
                    onClick = { onPlay(item) },
                    trailing = {
                        RemoveFromQueueButton(onClick = { queue.removeAt(index) })
                    },
                )
            }
        }
    }
}

/** 队列行右下角的小叉：只把这一个视频移出队列。 */
@Composable
private fun RemoveFromQueueButton(onClick: () -> Unit) {
    val scheme = COUITheme.colorScheme
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "移出队列" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = COUIIcons.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = scheme.onSurfaceSecondary,
        )
    }
}
