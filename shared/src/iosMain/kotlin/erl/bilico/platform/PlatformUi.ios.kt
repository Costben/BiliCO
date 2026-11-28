package erl.bilico.platform

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import erl.bilico.ui.LocalRouteIsTop
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiSystemBarsApplier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.atan2
import kotlinx.cinterop.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.CoreMotion.CMMotionManager
import platform.Foundation.*
import platform.Photos.*
import platform.PhotosUI.*
import platform.UIKit.*
import platform.WebKit.*
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

internal fun findKeyWindow(): UIWindow? {
    val windows = UIApplication.sharedApplication.windows
    return windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        ?: UIApplication.sharedApplication.keyWindow
        ?: windows.firstOrNull() as? UIWindow
}

private fun topViewController(): UIViewController? {
    var top = findKeyWindow()?.rootViewController ?: return null
    while (true) {
        val presented = top.presentedViewController ?: break
        top = presented
    }
    return top
}

@OptIn(ExperimentalForeignApi::class)
actual fun shareText(context: PlatformContext, text: String, title: String) {
    dispatch_async(dispatch_get_main_queue()) {
        val presenter = topViewController() ?: return@dispatch_async
        val controller = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        controller.popoverPresentationController?.let { popover ->
            val anchorView = presenter.view
            popover.sourceView = anchorView
            val (w, h) = anchorView.bounds.useContents { size.width to size.height }
            popover.sourceRect = CGRectMake(w / 2.0, h / 2.0, 1.0, 1.0)
            popover.permittedArrowDirections = 0uL
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun saveImageBytesToPhotos(context: PlatformContext, bytes: ByteArray): Result<Unit> =
    runCatching {
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val image = UIImage.imageWithData(data) ?: error("图片解码失败")
        val authStatus = suspendCancellableCoroutine<Long> { cont ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
                if (cont.isActive) cont.resume(status)
            }
        }
        if (authStatus != PHAuthorizationStatusAuthorized && authStatus != PHAuthorizationStatusLimited) {
            error("未授予相册写入权限")
        }
        suspendCancellableCoroutine<Unit> { cont ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = { PHAssetChangeRequest.creationRequestForAssetFromImage(image) },
                completionHandler = { success, error ->
                    if (!cont.isActive) return@performChanges
                    if (success) cont.resume(Unit)
                    else cont.resumeWithException(
                        IllegalStateException(error?.localizedDescription?.takeIf { it.isNotBlank() } ?: "保存到相册失败"),
                    )
                },
            )
        }
    }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
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

actual val useNativeFullscreenRotation: Boolean = true

private fun postNotification(name: String, key: String, value: NSNumber) {
    val post = {
        NSNotificationCenter.defaultCenter.postNotificationName(name, null, mapOf(key to value))
    }
    if (NSThread.isMainThread) post() else dispatch_async(dispatch_get_main_queue(), post)
}

actual fun setScreenOrientation(context: PlatformContext, orientation: PlatformOrientation) {
    // UIInterfaceOrientationMaskLandscapeRight = 逆时针 90°（听筒/刘海在左），LandscapeLeft = 顺时针 90°（听筒/刘海在右）。
    val mask = when (orientation) {
        PlatformOrientation.Portrait -> UIInterfaceOrientationMaskPortrait
        PlatformOrientation.Landscape -> UIInterfaceOrientationMaskLandscape
        PlatformOrientation.LandscapeLeft -> UIInterfaceOrientationMaskLandscapeLeft
        PlatformOrientation.LandscapeRight -> UIInterfaceOrientationMaskLandscapeRight
        PlatformOrientation.Unspecified -> UIInterfaceOrientationMaskAllButUpsideDown
    }
    postNotification("BiliCoOrientationChanged", "mask", NSNumber(unsignedLong = mask))
}

actual fun currentWindowLandscapeOrientation(context: PlatformContext): PlatformOrientation? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    val sceneOrientation = findKeyWindow()?.windowScene?.interfaceOrientation
        ?: scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }?.interfaceOrientation
        ?: scenes.firstOrNull()?.interfaceOrientation
    return when (sceneOrientation) {
        UIInterfaceOrientationLandscapeRight -> PlatformOrientation.LandscapeRight
        UIInterfaceOrientationLandscapeLeft -> PlatformOrientation.LandscapeLeft
        else -> null
    }
}

actual fun setSystemBarsVisible(context: PlatformContext, visible: Boolean) {
    postNotification("BiliCoSystemBarsChanged", "visible", NSNumber(bool = visible))
}

actual fun setStatusBarDarkIcons(context: PlatformContext, darkIcons: Boolean?) {
    val styleValue = when (darkIcons) { true -> 1; false -> 0; null -> -1 }
    postNotification("BiliCoStatusBarStyleChanged", "darkIcons", NSNumber(int = styleValue))
}

@Composable
actual fun rememberPlatformSystemBarsApplier(context: PlatformContext): CouiSystemBarsApplier? =
    remember(context) {
        var lastDarkIcons: Boolean? = null
        var lastVisible: Boolean? = null
        CouiSystemBarsApplier { style ->
            if (lastDarkIcons != style.darkStatusBarIcons) {
                lastDarkIcons = style.darkStatusBarIcons
                setStatusBarDarkIcons(context, style.darkStatusBarIcons)
            }
            val visible = style.statusBarVisible && style.navigationBarVisible
            if (lastVisible != visible) {
                lastVisible = visible
                setSystemBarsVisible(context, visible)
            }
        }
    }

actual fun currentAppVersionCode(context: PlatformContext): Long {
    val raw = NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String
    return raw?.toLongOrNull() ?: 140L
}

actual fun currentAppVersionName(context: PlatformContext): String {
    val raw = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String
    return raw?.ifBlank { null } ?: "0.4.2-pre"
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
    return drawWithCache {
        val halfW = size.width / 2f
        val halfH = size.height / 2f
        if (halfW <= 0f || halfH <= 0f) return@drawWithCache onDrawBehind {}
        val bottom = ChipTabCausticBottomOffset.toPx().toInt().toFloat()
        val sdf = ChipTabCausticSdfWidth.toPx().toInt().toFloat()
        val cornerPx = (size.minDimension / 2f) * 1.41421356f
        val builder = RuntimeShaderBuilder(effect).apply {
            uniform("in_half", halfW, halfH)
            uniform("in_corner", cornerPx)
            uniform("in_bottom", bottom)
            uniform("in_sdf", sdf)
            uniform("in_color", ChipTabCausticDark.red, ChipTabCausticDark.green, ChipTabCausticDark.blue, ChipTabCausticDark.alpha)
            uniform("in_background", background.red, background.green, background.blue)
        }
        val brush = ShaderBrush(builder.makeShader().asComposeShader())
        val drawSize = Size(size.width, size.height + bottom)
        onDrawBehind { drawRect(brush = brush, size = drawSize, blendMode = BlendMode.Plus) }
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
    val currentOnCookiesChanged = rememberUpdatedState(onCookiesChanged)
    val currentOnUrlChanged = rememberUpdatedState(onUrlChanged)
    val currentOnTitleChanged = rememberUpdatedState(onTitleChanged)
    val currentOnLoadingChanged = rememberUpdatedState(onLoadingChanged)
    val delegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            private fun collectCookies(webView: WKWebView) {
                webView.URL?.absoluteString?.let { currentOnUrlChanged.value(it) }
                webView.title?.let { currentOnTitleChanged.value(it) }
                val store: WKHTTPCookieStore = webView.configuration.websiteDataStore.httpCookieStore
                store.getAllCookies { list ->
                    val map = LinkedHashMap<String, String>()
                    list?.forEach { raw ->
                        val cookie = raw as? NSHTTPCookie ?: return@forEach
                        if (cookie.domain.lowercase().endsWith("bilibili.com")) {
                            map[cookie.name] = cookie.value
                        }
                    }
                    if (map.isNotEmpty()) currentOnCookiesChanged.value(map)
                }
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
                currentOnLoadingChanged.value(true)
                webView.URL?.absoluteString?.let { currentOnUrlChanged.value(it) }
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                currentOnLoadingChanged.value(false)
                collectCookies(webView)
            }
        }
    }

    UIKitView(
        factory = {
            val config = WKWebViewConfiguration()
            val store = config.websiteDataStore.httpCookieStore
            val cookiesToSet = initialCookies.mapNotNull { (name, value) ->
                if (name.isBlank() || value.isBlank()) return@mapNotNull null
                val props: Map<Any?, *> = mapOf(
                    NSHTTPCookieName to name,
                    NSHTTPCookieValue to value,
                    NSHTTPCookieDomain to ".bilibili.com",
                    NSHTTPCookiePath to "/",
                )
                NSHTTPCookie.cookieWithProperties(props)
            }
            val webView = WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                customUserAgent = userAgent
                navigationDelegate = delegate
            }
            val targetUrl = NSURL.URLWithString(url)
            if (targetUrl != null) {
                if (cookiesToSet.isEmpty()) {
                    webView.loadRequest(NSURLRequest.requestWithURL(targetUrl))
                } else {
                    var remaining = cookiesToSet.size
                    cookiesToSet.forEach { cookie ->
                        store.setCookie(cookie) {
                            dispatch_async(dispatch_get_main_queue()) {
                                remaining--
                                if (remaining == 0) {
                                    webView.loadRequest(NSURLRequest.requestWithURL(targetUrl))
                                }
                            }
                        }
                    }
                }
            }
            webView
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberDeviceOrientationSide(mode: Int): Int {
    if (mode == 0 || !LocalRouteIsTop.current) return 0
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

private fun ByteArray.matchesBytes(offset: Int, vararg expected: Int): Boolean =
    size >= offset + expected.size && expected.indices.all { i -> (this[offset + i].toInt() and 0xFF) == expected[i] }

private val HEIC_BRANDS = setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif")

private fun detectImageMimeAndExt(bytes: ByteArray, typeIds: List<String>): Pair<String, String> {
    when {
        bytes.matchesBytes(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> return "image/png" to "png"
        bytes.matchesBytes(0, 0x47, 0x49, 0x46, 0x38) -> return "image/gif" to "gif"
        bytes.matchesBytes(0, 0x52, 0x49, 0x46, 0x46) && bytes.matchesBytes(8, 0x57, 0x45, 0x42, 0x50) ->
            return "image/webp" to "webp"
        bytes.size >= 12 && bytes.matchesBytes(4, 0x66, 0x74, 0x79, 0x70) &&
            bytes.copyOfRange(8, 12).decodeToString().lowercase() in HEIC_BRANDS ->
            return "image/heic" to "heic"
        bytes.matchesBytes(0, 0xFF, 0xD8, 0xFF) -> return "image/jpeg" to "jpg"
    }
    val lowered = typeIds.map { it.lowercase() }
    return when {
        lowered.any { "png" in it } -> "image/png" to "png"
        lowered.any { "webp" in it } -> "image/webp" to "webp"
        lowered.any { "gif" in it } -> "image/gif" to "gif"
        lowered.any { "heic" in it || "heif" in it } -> "image/heic" to "heic"
        else -> "image/jpeg" to "jpg"
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoPicker(
    maxSelection: Int,
    onPicked: (List<PickedPhotoData>) -> Unit,
): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val delegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, completion = null)
                val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                if (results.isEmpty()) return
                val collected = arrayOfNulls<PickedPhotoData>(results.size)
                var remaining = results.size
                results.forEachIndexed { idx, res ->
                    val provider = res.itemProvider
                    val suggestedName = provider.suggestedName?.trim()?.takeIf { it.isNotEmpty() }
                    val typeIds = provider.registeredTypeIdentifiers.filterIsInstance<String>()
                    provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                        val item = if (data != null && data.length > 0uL) {
                            val bytes = ByteArray(data.length.toInt())
                            bytes.usePinned { pinned ->
                                memcpy(pinned.addressOf(0), data.bytes, data.length)
                            }
                            val (mime, ext) = detectImageMimeAndExt(bytes, typeIds)
                            val fileName = when {
                                suggestedName == null -> "image_$idx.$ext"
                                suggestedName.contains('.') -> suggestedName
                                else -> "$suggestedName.$ext"
                            }
                            PickedPhotoData(
                                id = "${randomUuid()}-$idx",
                                fileName = fileName,
                                mime = mime,
                                bytes = bytes,
                            )
                        } else {
                            null
                        }
                        dispatch_async(dispatch_get_main_queue()) {
                            if (item != null) collected[idx] = item
                            remaining--
                            if (remaining == 0) {
                                val out = collected.filterNotNull()
                                if (out.isNotEmpty()) currentOnPicked.value(out)
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
            topViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

actual fun coil3.Bitmap.toComposeImageBitmap(): ImageBitmap = this.asComposeImageBitmap()
