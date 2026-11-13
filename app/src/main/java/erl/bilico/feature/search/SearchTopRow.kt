package erl.bilico.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import erl.bilico.ui.FeedAvatar
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.TitleRowHeight
import erl.bilico.ui.components.SearchClearButton
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Email

/**
 * 推荐页顶栏标题行：1:1 对齐 Cobo COUI 的 `FeedTitleRow` 结构。
 *
 * 挂在最顶层（Layer 5），常态使用首页 `backdrop`，搜索面板挂载期间切换为搜索面板的 `searchBackdrop`；
 * 高度固定为 [TitleRowHeight]（44dp），滚动折叠时只折掉头像，搜索展开期间锁定不折叠。
 */
@Composable
internal fun FeedTitleRow(
    searchState: SearchUiState,
    onQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchOpen: () -> Unit,
    onSearchCancel: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    nav: NavState,
    collapsed: () -> Float,
) {
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TitleRowHeight),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -collapsed() * titleHeightPx / 2f
                        alpha = 1f - collapsed()
                    },
            ) {
                TopAppBarSearchBar(
                    query = searchState.query,
                    onQueryChange = onQueryChange,
                    expanded = searchExpanded,
                    onExpandedChange = { isExpanded ->
                        if (isExpanded) onSearchOpen() else onSearchCancel()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            val enter = event.key == Key.Enter ||
                                event.key == Key.NumPadEnter ||
                                event.key == Key.Search
                            if (searchExpanded && event.type == KeyEventType.KeyDown && enter) {
                                onSearchSubmit(searchState.query)
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = "搜索视频 / 用户 / 专栏",
                    cancelText = "取消",
                    onSearch = onSearchSubmit,
                    onCancel = onSearchCancel,
                    enabled = searchExpanded || collapsed() < 0.95f,
                    searchButtonContentDescription = "搜索",
                    gradientColor = Color.Transparent,
                    titleContent = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(TitleRowHeight)
                                .clipToBounds(),
                        ) {
                            FeedAvatar(
                                url = avatarUrl,
                                onClick = onAvatarClick,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .graphicsLayer {
                                        translationY = -collapsed() * titleHeightPx / 2f
                                    },
                            )
                        }
                    },
                    extraActions = {
                        HighlightedIconButton(
                            onClick = { nav.push(Route.Messages) },
                            enabled = !searchExpanded,
                            modifier = Modifier.couiTopAppBarActionItem(),
                        ) {
                            Icon(
                                imageVector = COUIIcons.Email,
                                contentDescription = "消息",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    trailingIcon = {
                        if (searchState.query.isNotEmpty()) {
                            SearchClearButton(
                                onClick = { onQueryChange("") },
                            )
                        }
                    },
                )
            }
        }
    }
}
