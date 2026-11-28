package erl.bilico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.PlatformOrientation
import erl.bilico.platform.currentWindowLandscapeOrientation
import erl.bilico.platform.setScreenOrientation
import io.github.suqi8.coui.kmp.utils.CouiSystemBarsEffect

/** 横屏全屏：切换屏幕方向并隐藏系统栏，锁屏时固定当前单向横屏防止 180° 翻转，退出时恢复。 */
@Composable
fun FullscreenEffect(
    enabled: Boolean,
    locked: Boolean = false,
) {
    val context = LocalPlatformContext.current

    CouiSystemBarsEffect(
        darkStatusBarIcons = false,
        darkNavigationBarIcons = false,
        statusBarVisible = false,
        navigationBarVisible = false,
        active = { enabled },
    )

    DisposableEffect(enabled, locked) {
        if (enabled) {
            val targetOrientation = if (locked) {
                currentWindowLandscapeOrientation(context) ?: PlatformOrientation.LandscapeRight
            } else {
                PlatformOrientation.Landscape
            }
            setScreenOrientation(context, targetOrientation)
        } else {
            setScreenOrientation(context, PlatformOrientation.Portrait)
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            setScreenOrientation(context, PlatformOrientation.Unspecified)
        }
    }
}
