package erl.bilico.platform

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private val DEFAULT_COOKIE_DOMAINS = listOf(
    "https://www.bilibili.com",
    "https://passport.bilibili.com",
    "https://api.bilibili.com",
    "https://m.bilibili.com",
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun RichBiliWebView(
    url: String,
    initialCookies: Map<String, String>,
    cookieDomains: List<String>,
    clearCookiesOnInit: Boolean,
    userAgent: String,
    controller: BiliWebViewController,
    onProgressChanged: (Int) -> Unit,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onCloseRequest: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val currentOnProgress by rememberUpdatedState(onProgressChanged)
    val currentOnCookies by rememberUpdatedState(onCookiesChanged)
    val currentOnUrl by rememberUpdatedState(onUrlChanged)
    val currentOnTitle by rememberUpdatedState(onTitleChanged)
    val currentOnClose by rememberUpdatedState(onCloseRequest)
    val domains = remember(cookieDomains) { cookieDomains.ifEmpty { DEFAULT_COOKIE_DOMAINS } }

    val webView = remember(url, clearCookiesOnInit) {
        if (clearCookiesOnInit) {
            runCatching {
                val manager = CookieManager.getInstance()
                manager.removeAllCookies(null)
                manager.flush()
            }
        } else {
            syncAndroidWebCookies(initialCookies, domains)
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
            settings.userAgentString = userAgent
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    currentOnProgress(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) currentOnTitle(title)
                }
            }
            webViewClient = object : WebViewClient() {
                private fun emitCookies(pageUrl: String?) {
                    val cookies = readAndroidWebCookies(domains, pageUrl)
                    if (cookies.isNotEmpty()) currentOnCookies(cookies)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url ?: return false
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    if (scheme == "http" || scheme == "https") return false
                    if (scheme == "bilibili" && uri.host == "browser" && uri.path?.contains("close") == true) {
                        currentOnClose()
                    }
                    emitCookies(view.url)
                    return true
                }

                override fun onPageCommitVisible(view: WebView, pageUrl: String?) {
                    controller.canGoBack = view.canGoBack()
                    if (!pageUrl.isNullOrBlank()) {
                        controller.currentUrl = pageUrl
                        currentOnUrl(pageUrl)
                    }
                    emitCookies(pageUrl)
                }

                override fun onPageFinished(view: WebView, pageUrl: String?) {
                    controller.canGoBack = view.canGoBack()
                    if (!pageUrl.isNullOrBlank()) {
                        controller.currentUrl = pageUrl
                        currentOnUrl(pageUrl)
                    }
                    view.title?.takeIf { it.isNotBlank() }?.let(currentOnTitle)
                    emitCookies(pageUrl)
                }

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    pageUrl: String?,
                    isReload: Boolean,
                ) {
                    controller.canGoBack = view.canGoBack()
                    if (!pageUrl.isNullOrBlank()) {
                        controller.currentUrl = pageUrl
                        currentOnUrl(pageUrl)
                    }
                    emitCookies(pageUrl)
                }
            }
            loadUrl(url)
        }
    }

    DisposableEffect(webView, controller, domains) {
        controller.canGoBack = webView.canGoBack()
        controller.currentUrl = webView.url ?: url
        controller.goBackAction = {
            if (webView.canGoBack()) {
                webView.goBack()
                controller.canGoBack = webView.canGoBack()
                true
            } else {
                false
            }
        }
        controller.reloadAction = { webView.reload() }
        controller.readCookiesAction = {
            runCatching { CookieManager.getInstance().flush() }
            readAndroidWebCookies(domains, webView.url)
        }
        onDispose {
            controller.goBackAction = null
            controller.reloadAction = null
            controller.readCookiesAction = null
            controller.canGoBack = false
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
    )
}

private fun syncAndroidWebCookies(cookies: Map<String, String>, domains: List<String>) {
    if (cookies.isEmpty()) return
    runCatching {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        domains.forEach { domainUrl ->
            cookies.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotEmpty()) {
                    manager.setCookie(domainUrl, "$name=$value; Domain=.bilibili.com; Path=/")
                }
            }
        }
        manager.flush()
    }
}

private fun readAndroidWebCookies(domains: List<String>, currentUrl: String?): Map<String, String> {
    val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return emptyMap()
    val result = LinkedHashMap<String, String>()
    val targets = buildList {
        addAll(domains)
        if (!currentUrl.isNullOrBlank()) add(currentUrl)
    }.distinct()
    targets.forEach { domainUrl ->
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
