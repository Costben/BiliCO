package erl.bilico.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/** ExoPlayer 原生支持 AV1 DASH；iOS AVPlayer 解码 av01 DASH 会出现 0x0 黑屏。 */
expect val platformSupportsAv1Dash: Boolean

/** Android HDR 播放使用 SurfaceView 挖洞合成，转场与弹窗模糊期间需要标记保护。 */
expect val platformNeedsSurfaceViewGuard: Boolean

/** Android 展开转场使用离屏合成减少 GPU 管线现编掉帧；iOS Metal 直接渲染避免每帧离屏纹理开销。 */
expect val platformUsesOffscreenExpandCompositing: Boolean

/** iOS UIKitView 留下的 BlendMode.Clear 透明洞需要根 Backdrop 两趟补底；Android 单趟录制即可。 */
expect val platformNeedsBackdropHoleFill: Boolean

/** iOS 底部 Home Indicator 使用紧凑间距；Android 保留系统 navigationBarsPadding 避免与三键导航栏重叠。 */
expect val platformUseCompactNavBarInset: Boolean

/** 平台对应的移动端 WebView User-Agent。 */
expect val platformMobileWebUserAgent: String

/** 读取设备物理屏幕圆角半径（Android API 31+ 从 Display 获取，iOS 使用 52dp）。 */
@Composable
expect fun rememberPlatformScreenCornerRadius(): Dp

/** 稳定状态栏高度（px）：全屏隐藏状态栏时仍保留真实高度，避免退场跳变。 */
@Composable
expect fun rememberPlatformStatusBarTopPx(): Float

/** 从系统 WebView Cookie 存储兜底读取 B 站鉴权 Cookie（仅 Android 有效）。 */
expect fun readWebAuthCookies(allowedNames: Set<String>): Map<String, String>

/** NFKC 规范化（全角转半角、兼容字符归一）。 */
expect fun normalizeNfkc(input: String): String

/** 繁体转简体（Android API 29+ 走 ICU Transliterator）。 */
expect fun transliterateToSimplified(text: String): String

/** 单个汉字转不带声调的拼音并保留 ü（Android API 29+ 走 ICU Transliterator）。 */
expect fun hanCharToPinyin(ch: Char): String
