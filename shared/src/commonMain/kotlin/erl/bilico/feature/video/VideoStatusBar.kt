package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.setStatusBarDarkIcons
import erl.bilico.ui.isDarkTheme

/**
 * 播放页顶部的状态栏：不管深浅色模式，这一条恒为纯黑，状态栏图标恒为白。
 */
@Composable
internal fun PlayerStatusBar(context: PlatformContext?, darkMode: Int) {
    val dark = isDarkTheme(darkMode)
    DisposableEffect(context, dark) {
        if (context != null) setStatusBarDarkIcons(context, false)
        onDispose { if (context != null) setStatusBarDarkIcons(context, !dark) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Color.Black),
    )
}
