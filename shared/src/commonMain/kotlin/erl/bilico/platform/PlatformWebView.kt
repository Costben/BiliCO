package erl.bilico.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** 跨平台 WebView 控制器：支持前进/后退检测、页内返回、原地刷新以及即时读取当前 Cookie。 */
@Stable
class BiliWebViewController {
    var canGoBack by mutableStateOf(false)
        internal set

    var currentUrl by mutableStateOf<String?>(null)
        internal set

    internal var goBackAction: (() -> Boolean)? = null
    internal var reloadAction: (() -> Unit)? = null
    internal var readCookiesAction: (() -> Map<String, String>)? = null

    fun goBack(): Boolean = goBackAction?.invoke() == true

    fun reload() {
        reloadAction?.invoke()
    }

    fun readCookies(): Map<String, String> = readCookiesAction?.invoke().orEmpty()
}

@Composable
fun rememberBiliWebViewController(): BiliWebViewController = remember { BiliWebViewController() }

/**
 * 完整功能内嵌 WebView：
 * Android 端恢复页内返回、加载进度、多域名 Cookie 同步/读取、zoom 与非 http(s) scheme 拦截；
 * iOS 端复用现有 [BiliWebView] 并接入刷新控制。
 */
@Composable
expect fun RichBiliWebView(
    url: String,
    initialCookies: Map<String, String> = emptyMap(),
    cookieDomains: List<String> = emptyList(),
    clearCookiesOnInit: Boolean = false,
    userAgent: String = platformMobileWebUserAgent,
    controller: BiliWebViewController = rememberBiliWebViewController(),
    onProgressChanged: (Int) -> Unit = {},
    onCookiesChanged: (Map<String, String>) -> Unit = {},
    onUrlChanged: (String) -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onCloseRequest: () -> Unit = {},
    modifier: Modifier = Modifier,
)
