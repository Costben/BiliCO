package erl.bilico

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
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
        enableEdgeToEdge()
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
                    onDispose { }
                }
                CompositionLocalProvider(LocalInPipMode provides inPipMode) {
                    BiliCoRoot(container)
                }
            }
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
}
