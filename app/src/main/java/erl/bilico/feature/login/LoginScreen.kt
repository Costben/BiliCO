package erl.bilico.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.SecondaryPage

/**
 * 单独的登录页：给已经登录的人「添加账号」用。
 * 进来之后账号一换人（或者从游客登录成功）就自动退回上一页。
 */
@Composable
fun LoginScreen(
    container: AppContainer,
    nav: NavState,
) {
    val account by container.session.account.collectAsStateWithLifecycle()
    val viewModel: LoginViewModel = viewModel {
        LoginViewModel(container.session)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entryMid = remember { account?.mid }

    DisposableEffect(viewModel) {
        onDispose { viewModel.stop() }
    }
    LaunchedEffect(account) {
        val current = account ?: return@LaunchedEffect
        if (current.mid != entryMid) nav.pop()
    }

    SecondaryPage(title = "登录", onBack = nav::pop) {
        item(key = "login") {
            LoginContent(
                state = state,
                onRefresh = viewModel::refreshQr,
                onWebLogin = { nav.push(Route.WebLogin) },
            )
        }
    }
}
