package erl.bilico.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
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
 * 推荐页顶栏的第一行：前端库那套按钮展开式搜索栏。
 *
 * 只画内容，不画材质也不吃状态栏内边距：单行时由外壳的顶栏铺底，出结果时它和分类胶囊
 * 一起落在同一层玻璃上。
 *
 * @param barExtra 搜索展开时这一行多让出来的高度，收起时传 0。
 * @param collapsed 标题行的折叠进度，只在搜索收起时生效。
 */
@Composable
internal fun SearchRow(
    searchState: SearchUiState,
    searchViewModel: SearchViewModel,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    nav: NavState,
    container: AppContainer,
    collapsed: () -> Float,
    barExtra: Dp,
    modifier: Modifier = Modifier,
) {
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(TitleRowHeight + barExtra)
            .graphicsLayer {
                if (!searchExpanded) {
                    translationY = -collapsed() * titleHeightPx
                    alpha = 1f - collapsed()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        TopAppBarSearchBar(
            query = searchState.query,
            onQueryChange = searchViewModel::onQueryChange,
            expanded = searchExpanded,
            onExpandedChange = { isExpanded ->
                onSearchExpandedChange(isExpanded)
                if (!isExpanded) searchViewModel.reset()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "搜索视频 / 用户 / 专栏",
            cancelText = "取消",
            onSearch = { keyword ->
                container.settings.addSearchHistory(keyword)
                searchViewModel.submit(keyword)
            },
            onCancel = {
                onSearchExpandedChange(false)
                searchViewModel.reset()
            },
            gradientColor = Color.Transparent,
            titleContent = {
                FeedAvatar(
                    url = avatarUrl,
                    onClick = onAvatarClick,
                )
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
                        onClick = { searchViewModel.onQueryChange("") },
                    )
                }
            },
        )
    }
}
