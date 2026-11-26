package erl.bilico.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.platform.RichBiliWebView
import erl.bilico.platform.platformMobileWebUserAgent
import erl.bilico.platform.rememberBiliWebViewController
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

private const val BILI_PROFILE_SETTING_URL = "https://account.bilibili.com/account/setting"

private val PROFILE_COOKIE_DOMAINS = listOf(
    "https://www.bilibili.com",
    "https://account.bilibili.com",
    "https://api.bilibili.com",
    "https://m.bilibili.com",
)

/** B 站网页版个人资料页：原生接口受限或需额外安全验证时的兜底入口，离开时刷新本地账号资料。 */
@Composable
fun WebProfileEditScreen(
    container: AppContainer,
    nav: NavState,
) {
    val controller = rememberBiliWebViewController()
    var progress by remember { mutableIntStateOf(0) }
    val closeAndRefresh: () -> Unit = {
        container.scope.launch {
            container.session.refreshProfile()
            container.memberProfile.fetchEditableProfile()
        }
        nav.pop()
    }

    PlatformBackHandler {
        if (!controller.goBack()) closeAndRefresh()
    }

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
            if (progress in 1..99) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(COUITheme.colorScheme.dividerLine),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress / 100f)
                            .background(COUITheme.colorScheme.primary),
                    )
                }
            }
            RichBiliWebView(
                url = BILI_PROFILE_SETTING_URL,
                initialCookies = remember(container) { container.session.webCookies() },
                cookieDomains = PROFILE_COOKIE_DOMAINS,
                userAgent = platformMobileWebUserAgent,
                controller = controller,
                onProgressChanged = { progress = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }

        PinnedBarDensity {
            SmallTopAppBar(
                title = "编辑资料",
                modifier = Modifier.align(Alignment.TopCenter),
                color = pageColor,
                showDivider = true,
                navigationIcon = {
                    BackIconButton {
                        if (!controller.goBack()) closeAndRefresh()
                    }
                },
                actions = {
                    TextButton(
                        text = "刷新",
                        onClick = { controller.reload() },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                },
            )
        }
    }
}
