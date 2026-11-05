package erl.bilico.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import erl.bilico.ui.components.PlayerControlsPadding
import erl.bilico.ui.components.PlayerScrim
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 直播播放器：没有进度条，其余控件与视频页一致。 */
@Composable
internal fun LivePlayer(
    player: Player,
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    errorMessage: String?,
    statusText: String?,
    fullscreen: Boolean,
    onPlayPause: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onQualityClick: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                .padding(PlayerControlsPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = COUIIcons.ChevronBackward,
                contentDescription = "返回",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp),
                tint = Color.White,
            )
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                style = COUITheme.textStyles.body2,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = errorMessage,
                    style = COUITheme.textStyles.footnote1,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "重试",
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PlayerScrim)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    style = COUITheme.textStyles.button,
                    color = Color.White,
                )
            }
        } else if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isPlaying) COUIIcons.Pause else COUIIcons.Play,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPlayPause)
                    .padding(6.dp),
                tint = Color.White,
            )
            Text(
                text = statusText.orEmpty(),
                modifier = Modifier.padding(start = 6.dp),
                style = COUITheme.textStyles.footnote2,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.weight(1f))
            ControlLabel(text = "画质", onClick = onQualityClick)
            ControlLabel(
                text = if (fullscreen) "退出" else "全屏",
                onClick = onToggleFullscreen,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ControlLabel(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = COUITheme.textStyles.footnote1,
        color = Color.White,
    )
}
