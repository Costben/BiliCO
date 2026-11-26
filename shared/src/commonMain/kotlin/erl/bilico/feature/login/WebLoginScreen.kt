package erl.bilico.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.di.AppContainer
import erl.bilico.platform.BiliWebView
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.platform.clearWebCookies
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

private const val BILI_H5_LOGIN_URL = "https://passport.bilibili.com/h5-app/passport/login"
private const val MOBILE_WEB_UA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

@Composable
fun WebLoginScreen(
    container: AppContainer,
    nav: NavState,
) {
    val scope = rememberCoroutineScope()
    var reloadNonce by remember { mutableIntStateOf(0) }
    var latestCookies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var hint by remember {
        mutableStateOf("支持短信验证码与密码登录，登录成功后自动保存返回")
    }

    fun trySyncCookies(manualTrigger: Boolean, captured: Map<String, String> = latestCookies) {
        if (saving) return
        val sessData = captured["SESSDATA"]?.trim().orEmpty()
        if (sessData.isEmpty()) {
            if (manualTrigger) {
                hint = "尚未检测到登录状态，请先在页面中完成登录"
            }
            return
        }
        saving = true
        hint = "检测到登录凭证，正在同步账号信息…"
        scope.launch {
            container.session.loginWithCookies(captured)
                .onSuccess { account ->
                    hint = "已登录：${account.name.ifBlank { account.mid.toString() }}"
                    nav.pop()
                }
                .onFailure { err ->
                    saving = false
                    hint = err.message ?: "读取账号信息失败，请重试"
                }
        }
    }

    PlatformBackHandler { nav.pop() }

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
            androidx.compose.runtime.key(reloadNonce) {
                BiliWebView(
                    url = BILI_H5_LOGIN_URL,
                    initialCookies = emptyMap(),
                    userAgent = MOBILE_WEB_UA,
                    onCookiesChanged = { map ->
                        latestCookies = map
                        if (!map["SESSDATA"].isNullOrBlank()) {
                            trySyncCookies(manualTrigger = false, captured = map)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
            HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(COUITheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                TextButton(
                    text = if (saving) "同步中…" else "完成同步",
                    enabled = !saving,
                    onClick = { trySyncCookies(manualTrigger = true) },
                    modifier = Modifier.testTag("web_login_sync"),
                )
            }
        }

        PinnedBarDensity {
            SmallTopAppBar(
                title = "网页登录",
                modifier = Modifier.align(Alignment.TopCenter),
                color = pageColor,
                showDivider = true,
                navigationIcon = {
                    BackIconButton { nav.pop() }
                },
                actions = {
                    TextButton(
                        text = "刷新",
                        onClick = {
                            clearWebCookies()
                            reloadNonce++
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                },
            )
        }
    }
}
