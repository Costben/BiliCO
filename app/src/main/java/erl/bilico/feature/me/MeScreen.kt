package erl.bilico.feature.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import erl.bilico.data.model.Account
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.login.LoginContent
import erl.bilico.feature.login.LoginViewModel
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
fun MeScreen(container: AppContainer) {
    val account by container.session.account.collectAsStateWithLifecycle()

    Scaffold(topBar = { SmallTopAppBar(title = "我的") }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = account
            if (current == null) {
                val loginViewModel: LoginViewModel = viewModel {
                    LoginViewModel(container.session)
                }
                val loginState by loginViewModel.state.collectAsStateWithLifecycle()
                DisposableEffect(loginViewModel) {
                    onDispose { loginViewModel.stop() }
                }
                LoginContent(state = loginState, onRefresh = loginViewModel::refreshQr)
            } else {
                AccountContent(
                    account = current,
                    onLogout = {
                        container.scope.launch { container.session.logout() }
                    },
                )
            }
        }
    }
}

@Composable
private fun AccountContent(
    account: Account,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AsyncImage(
            model = Formatters.normalizeImageUrl(account.face),
            contentDescription = "头像",
            modifier = Modifier.size(84.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )

        Spacer(Modifier.height(16.dp))

        Text(text = account.name, style = COUITheme.textStyles.title3)

        Spacer(Modifier.height(4.dp))

        Text(
            text = "UID ${account.mid}",
            style = COUITheme.textStyles.footnote2,
            color = COUITheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onLogout,
        ) {
            Text("退出登录")
        }
    }
}
