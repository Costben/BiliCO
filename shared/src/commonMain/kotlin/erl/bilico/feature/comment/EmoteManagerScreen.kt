package erl.bilico.feature.comment

import androidx.compose.runtime.Composable
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.components.InAppWebScreen

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
    InAppWebScreen(
        container = container,
        nav = nav,
        url = url,
        defaultTitle = "我的表情",
    )
}
