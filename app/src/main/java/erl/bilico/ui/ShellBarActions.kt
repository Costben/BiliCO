package erl.bilico.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.NavDynamicFilledIcon
import erl.bilico.ui.components.NavDynamicOutlineIcon
import erl.bilico.ui.components.NavHomeFilledIcon
import erl.bilico.ui.components.NavHomeOutlineIcon
import erl.bilico.ui.components.NavMeFilledIcon
import erl.bilico.ui.components.NavMeOutlineIcon
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Search
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 顶栏左上角头像的直径。 */
private val AvatarSize = 32.dp

/** 底栏双击判定窗口，和系统双击超时一致。 */
private const val BottomBarDoubleTapMillis = 300L

/** 底栏三格的总宽：三格各 50dp、两处间距 8dp、左右内边距各 12dp，加起来 190dp。 */
private val BottomBarWidth = 190.dp

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
internal fun FloatingBottomBar(
    tab: Int,
    onTab: (Int) -> Unit,
    onReselect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 底栏 item 用的是 combinedClickable，没有双击回调：自己记上一次点在同一格的时刻，
    // 判定窗口内再点一次就当双击，回顶刷新当前 tab。
    var lastTapTab by remember { mutableIntStateOf(-1) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val click: (Int) -> Unit = { index ->
        val now = SystemClock.uptimeMillis()
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
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 底栏就三个 tab：推荐、动态、我的。直播是推荐页里的第四个分段。
        // 宽度收在 190dp：三格各 50dp，和 Cobo 底栏的格子一样宽；放开的话每格会用满 70dp，
        // 三个 tab 撑得很散。
        BottomFloatingNavigationBar(modifier = Modifier.width(BottomBarWidth)) {
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
