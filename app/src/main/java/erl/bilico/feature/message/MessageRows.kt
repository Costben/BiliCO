package erl.bilico.feature.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.ImSessionUi
import erl.bilico.data.model.MessageEntry
import erl.bilico.data.model.MessageKind
import erl.bilico.data.util.Formatters
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.BellOutlineIcon
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SearchResultContainer
import io.github.suqi8.coui.kmp.basic.SearchResultContainerDefaults
import io.github.suqi8.coui.kmp.basic.SearchResultsRiseTrigger
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.rememberSearchResultsTransitionState
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun MessageRow(
    entry: MessageEntry,
    onClick: (() -> Unit)?,
    onOpenUser: (() -> Unit)?,
) {
    val avatarUrl = remember(entry.avatar) { Formatters.sizedImageUrl(entry.avatar, 132, 132) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row {
            if (avatarUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(COUITheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = BellOutlineIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            } else {
                Avatar(
                    url = avatarUrl,
                    size = 40.dp,
                    modifier = if (onOpenUser != null) {
                        Modifier.clip(CircleShape).clickable(onClick = onOpenUser)
                    } else {
                        Modifier
                    },
                )
            }
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

@Composable
internal fun SessionList(
    state: MessageUiState,
    loggedIn: Boolean,
    contentTop: Dp,
    viewModel: MessageViewModel,
    nav: NavState,
) {
    val listState = rememberLazyListState()
    val itemCount by rememberUpdatedState(state.sessions.size)
    val transitionSpec = SearchResultContainerDefaults.transitionSpec(
        riseTrigger = SearchResultsRiseTrigger.OnlyAfterLoading,
    )
    val transitionState = rememberSearchResultsTransitionState(
        showingResults = true,
        loading = loggedIn && state.isSessionsLoading,
        queryKey = state.sessionsLoaded,
        spec = transitionSpec,
    )

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= 0 && itemCount > 0 && lastVisible >= itemCount - 3) {
                    viewModel.loadMore(MessageKind.SESSION)
                }
            }
    }

    SearchResultContainer(
        state = transitionState,
        modifier = Modifier.fillMaxSize(),
        loadingTopPadding = contentTop,
        loadingContent = { LoadingState() },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = contentTop,
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

            if (!resultsVisible) return@LazyColumn

            when {
                state.sessionsError != null -> item(key = "state") {
                    ErrorState(state.sessionsError.orEmpty(), onRetry = { viewModel.reload(MessageKind.SESSION) })
                }
                state.sessionsLoaded && state.sessions.isEmpty() -> item(key = "state") {
                    EmptyState("暂无私信消息")
                }
            }

            items(
                count = state.sessions.size,
                key = { state.sessions[it].talkerId },
                contentType = { "session" },
            ) { index ->
                val session = state.sessions[index]
                SessionRow(
                    session = session,
                    onClick = {
                        nav.push(Route.Chat(session.talkerId, session.name, session.face))
                    },
                    onOpenUser = session.talkerId.takeIf { it > 0L }?.let { mid ->
                        { nav.push(Route.Space(mid)) }
                    },
                )
            }

            if (state.isSessionsLoadingMore) {
                item(key = "loading-more") { LoadingState() }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ImSessionUi,
    onClick: () -> Unit,
    onOpenUser: (() -> Unit)?,
) {
    val avatarUrl = remember(session.face) { Formatters.sizedImageUrl(session.face, 132, 132) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(
                    url = avatarUrl,
                    size = 44.dp,
                    modifier = if (onOpenUser != null) {
                        Modifier.clip(CircleShape).clickable(onClick = onOpenUser)
                    } else {
                        Modifier
                    },
                )
                if (session.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFA3E3E)),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session.name.ifBlank { "用户 ${session.talkerId}" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (session.timeText.isNotBlank()) {
                        Text(
                            text = session.timeText,
                            fontSize = 11.sp,
                            color = COUITheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (session.lastMessage.isNotBlank()) {
                    Text(
                        text = session.lastMessage,
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 13.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
