package erl.bilico.feature.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.chip.ChipTab
import erl.bilico.data.model.SpaceProfile
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.dynamic.DynamicCard
import erl.bilico.ui.AppBarSurface
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LibraryVideoRow
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SearchClearButton
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SpaceScreen(
    nav: NavState,
    container: AppContainer,
    mid: Long,
) {
    val viewModel: SpaceViewModel = viewModel(key = "space-$mid") {
        SpaceViewModel(container.space, container.dynamic, mid)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.selectedTab) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val currentSize = when (viewModel.state.value.selectedTab) {
                    SpaceTab.VIDEOS -> viewModel.state.value.videos.size
                    SpaceTab.DYNAMICS -> viewModel.state.value.filteredDynamics.size
                }
                if (lastVisible != null && currentSize > 0 && lastVisible >= currentSize - 2) {
                    viewModel.loadMore()
                }
            }
    }

    SecondaryPage(
        title = state.profile?.name?.ifBlank { "个人空间" } ?: "个人空间",
        onBack = nav::pop,
        listState = listState,
        topBar = {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val pageBarHeight = statusBarTop + 44.dp
            Box(Modifier.fillMaxWidth()) {
                AppBarSurface(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = pageContainerColor(),
                    scrollFraction = { 1f },
                ) {
                    Box(Modifier.fillMaxWidth().height(pageBarHeight))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarTop)
                        .height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TopAppBarSearchBar(
                        query = state.searchKeyword,
                        onQueryChange = viewModel::updateKeyword,
                        expanded = state.isSearchActive,
                        onExpandedChange = { active ->
                            viewModel.toggleSearch(active)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = if (state.selectedTab == SpaceTab.VIDEOS) "搜索他的投稿" else "搜索他的动态",
                        cancelText = "取消",
                        onSearch = { kw ->
                            viewModel.submitSearch(kw)
                        },
                        onCancel = {
                            viewModel.toggleSearch(false)
                        },
                        gradientColor = Color.Transparent,
                        titleContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BackIconButton(onClick = nav::pop)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = state.profile?.name?.ifBlank { "个人空间" } ?: "个人空间",
                                    style = COUITheme.textStyles.title2,
                                    color = COUITheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        trailingIcon = {
                            if (state.searchKeyword.isNotEmpty()) {
                                SearchClearButton(
                                    onClick = {
                                        viewModel.updateKeyword("")
                                        viewModel.submitSearch("")
                                    },
                                )
                            }
                        },
                    )
                }
            }
        },
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

        item(key = "space-tabs") {
            val backdrop = rememberCouiBackdrop()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpaceTab.entries.forEach { tab ->
                    ChipTab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = tab.title,
                        backdrop = backdrop,
                    )
                }
            }
        }

        when (state.selectedTab) {
            SpaceTab.VIDEOS -> {
                if (state.videos.isEmpty() && !state.isVideosLoading) {
                    item(key = "videos-empty") {
                        EmptyState(if (state.searchKeyword.isNotBlank()) "未找到相关投稿" else "还没有投稿")
                    }
                }

                items(
                    count = state.videos.size,
                    key = { "v-${state.videos[it].bvid}" },
                    contentType = { "space-video" },
                ) { index ->
                    val video = state.videos[index]
                    LibraryVideoRow(
                        video = video,
                        onClick = { nav.push(Route.Video(video.bvid)) },
                    )
                }

                if (state.isVideosLoadingMore) {
                    item(key = "videos-loading-more") { LoadingState() }
                }
            }
            SpaceTab.DYNAMICS -> {
                val dynamicsList = state.filteredDynamics
                if (dynamicsList.isEmpty() && !state.isDynamicsLoading) {
                    item(key = "dynamics-empty") {
                        EmptyState(if (state.searchKeyword.isNotBlank()) "未找到相关动态" else "还没有动态")
                    }
                }

                items(
                    count = dynamicsList.size,
                    key = { "d-${dynamicsList[it].dynamicId}" },
                    contentType = { "space-dynamic" },
                ) { index ->
                    val dyn = dynamicsList[index]
                    DynamicCard(
                        item = dyn,
                        onOpenUser = { userMid -> if (userMid != mid) nav.push(Route.Space(userMid)) },
                        onOpenVideo = { bvid -> nav.push(Route.Video(bvid)) },
                        onOpenImage = { _, _ -> },
                        onOpenDynamic = { target ->
                            val articleId = target.article?.articleId ?: 0L
                            val dynId = target.dynamicId.ifBlank { target.article?.dynamicId.orEmpty() }
                            val headerTitle = target.title.ifBlank { target.authorName }
                            if (articleId > 0L) {
                                nav.push(Route.Article(articleId, headerTitle))
                            } else if (dynId.isNotBlank()) {
                                nav.push(Route.Opus(dynId, headerTitle))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onOpenLive = { roomId -> nav.push(Route.LiveRoom(roomId)) },
                    )
                }

                if (state.isDynamicsLoadingMore) {
                    item(key = "dynamics-loading-more") { LoadingState() }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: SpaceProfile) {
    val avatarUrl = remember(profile.face) { Formatters.sizedImageUrl(profile.face, 192, 192) }
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(url = avatarUrl, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = COUITheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ProfileStatItem("粉丝", profile.followersText)
                        ProfileStatItem("关注", profile.followingText)
                        ProfileStatItem("获赞", profile.likesText)
                    }
                }
            }

            if (profile.sign.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = profile.sign,
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun ProfileStatItem(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = COUITheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
}