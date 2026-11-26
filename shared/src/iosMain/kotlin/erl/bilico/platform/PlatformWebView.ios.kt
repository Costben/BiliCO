package erl.bilico.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.timeIntervalSince1970
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKHTTPCookieStoreObserverProtocol
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private class IosWebViewHolder {
    var webView: WKWebView? = null
    var cookieStore: WKHTTPCookieStore? = null
    var latestCookies: Map<String, String> = emptyMap()
    var disposed: Boolean = false
}

@OptIn(ExperimentalForeignApi::class)
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
    var reloadNonce by remember(url, clearCookiesOnInit) { mutableIntStateOf(0) }

    key(url, clearCookiesOnInit, reloadNonce) {
        IosRichBiliWebViewInstance(
            url = url,
            initialCookies = initialCookies,
            clearCookiesOnInit = clearCookiesOnInit,
            userAgent = userAgent,
            controller = controller,
            onProgressChanged = onProgressChanged,
            onCookiesChanged = onCookiesChanged,
            onUrlChanged = onUrlChanged,
            onTitleChanged = onTitleChanged,
            onCloseRequest = onCloseRequest,
            onRequestRecreate = { reloadNonce++ },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosRichBiliWebViewInstance(
    url: String,
    initialCookies: Map<String, String>,
    clearCookiesOnInit: Boolean,
    userAgent: String,
    controller: BiliWebViewController,
    onProgressChanged: (Int) -> Unit,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onCloseRequest: () -> Unit,
    onRequestRecreate: () -> Unit,
    modifier: Modifier,
) {
    val currentOnProgress by rememberUpdatedState(onProgressChanged)
    val currentOnCookies by rememberUpdatedState(onCookiesChanged)
    val currentOnUrl by rememberUpdatedState(onUrlChanged)
    val currentOnTitle by rememberUpdatedState(onTitleChanged)
    val currentOnClose by rememberUpdatedState(onCloseRequest)
    val currentOnRecreate by rememberUpdatedState(onRequestRecreate)

    val holder = remember { IosWebViewHolder() }

    fun collectCookiesFrom(store: WKHTTPCookieStore?) {
        val targetStore = store ?: return
        targetStore.getAllCookies { list ->
            dispatch_async(dispatch_get_main_queue()) {
                if (holder.disposed) return@dispatch_async
                val now = NSDate().timeIntervalSince1970
                val map = LinkedHashMap<String, String>()
                list?.forEach { raw ->
                    val cookie = raw as? NSHTTPCookie ?: return@forEach
                    val domain = cookie.domain.lowercase().trimStart('.')
                    if (domain == "bilibili.com" || domain.endsWith(".bilibili.com")) {
                        val expires = cookie.expiresDate?.timeIntervalSince1970
                        if (expires != null && expires <= now) return@forEach
                        val name = cookie.name.trim()
                        val value = cookie.value
                        if (name.isNotEmpty() && value.isNotEmpty()) {
                            map[name] = value
                        }
                    }
                }
                holder.latestCookies = map
                if (map.isNotEmpty()) {
                    currentOnCookies(map)
                }
            }
        }
    }

    fun syncMetadata(webView: WKWebView) {
        if (holder.disposed) return
        controller.canGoBack = webView.canGoBack
        webView.URL?.absoluteString?.takeIf { it.isNotBlank() }?.let { pageUrl ->
            controller.currentUrl = pageUrl
            currentOnUrl(pageUrl)
        }
        webView.title?.takeIf { it.isNotBlank() }?.let { title ->
            currentOnTitle(title)
        }
    }

    val cookieObserver = remember {
        object : NSObject(), WKHTTPCookieStoreObserverProtocol {
            override fun cookiesDidChangeInCookieStore(cookieStore: WKHTTPCookieStore) {
                if (holder.disposed) return
                collectCookiesFrom(cookieStore)
            }
        }
    }

    val navDelegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit,
            ) {
                val request = decidePolicyForNavigationAction.request
                val targetUrl = request.URL
                val scheme = targetUrl?.scheme?.lowercase().orEmpty()
                if (scheme.isEmpty() || scheme == "http" || scheme == "https" ||
                    scheme == "about" || scheme == "blob" || scheme == "data"
                ) {
                    if (decidePolicyForNavigationAction.targetFrame == null &&
                        (scheme == "http" || scheme == "https")
                    ) {
                        webView.loadRequest(request)
                        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                    } else {
                        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                    }
                    return
                }
                if (scheme == "bilibili" && targetUrl?.host == "browser" &&
                    targetUrl.path?.contains("close") == true
                ) {
                    currentOnClose()
                }
                collectCookiesFrom(webView.configuration.websiteDataStore.httpCookieStore)
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
                if (holder.disposed) return
                currentOnProgress(20)
                syncMetadata(webView)
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didCommitNavigation: WKNavigation?) {
                if (holder.disposed) return
                currentOnProgress(65)
                syncMetadata(webView)
                collectCookiesFrom(webView.configuration.websiteDataStore.httpCookieStore)
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                if (holder.disposed) return
                currentOnProgress(100)
                syncMetadata(webView)
                collectCookiesFrom(webView.configuration.websiteDataStore.httpCookieStore)
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailNavigation: WKNavigation?,
                withError: NSError,
            ) {
                if (holder.disposed) return
                currentOnProgress(100)
                syncMetadata(webView)
                collectCookiesFrom(webView.configuration.websiteDataStore.httpCookieStore)
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: WKNavigation?,
                withError: NSError,
            ) {
                if (holder.disposed) return
                currentOnProgress(100)
                syncMetadata(webView)
                collectCookiesFrom(webView.configuration.websiteDataStore.httpCookieStore)
            }
        }
    }

    val uiDelegate = remember {
        object : NSObject(), WKUIDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                createWebViewWithConfiguration: WKWebViewConfiguration,
                forNavigationAction: WKNavigationAction,
                windowFeatures: WKWindowFeatures,
            ): WKWebView? {
                val request = forNavigationAction.request
                val scheme = request.URL?.scheme?.lowercase().orEmpty()
                if (scheme == "http" || scheme == "https") {
                    webView.loadRequest(request)
                }
                return null
            }
        }
    }

    DisposableEffect(controller, url, clearCookiesOnInit) {
        controller.canGoBack = holder.webView?.canGoBack == true
        controller.currentUrl = holder.webView?.URL?.absoluteString ?: url
        controller.goBackAction = {
            val wv = holder.webView
            if (wv != null && wv.canGoBack) {
                wv.goBack()
                controller.canGoBack = wv.canGoBack
                true
            } else {
                false
            }
        }
        controller.reloadAction = {
            if (clearCookiesOnInit) {
                currentOnRecreate()
            } else {
                val wv = holder.webView
                if (wv?.URL != null) {
                    wv.reload()
                } else {
                    NSURL.URLWithString(url)?.let { wv?.loadRequest(NSURLRequest.requestWithURL(it)) }
                }
            }
        }
        controller.readCookiesAction = {
            collectCookiesFrom(holder.cookieStore)
            holder.latestCookies
        }
        onDispose {
            controller.goBackAction = null
            controller.reloadAction = null
            controller.readCookiesAction = null
            controller.canGoBack = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            holder.disposed = true
            holder.cookieStore?.removeObserver(cookieObserver)
            holder.cookieStore = null
            holder.webView?.let { wv ->
                wv.stopLoading()
                wv.navigationDelegate = null
                wv.UIDelegate = null
            }
            holder.webView = null
        }
    }

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration().apply {
                // 需要清空登录态初始化时（如网页登录）直接挂载全新非持久化 DataStore，
                // 彻底隔离旧账号 Cookie，避免异步清理未落盘就被读取导致立刻自动登录弹回。
                websiteDataStore = if (clearCookiesOnInit) {
                    WKWebsiteDataStore.nonPersistentDataStore()
                } else {
                    WKWebsiteDataStore.defaultDataStore()
                }
                allowsInlineMediaPlayback = true
            }
            val store = config.websiteDataStore.httpCookieStore
            holder.cookieStore = store
            store.addObserver(cookieObserver)

            val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                customUserAgent = userAgent
                navigationDelegate = navDelegate
                UIDelegate = uiDelegate
                allowsBackForwardNavigationGestures = false
            }
            holder.webView = webView

            val targetUrl = NSURL.URLWithString(url)
            if (targetUrl != null) {
                val cookiesToSet = if (clearCookiesOnInit) {
                    emptyList()
                } else {
                    initialCookies.mapNotNull { (name, value) ->
                        if (name.isBlank() || value.isBlank()) return@mapNotNull null
                        val props: Map<Any?, *> = mapOf(
                            NSHTTPCookieName to name,
                            NSHTTPCookieValue to value,
                            NSHTTPCookieDomain to ".bilibili.com",
                            NSHTTPCookiePath to "/",
                        )
                        NSHTTPCookie.cookieWithProperties(props)
                    }
                }
                if (cookiesToSet.isEmpty()) {
                    webView.loadRequest(NSURLRequest.requestWithURL(targetUrl))
                } else {
                    var remaining = cookiesToSet.size
                    cookiesToSet.forEach { cookie ->
                        store.setCookie(cookie) {
                            dispatch_async(dispatch_get_main_queue()) {
                                remaining--
                                if (remaining == 0 && !holder.disposed) {
                                    webView.loadRequest(NSURLRequest.requestWithURL(targetUrl))
                                }
                            }
                        }
                    }
                }
            }
            webView
        },
        modifier = modifier,
    )
}
