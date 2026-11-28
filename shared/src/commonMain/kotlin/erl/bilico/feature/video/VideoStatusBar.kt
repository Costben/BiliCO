package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import erl.bilico.platform.PlatformContext
import erl.bilico.player.LocalInPipMode
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized
import io.github.suqi8.coui.kmp.utils.CouiSystemBarsEffect

/**
 * 播放页顶部的状态栏：不管深浅色模式，这一条恒为纯黑，状态栏图标恒为白。
 */
@Composable
internal fun PlayerStatusBar(context: PlatformContext?, darkMode: Int) {
    val entry = LocalNavEntry.current
    val isTop = LocalRouteIsTop.current
    val inPip = LocalInPipMode.current
    val minimized = LocalRouteMinimized.current
    CouiSystemBarsEffect(
        darkStatusBarIcons = false,
        darkNavigationBarIcons = null,
        active = {
            !minimized && !inPip && (
                entry == null || (
                    isTop &&
                        !entry.exiting &&
                        !entry.collapsedBarVisible &&
                        (!entry.previewing || entry.progress.value >= 0.5f)
                    )
                )
        },
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Color.Black),
    )
}
