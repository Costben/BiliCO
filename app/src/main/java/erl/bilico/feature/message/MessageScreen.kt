package erl.bilico.feature.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import erl.bilico.ui.components.SecondaryPagerPage
import erl.bilico.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    val pagerState = rememberPagerState { MESSAGE_KINDS.size }
    val scope = rememberCoroutineScope()

    // 滑停一页就补拉这一类；点标签走的是同一个入口。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.ensureLoaded(MESSAGE_KINDS[it].first) }
    }
    LaunchedEffect(account) {
        if (account != null) viewModel.ensureLoaded(MESSAGE_KINDS[pagerState.settledPage].first)
    }

    SecondaryPagerPage(
        title = "消息",
        onBack = nav::pop,
        pagerState = pagerState,
        header = {
            ChipTabGroup(
                tabs = MESSAGE_KINDS.map { ChipTabData(label = it.second) },
                selectedIndex = pagerState.currentPage,
                onSelectedChange = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
            )
        },
    ) { index, contentTop ->
        val kind = MESSAGE_KINDS[index].first
        MessageList(
            kind = kind,
            state = state.of(kind),
            loggedIn = account != null,
            contentTop = contentTop,
            viewModel = viewModel,
            nav = nav,
        )
    }
}

@Composable
private fun MessageList(
    kind: MessageKind,
    state: MessageKindState,
    loggedIn: Boolean,
    contentTop: Dp,
    viewModel: MessageViewModel,
    nav: NavState,
) {
    val listState = rememberLazyListState()
    val itemCount by rememberUpdatedState(state.items.size)

    LaunchedEffect(kind, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= 0 && itemCount > 0 && lastVisible >= itemCount - 3) {
                    viewModel.loadMore(kind)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = contentTop + 4.dp,
            end = 12.dp,
            bottom = 24.dp + navBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!loggedIn) {
            item(key = "login") {
                EmptyState(
                    text = "登录后查看",
                    action = { TextButton(text = "去登录", onClick = nav::popToMe) },
                )
            }
            return@LazyColumn
        }

        when {
            state.isLoading -> item(key = "state") { LoadingState() }
            state.error != null -> item(key = "state") {
                ErrorState(state.error.orEmpty(), onRetry = { viewModel.reload(kind) })
            }
            state.loaded && state.items.isEmpty() -> item(key = "state") {
                EmptyState("暂时没有新消息")
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
