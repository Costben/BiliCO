package erl.bilico.feature.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.SpaceProfile
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LibraryVideoRow
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.components.SectionTitle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SpaceScreen(
    nav: NavState,
    container: AppContainer,
    mid: Long,
) {
    val viewModel: SpaceViewModel = viewModel(key = "space-$mid") {
        SpaceViewModel(container.space, mid)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val size = viewModel.state.value.videos.size
                if (lastVisible != null && size > 0 && lastVisible >= size - 2) {
                    viewModel.loadMore()
                }
            }
    }

    SecondaryPage(
        title = state.profile?.name?.ifBlank { "个人空间" } ?: "个人空间",
        onBack = nav::pop,
        listState = listState,
    ) {
        val profile = state.profile
        if (profile == null) {
            item(key = "state") {
                if (state.error != null) {
                    ErrorState(state.error.orEmpty(), onRetry = viewModel::retry)
                } else {
                    LoadingState()
                }
            }
        } else {
            item(key = "profile") { ProfileCard(profile) }
        }

        item(key = "videos-title") { SectionTitle("投稿", horizontalPadding = 6.dp) }

        if (state.videos.isEmpty() && !state.isLoading) {
            item(key = "empty") {
                EmptyState(if (state.error != null) state.error.orEmpty() else "还没有投稿")
            }
        }

        items(
            count = state.videos.size,
            key = { state.videos[it].bvid },
            contentType = { "space-video" },
        ) { index ->
            val video = state.videos[index]
            LibraryVideoRow(
                video = video,
                onClick = { nav.push(Route.Video(video.bvid)) },
            )
        }

        if (state.isLoadingMore) {
            item(key = "loading-more") { LoadingState() }
        }
    }
}

@Composable
private fun ProfileCard(profile: SpaceProfile) {
    val avatarUrl = remember(profile.face) { Formatters.sizedImageUrl(profile.face, 192, 192) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = avatarUrl, size = 64.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = profile.name.ifBlank { "UID ${profile.mid}" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.sign.ifBlank { "这个人很神秘，什么都没有写" },
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell("粉丝", profile.followersText)
            StatCell("关注", profile.followingText)
            StatCell("获赞", profile.likesText)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = COUITheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 11.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
    Spacer(Modifier.width(8.dp))
}
