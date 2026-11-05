package erl.bilico

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.ui.BiliCoRoot
import erl.bilico.ui.BiliCoTheme
import erl.bilico.ui.isDarkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BiliCoApplication).container

        setContent {
            val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
            BiliCoTheme(darkMode) {
                val dark = isDarkTheme(darkMode)
                DisposableEffect(dark) {
                    // 窗口底色决定 Activity 切换和首帧之前露出来的颜色，要跟应用自己的主题走，
                    // 否则深色应用配浅色系统会白闪一下。
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
                BiliCoRoot(container)
            }
        }
    }
}
