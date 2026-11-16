package erl.bilico.feature.comment

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.components.BackIconButton
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

private const val EMOTE_WEB_UA =
    "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro Build/BP2A.250605.031.A2; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/136.0.0.0 Mobile Safari/537.36"

private val EMOTE_COOKIE_DOMAINS = listOf(
    "https://www.bilibili.com",
    "https://m.bilibili.com",
    "https://api.bilibili.com",
    "https://mall.bilibili.com",
)

/**
 * 我的表情管理 H5 页：把当前登录态 Cookie 同步到 WebView，支持查看、添加与管理表情包，
 * 退出返回后由评论面板自动刷新最新表情包列表。
 */
@Composable
fun EmoteManagerScreen(
    container: AppContainer,
    nav: NavState,
    url: String,
) {
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("我的表情") }
    var webView by remember { mutableStateOf<WebView?>(null) }

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
            EmoteManagerWebView(
                url = url,
                cookies = remember(container) { container.session.webCookies() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                onCreated = { webView = it },
                onBackAvailable = { canGoBack = it },
                onProgressChanged = { progress = it },
                onTitleChanged = { title ->
                    if (title.isNotBlank() && !title.startsWith("http") && !title.contains("bilibili.com/")) {
                        pageTitle = title
                    }
                },
                onCloseRequest = { nav.pop() },
            )
        }

        PinnedBarDensity {
            SmallTopAppBar(
                title = pageTitle,
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
private fun EmoteManagerWebView(
    url: String,
    cookies: Map<String, String>,
    modifier: Modifier = Modifier,
    onCreated: (WebView) -> Unit,
    onBackAvailable: (Boolean) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onTitleChanged: (String) -> Unit,
    onCloseRequest: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnBackAvailable by rememberUpdatedState(onBackAvailable)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnTitleChanged by rememberUpdatedState(onTitleChanged)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)

    val webView = remember(url) {
        syncCookiesToWebView(cookies)
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = EMOTE_WEB_UA
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    currentOnProgressChanged(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) currentOnTitleChanged(title)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url ?: return false
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme == "http" || scheme == "https") return false
                    if (scheme == "bilibili" && uri.host == "browser" && uri.path?.contains("close") == true) {
                        currentOnCloseRequest()
                    }
                    return true
                }

                override fun onPageCommitVisible(view: WebView, url: String?) {
                    currentOnBackAvailable(view.canGoBack())
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    currentOnBackAvailable(view.canGoBack())
                }

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String?,
                    isReload: Boolean,
                ) {
                    currentOnBackAvailable(view.canGoBack())
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

private fun syncCookiesToWebView(cookies: Map<String, String>) {
    if (cookies.isEmpty()) return
    runCatching {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        EMOTE_COOKIE_DOMAINS.forEach { domainUrl ->
            cookies.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotEmpty()) {
                    manager.setCookie(domainUrl, "$name=$value; Domain=.bilibili.com; Path=/")
                }
            }
        }
        manager.flush()
    }
}
