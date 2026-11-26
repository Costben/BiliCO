package erl.bilico.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

actual val platformSupportsAv1Dash: Boolean = false

actual val platformNeedsSurfaceViewGuard: Boolean = false

actual val platformUsesOffscreenExpandCompositing: Boolean = false

actual val platformNeedsBackdropHoleFill: Boolean = true

actual val platformUseCompactNavBarInset: Boolean = true

actual val platformMobileWebUserAgent: String =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

@Composable
actual fun rememberPlatformScreenCornerRadius(): Dp = 52.dp

@Composable
actual fun rememberPlatformStatusBarTopPx(): Float {
    val density = LocalDensity.current
    return WindowInsets.statusBars.getTop(density).toFloat()
        .takeIf { it > 0f } ?: with(density) { 44.dp.toPx() }
}

actual fun readWebAuthCookies(allowedNames: Set<String>): Map<String, String> = emptyMap()

actual fun normalizeNfkc(input: String): String = input.lowercase()

actual fun transliterateToSimplified(text: String): String = text

actual fun hanCharToPinyin(ch: Char): String = ""
