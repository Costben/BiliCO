package erl.bilico.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun LoginContent(
    state: LoginUiState,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            cornerRadius = 18.dp,
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(text = "扫码登录", style = COUITheme.textStyles.title3)

                if (state.qrCode != null) {
                    Image(
                        bitmap = state.qrCode,
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(220.dp),
                    )
                } else {
                    Spacer(Modifier.height(40.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = COUITheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(40.dp))
                }

                Text(
                    text = state.statusText,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    textAlign = TextAlign.Center,
                )

                state.error?.let { message ->
                    Text(
                        text = message,
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                if (state.isExpired || (state.qrCode == null && !state.isLoading)) {
                    Button(onClick = onRefresh) { Text("刷新二维码") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "登录后默认 1080P，未登录播放接口允许的最高清晰度",
            style = COUITheme.textStyles.footnote2,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}
