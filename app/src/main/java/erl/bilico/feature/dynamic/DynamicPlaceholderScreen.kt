package erl.bilico.feature.dynamic

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import erl.bilico.ui.components.MessageState
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar

@Composable
fun DynamicPlaceholderScreen() {
    Scaffold(topBar = { SmallTopAppBar(title = "动态") }) { padding ->
        MessageState(
            message = "动态将在下一阶段接入",
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
