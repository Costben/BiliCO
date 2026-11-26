package erl.bilico.platform

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.RuntimeShader
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val mainHandler = Handler(Looper.getMainLooper())

private fun runOnMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        mainHandler.post(block)
    }
}

actual fun uptimeMillis(): Long = SystemClock.uptimeMillis()

actual fun copyToClipboard(context: PlatformContext, text: String, label: String) {
    val cm = context.androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label.ifBlank { "BiliCO" }, text))
}

actual fun openExternalUrl(context: PlatformContext, url: String) {
    val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        val host = AndroidPlatformBridge.currentActivity() ?: context.androidContext
        host.startActivity(intent)
    }
}

actual fun shareText(context: PlatformContext, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
    }
    val chooser = Intent.createChooser(send, title.ifBlank { "分享" }).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        val host = AndroidPlatformBridge.currentActivity() ?: context.androidContext
        host.startActivity(chooser)
    }
}

actual suspend fun saveImageBytesToPhotos(context: PlatformContext, bytes: ByteArray): Result<Unit> =
    withContext(ioDispatcher) {
        runCatching {
            val fileName = "BiliCO_${System.currentTimeMillis()}.jpg"
            val resolver = context.androidContext.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BiliCO")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建相册条目")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("无法写入相册文件")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "BiliCO",
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
            }
            Unit
        }
    }

actual fun generateQrCodeBitmap(content: String, sizePx: Int): ImageBitmap? {
    if (content.isBlank() || sizePx <= 0) return null
    return runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }.asImageBitmap()
    }.getOrNull()
}

actual fun keepScreenOn(context: PlatformContext, enabled: Boolean) {
    runOnMain {
        val window = AndroidPlatformBridge.currentActivity()?.window ?: return@runOnMain
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

actual fun readScreenBrightness(): Float {
    val activity = AndroidPlatformBridge.currentActivity()
    val windowValue = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowValue in 0f..1f) return windowValue.coerceIn(0.02f, 1f)
    val ctx = activity ?: runCatching { AndroidPlatformBridge.requireAppContext() }.getOrNull()
        ?: return 0.5f
    val raw = runCatching {
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (raw / 255f).coerceIn(0.02f, 1f)
}

actual fun writeScreenBrightness(value: Float) {
    val clamped = value.coerceIn(0.02f, 1f)
    runOnMain {
        val window = AndroidPlatformBridge.currentActivity()?.window ?: return@runOnMain
        window.attributes = window.attributes.apply {
            screenBrightness = clamped
        }
    }
}

actual fun readSystemVolume(): Float {
    val ctx = runCatching { AndroidPlatformBridge.requireAppContext() }.getOrNull() ?: return 0.5f
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0.5f
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    return (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
}

actual fun writeSystemVolume(value: Float) {
    val ctx = runCatching { AndroidPlatformBridge.requireAppContext() }.getOrNull() ?: return
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val target = (value.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
    runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
}

actual val useNativeFullscreenRotation: Boolean = false

actual fun setScreenOrientation(context: PlatformContext, orientation: PlatformOrientation) {
    runOnMain {
        val activity = AndroidPlatformBridge.currentActivity() ?: return@runOnMain
        activity.requestedOrientation = when (orientation) {
            PlatformOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlatformOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PlatformOrientation.LandscapeLeft -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlatformOrientation.LandscapeRight -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            PlatformOrientation.Unspecified -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

actual fun setSystemBarsVisible(context: PlatformContext, visible: Boolean) {
    runOnMain {
        val window = AndroidPlatformBridge.currentActivity()?.window ?: return@runOnMain
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

actual fun setStatusBarDarkIcons(context: PlatformContext, darkIcons: Boolean?) {
    if (darkIcons == null) return
    runOnMain {
        val window = AndroidPlatformBridge.currentActivity()?.window ?: return@runOnMain
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = darkIcons
    }
}

actual fun currentAppVersionCode(context: PlatformContext): Long =
    runCatching {
        val pm = context.androidContext.packageManager
        val info = pm.getPackageInfo(context.androidContext.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }.getOrDefault(137L)

actual fun currentAppVersionName(context: PlatformContext): String =
    runCatching {
        val pm = context.androidContext.packageManager
        pm.getPackageInfo(context.androidContext.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.3.15-pre"

private val ChipTabCausticDark = Color(0x33FFFFFF)
private val ChipTabCausticBottomOffset = 4.299988.dp
private val ChipTabCausticSdfWidth = 9.22998.dp

private const val CHIP_TAB_CAUSTIC_AGSL = """
uniform float2 in_half;
uniform float in_corner;
uniform float in_bottom;
uniform float in_sdf;
uniform float4 in_color;
uniform float3 in_background;
float RBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}
half4 main(float2 pos) {
    float2 pos_optics = pos - in_half - float2(0.0, in_bottom * 0.5);
    float2 halfSizeOptics = in_half + float2(0.0, in_bottom * 0.5);
    float sdf = min(in_sdf, min(in_half.x, in_half.y));
    if (in_color.a <= 0.001 || sdf <= 0.0 || any(greaterThan(abs(pos_optics), halfSizeOptics))) {
        return half4(0.0);
    }
    halfSizeOptics = max(halfSizeOptics - sdf, float2(0.0));
    float cornerOptics = min(in_corner, min(halfSizeOptics.x, halfSizeOptics.y));
    float opticsSdf = RBox(pos_optics, halfSizeOptics, cornerOptics);
    float value = smoothstep(sdf, 0.0, opticsSdf);
    float fade = smoothstep(halfSizeOptics.y + sdf, -halfSizeOptics.y - sdf, -pos_optics.y);
    value *= fade > 0.0 ? pow(fade, 18.0) : 0.0;
    value = (value > 0.0 ? pow(value, 0.6) : 0.0) * 0.6 + 0.4 * value;
    float gray = clamp((dot(float3(0.299, 0.587, 0.114), in_background) - 0.45) * 0.6, 0.0, 1.0);
    float alpha = in_color.a * value * (1.0 - gray) * (1.0 - gray);
    return half4(in_color.rgb * alpha, alpha);
}
"""

@Composable
actual fun Modifier.chipTabCausticLight(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val shader = remember { runCatching { RuntimeShader(CHIP_TAB_CAUSTIC_AGSL) }.getOrNull() }
        ?: return this
    val background = COUITheme.colorScheme.background
    return drawBehind {
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        if (halfW <= 0f || halfH <= 0f) return@drawBehind
        val bottom = ChipTabCausticBottomOffset.toPx().toInt().toFloat()
        val sdf = ChipTabCausticSdfWidth.toPx().toInt().toFloat()
        val cornerPx = (size.minDimension / 2f) * 1.41421356f
        shader.setFloatUniform("in_half", halfW, halfH)
        shader.setFloatUniform("in_corner", cornerPx)
        shader.setFloatUniform("in_bottom", bottom)
        shader.setFloatUniform("in_sdf", sdf)
        shader.setFloatUniform(
            "in_color",
            ChipTabCausticDark.red,
            ChipTabCausticDark.green,
            ChipTabCausticDark.blue,
            ChipTabCausticDark.alpha,
        )
        shader.setFloatUniform("in_background", background.red, background.green, background.blue)
        drawRect(
            brush = ShaderBrush(shader),
            size = Size(size.width, size.height + bottom),
            blendMode = BlendMode.Plus,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun BiliWebView(
    url: String,
    initialCookies: Map<String, String>,
    userAgent: String,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val currentOnCookies by rememberUpdatedState(onCookiesChanged)
    val currentOnUrl by rememberUpdatedState(onUrlChanged)
    val currentOnTitle by rememberUpdatedState(onTitleChanged)
    val currentOnLoading by rememberUpdatedState(onLoadingChanged)

    AndroidView(
        factory = { ctx ->
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            initialCookies.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    cookieManager.setCookie(
                        "https://www.bilibili.com",
                        "$name=$value; Domain=.bilibili.com; Path=/",
                    )
                }
            }
            cookieManager.flush()

            WebView(ctx).apply {
                cookieManager.setAcceptThirdPartyCookies(this, true)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = userAgent
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        if (!title.isNullOrBlank()) currentOnTitle(title)
                    }
                }
                webViewClient = object : WebViewClient() {
                    private fun syncCookies(pageUrl: String?) {
                        val targetUrl = pageUrl ?: "https://www.bilibili.com"
                        val raw = cookieManager.getCookie(targetUrl)
                            ?: cookieManager.getCookie("https://www.bilibili.com")
                            ?: return
                        val parsed = LinkedHashMap<String, String>()
                        raw.split(';').forEach { part ->
                            val idx = part.indexOf('=')
                            if (idx > 0) {
                                val k = part.substring(0, idx).trim()
                                val v = part.substring(idx + 1).trim()
                                if (k.isNotEmpty()) parsed[k] = v
                            }
                        }
                        if (parsed.isNotEmpty()) currentOnCookies(parsed)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = false

                    override fun onPageStarted(
                        view: WebView?,
                        pageUrl: String?,
                        favicon: Bitmap?,
                    ) {
                        currentOnLoading(true)
                        pageUrl?.let(currentOnUrl)
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        currentOnLoading(false)
                        pageUrl?.let(currentOnUrl)
                        view?.title?.let(currentOnTitle)
                        syncCookies(pageUrl)
                    }
                }
                loadUrl(url)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = modifier,
    )
}

@Composable
actual fun rememberDeviceOrientationSide(mode: Int): Int {
    if (mode == 0) return 0
    val platformCtx = LocalPlatformContext.current
    var side by remember { mutableIntStateOf(0) }
    DisposableEffect(mode, platformCtx) {
        var pendingTarget: Int? = null
        var pendingTicks = 0
        val listener = object : OrientationEventListener(platformCtx.androidContext) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val delta = when (mode) {
                    1 -> 30
                    2 -> 38
                    else -> 45
                }
                val target = when {
                    orientation in (270 - delta)..(270 + delta) -> 1
                    orientation in (90 - delta)..(90 + delta) -> -1
                    orientation <= (90 - delta - 10) || orientation >= (270 + delta + 10) -> 0
                    else -> null
                }
                if (target == null || target == side) {
                    pendingTarget = null
                    pendingTicks = 0
                } else if (pendingTarget == target) {
                    pendingTicks++
                    if (pendingTicks >= 2) {
                        side = target
                        pendingTarget = null
                        pendingTicks = 0
                    }
                } else {
                    pendingTarget = target
                    pendingTicks = 1
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return side
}

@Composable
actual fun rememberPhotoPicker(
    maxSelection: Int,
    onPicked: (List<PickedPhotoData>) -> Unit,
): () -> Unit {
    val platformCtx = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)

    fun readUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val picked = withContext(ioDispatcher) {
                val resolver = platformCtx.androidContext.contentResolver
                uris.mapIndexedNotNull { idx, uri ->
                    runCatching {
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?.takeIf { it.isNotEmpty() } ?: return@runCatching null
                        val mime = resolver.getType(uri) ?: "image/jpeg"
                        var fileName = "image_$idx.jpg"
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (col >= 0) cursor.getString(col)?.takeIf { it.isNotBlank() }?.let { fileName = it }
                                }
                            }
                        PickedPhotoData(
                            id = "${randomUuid()}-$idx",
                            fileName = fileName,
                            mime = mime,
                            bytes = bytes,
                        )
                    }.getOrNull()
                }
            }
            if (picked.isNotEmpty()) currentOnPicked(picked)
        }
    }

    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) readUris(listOf(uri))
    }

    val multiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxSelection.coerceAtLeast(2)),
    ) { uris ->
        readUris(uris)
    }

    return {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (maxSelection <= 1) {
            singleLauncher.launch(request)
        } else {
            multiLauncher.launch(request)
        }
    }
}

actual fun coil3.Bitmap.toComposeImageBitmap(): ImageBitmap = this.asImageBitmap()
