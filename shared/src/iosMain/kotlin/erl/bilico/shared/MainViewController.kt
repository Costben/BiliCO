package erl.bilico.shared

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = {
        // 禁用后台线程并行编码 Metal：避免 Compose 渲染帧与主线程 UIKit AVPlayerLayer /
        // CATransaction 竞态导致视频播放区及简介/评论栏在触摸滚动时高频闪烁。
        parallelRendering = false
    },
) {
    BiliCoApp()
}
