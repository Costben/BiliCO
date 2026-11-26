package erl.bilico.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import erl.bilico.di.AppContainer
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.platform.RichBiliWebView
import erl.bilico.platform.openExternalUrl
import erl.bilico.platform.platformMobileWebUserAgent
import erl.bilico.platform.rememberBiliWebViewController
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem
import io.github.suqi8.coui.kmp.theme.COUITheme

private val IN_APP_COOKIE_DOMAINS = listOf(
    "https://www.bilibili.com",
    "https://m.bilibili.com",
    "https://t.bilibili.com",
    "https://api.bilibili.com",
    "https://mall.bilibili.com",
    "https://live.bilibili.com",
    "https://space.bilibili.com",
)

/** 通用应用内 H5 WebView 页面：自动同步 B 站登录 Cookie，支持互动抽奖、活动网页链接等直接访问。 */
@Composable
fun InAppWebScreen(
    container: AppContainer,
    nav: NavState,
    url: String,
    defaultTitle: String = "网页",
) {
    val context = LocalPlatformContext.current
    val controller = rememberBiliWebViewController()
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember(url, defaultTitle) { mutableStateOf(defaultTitle.ifBlank { "网页" }) }
    var currentUrl by remember(url) { mutableStateOf(url) }

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
                url = url,
                initialCookies = remember(container) { container.session.webCookies() },
                cookieDomains = IN_APP_COOKIE_DOMAINS,
                userAgent = platformMobileWebUserAgent,
                controller = controller,
                onProgressChanged = { progress = it },
                onTitleChanged = { title ->
                    if (title.isNotBlank() && !title.startsWith("http") && !title.contains("bilibili.com/")) {
                        pageTitle = title
                    }
                },
                onUrlChanged = { nextUrl ->
                    if (nextUrl.isNotBlank()) currentUrl = nextUrl
                },
                onCloseRequest = { nav.pop() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                        if (!controller.goBack()) nav.pop()
                    }
                },
                actions = {
                    HighlightedIconButton(
                        onClick = { controller.reload() },
                        modifier = Modifier.couiTopAppBarActionItem(),
                    ) {
                        Icon(
                            imageVector = WebRefreshIcon,
                            contentDescription = "刷新",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    HighlightedIconButton(
                        onClick = {
                            val target = controller.currentUrl?.takeIf { it.isNotBlank() } ?: currentUrl
                            openExternalUrl(context, target)
                        },
                        modifier = Modifier.couiTopAppBarActionItem(),
                    ) {
                        Icon(
                            imageVector = OpenInBrowserIcon,
                            contentDescription = "在浏览器中打开",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        }
    }
}

private val WebRefreshIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WebRefresh",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val stroke = SolidColor(Color.Black)
        path(
            stroke = stroke,
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(20f, 12f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 4f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17.66f, 6.34f)
            lineTo(19.6f, 8.2f)
            moveTo(19.6f, 3.8f)
            lineTo(19.6f, 8.2f)
            lineTo(15.2f, 8.2f)
        }
    }.build()
}

private val OpenInBrowserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "OpenInBrowser",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val stroke = SolidColor(Color.Black)
        path(
            stroke = stroke,
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 12f)
            moveTo(3.4f, 12f)
            lineTo(20.6f, 12f)
            moveTo(12f, 3f)
            curveTo(14.5f, 5.6f, 15.8f, 8.7f, 15.8f, 12f)
            curveTo(15.8f, 15.3f, 14.5f, 18.4f, 12f, 21f)
            moveTo(12f, 3f)
            curveTo(9.5f, 5.6f, 8.2f, 8.7f, 8.2f, 12f)
            curveTo(8.2f, 15.3f, 9.5f, 18.4f, 12f, 21f)
        }
    }.build()
}
