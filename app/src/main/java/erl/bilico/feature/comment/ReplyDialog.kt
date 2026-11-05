package erl.bilico.feature.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import erl.bilico.data.model.Comment
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.components.CoverImage
import erl.bilico.ui.components.MessageState
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 楼中楼从底部弹出，自己分页，关闭即丢弃。 */
@Composable
internal fun ReplyDialog(
    container: AppContainer,
    oid: Long,
    comment: Comment,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var items by remember(comment.id) { mutableStateOf<List<Comment>>(emptyList()) }
    var page by remember(comment.id) { mutableIntStateOf(0) }
    var isLoading by remember(comment.id) { mutableStateOf(true) }
    var isEnd by remember(comment.id) { mutableStateOf(false) }
    var error by remember(comment.id) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun loadNext() {
        if (isLoading && page > 0) return
        if (isEnd) return
        isLoading = true
        error = null
        scope.launch {
            val next = page + 1
            container.comments.replies(oid, comment.id, next)
                .onSuccess { list ->
                    page = next
                    items = (items + list).distinctBy { it.id }
                    isEnd = list.isEmpty()
                }
                .onFailure { failure ->
                    error = failure.message ?: "回复加载失败"
                }
            isLoading = false
        }
    }

    LaunchedEffect(comment.id) {
        if (items.isEmpty()) loadNext()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible != null && items.isNotEmpty() && lastVisible >= items.size - 2) {
                    loadNext()
                }
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(COUITheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "回复（${comment.replyCount}）",
                        style = COUITheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = COUIIcons.Close,
                        contentDescription = "关闭",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss)
                            .padding(6.dp),
                        tint = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
                HorizontalDivider(color = COUITheme.colorScheme.dividerLine)

                when {
                    items.isEmpty() && isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(size = 22.dp)
                    }

                    items.isEmpty() && error != null -> MessageState(
                        message = error.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            Text(
                                text = "重试",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { loadNext() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                style = COUITheme.textStyles.footnote1,
                                color = COUITheme.colorScheme.primary,
                            )
                        },
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(items, key = { it.id }) { reply ->
                            ReplyRow(reply)
                        }
                        if (isLoading) {
                            item(key = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(size = 16.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyRow(reply: Comment) {
    val density = LocalDensity.current
    val avatarPx = with(density) { 30.dp.roundToPx() }

    Row(modifier = Modifier.fillMaxWidth()) {
        CoverImage(
            url = Formatters.sizedImageUrl(reply.avatar, avatarPx, avatarPx),
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.userName,
                    modifier = Modifier.weight(1f, fill = false),
                    style = COUITheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reply.level > 0) {
                    Text(
                        text = "Lv${reply.level}",
                        modifier = Modifier.padding(start = 6.dp),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = Formatters.formatTimeAgo(reply.timeSec),
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = reply.content.ifBlank { "[图片评论]" },
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurface,
            )
            if (reply.like > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "赞 ${Formatters.formatStat(reply.like.toLong())}",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}
