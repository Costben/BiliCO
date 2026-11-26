package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import erl.bilico.platform.readScreenBrightness
import erl.bilico.platform.writeScreenBrightness

/** 离开播放页时将手势调过的屏幕亮度恢复到进入播放页前的值。 */
@Composable
internal fun PlayerWindowBrightnessEffect(active: Boolean) {
    var savedBrightness by remember { mutableFloatStateOf(-1f) }
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active && !wasActive) {
            savedBrightness = readScreenBrightness()
        } else if (!active && wasActive && savedBrightness in 0.01f..1f) {
            writeScreenBrightness(savedBrightness)
            savedBrightness = -1f
        }
        wasActive = active
    }
}
