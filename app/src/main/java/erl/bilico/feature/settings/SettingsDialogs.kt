package erl.bilico.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.Account
import erl.bilico.data.model.SponsorCategory
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.session.StoredSession
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.HomeTabsLayout
import erl.bilico.data.settings.PlayerTopBarLayout
import erl.bilico.di.AppContainer
import erl.bilico.feature.me.AccountSwitchDialog
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.feature.video.PlayerTopBarSettingsDialog
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.ConfirmDialog
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ListPopupColumn
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

/** 单选弹窗：和 Cobo 的设置项同一套组件，胶囊高亮由 ListPopupColumn 自己铺。 */
@Composable
internal fun OptionPicker(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = title,
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!settled) {
                settled = true
                val picked = result
                if (picked != null) onPick(picked) else onDismiss()
            }
        },
    ) {
        Column {
            Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) {
                ListPopupColumn {
                    options.forEachIndexed { index, label ->
                        DropdownImpl(
                            item = DropdownItem(text = label),
                            optionSize = options.size,
                            isSelected = index == selectedIndex,
                            index = index,
                            groupKey = "settings-option",
                            onSelectedIndexChange = {
                                result = index
                                show = false
                            },
                        )
                    }
                }
            }
            DialogButtonBar(
                negative = DialogButtonBarAction(text = "取消", onClick = { show = false }),
                positive = null,
            )
        }
    }
}

@Composable
internal fun SponsorCategoriesDialog(
    enabledIds: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    OverlayDialog(
        show = show,
        title = "跳过片段类型",
        outsideMargin = DpSize(16.dp, 8.dp),
        onDismissRequest = { show = false },
        onDismissFinished = {
            if (!dismissed) {
                dismissed = true
                onDismiss()
            }
        },
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .scrollEndHaptic(),
            ) {
                SponsorCategory.entries.forEachIndexed { index, category ->
                    val pos = when (index) {
                        0 -> CardListPosition.Head
                        SponsorCategory.entries.lastIndex -> CardListPosition.Tail
                        else -> CardListPosition.Middle
                    }
                    SwitchPreference(
                        checked = category.id in enabledIds,
                        onCheckedChange = { checked -> onToggle(category.id, checked) },
                        title = category.label,
                        startAction = {
                            Box(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(category.colorArgb)),
                            )
                        },
                        cardListPosition = pos,
                    )
                }
            }
            DialogButtonBar(
                negative = null,
                positive = DialogButtonBarAction(text = "完成", onClick = { show = false }),
            )
        }
    }
}

@Composable
internal fun SettingsDialogsHost(
    container: AppContainer,
    nav: NavState,
    cdnOption: String,
    danmakuSettings: DanmakuSettings,
    topBarLayoutFullscreen: PlayerTopBarLayout,
    topBarLayoutPortrait: PlayerTopBarLayout,
    homeTabsLayout: HomeTabsLayout,
    sponsorCategories: Set<String>,
    account: Account?,
    sessions: List<StoredSession>,
    pickCdn: Boolean,
    showDanmakuSettings: Boolean,
    showTopBarSettings: Boolean,
    showHomeTabsSettings: Boolean,
    showSponsorCategories: Boolean,
    switchAccounts: Boolean,
    confirmLogout: Boolean,
    onDismissCdn: () -> Unit,
    onDismissDanmaku: () -> Unit,
    onDismissTopBar: () -> Unit,
    onDismissHomeTabs: () -> Unit,
    onDismissSponsorCategories: () -> Unit,
    onDismissSwitchAccounts: () -> Unit,
    onDismissLogout: () -> Unit,
) {
    if (pickCdn) {
        OptionPicker(
            title = "播放线路",
            options = CdnOption.entries.map { it.label },
            selectedIndex = CdnOption.entries.indexOfFirst { it.id == cdnOption }
                .coerceAtLeast(0),
            onPick = { index ->
                container.settings.setCdnOption(CdnOption.entries[index].id)
                onDismissCdn()
            },
            onDismiss = onDismissCdn,
        )
    }

    if (showDanmakuSettings) {
        DanmakuSettingsDialog(
            settings = danmakuSettings,
            repository = container.settings,
            onDismiss = onDismissDanmaku,
        )
    }

    if (showTopBarSettings) {
        PlayerTopBarSettingsDialog(
            fullscreen = topBarLayoutFullscreen,
            portrait = topBarLayoutPortrait,
            onToggle = container.settings.playerTopBar::setVisible,
            onMove = container.settings.playerTopBar::moveItem,
            onDismiss = onDismissTopBar,
        )
    }

    if (showHomeTabsSettings) {
        HomeTabsSettingsDialog(
            layout = homeTabsLayout,
            onToggle = container.settings.feed::setHomeTabVisible,
            onMove = container.settings.feed::moveHomeTab,
            onReset = container.settings.feed::resetHomeTabs,
            onDismiss = onDismissHomeTabs,
        )
    }

    if (showSponsorCategories) {
        SponsorCategoriesDialog(
            enabledIds = sponsorCategories,
            onToggle = container.settings::setSponsorCategoryEnabled,
            onDismiss = onDismissSponsorCategories,
        )
    }

    if (switchAccounts) {
        AccountSwitchDialog(
            sessions = sessions,
            activeMid = account?.mid,
            onDismiss = onDismissSwitchAccounts,
            onSwitch = { mid ->
                onDismissSwitchAccounts()
                container.scope.launch { container.session.switchAccount(mid) }
            },
            onRemove = { mid ->
                container.scope.launch { container.session.removeAccount(mid) }
            },
            onAdd = {
                onDismissSwitchAccounts()
                nav.push(Route.Login)
            },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            message = "只退出当前账号。还有别的已保存账号时会自动换过去，没有的话需要重新扫码登录。",
            confirmText = "退出",
            onDismiss = onDismissLogout,
            onConfirm = {
                onDismissLogout()
                container.scope.launch { container.session.logout() }
            },
        )
    }
}
