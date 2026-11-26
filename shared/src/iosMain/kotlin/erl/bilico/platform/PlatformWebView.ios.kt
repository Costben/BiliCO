package erl.bilico.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

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
    var reloadNonce by remember(url) { mutableIntStateOf(0) }
    var latestCookies by remember(url) { mutableStateOf<Map<String, String>>(emptyMap()) }

    DisposableEffect(controller, url) {
        controller.canGoBack = false
        controller.currentUrl = url
        controller.goBackAction = { false }
        controller.reloadAction = {
            if (clearCookiesOnInit) clearWebCookies()
            reloadNonce++
        }
        controller.readCookiesAction = { latestCookies }
        onDispose {
            controller.goBackAction = null
            controller.reloadAction = null
            controller.readCookiesAction = null
        }
    }

    key(reloadNonce) {
        BiliWebView(
            url = url,
            initialCookies = if (clearCookiesOnInit) emptyMap() else initialCookies,
            userAgent = userAgent,
            onCookiesChanged = { map ->
                latestCookies = map
                onCookiesChanged(map)
            },
            onUrlChanged = { nextUrl ->
                if (nextUrl.isNotBlank()) controller.currentUrl = nextUrl
                onUrlChanged(nextUrl)
            },
            onTitleChanged = onTitleChanged,
            onLoadingChanged = { loading -> onProgressChanged(if (loading) 35 else 100) },
            modifier = modifier,
        )
    }
}
