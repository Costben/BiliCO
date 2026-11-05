package erl.bilico.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.repository.DynamicFeedType
import erl.bilico.di.AppContainer
import erl.bilico.ui.components.LoadingBox
import erl.bilico.ui.components.MessageState
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun DynamicScreen(
    container: AppContainer,
    onOpenVideo: (String) -> Unit,
    onOpenMe: () -> Unit,
) {
    val viewModel: DynamicViewModel = viewModel {
        DynamicViewModel(container.dynamic, container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()
    val loggedIn = account != null

    LaunchedEffect(loggedIn) { viewModel.onLoginStateChanged(loggedIn) }

    var viewer by remember { mutableStateOf<Pair<List<DynamicImage>, Int>?>(null) }

    Scaffold(topBar = { SmallTopAppBar(title = "动态") }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!loggedIn) {
                MessageState(
                    message = "登录后查看动态",
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        Button(onClick = onOpenMe) { Text("去登录") }
                    },
                )
            } else {
                DynamicContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenVideo = onOpenVideo,
                    onOpenImage = { images, index -> viewer = images to index },
                )
            }
        }
    }

    viewer?.let { (images, index) ->
        DynamicImageViewer(
            images = images,
            initialIndex = index,
            onDismiss = { viewer = null },
        )
    }
}

@Composable
private fun DynamicContent(
    state: DynamicUiState,
    viewModel: DynamicViewModel,
    onOpenVideo: (String) -> Unit,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.items.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FilterRow(filter = state.filter, onSelect = viewModel::setFilter)
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isInitialLoading -> LoadingBox()
                    state.items.isEmpty() && state.error != null -> MessageState(
                        message = state.error.orEmpty(),
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            Button(onClick = viewModel::refresh) { Text("重试") }
                        },
                    )
                    state.items.isEmpty() -> MessageState(
                        message = "暂时没有动态",
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                DynamicCard(
                                    item = item,
                                    onOpenVideo = onOpenVideo,
                                    onOpenImage = onOpenImage,
                                )
                            }
                            if (state.isLoadingMore) {
                                item(key = "loading-more") {
                                    Box(
                                        Modifier.fillMaxWidth().height(56.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.height(20.dp),
                                            color = COUITheme.colorScheme.primary,
                                            strokeWidth = 2.dp,
                                        )
                                    }
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
private fun FilterRow(
    filter: DynamicFeedType,
    onSelect: (DynamicFeedType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(COUITheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            label = "全部",
            active = filter == DynamicFeedType.ALL,
            onClick = { onSelect(DynamicFeedType.ALL) },
        )
        FilterChip(
            label = "视频",
            active = filter == DynamicFeedType.VIDEO,
            onClick = { onSelect(DynamicFeedType.VIDEO) },
        )
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (active) COUITheme.colorScheme.primary else COUITheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        style = COUITheme.textStyles.footnote1,
        color = if (active) COUITheme.colorScheme.onPrimary else COUITheme.colorScheme.onSurfaceSecondary,
    )
}
