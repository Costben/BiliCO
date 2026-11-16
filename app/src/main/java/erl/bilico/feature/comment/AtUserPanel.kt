package erl.bilico.feature.comment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.FollowingUser
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.components.SearchClearButton
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Search
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class AtCandidate(
    val user: FollowingUser,
    val subtitle: String,
    val isFollowing: Boolean,
)

/**
 * `@` 用户面板：
 * - 顶部自带搜索框，输入后立即筛选已加载的关注列表，并防抖检索全站用户（可按昵称 @ 指定人）；
 * - 空搜索时展示已关注用户并随滚动自动翻页加载，避免只停在第一页导致人太少。
 */
@Composable
internal fun AtUserPanel(
    container: AppContainer,
    loggedIn: Boolean,
    followings: List<FollowingUser>,
    followingsLoading: Boolean,
    followingsHasMore: Boolean,
    followingsFailed: Boolean,
    onLoadMoreFollowings: () -> Unit,
    onPick: (FollowingUser) -> Unit,
) {
    if (!loggedIn) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EMPTY_PANEL_HEIGHT)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "登录后才能 @ 用户",
                style = COUITheme.textStyles.footnote2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
        }
        return
    }

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AtCandidate>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchPage by remember { mutableIntStateOf(1) }
    var searchHasMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val trimmed = query.trim()

    LaunchedEffect(trimmed) {
        if (trimmed.isEmpty()) {
            searchResults = emptyList()
            searchLoading = false
            searchError = null
            searchPage = 1
            searchHasMore = false
            return@LaunchedEffect
        }
        searchLoading = true
        searchError = null
        searchPage = 1
        delay(280)
        container.search.searchUsers(trimmed, 1)
            .onSuccess { list ->
                searchResults = list.map { u ->
                    val sub = buildString {
                        if (u.fansText.isNotBlank()) append("${u.fansText}粉丝")
                        if (u.sign.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(u.sign)
                        }
                    }
                    AtCandidate(
                        user = FollowingUser(u.mid, u.name, u.face, u.sign),
                        subtitle = sub,
                        isFollowing = false,
                    )
                }
                searchHasMore = list.size >= 20
                searchLoading = false
            }
            .onFailure { err ->
                searchResults = emptyList()
                searchError = err.message ?: "搜索失败"
                searchHasMore = false
                searchLoading = false
            }
    }

    LaunchedEffect(trimmed, searchPage) {
        if (trimmed.isEmpty() || searchPage <= 1) return@LaunchedEffect
        searchLoading = true
        container.search.searchUsers(trimmed, searchPage)
            .onSuccess { list ->
                val appended = list.map { u ->
                    val sub = buildString {
                        if (u.fansText.isNotBlank()) append("${u.fansText}粉丝")
                        if (u.sign.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(u.sign)
                        }
                    }
                    AtCandidate(
                        user = FollowingUser(u.mid, u.name, u.face, u.sign),
                        subtitle = sub,
                        isFollowing = false,
                    )
                }
                val existingMids = searchResults.mapTo(HashSet()) { it.user.mid }
                searchResults = searchResults + appended.filter { it.user.mid !in existingMids }
                searchHasMore = list.size >= 20
                searchLoading = false
            }
            .onFailure {
                searchHasMore = false
                searchLoading = false
            }
    }

    val displayItems = remember(trimmed, followings, searchResults) {
        if (trimmed.isEmpty()) {
            followings.map { u -> AtCandidate(u, u.sign, isFollowing = true) }
        } else {
            val followingMids = followings.mapTo(HashSet()) { it.mid }
            val localMatches = followings
                .filter { it.name.contains(trimmed, ignoreCase = true) }
                .map { u -> AtCandidate(u, if (u.sign.isNotBlank()) "已关注 · ${u.sign}" else "已关注", isFollowing = true) }
            val localMids = localMatches.mapTo(HashSet()) { it.user.mid }
            val remoteFiltered = searchResults
                .filter { it.user.mid !in localMids }
                .map { cand ->
                    if (cand.user.mid in followingMids) {
                        cand.copy(
                            subtitle = if (cand.subtitle.isNotBlank()) "已关注 · ${cand.subtitle}" else "已关注",
                            isFollowing = true,
                        )
                    } else {
                        cand
                    }
                }
            localMatches + remoteFiltered
        }
    }

    LaunchedEffect(trimmed) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState, trimmed, displayItems.size, followingsHasMore, searchHasMore, followingsLoading, searchLoading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastIdx ->
                if (lastIdx == null || displayItems.isEmpty()) return@collect
                if (lastIdx >= displayItems.size - 4) {
                    if (trimmed.isEmpty()) {
                        if (followingsHasMore && !followingsLoading) onLoadMoreFollowings()
                    } else if (searchHasMore && !searchLoading) {
                        searchPage += 1
                    }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(EMOTE_PANEL_HEIGHT)
            .padding(top = 8.dp),
    ) {
        AtSearchBar(
            query = query,
            onQueryChange = { query = it },
            onSearch = { keyboard?.hide() },
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(thickness = 0.5.dp, color = COUITheme.colorScheme.dividerLine)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(SheetScrollBlocker)
                .overScrollVertical(nestedScrollToParent = false),
            contentAlignment = Alignment.Center,
        ) {
            when {
                displayItems.isNotEmpty() -> {
                    val density = LocalDensity.current
                    val avatarPx = remember(density) { with(density) { (32.dp * 1.5f).toPx().toInt() } }
                    LazyColumn(
                        state = listState,
                        overscrollEffect = null,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(
                            count = displayItems.size,
                            key = { idx -> displayItems[idx].user.mid },
                        ) { index ->
                            val item = displayItems[index]
                            AtUserRow(
                                candidate = item,
                                avatarPx = avatarPx,
                                onClick = {
                                    query = ""
                                    onPick(item.user)
                                },
                            )
                        }
                        if ((trimmed.isEmpty() && followingsLoading) || (trimmed.isNotEmpty() && searchLoading)) {
                            item(key = "at_loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(size = 16.dp)
                                }
                            }
                        }
                    }
                }

                (trimmed.isEmpty() && followingsLoading) || (trimmed.isNotEmpty() && searchLoading) ->
                    CircularProgressIndicator(size = 18.dp)

                trimmed.isNotEmpty() -> Text(
                    text = searchError ?: "未找到相关用户",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )

                followingsFailed -> Text(
                    text = "关注列表加载失败，可直接输入昵称搜索",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )

                else -> Text(
                    text = "暂无关注，可在上方搜索任何用户",
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
private fun AtSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .squircleSurface(
                color = COUITheme.colorScheme.surfaceContainer,
                cornerRadius = 12.dp,
            )
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = COUIIcons.Basic.Search,
            contentDescription = "搜索用户",
            modifier = Modifier.size(16.dp),
            tint = COUITheme.colorScheme.onSurfaceSecondary,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "搜索要 @ 的用户昵称",
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = COUITheme.textStyles.body2.copy(color = COUITheme.colorScheme.onSurface),
                cursorBrush = SolidColor(COUITheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            SearchClearButton(onClick = { onQueryChange("") })
        }
    }
}

@Composable
private fun AtUserRow(
    candidate: AtCandidate,
    avatarPx: Int,
    onClick: () -> Unit,
) {
    val user = candidate.user
    val avatarUrl = remember(user.face, avatarPx) { Formatters.sizedImageUrl(user.face, avatarPx, avatarPx) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = COUITheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = COUITheme.colorScheme.onSurface,
            )
            if (candidate.subtitle.isNotBlank()) {
                Text(
                    text = candidate.subtitle,
                    style = COUITheme.textStyles.footnote2.copy(fontSize = 11.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}
