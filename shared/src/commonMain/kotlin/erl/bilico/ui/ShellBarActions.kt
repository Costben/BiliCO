package erl.bilico.ui

import erl.bilico.platform.uptimeMillis
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import erl.bilico.data.util.Formatters
import erl.bilico.di.LocalAppContainer
import erl.bilico.feature.search.FeedTitleRow
import erl.bilico.feature.search.SearchUiState
import erl.bilico.ui.components.CouiMenuItem
import erl.bilico.ui.components.CouiPopupMenu
import erl.bilico.ui.components.NavDynamicFilledIcon
import erl.bilico.ui.components.NavDynamicOutlineIcon
import erl.bilico.ui.components.NavHomeFilledIcon
import erl.bilico.ui.components.NavHomeOutlineIcon
import erl.bilico.ui.components.NavMeFilledIcon
import erl.bilico.ui.components.NavMeOutlineIcon
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.basic.scrollToTopOnTap
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Search
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 顶栏左上角头像的直径。 */
private val AvatarSize = 32.dp

/** 底栏双击判定窗口，和系统双击超时一致。 */
private const val BottomBarDoubleTapMillis = 300L

/** 顶栏左上角的账号头像；未登录时退回一个中性图标。 */
@Composable
internal fun FeedAvatar(
    url: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizePx = with(LocalDensity.current) { AvatarSize.roundToPx() }
    val model = remember(url, sizePx) { Formatters.sizedImageUrl(url, sizePx, sizePx) }
    Box(
        modifier = modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(COUITheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .testTag("top_avatar"),
        contentAlignment = Alignment.Center,
    ) {
        if (model.isBlank()) {
            Icon(COUIIcons.Contacts, "我的", Modifier.size(20.dp))
        } else {
            AsyncImage(
                model = model,
                contentDescription = "我的",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** 顶栏右上角消息按钮：聚合私信与通知未读总数显示 COUI 红点角标。 */
@Composable
internal fun MessagesActionButton(
    nav: NavState,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val unreadState = LocalAppContainer.current?.messages?.unread?.collectAsStateWithLifecycle()
    val unreadCount = unreadState?.value?.total ?: 0
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        HighlightedIconButton(
            onClick = { nav.push(Route.Messages) },
            enabled = enabled,
            modifier = Modifier
                .couiTopAppBarActionItem()
                .testTag("top_messages"),
        ) {
            Icon(COUIIcons.Email, "消息", Modifier.size(24.dp))
        }
        if (unreadCount > 0) {
            Badge(
                count = unreadCount,
                stroke = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
}

@Composable
internal fun RowScope.TabActions(
    tab: Int,
    nav: NavState,
    onSearch: () -> Unit,
    onEditProfile: () -> Unit = {},
) {
    when (tab) {
        0 -> {
            HighlightedIconButton(
                onClick = onSearch,
                modifier = Modifier
                    .couiTopAppBarActionItem()
                    .testTag("top_search"),
            ) {
                Icon(COUIIcons.Basic.Search, "搜索", Modifier.size(24.dp))
            }
            MessagesActionButton(nav = nav)
        }

        ME_TAB_INDEX -> {
            var meMenuExpanded by remember { mutableStateOf(false) }
            val menuScheme = COUITheme.colorScheme
            Box {
                HighlightedIconButton(
                    onClick = { meMenuExpanded = true },
                    modifier = Modifier
                        .couiTopAppBarActionItem()
                        .testTag("top_me_menu"),
                ) {
                    Icon(COUIIcons.More, "更多", Modifier.size(24.dp))
                }
                CouiPopupMenu(
                    show = meMenuExpanded,
                    onDismiss = { meMenuExpanded = false },
                    alignment = PopupPositionProvider.Align.End,
                    items = listOf(
                        CouiMenuItem(
                            label = "编辑资料",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = COUIIcons.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = menuScheme.onSurfaceContainer,
                                    )
                                }
                            },
                            onClick = onEditProfile,
                        ),
                        CouiMenuItem(
                            label = "设置",
                            icon = { iconModifier ->
                                Box(iconModifier, contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = COUIIcons.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = menuScheme.onSurfaceContainer,
                                    )
                                }
                            },
                            onClick = { nav.push(Route.Settings) },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun PageTitleBar(
    title: String,
    collapsed: () -> Float,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val titleHeightPx = with(LocalDensity.current) { TitleRowHeight.toPx() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        Box(Modifier.fillMaxWidth().height(TitleRowHeight)) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = -collapsed() * titleHeightPx
                            alpha = 1f - collapsed()
                        },
                ) {
                    Text(
                        title,
                        color = COUITheme.colorScheme.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 6.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = TopAppBarDefaults.ActionIconPadding)
                    .graphicsLayer {
                        translationY = -collapsed() * titleHeightPx / 2f
                        alpha = 1f - collapsed()
                    },
                horizontalArrangement = Arrangement.spacedBy(
                    TopAppBarDefaults.ActionMenuItemSpacing,
                    Alignment.End,
                ),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
internal fun FloatingBottomBar(
    tab: Int,
    onTab: (Int) -> Unit,
    onReselect: (Int) -> Unit,
    barBounds: TopBarBounds? = null,
    leftOrbVisible: Boolean = false,
    onLeftOrbClick: () -> Unit = {},
    orbVisible: Boolean = false,
    orbOrigin: ExpandOrigin? = null,
    orbArtworkUrl: String = "",
    orbIsPlaying: Boolean = false,
    onOrbClick: () -> Unit = {},
    onOrbTogglePlay: () -> Unit = {},
    onOrbStop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 底栏 item 用的是 combinedClickable，没有双击回调：自己记上一次点在同一格的时刻，
    // 判定窗口内再点一次就当双击，回顶刷新当前 tab。
    var lastTapTab by remember { mutableIntStateOf(-1) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val click: (Int) -> Unit = { index ->
        val now = uptimeMillis()
        when {
            index != tab -> {
                lastTapTab = -1
                onTab(index)
            }

            lastTapTab == index && now - lastTapTime < BottomBarDoubleTapMillis -> {
                lastTapTab = -1
                onReselect(index)
            }

            else -> {
                lastTapTab = index
                lastTapTime = now
            }
        }
    }

    var orbMenuExpanded by remember { mutableStateOf(false) }

    if (barBounds != null) {
        DisposableEffect(barBounds) {
            onDispose { barBounds.reportBottomBar(0f) }
        }
    }

    val density = LocalDensity.current
    val bottomInset = if (WindowInsets.navigationBars.getBottom(density) > 0) {
        BottomFloatingNavigationBarDefaults.MenuToScreenNaviDistance
    } else {
        0.dp
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = bottomInset),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 预先占住右下角融合球槽位坐标，首帧收起时就有精确落点。
        if (orbOrigin != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BottomFloatingNavigationBarDefaults.MinHeightWithoutPaddingVertical)
                    .padding(horizontal = BottomFloatingAccessoryNavigationBarDefaults.PaddingHorizontal),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .size(BottomFloatingAccessoryNavigationBarDefaults.AccessorySize)
                        .onGloballyPositioned { coords ->
                            orbOrigin.bounds = Rect(
                                coords.positionInRoot(),
                                Size(coords.size.width.toFloat(), coords.size.height.toFloat()),
                            )
                        },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (barBounds != null) Modifier.reportBottomBarTop(barBounds) else Modifier),
            contentAlignment = Alignment.BottomCenter,
        ) {
            BottomFloatingAccessoryNavigationBar(
                startAccessoryVisible = leftOrbVisible,
                endAccessoryVisible = orbVisible,
                startAccessory = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("dynamic_compose_orb")
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = onLeftOrbClick,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = COUIIcons.Add,
                            contentDescription = "发动态",
                            modifier = Modifier.size(24.dp),
                            tint = COUITheme.colorScheme.onSurface,
                        )
                    }
                },
                endAccessory = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("playback_fusion_orb")
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onLongClick = { orbMenuExpanded = true },
                                onClick = onOrbClick,
                            )
                            .drawWithContent {
                                if (orbOrigin != null) {
                                    orbOrigin.refreshKey
                                    orbOrigin.layer.record { this@drawWithContent.drawContent() }
                                    drawLayer(orbOrigin.layer)
                                } else {
                                    drawContent()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PlaybackOrbContent(
                            artworkUrl = orbArtworkUrl,
                            isPlaying = orbIsPlaying,
                        )
                        PlaybackOrbMenu(
                            expanded = orbMenuExpanded,
                            isPlaying = orbIsPlaying,
                            onDismissRequest = { orbMenuExpanded = false },
                            onTogglePlay = onOrbTogglePlay,
                            onStopPlayback = onOrbStop,
                        )
                    }
                },
            ) {
                BottomFloatingNavigationBarItem(
                    selected = tab == 0,
                    onClick = { click(0) },
                    icon = NavHomeOutlineIcon,
                    selectedIcon = NavHomeFilledIcon,
                    label = "推荐",
                    modifier = Modifier.testTag("bottom_tab_feed"),
                )
                BottomFloatingNavigationBarItem(
                    selected = tab == 1,
                    onClick = { click(1) },
                    icon = NavDynamicOutlineIcon,
                    selectedIcon = NavDynamicFilledIcon,
                    label = "动态",
                    modifier = Modifier.testTag("bottom_tab_dynamic"),
                )
                BottomFloatingNavigationBarItem(
                    selected = tab == ME_TAB_INDEX,
                    onClick = { click(ME_TAB_INDEX) },
                    icon = NavMeOutlineIcon,
                    selectedIcon = NavMeFilledIcon,
                    label = "我的",
                    modifier = Modifier.testTag("bottom_tab_me"),
                )
            }
        }
    }
}

@Composable
internal fun MainTitleRow(
    tab: Int,
    nav: NavState,
    collapsed: () -> Float,
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    searchState: SearchUiState,
    onQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchOpen: () -> Unit,
    onSearchCancel: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    showingResults: Boolean,
    onTabChange: (Int) -> Unit,
    onSortMenuOpen: () -> Unit,
    onEditProfile: () -> Unit,
    panelAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = tab,
        animationSpec = tween(MainTabFadeMillis),
        modifier = modifier.scrollToTopOnTap(),
        label = "mainTitleRow",
    ) { page ->
        if (page == 0) {
            FeedTitleRow(
                searchState = searchState,
                onQueryChange = onQueryChange,
                searchExpanded = searchExpanded,
                onSearchOpen = onSearchOpen,
                onSearchCancel = onSearchCancel,
                onSearchSubmit = onSearchSubmit,
                avatarUrl = avatarUrl,
                onAvatarClick = onAvatarClick,
                nav = nav,
                collapsed = collapsed,
                showingResults = showingResults,
                onTabChange = onTabChange,
                onSortMenuOpen = onSortMenuOpen,
                panelAlpha = panelAlpha,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PageTitleBar(
                title = tabTitles.getOrElse(page) { "" },
                collapsed = collapsed,
                actions = { TabActions(page, nav, onSearch = {}, onEditProfile = onEditProfile) },
            )
        }
    }
}
