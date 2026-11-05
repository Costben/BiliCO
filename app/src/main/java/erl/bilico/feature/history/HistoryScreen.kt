package erl.bilico.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.HistoryItem
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.CoverImage
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
fun HistoryScreen(
    container: AppContainer,
    nav: NavState,
) {
    val loggedIn by container.session.account.collectAsStateWithLifecycle()
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(container.history) }
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

    SecondaryPage(
        title = "历史记录",
        onBack = nav::pop,
        listState = listState,
    ) {
        if (loggedIn == null) {
            item(key = "login") {
                EmptyState(
                    text = "登录后查看",
                    action = {
                        TextButton(text = "去登录", onClick = nav::popToMe)
                    },
                )
            }
            return@SecondaryPage
        }
        if (state.items.isEmpty()) {
            item(key = "state") {
                when {
                    state.isInitialLoading -> LoadingState()
                    state.error != null -> ErrorState(state.error.orEmpty(), onRetry = viewModel::refresh)
                    else -> EmptyState("还没有观看记录")
                }
            }
        }
        items(
            count = state.items.size,
            key = { state.items[it].bvid },
            contentType = { "history-row" },
        ) { index ->
            val item = state.items[index]
            HistoryRow(
                item = item,
                onClick = { nav.push(Route.Video(item.bvid, item.progressSec.toLong())) },
            )
        }
        if (state.items.isNotEmpty() && state.isLoadingMore) {
            item(key = "loading-more") { LoadingState() }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    val density = LocalDensity.current
    val coverWidthPx = remember(density) { with(density) { 132.dp.roundToPx() } }
    val coverHeightPx = remember(coverWidthPx) { coverWidthPx * 9 / 16 }
    val coverUrl = remember(item.cover, coverWidthPx, coverHeightPx) {
        Formatters.sizedImageUrl(item.cover, coverWidthPx, coverHeightPx)
    }
    val playedFraction = remember(item.progressSec, item.durationSec) {
        if (item.durationSec > 0 && item.progressSec > 0) {
            (item.progressSec.toFloat() / item.durationSec).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(8.dp),
        onClick = onClick,
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                CoverImage(url = coverUrl, modifier = Modifier.fillMaxSize())
                if (playedFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(playedFraction)
                            .height(3.dp)
                            .background(COUITheme.colorScheme.primary),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
                    .height(132.dp * 9 / 16),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(item.upName.ifBlank { "未知 UP" })
                        val time = Formatters.formatViewDate(item.viewAtSec)
                        if (time.isNotBlank()) append(" · ").append(time)
                    },
                    fontSize = 11.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
