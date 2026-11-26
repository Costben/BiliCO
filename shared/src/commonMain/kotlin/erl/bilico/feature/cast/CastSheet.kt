package erl.bilico.feature.cast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import erl.bilico.feature.video.PlayerModalHost
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalDismissState

@Composable
fun CastSheet(
    request: CastMediaRequest,
    inlineInStage: Boolean = false,
    onDismiss: () -> Unit,
) {
    PlayerModalHost(
        inlineInStage = inlineInStage,
        onDismiss = onDismiss,
    ) {
        val dismiss = LocalDismissState.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            Text(
                text = "投屏",
                style = COUITheme.textStyles.title3,
                color = COUITheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "iOS 版暂不支持局域网 DLNA 投屏，请在控制中心使用系统「屏幕镜像」或隔空播放。",
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { dismiss?.invoke() ?: onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("知道了")
            }
        }
    }
}
