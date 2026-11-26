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
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.platform.RichBiliWebView
import erl.bilico.platform.platformMobileWebUserAgent
import erl.bilico.platform.rememberBiliWebViewController
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

private val LOGIN_COOKIE_DOMAINS = listOf(
    "https://www.bilibili.com",
    "https://passport.bilibili.com",
    "https://api.bilibili.com",
    "https://m.bilibili.com",
)

@Composable
fun WebLoginScreen(
    container: AppContainer,
    nav: NavState,
) {
    val scope = rememberCoroutineScope()
    val controller = rememberBiliWebViewController()
    var latestCookies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var hint by remember {
        mutableStateOf("支持短信验证码与密码登录，登录成功后自动保存返回")
    }

    fun trySyncCookies(manualTrigger: Boolean, captured: Map<String, String>? = null) {
        if (saving) return
        val cookies = captured ?: controller.readCookies().ifEmpty { latestCookies }
        val sessData = cookies["SESSDATA"]?.trim().orEmpty()
        val jct = cookies["bili_jct"]?.trim().orEmpty()
        if (sessData.isEmpty()) {
            if (manualTrigger) {
                hint = "尚未检测到登录状态，请先在页面中完成登录"
            }
            return
        }
        if (!manualTrigger && jct.isEmpty()) {
            // 等待跨域名重定向写完 bili_jct，避免只保存 SESSDATA 导致点赞/拉黑/反馈提示「登录后可用」。
            hint = "检测到登录态，正在同步安全校验 Cookie…"
            return
        }
        saving = true
        hint = "检测到登录凭证，正在同步账号信息…"
        scope.launch {
            container.session.loginWithCookies(cookies)
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

    PlatformBackHandler {
        if (!controller.goBack()) nav.pop()
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
            RichBiliWebView(
                url = BILI_H5_LOGIN_URL,
                initialCookies = emptyMap(),
                cookieDomains = LOGIN_COOKIE_DOMAINS,
                clearCookiesOnInit = true,
                userAgent = platformMobileWebUserAgent,
                controller = controller,
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
                    BackIconButton {
                        if (!controller.goBack()) nav.pop()
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
