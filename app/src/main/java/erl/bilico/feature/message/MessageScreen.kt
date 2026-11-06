package erl.bilico.feature.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.MessageEntry
import erl.bilico.data.model.MessageKind
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SecondaryPage
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MessageScreen(
    container: AppContainer,
    nav: NavState,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val viewModel: MessageViewModel = viewModel {
        MessageViewModel(container.messages, container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    SecondaryPage(title = "消息", onBack = nav::pop, listState = listState) {
        item(key = "kinds") {
            MessageKindTabs(selected = state.kind, onSelect = viewModel::select)
        }

        if (account == null) {
            item(key = "login") {
                EmptyState(
                    text = "登录后查看",
                    action = { TextButton(text = "去登录", onClick = nav::popToMe) },
                )
            }
            return@SecondaryPage
        }

        if (state.items.isEmpty()) {
            item(key = "state") {
                when {
                    state.isLoading -> LoadingState()
                    state.error != null -> ErrorState(state.error.orEmpty(), onRetry = viewModel::refresh)
                    else -> EmptyState("暂时没有新消息")
                }
            }
        }

        items(
            count = state.items.size,
            key = { state.items[it].key },
            contentType = { "message" },
        ) { index ->
            MessageRow(entry = state.items[index])
        }

        if (state.isLoadingMore) {
            item(key = "loading-more") { LoadingState() }
        }
    }
}

@Composable
private fun MessageKindTabs(selected: MessageKind, onSelect: (MessageKind) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MESSAGE_KINDS.forEach { kind ->
            val active = kind.first == selected
            Text(
                text = kind.second,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active) {
                            COUITheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            COUITheme.colorScheme.surfaceContainer
                        },
                    )
                    .clickable { onSelect(kind.first) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                color = if (active) {
                    COUITheme.colorScheme.primary
                } else {
                    COUITheme.colorScheme.onSurfaceSecondary
                },
            )
        }
    }
}

@Composable
private fun MessageRow(entry: MessageEntry) {
    val avatarUrl = remember(entry.avatar) { Formatters.sizedImageUrl(entry.avatar, 132, 132) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row {
            Avatar(url = avatarUrl, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name.ifBlank { "未知用户" },
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.timeText,
                        fontSize = 11.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
                if (entry.title.isNotBlank()) {
                    Text(
                        text = entry.title,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.content.isNotBlank()) {
                    Text(
                        text = entry.content,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 13.sp,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.extra.isNotBlank()) {
                    Text(
                        text = entry.extra,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 11.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }
    }
}

private val MESSAGE_KINDS = listOf(
    MessageKind.REPLY to "回复我的",
    MessageKind.AT to "@我的",
    MessageKind.LIKE to "收到的赞",
    MessageKind.SYSTEM to "系统通知",
)
