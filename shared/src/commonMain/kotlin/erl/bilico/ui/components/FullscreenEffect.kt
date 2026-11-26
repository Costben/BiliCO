package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.PlatformOrientation
import erl.bilico.platform.currentWindowLandscapeOrientation
import erl.bilico.platform.setScreenOrientation
import erl.bilico.platform.setSystemBarsVisible

/** 横屏全屏：切换屏幕方向并隐藏系统栏，锁屏时固定当前单向横屏防止 180° 翻转，退出时恢复。 */
@Composable
fun FullscreenEffect(
    enabled: Boolean,
    locked: Boolean = false,
) {
    val context = LocalPlatformContext.current

    DisposableEffect(enabled, locked) {
        if (enabled) {
            val targetOrientation = if (locked) {
                currentWindowLandscapeOrientation(context) ?: PlatformOrientation.LandscapeRight
            } else {
                PlatformOrientation.Landscape
            }
            setScreenOrientation(context, targetOrientation)
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
