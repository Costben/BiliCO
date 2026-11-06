package erl.bilico.feature.video

import android.app.Activity
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
import androidx.core.view.WindowCompat
import erl.bilico.ui.isDarkTheme

/**
 * 播放页顶部的状态栏：不管深浅色模式，这一条恒为纯黑，状态栏图标恒为白。
 *
 * 离开页面时按当前主题把图标颜色还回去，后面的页面继续跟着 `SystemBarStyle.auto` 走。
 */
@Composable
internal fun PlayerStatusBar(activity: Activity?, darkMode: Int) {
    val dark = isDarkTheme(darkMode)
    DisposableEffect(activity, dark) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = !dark }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Color.Black),
    )
}
