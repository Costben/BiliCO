package erl.bilico.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.di.AppContainer
import erl.bilico.platform.PlatformContext
import erl.bilico.ui.BiliCoRoot
import erl.bilico.ui.BiliCoTheme

@Composable
fun BiliCoApp(context: PlatformContext = PlatformContext()) {
    val container = remember(context) { AppContainer(context) }
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val accentColor by container.settings.accentColor.collectAsStateWithLifecycle()
    val accentHyperlink by container.settings.accentHyperlink.collectAsStateWithLifecycle()
    val meIconColorMode by container.settings.meIconColorMode.collectAsStateWithLifecycle()
    BiliCoTheme(
        darkMode = darkMode,
        accentColor = accentColor,
        accentHyperlink = accentHyperlink,
        meIconColorMode = meIconColorMode,
    ) {
        BiliCoRoot(container = container)
    }
}
