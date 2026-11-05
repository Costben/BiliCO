package erl.bilico.feature.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import erl.bilico.ui.components.MessageState
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar

@Composable
fun LivePlaceholderScreen() {
    Scaffold(topBar = { SmallTopAppBar(title = "直播") }) { padding ->
        MessageState(
            message = "直播将在下一阶段接入",
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
