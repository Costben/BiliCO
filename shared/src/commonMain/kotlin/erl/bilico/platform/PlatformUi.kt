package erl.bilico.platform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Text
import kotlinx.coroutines.delay

data class ScreenConfig(
    val screenWidthDp: Int = 390,
    val screenHeightDp: Int = 844,
    val isLandscape: Boolean = false,
)

val LocalPlatformContext = staticCompositionLocalOf<PlatformContext> { PlatformContext() }

val LocalScreenConfig = staticCompositionLocalOf { ScreenConfig() }

class BackDispatcher {
    private val handlers = mutableStateListOf<() -> Boolean>()

    fun register(handler: () -> Boolean): () -> Unit {
        handlers += handler
        return { handlers.remove(handler) }
    }

    fun dispatch(): Boolean {
        for (i in handlers.lastIndex downTo 0) {
            if (handlers[i]()) return true
        }
        return false
    }
}

val LocalBackDispatcher = staticCompositionLocalOf<BackDispatcher?> { null }

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val dispatcher = LocalBackDispatcher.current ?: return
    val currentEnabled = remember { mutableStateOf(enabled) }.apply { value = enabled }
    val currentOnBack = remember { mutableStateOf(onBack) }.apply { value = onBack }
    DisposableEffect(dispatcher) {
        val unregister = dispatcher.register {
            if (currentEnabled.value) {
                currentOnBack.value()
                true
            } else {
                false
            }
        }
        onDispose { unregister() }
    }
}

object PlatformToastBus {
    var currentMessage by mutableStateOf<String?>(null)
        internal set
    internal var sequence by mutableStateOf(0)

    fun show(message: String) {
        if (message.isBlank()) return
        currentMessage = message
        sequence++
    }
}

@Composable
fun PlatformToastHost(modifier: Modifier = Modifier) {
    val msg = PlatformToastBus.currentMessage
    val seq = PlatformToastBus.sequence
    LaunchedEffect(seq) {
        if (seq > 0) {
            delay(2200)
            if (PlatformToastBus.sequence == seq) {
                PlatformToastBus.currentMessage = null
            }
        }
    }
    Box(
        modifier = modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = !msg.isNullOrBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xD91E1E1E))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(
                    text = msg.orEmpty(),
                    color = Color.White,
                    fontSize = 13.5.sp,
                )
            }
        }
    }
}

fun showToast(context: PlatformContext, message: String) {
    PlatformToastBus.show(message)
}

fun showToast(message: String) {
    PlatformToastBus.show(message)
}

enum class PlatformOrientation {
    Portrait,
    Landscape,
    LandscapeLeft,
    LandscapeRight,
    Unspecified,
}

expect fun uptimeMillis(): Long

expect fun copyToClipboard(context: PlatformContext, text: String, label: String = "")

expect fun openExternalUrl(context: PlatformContext, url: String)

expect fun shareText(context: PlatformContext, text: String, title: String = "")

expect suspend fun saveImageBytesToPhotos(context: PlatformContext, bytes: ByteArray): Result<Unit>

expect fun generateQrCodeBitmap(content: String, sizePx: Int): ImageBitmap?

expect fun keepScreenOn(context: PlatformContext, enabled: Boolean)

expect fun readScreenBrightness(): Float

expect fun writeScreenBrightness(value: Float)

expect fun readSystemVolume(): Float

expect fun writeSystemVolume(value: Float)

expect val useNativeFullscreenRotation: Boolean

expect fun setScreenOrientation(context: PlatformContext, orientation: PlatformOrientation)

expect fun setSystemBarsVisible(context: PlatformContext, visible: Boolean)

expect fun setStatusBarDarkIcons(context: PlatformContext, darkIcons: Boolean?)

expect fun currentAppVersionCode(context: PlatformContext): Long

expect fun currentAppVersionName(context: PlatformContext): String

@Composable
expect fun Modifier.chipTabCausticLight(): Modifier

@Composable
expect fun BiliWebView(
    url: String,
    initialCookies: Map<String, String> = emptyMap(),
    userAgent: String,
    onCookiesChanged: (Map<String, String>) -> Unit = {},
    onUrlChanged: (String) -> Unit = {},
    onTitleChanged: (String) -> Unit = {},
    onLoadingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
)

@Composable
expect fun rememberDeviceOrientationSide(mode: Int): Int

data class PickedPhotoData(
    val id: String,
    val fileName: String,
    val mime: String,
    val bytes: ByteArray,
)

@Composable
expect fun rememberPhotoPicker(
    maxSelection: Int,
    onPicked: (List<PickedPhotoData>) -> Unit,
): () -> Unit

expect fun coil3.Bitmap.toComposeImageBitmap(): ImageBitmap
