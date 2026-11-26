package erl.bilico.ui

import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.formatHexColor6
import erl.bilico.platform.hsvToArgb
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.Colors
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import kotlin.math.cos
import kotlinx.coroutines.delay

/** 浅色页面色。COUI 的浅色卡片是白的，底面留白会让卡片边界消失。 */
private val LightPage = Color(0xFFF0F1F2)

internal val DefaultAccentSwatchColor = Color(0xFF0080FF)

/** 非 0xFF 透明度的哨兵值：表示启用「Jeb_」色相缓慢循环强调色，与 0xFFRRGGBB 静态色互不冲突。 */
internal const val AccentHueCycle = 1

private const val HueCyclePeriodMs = 36_000L
private const val HueCycleTickMs = 120L

/** 「我的」页入口左侧图标配色模式：0 默认色（ColorOS 17 彩色），1 黑白色，2 强调色。 */
internal const val MeIconModeColorful = 0
internal const val MeIconModeMonochrome = 1
internal const val MeIconModeAccent = 2

/**
 * ColorOS 17 设置页同款双色图标功能色系（浅色 / 深色双套值，暗底略提亮保证对比度）：
 * - [Blue]：核心主干、连接、账号身份与个人数据记录
 * - [Orange]：个性化外观、交互体验与片单操作
 * - [Yellow]：珍藏星标与特色内容亮点探索
 * - [Green]：对话通信、空间定位与应用状态通道
 * - [Red]：强情感 / 高警示（极度克制，全页仅用于退出登录）
 * - [Neutral]：系统设置、底层维护与元信息兜底（对齐 ColorOS「系统与更新 / 关于本机」）
 */
internal enum class MeIconPalette(val light: Color, val dark: Color) {
    Green(Color(0xFF00BD13), Color(0xFF24B232)),
    Blue(Color(0xFF0080FF), Color(0xFF1A8CFF)),
    Orange(Color(0xFFFF7700), Color(0xFFF08222)),
    Yellow(Color(0xFFFFB200), Color(0xFFE5A100)),
    Red(Color(0xFFEB3B2F), Color(0xFFEB493D)),
    Neutral(Color(0xFF5C6066), Color(0xFF9EA4AD)),
    ;

    fun resolve(dark: Boolean): Color = if (dark) this.dark else light
}

private val LocalAccentColor = compositionLocalOf { 0 }
private val LocalAccentHyperlink = compositionLocalOf { false }
private val LocalMeIconColorMode = compositionLocalOf { MeIconModeColorful }

private val DefaultHyperlinkLightColor = Color(0xFF1367B1)
private val DefaultHyperlinkDarkColor = Color(0xFF5CA8FF)

internal data class AccentPreset(val label: String, val argb: Int)

internal val AccentPresets = listOf(
    AccentPreset("默认", 0),
    AccentPreset("B 站粉", 0xFFFB7299.toInt()),
    AccentPreset("活力橙", 0xFFFF8200.toInt()),
    AccentPreset("珊瑚红", 0xFFE53935.toInt()),
    AccentPreset("紫罗兰", 0xFF8E24AA.toInt()),
    AccentPreset("靛青蓝", 0xFF3F51B5.toInt()),
    AccentPreset("天空青", 0xFF00ACC1.toInt()),
    AccentPreset("翡翠绿", 0xFF009688.toInt()),
    AccentPreset("生机绿", 0xFF34C759.toInt()),
    AccentPreset("Jeb_", AccentHueCycle),
)

internal fun hueCycleArgb(hue: Float, dark: Boolean): Int {
    val wrapped = ((hue % 360f) + 360f) % 360f
    val saturation = if (dark) 0.78f else 0.82f
    val value = if (dark) {
        0.94f
    } else {
        // 浅色模式下黄绿到青段天然亮度偏高，平滑压低明度保证白底文字与链接可读。
        val delta = (wrapped - 110f).coerceIn(-80f, 80f)
        val penalty = ((cos((delta / 80f * kotlin.math.PI).toDouble()) + 1.0) * 0.5).toFloat()
        0.88f - 0.18f * penalty
    }
    return hsvToArgb(wrapped, saturation, value)
}

internal fun formatHexColor(argb: Int): String = formatHexColor6(argb)

internal fun parseHexColor(input: String): Int? {
    val clean = input.trim().removePrefix("#")
    if (clean.length != 6 || clean.any { it.digitToIntOrNull(16) == null }) return null
    val rgb = clean.toIntOrNull(16) ?: return null
    return rgb or 0xFF000000.toInt()
}

internal fun accentColorSummary(argb: Int): String {
    if (argb == 0) return "默认"
    if (argb == AccentHueCycle) return "Jeb_ · 色相缓慢循环"
    val normalized = argb or 0xFF000000.toInt()
    val preset = AccentPresets.firstOrNull { it.argb != 0 && it.argb != AccentHueCycle && it.argb == normalized }
    val hex = formatHexColor(normalized)
    return if (preset != null) "${preset.label} ($hex)" else "自定义 ($hex)"
}

/**
 * 在保留 COUI 中性页面与卡片底色的前提下，仅把强调色家族替换成用户选定的颜色。
 *
 * [keepFocusOutlineStatic] 在 Jeb_ 动态循环模式下置 true，保持 `LocalIndication` 引用稳定，
 * 避免每个色相帧触发 `staticCompositionLocalOf` 引起整棵页面树重组。
 */
internal fun Colors.withAccent(
    accentArgb: Int,
    dark: Boolean,
    keepFocusOutlineStatic: Boolean = false,
): Colors {
    if (accentArgb == 0) return this
    val raw = Color(accentArgb or 0xFF000000.toInt())
    val primaryColor = if (dark && raw.luminance() < 0.10f) lerp(raw, Color.White, 0.25f) else raw
    val onPrimaryColor = if (primaryColor.luminance() > 0.55f) Color(0xE6000000) else Color.White
    val container = if (dark) lerp(primaryColor, Color.White, 0.22f) else primaryColor
    return copy(
        primary = primaryColor,
        onPrimary = onPrimaryColor,
        primaryVariant = primaryColor,
        onPrimaryVariant = lerp(primaryColor, Color.White, if (dark) 0.55f else 0.68f),
        disabledPrimary = primaryColor.copy(alpha = 0.30f),
        disabledOnPrimary = if (dark) Color(0xFF404040) else onPrimaryColor,
        disabledPrimaryButton = primaryColor.copy(alpha = 0.30f),
        disabledOnPrimaryButton = onPrimaryColor.copy(alpha = if (dark) 0.16f else 0.54f),
        disabledPrimarySlider = primaryColor.copy(alpha = 0.30f),
        primaryContainer = container,
        onPrimaryContainer = onPrimaryColor,
        tertiaryContainer = primaryColor.copy(alpha = if (dark) 0.25f else 0.15f),
        onTertiaryContainer = primaryColor,
        tertiaryContainerVariant = primaryColor.copy(alpha = if (dark) 0.25f else 0.15f),
        sliderKeyPointForeground = lerp(primaryColor, Color.White, if (dark) 0.30f else 0.43f),
        containerTheme = primaryColor,
        focus = primaryColor.copy(alpha = if (dark) 0x33 / 255f else 0x1F / 255f),
        focusOutline = if (keepFocusOutlineStatic) focusOutline else container.copy(alpha = 0x66 / 255f),
    )
}

/** 深色模式设置：0 跟随系统，1 浅色，2 深色。 */
@Composable
fun isDarkTheme(darkMode: Int): Boolean = when (darkMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun BiliCoTheme(
    darkMode: Int,
    accentColor: Int = LocalAccentColor.current,
    accentHyperlink: Boolean = LocalAccentHyperlink.current,
    meIconColorMode: Int = LocalMeIconColorMode.current,
    content: @Composable () -> Unit,
) {
    val isHueCycle = accentColor == AccentHueCycle
    var cycleHue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isHueCycle) {
        if (!isHueCycle) return@LaunchedEffect
        val startMs = currentTimeMillis()
        while (true) {
            val elapsed = (currentTimeMillis() - startMs) % HueCyclePeriodMs
            cycleHue = elapsed.toFloat() / HueCyclePeriodMs * 360f
            delay(HueCycleTickMs)
        }
    }
    val lightAccent = if (isHueCycle) hueCycleArgb(cycleHue, dark = false) else accentColor
    val darkAccent = if (isHueCycle) hueCycleArgb(cycleHue, dark = true) else accentColor
    val controller = remember(darkMode, lightAccent, darkAccent, isHueCycle) {
        ThemeController(
            colorSchemeMode = when (darkMode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            },
            lightColors = lightColorScheme(background = LightPage)
                .withAccent(lightAccent, dark = false, keepFocusOutlineStatic = isHueCycle),
            darkColors = darkColorScheme()
                .withAccent(darkAccent, dark = true, keepFocusOutlineStatic = isHueCycle),
        )
    }
    CompositionLocalProvider(
        LocalAccentColor provides accentColor,
        LocalAccentHyperlink provides accentHyperlink,
        LocalMeIconColorMode provides meIconColorMode,
    ) {
        COUITheme(controller = controller, content = content)
    }
}

/**
 * 超链接文本颜色（话题、@提及、网址、BV 号、时间戳与回复链接等）：
 * 开启「超链接应用主题色」时跟随当前主题强调色，关闭时保持默认链接蓝。
 */
@Composable
internal fun hyperlinkColor(): Color {
    val scheme = COUITheme.colorScheme
    if (LocalAccentHyperlink.current) return scheme.primary
    return if (scheme.background.luminance() < 0.5f) {
        DefaultHyperlinkDarkColor
    } else {
        DefaultHyperlinkLightColor
    }
}

/**
 * 「我的」页面入口左侧图标颜色：
 * 0 默认色（ColorOS 17 彩色），1 黑白色（onSurface），2 强调色（主题色 primary）。
 */
@Composable
internal fun meEntryIconColor(palette: MeIconPalette): Color {
    val scheme = COUITheme.colorScheme
    return when (LocalMeIconColorMode.current) {
        MeIconModeMonochrome -> scheme.onSurface
        MeIconModeAccent -> scheme.primary
        else -> palette.resolve(dark = scheme.background.luminance() < 0.5f)
    }
}

/** 卡片内部嵌块（投票框、分P/合集内嵌行）通用底色：暗色用半透明白提亮一级，亮色用浅灰与白卡拉出层次。 */
@Composable
internal fun nestedBlockColor(): Color {
    val scheme = COUITheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) scheme.surfaceContainer else scheme.background
}
