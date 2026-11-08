package erl.bilico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Search
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 顶栏左上角头像的直径。 */
private val AvatarSize = 32.dp

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
        0 -> HighlightedIconButton(
            onClick = onSearch,
            modifier = Modifier
                .couiTopAppBarActionItem()
                .testTag("top_search"),
        ) {
            Icon(COUIIcons.Basic.Search, "搜索", Modifier.size(24.dp))
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
