package erl.bilico.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.platform.BiliWebView
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import kotlinx.coroutines.launch

private const val BILI_PROFILE_SETTING_URL = "https://account.bilibili.com/account/setting"
private const val PROFILE_WEB_UA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

@Composable
fun WebProfileEditScreen(
    container: AppContainer,
    nav: NavState,
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    val closeAndRefresh: () -> Unit = {
        container.scope.launch {
            container.session.refreshProfile()
            container.memberProfile.fetchEditableProfile()
        }
        nav.pop()
    }

    PlatformBackHandler { closeAndRefresh() }

    val pageColor = pageContainerColor()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = statusBarTop + pinnedHeight(TopAppBarDefaults.SmallTopAppBarCenterHeight)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarHeight)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            key(reloadKey) {
                BiliWebView(
                    url = BILI_PROFILE_SETTING_URL,
                    initialCookies = remember(container) { container.session.webCookies() },
                    userAgent = PROFILE_WEB_UA,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }

        PinnedBarDensity {
            SmallTopAppBar(
                title = "编辑资料",
                modifier = Modifier.align(Alignment.TopCenter),
                color = pageColor,
                showDivider = true,
                navigationIcon = { BackIconButton(onClick = closeAndRefresh) },
                actions = {
                    TextButton(
                        text = "刷新",
                        onClick = { reloadKey++ },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                },
            )
        }
    }
}
