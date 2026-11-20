package erl.bilico.feature.video

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 播放页的亮度手势改的是宿主窗口的 [WindowManager.LayoutParams.screenBrightness]，
 * 而窗口亮度会盖住整个 Activity——播放页、底下的首页、后面推进来的每一页都在同一个窗口里。
 *
 * 所以栈顶一旦不再是播放页（返回、被别的页面盖住、收进融合球、进小窗），
 * 就把亮度还给系统，否则回到首页、设置页还停在调过的那一档，整个界面一直刺眼。
 */
@Composable
internal fun PlayerWindowBrightnessEffect(active: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(activity, active) {
        if (active) return@LaunchedEffect
        val window = activity?.window ?: return@LaunchedEffect
        if (window.attributes.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            return@LaunchedEffect
        }
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
}
