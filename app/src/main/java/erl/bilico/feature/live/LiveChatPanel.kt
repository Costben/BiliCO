package erl.bilico.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.live.LiveDanmakuState
import erl.bilico.data.model.LiveDanmakuMessage
import erl.bilico.ui.components.DanmakuToggle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 直播间弹幕面板：新消息追加在底部，列表跟着滚到最后一条。 */
@Composable
internal fun LiveDanmakuPanel(
    messages: List<LiveDanmakuMessage>,
    firstSeq: Long,
    state: LiveDanmakuState,
    error: String?,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDanmakuSettings: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "弹幕",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = danmakuStatusText(state, error),
                fontSize = 11.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
            DanmakuToggle(
                enabled = danmakuEnabled,
                onToggle = onToggleDanmaku,
                onOpenSettings = onOpenDanmakuSettings,
            )
        }

        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = when (state) {
                        LiveDanmakuState.Connected -> "还没有人说话"
                        LiveDanmakuState.Connecting, LiveDanmakuState.Idle -> "正在连接弹幕…"
                        LiveDanmakuState.Failed -> error ?: "弹幕连接失败"
                    },
                    fontSize = 12.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    count = messages.size,
                    key = { index -> firstSeq + index },
                    contentType = { "live-danmaku" },
                ) { index ->
                    DanmakuRow(messages[index])
                }
            }
        }
    }
}

@Composable
private fun DanmakuRow(message: LiveDanmakuMessage) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (message.medalName.isNotBlank()) {
                Text(
                    text = "${message.medalName} ${message.medalLevel}",
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(COUITheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 10.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.padding(horizontal = 2.dp))
            }
            Text(
                text = message.name,
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
        Text(
            text = message.text,
            modifier = Modifier.padding(top = 1.dp),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = COUITheme.colorScheme.onSurface,
        )
    }
}

private fun danmakuStatusText(state: LiveDanmakuState, error: String?): String = when (state) {
    LiveDanmakuState.Connected -> "已连接"
    LiveDanmakuState.Connecting -> "连接中…"
    LiveDanmakuState.Failed -> error ?: "连接失败"
    LiveDanmakuState.Idle -> ""
}
