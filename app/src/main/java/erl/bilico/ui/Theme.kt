package erl.bilico.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.lightColorScheme

/** 浅色页面色。COUI 的浅色卡片是白的，底面留白会让卡片边界消失。 */
private val LightPage = Color(0xFFF0F1F2)

/** 深色模式设置：0 跟随系统，1 浅色，2 深色。 */
@Composable
fun isDarkTheme(darkMode: Int): Boolean = when (darkMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun BiliCoTheme(darkMode: Int, content: @Composable () -> Unit) {
    val controller = remember(darkMode) {
        ThemeController(
            colorSchemeMode = when (darkMode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            },
            lightColors = lightColorScheme(background = LightPage),
        )
    }
    COUITheme(controller = controller, content = content)
}

/** 卡片内部嵌块（投票框、分P/合集内嵌行）通用底色：暗色用半透明白提亮一级，亮色用浅灰与白卡拉出层次。 */
@Composable
internal fun nestedBlockColor(): Color {
    val scheme = COUITheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) scheme.surfaceContainer else scheme.background
}
