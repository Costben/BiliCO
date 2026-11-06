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
internal fun rememberDeviceLandscape(): Boolean {
    val context = LocalContext.current
    var landscape by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // 0 度是竖屏，90 / 270 是横屏；只认这两段正负 35 度，中间的空档留给竖屏。
                val next = orientation in 55..125 || orientation in 235..305
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
