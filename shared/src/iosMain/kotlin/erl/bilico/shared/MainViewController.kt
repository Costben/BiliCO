package erl.bilico.shared

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import erl.bilico.player.IosMediaSession
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(): UIViewController {
    IosMediaSession.install()
    return ComposeUIViewController(
        configure = {
            // 禁用后台线程并行编码 Metal：避免 Compose 渲染帧与主线程 UIKit AVPlayerLayer /
            // CATransaction 竞态导致视频播放区及简介/评论栏在触摸滚动时高频闪烁。
            parallelRendering = false
            // 全应用输入面板与弹窗均已显式处理 WindowInsets.ime / imePadding，禁用 Compose iOS
            // 默认的整屏上移行为，避免面板退场或拖动时把底层背景页面连带顶起再回落。
            onFocusBehavior = OnFocusBehavior.DoNothing
        },
    ) {
        BiliCoApp()
    }
}
