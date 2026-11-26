package erl.bilico.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import erl.bilico.debug.diag
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISlider
import platform.Foundation.NSTimer
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import platform.UIKit.UIInterfaceOrientationMaskAllButUpsideDown
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskLandscapeLeft
import platform.UIKit.UIInterfaceOrientationMaskLandscapeRight
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIPasteboard
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.WebKit.WKHTTPCookieStore
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

actual fun uptimeMillis(): Long =
    (NSProcessInfo.processInfo.systemUptime * 1000.0).toLong()

actual fun copyToClipboard(context: PlatformContext, text: String, label: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun openExternalUrl(context: PlatformContext, url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

actual fun shareText(context: PlatformContext, text: String, title: String) {
    dispatch_async(dispatch_get_main_queue()) {
        val window = UIApplication.sharedApplication.windows.firstOrNull {
            (it as? UIWindow)?.isKeyWindow() == true
        } as? UIWindow ?: UIApplication.sharedApplication.keyWindow
        val root = window?.rootViewController ?: return@dispatch_async
        val controller = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        root.presentViewController(controller, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun saveImageBytesToPhotos(context: PlatformContext, bytes: ByteArray): Result<Unit> =
    runCatching {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val image = UIImage.imageWithData(data) ?: error("图片解码失败")
        suspendCancellableCoroutine<Unit> { cont ->
            dispatch_async(dispatch_get_main_queue()) {
                UIImageWriteToSavedPhotosAlbum(image, null, null, null)
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

@OptIn(ExperimentalForeignApi::class)
actual fun generateQrCodeBitmap(content: String, sizePx: Int): ImageBitmap? {
    if (content.isBlank() || sizePx <= 0) return null
    val nsString = NSString.create(string = content)
    val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return null
    val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return null
    filter.setValue(data, forKey = "inputMessage")
    filter.setValue("M", forKey = "inputCorrectionLevel")
    val output = filter.outputImage ?: return null
    val extentWidth = output.extent.useContents { size.width }.coerceAtLeast(1.0)
    val scale = sizePx.toDouble() / extentWidth
    val scaled = output.imageByApplyingTransform(CGAffineTransformMakeScale(scale, scale))
    val ciContext = CIContext.context()
    val cgImage = ciContext.createCGImage(scaled, fromRect = scaled.extent) ?: return null
    return try {
        val uiImage = UIImage.imageWithCGImage(cgImage)
        val pngData = UIImagePNGRepresentation(uiImage) ?: return null
        val pngBytes = ByteArray(pngData.length.toInt())
        if (pngBytes.isNotEmpty()) {
            pngBytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), pngData.bytes, pngData.length)
            }
        }
        Image.makeFromEncoded(pngBytes).toComposeImageBitmap()
    } finally {
        CGImageRelease(cgImage)
    }
}

actual fun keepScreenOn(context: PlatformContext, enabled: Boolean) {
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }
}

actual fun readScreenBrightness(): Float =
    platform.UIKit.UIScreen.mainScreen.brightness.toFloat().coerceIn(0.02f, 1f)

actual fun writeScreenBrightness(value: Float) {
    diag("ui") { "writeBrightness $value" }
    dispatch_async(dispatch_get_main_queue()) {
        platform.UIKit.UIScreen.mainScreen.brightness = value.coerceIn(0.02f, 1f).toDouble()
    }
}

@OptIn(ExperimentalForeignApi::class)
private object IosSystemVolumeBridge {
    private var volumeView: MPVolumeView? = null
    private var detachTimer: NSTimer? = null
    private var latestTargetVolume: Float? = null

    private fun keyWindow(): UIWindow? {
        val windows = UIApplication.sharedApplication.windows
        return windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
            ?: UIApplication.sharedApplication.keyWindow
            ?: windows.firstOrNull() as? UIWindow
    }

    private fun ensureAttached(): UISlider? {
        val view = volumeView ?: MPVolumeView(
            frame = CGRectMake(-2000.0, -2000.0, 1.0, 1.0),
        ).apply {
            alpha = 0.01
            clipsToBounds = true
            userInteractionEnabled = false
            volumeView = this
        }
        val window = keyWindow()
        if (window != null && view.superview !== window) {
            view.removeFromSuperview()
            window.addSubview(view)
            view.layoutIfNeeded()
        }
        return findSlider(view)
    }

    private fun findSlider(view: platform.UIKit.UIView): UISlider? {
        view.subviews.forEach { raw ->
            if (raw is UISlider) return raw
            if (raw is platform.UIKit.UIView) {
                findSlider(raw)?.let { return it }
            }
        }
        return null
    }

    private fun scheduleDetach() {
        detachTimer?.invalidate()
        detachTimer = NSTimer.scheduledTimerWithTimeInterval(
            interval = 1.6,
            repeats = false,
            block = { _ ->
                detachTimer = null
                latestTargetVolume = null
                volumeView?.removeFromSuperview()
            },
        )
    }

    fun warmUp() {
        dispatch_async(dispatch_get_main_queue()) {
            ensureAttached()
            scheduleDetach()
        }
    }

    fun read(): Float {
        warmUp()
        return AVAudioSession.sharedInstance().outputVolume.coerceIn(0f, 1f)
    }

    fun write(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        latestTargetVolume = clamped
        dispatch_async(dispatch_get_main_queue()) {
            val target = latestTargetVolume ?: clamped
            val slider = ensureAttached()
            if (slider != null) {
                slider.setValue(target, animated = false)
                slider.sendActionsForControlEvents(UIControlEventValueChanged)
            }
            // 首次挂载 MPVolumeView 时内部滑块可能在下一帧才完成系统绑定，补一次同步确保第一下拖动生效。
            dispatch_async(dispatch_get_main_queue()) {
                val pending = latestTargetVolume ?: return@dispatch_async
                val s = ensureAttached() ?: return@dispatch_async
                if (abs(s.value - pending) > 0.005f) {
                    s.setValue(pending, animated = false)
                    s.sendActionsForControlEvents(UIControlEventValueChanged)
                }
            }
            scheduleDetach()
        }
    }
}

actual fun readSystemVolume(): Float = IosSystemVolumeBridge.read()

actual fun writeSystemVolume(value: Float) {
    diag("ui") { "writeSystemVolume $value" }
    IosSystemVolumeBridge.write(value)
}

actual val useNativeFullscreenRotation: Boolean = true

actual fun setScreenOrientation(context: PlatformContext, orientation: PlatformOrientation) {
    diag("ui") { "setScreenOrientation $orientation" }
    dispatch_async(dispatch_get_main_queue()) {
        val mask = when (orientation) {
            PlatformOrientation.Portrait -> UIInterfaceOrientationMaskPortrait
            PlatformOrientation.Landscape -> UIInterfaceOrientationMaskLandscape
            PlatformOrientation.LandscapeLeft -> UIInterfaceOrientationMaskLandscapeLeft
            PlatformOrientation.LandscapeRight -> UIInterfaceOrientationMaskLandscapeRight
            PlatformOrientation.Unspecified -> UIInterfaceOrientationMaskAllButUpsideDown
        }
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = "BiliCoOrientationChanged",
            `object` = null,
            userInfo = mapOf("mask" to NSNumber(unsignedLong = mask)),
        )
    }
}

actual fun setSystemBarsVisible(context: PlatformContext, visible: Boolean) {
    diag("ui") { "setSystemBarsVisible $visible" }
    dispatch_async(dispatch_get_main_queue()) {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = "BiliCoSystemBarsChanged",
            `object` = null,
            userInfo = mapOf("visible" to NSNumber(bool = visible)),
        )
    }
}

actual fun setStatusBarDarkIcons(context: PlatformContext, darkIcons: Boolean?) {
    diag("ui") { "setStatusBarDarkIcons $darkIcons" }
    dispatch_async(dispatch_get_main_queue()) {
        val styleValue = when (darkIcons) {
            true -> 1
            false -> 0
            null -> -1
        }
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = "BiliCoStatusBarStyleChanged",
            `object` = null,
            userInfo = mapOf("darkIcons" to NSNumber(int = styleValue)),
        )
    }
}

actual fun currentAppVersionCode(context: PlatformContext): Long {
    val raw = NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String
    return raw?.toLongOrNull() ?: 137L
}

actual fun currentAppVersionName(context: PlatformContext): String {
    val raw = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
    return raw?.ifBlank { null } ?: "0.3.15-pre"
}

private val ChipTabCausticDark = Color(0x33FFFFFF)
private val ChipTabCausticBottomOffset = 4.299988.dp
private val ChipTabCausticSdfWidth = 9.22998.dp

private const val CHIP_TAB_CAUSTIC_SKSL = """
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
    val effect = remember { runCatching { RuntimeEffect.makeForShader(CHIP_TAB_CAUSTIC_SKSL) }.getOrNull() }
        ?: return this
    val background = COUITheme.colorScheme.background
    return drawBehind {
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        if (halfW <= 0f || halfH <= 0f) return@drawBehind
        val bottom = ChipTabCausticBottomOffset.toPx().toInt().toFloat()
        val sdf = ChipTabCausticSdfWidth.toPx().toInt().toFloat()
        val cornerPx = (size.minDimension / 2f) * 1.41421356f
        val builder = RuntimeShaderBuilder(effect).apply {
            uniform("in_half", halfW, halfH)
            uniform("in_corner", cornerPx)
            uniform("in_bottom", bottom)
            uniform("in_sdf", sdf)
            uniform(
                "in_color",
                ChipTabCausticDark.red,
                ChipTabCausticDark.green,
                ChipTabCausticDark.blue,
                ChipTabCausticDark.alpha,
            )
            uniform("in_background", background.red, background.green, background.blue)
        }
        drawRect(
            brush = ShaderBrush(builder.makeShader().asComposeShader()),
            size = Size(size.width, size.height + bottom),
            blendMode = BlendMode.Plus,
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
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
    val delegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            private fun collectCookies(webView: WKWebView) {
                webView.URL?.absoluteString?.let(onUrlChanged)
                webView.title?.let(onTitleChanged)
                val store: WKHTTPCookieStore = webView.configuration.websiteDataStore.httpCookieStore
                store.getAllCookies { list ->
                    val map = LinkedHashMap<String, String>()
                    list?.forEach { raw ->
                        val cookie = raw as? NSHTTPCookie ?: return@forEach
                        val domain = cookie.domain.lowercase()
                        if (domain.endsWith("bilibili.com")) {
                            map[cookie.name] = cookie.value
                        }
                    }
                    if (map.isNotEmpty()) onCookiesChanged(map)
                }
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
                onLoadingChanged(true)
                webView.URL?.absoluteString?.let(onUrlChanged)
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                onLoadingChanged(false)
                collectCookies(webView)
            }
        }
    }

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration()
            val store = config.websiteDataStore.httpCookieStore
            initialCookies.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    val props: Map<Any?, *> = mapOf(
                        NSHTTPCookieName to name,
                        NSHTTPCookieValue to value,
                        NSHTTPCookieDomain to ".bilibili.com",
                        NSHTTPCookiePath to "/",
                    )
                    NSHTTPCookie.cookieWithProperties(props)?.let { c ->
                        store.setCookie(c, completionHandler = null)
                    }
                }
            }
            WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                customUserAgent = userAgent
                navigationDelegate = delegate
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberDeviceOrientationSide(mode: Int): Int {
    if (mode == 0) return 0
    var side by remember { mutableIntStateOf(0) }
    DisposableEffect(mode) {
        val manager = CMMotionManager()
        if (!manager.accelerometerAvailable) {
            return@DisposableEffect onDispose {}
        }
        manager.accelerometerUpdateInterval = 0.12
        var pendingTarget: Int? = null
        var pendingTicks = 0
        manager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            val accel = data ?: return@startAccelerometerUpdatesToQueue
            val (ax, ay) = accel.acceleration.useContents { x to y }
            if (abs(ax) < 0.35 && abs(ay) < 0.35) return@startAccelerometerUpdatesToQueue
            val angleDeg = ((atan2(ax, -ay) * 180.0 / kotlin.math.PI) + 360.0) % 360.0
            val delta = when (mode) {
                1 -> 30.0
                2 -> 38.0
                else -> 45.0
            }
            val target = when {
                angleDeg in (270.0 - delta)..(270.0 + delta) -> 1
                angleDeg in (90.0 - delta)..(90.0 + delta) -> -1
                angleDeg <= (90.0 - delta - 10.0) || angleDeg >= (270.0 + delta + 10.0) -> 0
                else -> null
            }
            if (target == null || target == side) {
                pendingTarget = null
                pendingTicks = 0
            } else if (pendingTarget == target) {
                pendingTicks++
                if (pendingTicks >= 2) {
                    diag("ui") { "deviceSide $side -> $target (ax=$ax ay=$ay deg=${angleDeg.toInt()})" }
                    side = target
                    pendingTarget = null
                    pendingTicks = 0
                }
            } else {
                pendingTarget = target
                pendingTicks = 1
            }
        }
        onDispose { manager.stopAccelerometerUpdates() }
    }
    return side
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoPicker(
    maxSelection: Int,
    onPicked: (List<PickedPhotoData>) -> Unit,
): () -> Unit {
    val callback = remember { arrayOfNulls<(List<PickedPhotoData>) -> Unit>(1) }
    callback[0] = onPicked
    val delegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, completion = null)
                val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                if (results.isEmpty()) return
                val collected = ArrayList<PickedPhotoData>()
                var remaining = results.size
                results.forEachIndexed { idx, res ->
                    val provider = res.itemProvider
                    provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                        if (data != null && data.length > 0uL) {
                            val bytes = ByteArray(data.length.toInt())
                            bytes.usePinned { pinned ->
                                memcpy(pinned.addressOf(0), data.bytes, data.length)
                            }
                            collected += PickedPhotoData(
                                id = "${randomUuid()}-$idx",
                                fileName = "image_$idx.jpg",
                                mime = "image/jpeg",
                                bytes = bytes,
                            )
                        }
                        remaining--
                        if (remaining == 0 && collected.isNotEmpty()) {
                            dispatch_async(dispatch_get_main_queue()) {
                                callback[0]?.invoke(collected.toList())
                            }
                        }
                    }
                }
            }
        }
    }
    return {
        dispatch_async(dispatch_get_main_queue()) {
            val config = PHPickerConfiguration().apply {
                selectionLimit = maxSelection.toLong()
                filter = PHPickerFilter.imagesFilter()
            }
            val picker = PHPickerViewController(configuration = config)
            picker.delegate = delegate
            val window = UIApplication.sharedApplication.windows.firstOrNull {
                (it as? UIWindow)?.isKeyWindow() == true
            } as? UIWindow ?: UIApplication.sharedApplication.keyWindow
            window?.rootViewController?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

actual fun coil3.Bitmap.toComposeImageBitmap(): ImageBitmap = this.asComposeImageBitmap()
