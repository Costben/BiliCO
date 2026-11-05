package erl.bilico.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

@Composable
fun BiliCoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    COUITheme(
        colors = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
