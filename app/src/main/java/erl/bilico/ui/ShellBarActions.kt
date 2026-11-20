package erl.bilico.ui

import android.os.SystemClock
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.NavDynamicFilledIcon
import erl.bilico.ui.components.NavDynamicOutlineIcon
import erl.bilico.ui.components.NavHomeFilledIcon
import erl.bilico.ui.components.NavHomeOutlineIcon
import erl.bilico.ui.components.NavMeFilledIcon
import erl.bilico.ui.components.NavMeOutlineIcon
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Search
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Email
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

@Composable
internal fun RowScope.TabActions(tab: Int, nav: NavState, onSearch: () -> Unit) {
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
            HighlightedIconButton(
                onClick = { nav.push(Route.Messages) },
                modifier = Modifier
                    .couiTopAppBarActionItem()
                    .testTag("top_messages"),
            ) {
                Icon(COUIIcons.Email, "消息", Modifier.size(24.dp))
            }
        }

        ME_TAB_INDEX -> HighlightedIconButton(
            onClick = { nav.push(Route.Settings) },
            modifier = Modifier
                .couiTopAppBarActionItem()
                .testTag("top_settings"),
        ) {
            Icon(COUIIcons.Settings, "设置", Modifier.size(24.dp))
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
    onPrimaryReselect: () -> Unit = {},
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
    // 首页「推荐」按钮单击即触发（未置顶回顶、已置顶刷新）；其余底栏 Tab 保持双击刷新判定。
    var lastTapTab by remember { mutableIntStateOf(-1) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val click: (Int) -> Unit = { index ->
        val now = SystemClock.uptimeMillis()
        when {
            index != tab -> {
                lastTapTab = -1
                onTab(index)
            }

            index == FEED_TAB_INDEX -> {
                lastTapTab = -1
                onPrimaryReselect()
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

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
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
