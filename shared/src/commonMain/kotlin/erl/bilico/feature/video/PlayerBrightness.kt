package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import erl.bilico.platform.writeScreenBrightness

/**
 * 离开播放页（返回、被别的页面覆盖、收进融合球或进入画中画）时，将窗口亮度交还系统自动亮度
 * （Android 恢复 `BRIGHTNESS_OVERRIDE_NONE`，iOS 仅在手势改过亮度时还原进入前的值）。
 */
@Composable
internal fun PlayerWindowBrightnessEffect(active: Boolean) {
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active && wasActive) {
            writeScreenBrightness(-1f)
        }
        wasActive = active
    }
}
