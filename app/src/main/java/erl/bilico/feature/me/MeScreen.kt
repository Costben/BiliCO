package erl.bilico.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.Account
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.login.LoginContent
import erl.bilico.feature.login.LoginViewModel
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.ConfirmDialog
import erl.bilico.ui.components.navBottomPadding
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.BindScrollToTop
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Album
import io.github.suqi8.coui.kmp.icon.extended.Contacts
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.ListView
import io.github.suqi8.coui.kmp.icon.extended.Playlist
import io.github.suqi8.coui.kmp.icon.extended.Replace
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.icon.extended.Unlock
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val sessions by container.session.sessions.collectAsStateWithLifecycle()
    val current = account
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    var switchAccounts by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    BindScrollToTop(listState)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().scrollEndHaptic(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = topInset + 12.dp,
            end = 12.dp,
            bottom = 66.dp + navBottomPadding(),
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
                            icon = COUIIcons.Replace,
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
            item(key = "profile") { ProfileCard(current) }
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
                    icon = COUIIcons.ListView,
                    position = CardListPosition.Head,
                    onClick = { nav.push(Route.History) },
                )
                EntryRow(
                    title = "消息",
                    icon = COUIIcons.Email,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.Messages) },
                )
                EntryRow(
                    title = "收藏夹",
                    icon = COUIIcons.Favorites,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.Favorites) },
                )
                EntryRow(
                    title = "稍后再看",
                    icon = COUIIcons.Playlist,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.WatchLater) },
                )
                EntryRow(
                    title = "追番",
                    icon = COUIIcons.Album,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.BangumiFollow) },
                )
                EntryRow(
                    title = "关注",
                    icon = COUIIcons.Contacts,
                    position = CardListPosition.Middle,
                    onClick = { nav.push(Route.Following) },
                )
                EntryRow(
                    title = "设置",
                    icon = COUIIcons.Settings,
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
                        icon = COUIIcons.Replace,
                        position = CardListPosition.Head,
                        onClick = { switchAccounts = true },
                    )
                    EntryRow(
                        title = "退出登录",
                        icon = COUIIcons.Unlock,
                        position = CardListPosition.Tail,
                        onClick = { confirmLogout = true },
                    )
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

/** 带图标的一行设置项：COUI 的箭头行，图标塞在 startAction 里。 */
@Composable
private fun EntryRow(
    title: String,
    icon: ImageVector,
    position: CardListPosition,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        cardListPosition = position,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = COUITheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        },
        onClick = onClick,
    )
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

@Composable
private fun ProfileCard(account: Account) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = CardDefaults.GroupCornerRadius,
        weight = CardDefaults.GroupWeight,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatarUrl = remember(account.face) {
                Formatters.sizedImageUrl(account.face, 168, 168)
            }
            Avatar(url = avatarUrl, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = account.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = COUITheme.colorScheme.onSurface,
                    )
                    if (account.vip) {
                        Spacer(Modifier.width(6.dp))
                        VipBadge()
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "UID ${account.mid}",
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}

/** 官方那枚粉色的「大会员」标识。接口只给 vipStatus，拿不到会员等级和到期时间。 */
@Composable
private fun VipBadge() {
    Text(
        text = "大会员",
        modifier = Modifier
            .clip(SquircleShape(4.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFB7299), Color(0xFFFF9DBA))))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        color = Color.White,
    )
}
