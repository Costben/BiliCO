package erl.bilico.feature.login

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import erl.bilico.di.AppContainer
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

private const val BILI_H5_LOGIN_URL =
    "https://passport.bilibili.com/h5-app/passport/login"

private const val MOBILE_WEB_UA =
    "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro Build/BP2A.250605.031.A2; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/136.0.0.0 Mobile Safari/537.36"

private val BILI_COOKIE_DOMAINS = listOf(
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
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var saving by remember { mutableStateOf(false) }
    var hint by remember {
        mutableStateOf("支持短信验证码与密码登录，登录成功后自动保存返回")
    }

    fun trySyncCookies(manualTrigger: Boolean) {
        if (saving) return
        CookieManager.getInstance().flush()
        val cookies = readBiliWebViewCookies()
        val sessData = cookies["SESSDATA"]?.trim().orEmpty()
        if (sessData.isEmpty()) {
            if (manualTrigger) {
                hint = "尚未检测到登录状态，请先在页面中完成登录"
            }
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

    BackHandler {
        if (canGoBack) webView?.goBack() else nav.pop()
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
            BiliLoginWebView(
                url = BILI_H5_LOGIN_URL,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onCreated = { webView = it },
                onBackAvailable = { canGoBack = it },
                onCheckCookie = { trySyncCookies(manualTrigger = false) },
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
                        if (canGoBack) webView?.goBack() else nav.pop()
                    }
                },
                actions = {
                    TextButton(
                        text = "刷新",
                        onClick = { webView?.reload() },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BiliLoginWebView(
    url: String,
    modifier: Modifier = Modifier,
    onCreated: (WebView) -> Unit,
    onBackAvailable: (Boolean) -> Unit,
    onCheckCookie: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnBackAvailable by rememberUpdatedState(onBackAvailable)
    val currentOnCheckCookie by rememberUpdatedState(onCheckCookie)

    val webView = remember {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = MOBILE_WEB_UA
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val scheme = request.url?.scheme?.lowercase().orEmpty()
                    if (scheme != "http" && scheme != "https") {
                        currentOnCheckCookie()
                        return true
                    }
                    return false
                }

                override fun onPageCommitVisible(view: WebView, url: String?) {
                    currentOnBackAvailable(view.canGoBack())
                    currentOnCheckCookie()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    currentOnBackAvailable(view.canGoBack())
                    currentOnCheckCookie()
                }

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String?,
                    isReload: Boolean,
                ) {
                    currentOnBackAvailable(view.canGoBack())
                    currentOnCheckCookie()
                }
            }
            loadUrl(url)
        }
    }

    DisposableEffect(webView) {
        onCreated(webView)
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
    )
}

private fun readBiliWebViewCookies(): Map<String, String> {
    val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return emptyMap()
    val result = LinkedHashMap<String, String>()
    BILI_COOKIE_DOMAINS.forEach { domainUrl ->
        val raw = runCatching { manager.getCookie(domainUrl) }.getOrNull().orEmpty()
        raw.split(';').forEach { segment ->
            val trimmed = segment.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val key = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    result[key] = value
                }
            }
        }
    }
    return result
}
