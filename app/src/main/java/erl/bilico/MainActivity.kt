package erl.bilico

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import kotlin.math.abs
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.ui.BiliCoRoot
import erl.bilico.ui.BiliCoTheme
import erl.bilico.ui.isDarkTheme

class MainActivity : ComponentActivity() {

    private var inPipMode by mutableStateOf(false)

    private val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
        inPipMode = info.isInPictureInPictureMode
    }

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PlaybackBridge.ACTION_TOGGLE_PLAY -> PlaybackBridge.togglePlayPause(context)
                PlaybackBridge.ACTION_STOP_BG -> PlaybackBridge.stopPlayback(context)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 整个 App 只有播放页会真的转成横屏（由 VideoFullscreenStage 申请），其余页面一律锁竖屏。
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        voteHighRefreshRate()
        addOnPictureInPictureModeChangedListener(pipListener)
        val filter = IntentFilter().apply {
            addAction(PlaybackBridge.ACTION_TOGGLE_PLAY)
            addAction(PlaybackBridge.ACTION_STOP_BG)
        }
        ContextCompat.registerReceiver(
            this,
            playbackReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val container = (application as BiliCoApplication).container

        setContent {
            val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
            BiliCoTheme(darkMode) {
                val dark = isDarkTheme(darkMode)
                DisposableEffect(dark) {
                    window.setBackgroundDrawable(
                        ColorDrawable(
                            if (dark) Color.rgb(0x30, 0x30, 0x30)
                            else Color.rgb(0xF0, 0xF1, 0xF2)
                        )
                    )
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        window.attributes = window.attributes.apply {
                            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                            } else {
                                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                            }
                        }
                    }
                    onDispose { }
                }
                CompositionLocalProvider(LocalInPipMode provides inPipMode) {
                    BiliCoRoot(container)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        voteHighRefreshRate()
    }

    /**
     * 显式向系统与 OEM 调度器申请原生分辨率下的 120Hz 高刷新率模式：
     * 1. 通过 preferredDisplayModeId + preferredRefreshRate 突破非白名单应用默认 60Hz/90Hz 上限；
     * 2. 在 Android 15+ 上通过 decorView.setRequestedFrameRate(120f) 避免属性动画落入 60Hz 档。
     */
    private fun voteHighRefreshRate() {
        val displayModes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.supportedModes
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay?.supportedModes
        }
        val bestMode = displayModes
            ?.takeIf { it.isNotEmpty() }
            ?.let { modes ->
                val maxArea = modes.maxOf { it.physicalWidth.toLong() * it.physicalHeight.toLong() }
                val nativeModes = modes.filter {
                    it.physicalWidth.toLong() * it.physicalHeight.toLong() == maxArea
                }
                nativeModes.minByOrNull { abs(it.refreshRate - HIGH_REFRESH_RATE) }
                    ?.takeIf { it.refreshRate >= 115f }
                    ?: nativeModes.maxByOrNull { it.refreshRate }
            }

        window.attributes = window.attributes.apply {
            if (bestMode != null) {
                preferredDisplayModeId = bestMode.modeId
            }
            @Suppress("DEPRECATION")
            preferredRefreshRate = HIGH_REFRESH_RATE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.requestedFrameRate = HIGH_REFRESH_RATE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlaybackBridge.onUserLeaveHint(this)
    }

    override fun onDestroy() {
        removeOnPictureInPictureModeChangedListener(pipListener)
        runCatching { unregisterReceiver(playbackReceiver) }
        super.onDestroy()
    }

    private companion object {
        const val HIGH_REFRESH_RATE = 120f
    }
}
