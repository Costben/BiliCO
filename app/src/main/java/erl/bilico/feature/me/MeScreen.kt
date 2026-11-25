package erl.bilico.feature.me

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.feature.login.LoginContent
import erl.bilico.feature.login.LoginViewModel
import erl.bilico.ui.MeIconPalette
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.meEntryIconColor
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.MeBangumiIcon
import erl.bilico.ui.components.MeDownloadsIcon
import erl.bilico.ui.components.MeFavoritesIcon
import erl.bilico.ui.components.MeFollowIcon
import erl.bilico.ui.components.MeHistoryIcon
import erl.bilico.ui.components.MeLogoutIcon
import erl.bilico.ui.components.MeMessageIcon
import erl.bilico.ui.components.MeSettingsIcon
import erl.bilico.ui.components.MeSwitchAccountIcon
import erl.bilico.ui.components.MeWatchLaterIcon
import erl.bilico.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
    listState: LazyListState = rememberLazyListState(),
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val sessions by container.session.sessions.collectAsStateWithLifecycle()
    val unread by container.messages.unread.collectAsStateWithLifecycle()
    val downloadTasks by container.downloads.tasks.collectAsStateWithLifecycle()
    val activeDownloadCount = remember(downloadTasks) { downloadTasks.count { it.isActive } }
    val completedDownloadCount = remember(downloadTasks) { downloadTasks.count { it.isCompleted } }
    val current = account
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    var switchAccounts by remember { mutableStateOf(false) }
    val fallbackOverscroll = rememberOverscrollEffect()
    val fallbackScrollState = rememberScrollableState { 0f }
    val fitsInViewport = !listState.canScrollForward && !listState.canScrollBackward
    BindScrollToTop(listState)

    LaunchedEffect(current?.mid) {
        if (current != null) {
            container.session.refreshProfile()
            container.memberProfile.fetchEditableProfile()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .overscroll(fallbackOverscroll)
            .scrollable(
                state = fallbackScrollState,
                orientation = Orientation.Vertical,
                enabled = fitsInViewport,
                overscrollEffect = fallbackOverscroll,
            ),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = topInset + 12.dp,
                end = 12.dp,
                bottom = 112.dp + navBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (current == null) {
                // 登录态为空但名册里还有号时，切换入口不能跟着消失，否则只剩扫码一条路。
                if (sessions.isNotEmpty()) {
                    item(key = "account-switch") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = CardDefaults.GroupCornerRadius,
                            insideMargin = PaddingValues(0.dp),
                            weight = CardDefaults.GroupWeight,
                        ) {
                            EntryRow(
                                title = "切换账号",
                                icon = MeSwitchAccountIcon,
                                palette = MeIconPalette.Blue,
                                position = CardListPosition.None,
                                onClick = { switchAccounts = true },
                            )
                        }
                    }
                }
            item(key = "login") {
                LoginSection(
                    container = container,
                    onWebLogin = { nav.push(Route.WebLogin) },
                )
            }
        } else {
            item(key = "profile") {
                ProfileCard(
                    account = current,
                    onOpenSpace = { nav.push(Route.Space(current.mid)) },
                    onOpenFollowing = { nav.push(Route.Following) },
                )
            }
        }

        item(key = "entries") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = CardDefaults.GroupCornerRadius,
                insideMargin = PaddingValues(0.dp),
                weight = CardDefaults.GroupWeight,
            ) {
                EntryRow(
                    title = "历史记录",
                    icon = MeHistoryIcon,
                    palette = MeIconPalette.Blue,
                    position = CardListPosition.Head,
                    onClick = { nav.push(Route.History) },
                )
                EntryRow(
                    title = "消息",
                    icon = MeMessageIcon,
                    palette = MeIconPalette.Green,
                    position = CardListPosition.Middle,
                    statusText = unread.total.takeIf { it > 0 }?.toString(),
                    showRedDot = unread.total > 0,
                    onClick = { nav.push(Route.Messages) },
                )
                EntryRow(
                    title = "收藏夹",
                    icon = MeFavoritesIcon,
                    palette = MeIconPalette.Yellow,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.Favorites) },
                )
                EntryRow(
                    title = "稍后再看",
                    icon = MeWatchLaterIcon,
                    palette = MeIconPalette.Orange,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.WatchLater) },
                )
                EntryRow(
                    title = "离线缓存",
                    icon = MeDownloadsIcon,
                    palette = MeIconPalette.Blue,
                    position = CardListPosition.Middle,
                    endActionReplacement = when {
                        activeDownloadCount > 0 -> {
                            {
                                Box(
                                    modifier = Modifier.size(26.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        size = 24.dp,
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = activeDownloadCount.toString(),
                                        fontSize = 10.sp,
                                        lineHeight = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = COUITheme.colorScheme.primary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        completedDownloadCount > 0 -> {
                            {
                                Text(
                                    text = completedDownloadCount.toString(),
                                    style = COUITheme.textStyles.body2,
                                    color = COUITheme.colorScheme.onSurfaceSecondary,
                                    maxLines = 1,
                                )
                            }
                        }

                        else -> null
                    },
                    onClick = { nav.push(Route.Downloads) },
                )
                EntryRow(
                    title = "追番",
                    icon = MeBangumiIcon,
                    palette = MeIconPalette.Yellow,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.BangumiFollow) },
                )
                EntryRow(
                    title = "关注",
                    icon = MeFollowIcon,
                    palette = MeIconPalette.Blue,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.Following) },
                )
                EntryRow(
                    title = "设置",
                    icon = MeSettingsIcon,
                    palette = MeIconPalette.Neutral,
                    position = CardListPosition.Tail,
                    onClick = { nav.push(Route.Settings) },
                )
            }
        }

        if (current != null) {
            item(key = "logout") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = CardDefaults.GroupCornerRadius,
                    insideMargin = PaddingValues(0.dp),
                    weight = CardDefaults.GroupWeight,
                ) {
                    EntryRow(
                        title = "切换账号",
                        icon = MeSwitchAccountIcon,
                        palette = MeIconPalette.Blue,
                        position = CardListPosition.Head,
                        onClick = { switchAccounts = true },
                    )
                    EntryRow(
                        title = "退出登录",
                        icon = MeLogoutIcon,
                        palette = MeIconPalette.Red,
                        position = CardListPosition.Tail,
                        onClick = { confirmLogout = true },
                    )
                }
            }
        }
        }
    }

    if (switchAccounts) {
        AccountSwitchDialog(
            sessions = sessions,
            activeMid = current?.mid,
            onDismiss = { switchAccounts = false },
            onSwitch = { mid ->
                switchAccounts = false
                scope.launch { container.session.switchAccount(mid) }
            },
            onRemove = { mid ->
                scope.launch { container.session.removeAccount(mid) }
            },
            onAdd = {
                switchAccounts = false
                nav.push(Route.Login)
            },
        )
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            message = "只退出当前账号。还有别的已保存账号时会自动换过去，没有的话需要重新扫码或网页登录。",
            confirmText = "退出",
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                container.scope.launch { container.session.logout() }
            },
        )
    }
}

/** 带图标的一行设置项：默认用 COUI 箭头行，传入 [endActionReplacement] 时替换右侧箭头。 */
@Composable
private fun EntryRow(
    title: String,
    icon: ImageVector,
    palette: MeIconPalette,
    position: CardListPosition,
    statusText: String? = null,
    showRedDot: Boolean = false,
    endActionReplacement: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val startIcon: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = meEntryIconColor(palette),
            modifier = Modifier.size(24.dp),
        )
    }
    if (endActionReplacement != null) {
        BasicComponent(
            title = title,
            cardListPosition = position,
            startAction = startIcon,
            endActions = endActionReplacement,
            onClick = onClick,
        )
    } else {
        ArrowPreference(
            title = title,
            statusText = statusText,
            showRedDot = showRedDot,
            cardListPosition = position,
            startAction = startIcon,
            onClick = onClick,
        )
    }
}

@Composable
private fun LoginSection(
    container: AppContainer,
    onWebLogin: () -> Unit,
) {
    val loginViewModel: LoginViewModel = viewModel {
        LoginViewModel(container.session)
    }
    val loginState by loginViewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(loginViewModel) {
        onDispose { loginViewModel.stop() }
    }
    LoginContent(
        state = loginState,
        onRefresh = loginViewModel::refreshQr,
        onWebLogin = onWebLogin,
    )
}
