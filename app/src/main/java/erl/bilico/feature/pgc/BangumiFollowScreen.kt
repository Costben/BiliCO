package erl.bilico.feature.pgc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SecondaryGridPage
import io.github.suqi8.coui.kmp.basic.TextButton
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun BangumiFollowScreen(
    container: AppContainer,
    nav: NavState,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val viewModel: BangumiFollowViewModel = viewModel {
        BangumiFollowViewModel(container.pgc, container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val coverWidthPx = remember(screenWidthDp, density) {
        with(density) { (screenWidthDp.dp / 2 - 40.dp).roundToPx() }.coerceAtMost(600)
    }
    val coverHeightPx = coverWidthPx * 4 / 3

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    SecondaryGridPage(title = "追番", onBack = nav::pop, gridState = gridState) {
        if (account == null) {
            item(key = "login", span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth()) {
                    EmptyState(
                        text = "登录后查看",
                        action = { TextButton(text = "去登录", onClick = nav::popToMe) },
                    )
                }
            }
            return@SecondaryGridPage
        }

        if (state.items.isEmpty()) {
            item(key = "state", span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth()) {
                    when {
                        state.isLoading -> LoadingState()
                        state.error != null -> ErrorState(state.error.orEmpty()) {
                            viewModel.refresh()
                        }

                        else -> EmptyState("还没有追番")
                    }
                }
            }
        }

        items(
            items = state.items,
            key = { it.seasonId },
            contentType = { "bangumi-card" },
        ) { card ->
            BangumiRankCard(
                card = card,
                coverWidthPx = coverWidthPx,
                coverHeightPx = coverHeightPx,
                onClick = { nav.push(Route.Pgc(card.seasonId, 0L)) },
            )
        }

        if (state.isLoadingMore) {
            item(key = "loading-more", span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth()) { LoadingState() }
            }
        }
    }
}
