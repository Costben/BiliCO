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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.platform.AndroidPlatformBridge
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.handleAndroidPipUserLeaveHint
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.ui.BiliCoRoot
import erl.bilico.ui.BiliCoTheme
import erl.bilico.ui.isDarkTheme
import io.github.suqi8.coui.kmp.basic.ScrollToTopHost
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private var inPipMode by mutableStateOf(false)
    private var pendingVideoLink by mutableStateOf<ExternalVideoLink?>(null)

    private val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
        inPipMode = info.isInPictureInPictureMode
    }

    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val platformCtx = PlatformContext(context)
            when (intent.action) {
                PlaybackBridge.ACTION_TOGGLE_PLAY -> PlaybackBridge.togglePlayPause(platformCtx)
                PlaybackBridge.ACTION_STOP_BG -> PlaybackBridge.stopPlayback(platformCtx)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPlatformBridge.init(this)
        AndroidPlatformBridge.setCurrentActivity(this)
        pendingVideoLink = ExternalVideoLink.fromIntent(intent)
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
            val accentColor by container.settings.accentColor.collectAsStateWithLifecycle()
            val accentHyperlink by container.settings.accentHyperlink.collectAsStateWithLifecycle()
            val meIconColorMode by container.settings.meIconColorMode.collectAsStateWithLifecycle()
            BiliCoTheme(darkMode, accentColor, accentHyperlink, meIconColorMode) {
                val dark = isDarkTheme(darkMode)
                DisposableEffect(dark) {
                    window.setBackgroundDrawable(
                        ColorDrawable(
                            if (dark) Color.rgb(0x30, 0x30, 0x30)
                            else Color.rgb(0xF0, 0xF1, 0xF2),
                        ),
                    )
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
                    ScrollToTopHost {
                        val activeLink = pendingVideoLink
                        BiliCoRoot(
                            container = container,
                            pendingVideoTarget = activeLink?.toTargetString(),
                            pendingVideoStartSec = activeLink?.startSec ?: 0L,
                            onPendingVideoHandled = {
                                if (pendingVideoLink === activeLink) pendingVideoLink = null
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ExternalVideoLink.fromIntent(intent)?.let { pendingVideoLink = it }
    }

    override fun onResume() {
        super.onResume()
        AndroidPlatformBridge.setCurrentActivity(this)
        voteHighRefreshRate()
    }

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
        handleAndroidPipUserLeaveHint(this)
    }

    override fun onDestroy() {
        if (AndroidPlatformBridge.currentActivity() === this) {
            AndroidPlatformBridge.setCurrentActivity(null)
        }
        removeOnPictureInPictureModeChangedListener(pipListener)
        runCatching { unregisterReceiver(playbackReceiver) }
        super.onDestroy()
    }

    private companion object {
        const val HIGH_REFRESH_RATE = 120f
    }
}

private fun ExternalVideoLink.toTargetString(): String? =
    bvid ?: aid?.let { "https://www.bilibili.com/video/av$it" }
