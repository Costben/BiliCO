package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.PlatformOrientation
import erl.bilico.platform.setScreenOrientation
import erl.bilico.platform.setSystemBarsVisible

/** 横屏全屏：切换屏幕方向并隐藏系统栏，退出时恢复。 */
@Composable
fun FullscreenEffect(enabled: Boolean) {
    val context = LocalPlatformContext.current

    DisposableEffect(enabled) {
        if (enabled) {
            setScreenOrientation(context, PlatformOrientation.Landscape)
            setSystemBarsVisible(context, false)
        } else {
            setScreenOrientation(context, PlatformOrientation.Portrait)
            setSystemBarsVisible(context, true)
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            setScreenOrientation(context, PlatformOrientation.Unspecified)
            setSystemBarsVisible(context, true)
        }
    }
}
