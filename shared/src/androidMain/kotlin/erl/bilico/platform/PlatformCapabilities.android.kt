package erl.bilico.platform

import android.icu.text.Transliterator
import android.os.Build
import android.view.RoundedCorner
import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

actual val platformSupportsAv1Dash: Boolean = true

actual val platformNeedsSurfaceViewGuard: Boolean = true

actual val platformUsesOffscreenExpandCompositing: Boolean = true

actual val platformNeedsBackdropHoleFill: Boolean = false

actual val platformUseCompactNavBarInset: Boolean = false

actual val platformMobileWebUserAgent: String =
    "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro Build/BP2A.250605.031.A2; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/136.0.0.0 Mobile Safari/537.36"

@Composable
actual fun rememberPlatformScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val radius = context.display
                    .getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius ?: 0
                with(density) { radius.toDp() }
            }.getOrDefault(0.dp)
        } else {
            0.dp
        }
    }
}

@Composable
actual fun rememberPlatformStatusBarTopPx(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val fromInsets = ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: 0
        if (fromInsets > 0) {
            fromInsets.toFloat()
        } else {
            val resId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) view.resources.getDimensionPixelSize(resId).toFloat() else with(density) { 28.dp.toPx() }
        }
    }
}

private val AUTH_COOKIE_DOMAINS = listOf(
    "https://www.bilibili.com",
    "https://passport.bilibili.com",
    "https://api.bilibili.com",
    "https://m.bilibili.com",
)

actual fun readWebAuthCookies(allowedNames: Set<String>): Map<String, String> {
    if (allowedNames.isEmpty()) return emptyMap()
    val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return emptyMap()
    val result = LinkedHashMap<String, String>()
    AUTH_COOKIE_DOMAINS.forEach { domainUrl ->
        val raw = runCatching { manager.getCookie(domainUrl) }.getOrNull().orEmpty()
        raw.split(';').forEach { segment ->
            val trimmed = segment.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val key = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (key in allowedNames && value.isNotEmpty()) {
                    result[key] = value
                }
            }
        }
    }
    return result
}

actual fun normalizeNfkc(input: String): String =
    Normalizer.normalize(input, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

private object AndroidIcuTextBridge {
    private val t2s: Any? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { Transliterator.getInstance("Traditional-Simplified") }.getOrNull()
        } else {
            null
        }
    }

    private val hanLatin: Any? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
        } else {
            null
        }
    }

    private val charCache = ConcurrentHashMap<Char, String>()

    fun toSimplified(text: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return text
        val tr = t2s as? Transliterator ?: return text
        return runCatching { tr.transliterate(text) }.getOrDefault(text)
    }

    fun hanToPinyin(ch: Char): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""
        return charCache.getOrPut(ch) {
            val tr = hanLatin as? Transliterator ?: return@getOrPut ""
            val raw = runCatching { tr.transliterate(ch.toString()) }.getOrDefault("")
            stripToneMarksKeepUmlaut(raw)
        }
    }

    /** 去掉四声调号但保留 ü，让上层同时生成 v 与 u 两套拼音。 */
    private fun stripToneMarksKeepUmlaut(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw.lowercase(Locale.ROOT)) {
            when (c) {
                'ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü' -> sb.append('ü')
                else -> {
                    val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                    for (dc in decomposed) {
                        if (dc in 'a'..'z') sb.append(dc)
                    }
                }
            }
        }
        return sb.toString()
    }
}

actual fun transliterateToSimplified(text: String): String = AndroidIcuTextBridge.toSimplified(text)

actual fun hanCharToPinyin(ch: Char): String = AndroidIcuTextBridge.hanToPinyin(ch)
