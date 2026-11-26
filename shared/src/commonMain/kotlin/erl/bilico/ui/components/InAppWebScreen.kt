package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import erl.bilico.platform.BiliWebView
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.platform.openExternalUrl
import erl.bilico.ui.NavState
import erl.bilico.ui.PinnedBarDensity
import erl.bilico.ui.pageContainerColor
import erl.bilico.ui.pinnedHeight
import io.github.suqi8.coui.kmp.basic.HighlightedIconButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.couiTopAppBarActionItem

private const val IN_APP_WEB_UA =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

@Composable
fun InAppWebScreen(
    container: AppContainer,
    nav: NavState,
    url: String,
    defaultTitle: String = "网页",
) {
    val context = LocalPlatformContext.current
    var pageTitle by remember(url, defaultTitle) { mutableStateOf(defaultTitle.ifBlank { "网页" }) }
    var currentUrl by remember(url) { mutableStateOf(url) }
    var reloadKey by remember { mutableIntStateOf(0) }

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
            key(reloadKey) {
                BiliWebView(
                    url = url,
                    initialCookies = remember(container) { container.session.webCookies() },
                    userAgent = IN_APP_WEB_UA,
                    onTitleChanged = { title ->
                        if (title.isNotBlank() && !title.startsWith("http") && !title.contains("bilibili.com/")) {
                            pageTitle = title
                        }
                    },
                    onUrlChanged = { nextUrl ->
                        if (nextUrl.isNotBlank()) currentUrl = nextUrl
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }

        PinnedBarDensity {
            SmallTopAppBar(
                title = pageTitle,
                modifier = Modifier.align(Alignment.TopCenter),
                color = pageColor,
                showDivider = true,
                navigationIcon = { BackIconButton { nav.pop() } },
                actions = {
                    HighlightedIconButton(
                        onClick = { reloadKey++ },
                        modifier = Modifier.couiTopAppBarActionItem(),
                    ) {
                        Icon(
                            imageVector = WebRefreshIcon,
                            contentDescription = "刷新",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    HighlightedIconButton(
                        onClick = { openExternalUrl(context, currentUrl) },
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
