package erl.bilico.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SearchFilterTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarSearchBar
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.Sort

/**
 * 便签两行顶栏中搜索框行与胶囊行之间的间距：便签第一行 52dp 里 40dp 搜索框下方留 6dp，
 * 再加 SecondRowGap(11dp) 共 17dp 间隙；此处第一行 44dp 里搜索框下方留 2dp，因此第二行
 * 从第一行底再往下空 15dp，使搜索框与胶囊之间的视觉距离同样保持 17dp。
 */
internal val SearchChipRowGap = 15.dp

/**
 * 推荐页顶栏：搜索框一行，搜索结果态下再挂一行分类胶囊，两行共用一块玻璃。
 *
 * 材质、两行布局与胶囊采样由 coui 的 [SearchFilterTopAppBar] 统一处理：两行结果态默认
 * 使用 `TwoRowTopAppBarDefaults.ExtraHeight`（6dp）与双行渐隐曲线，在第一张结果卡顶边之上
 * 归零；没有结果时不传筛选行，栏高与渐隐带回到单行默认值。这里只负责搜索框本身。
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
    showingResults: Boolean,
    onTabChange: (Int) -> Unit,
    onSortMenuOpen: () -> Unit,
    panelAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    SearchFilterTopAppBar(
        filters = if (showingResults) SearchResultTabs else null,
        selectedFilter = searchState.tab,
        onFilterSelected = onTabChange,
        modifier = modifier,
        firstRowHeight = TitleRowHeight,
        rowGap = SearchChipRowGap,
        leadingIcon = COUIIcons.Sort,
        onLeadingIconClick = onSortMenuOpen,
        containerColor = pageContainerColor(),
        backdrop = LocalBackdrop.current,
        scrollFraction = panelAlpha,
    ) {
        // 整行高 + 顶部对齐：折叠时只平移内容，搜索框在行内的落点与迁移前一致。
        Box(Modifier.fillMaxWidth().height(TitleRowHeight)) {
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
