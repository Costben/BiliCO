package erl.bilico.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal fun VideoUiState.qualityLabel(): String? {
    val source = source ?: return null
    val active = source.qualities.firstOrNull { it.quality == activeQuality }
    return active?.label?.takeIf { it.isNotBlank() } ?: "${activeQuality}P"
}

internal val DETAIL_PAGES = listOf("info", "comment")

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 手机是不是横着拿的。
 *
 * 不能用 `LocalConfiguration.orientation`：播放页自己会改 `requestedOrientation`，
 * 屏幕是转给「全屏」用的，配置变横不代表用户把手机横过来了。加速度计读的是手机本身，
 * 竖屏时把手机转横（自动横屏）和全屏里把手机转回竖屏（自动退出）都能认出来。
 */
@Composable
internal fun rememberDeviceLandscape(mode: Int): Boolean {
    if (mode == 0) return false
    val context = LocalContext.current
    var landscape by remember { mutableStateOf(false) }
    DisposableEffect(context, mode) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val delta = when (mode) {
                    1 -> 15 // 迟钝防误触：75..105 / 255..285
                    2 -> 25 // 标准：65..115 / 245..295
                    else -> 35 // 灵敏：55..125 / 235..305
                }
                val next = orientation in (90 - delta)..(90 + delta) ||
                    orientation in (270 - delta)..(270 + delta)
                if (next != landscape) landscape = next
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return landscape
}

internal fun shareVideo(context: Context, title: String, bvid: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title\nhttps://www.bilibili.com/video/$bvid")
    }
    context.startActivity(Intent.createChooser(intent, "分享视频"))
}
