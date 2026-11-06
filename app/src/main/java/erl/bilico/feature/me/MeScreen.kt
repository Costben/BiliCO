package erl.bilico.feature.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    container: AppContainer,
    nav: NavState,
    topInset: Dp,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val current = account
    var confirmLogout by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = topInset + 12.dp,
            end = 12.dp,
            bottom = 66.dp + navBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (current == null) {
            item(key = "login") { LoginSection(container) }
        } else {
            item(key = "profile") { ProfileCard(current) }
        }

        item(key = "entries") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
            ) {
                ArrowPreference(
                    title = "历史记录",
                    cardListPosition = CardListPosition.Head,
                    onClick = { nav.push(Route.History) },
                )
                ArrowPreference(
                    title = "消息",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.Messages) },
                )
                ArrowPreference(
                    title = "收藏夹",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.Favorites) },
                )
                ArrowPreference(
                    title = "稍后再看",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.WatchLater) },
                )
                ArrowPreference(
                    title = "追番",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.BangumiFollow) },
                )
                ArrowPreference(
                    title = "关注",
                    cardListPosition = CardListPosition.Middle,
                    onClick = { nav.push(Route.Following) },
                )
                ArrowPreference(
                    title = "设置",
                    cardListPosition = CardListPosition.Tail,
                    onClick = { nav.push(Route.Settings) },
                )
            }
        }

        if (current != null) {
            item(key = "logout") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ArrowPreference(
                        title = "退出登录",
                        cardListPosition = CardListPosition.None,
                        onClick = { confirmLogout = true },
                    )
                }
            }
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            message = "会清除本地保存的 Cookie，下次需要重新扫码登录。",
            confirmText = "退出",
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                container.scope.launch { container.session.logout() }
            },
        )
    }
}

@Composable
private fun LoginSection(container: AppContainer) {
    val loginViewModel: LoginViewModel = viewModel {
        LoginViewModel(container.session)
    }
    val loginState by loginViewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(loginViewModel) {
        onDispose { loginViewModel.stop() }
    }
    LoginContent(state = loginState, onRefresh = loginViewModel::refreshQr)
}

@Composable
private fun ProfileCard(account: Account) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatarUrl = remember(account.face) {
                Formatters.sizedImageUrl(account.face, 168, 168)
            }
            Avatar(url = avatarUrl, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = account.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                )
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
