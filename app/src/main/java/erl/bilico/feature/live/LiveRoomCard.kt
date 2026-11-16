package erl.bilico.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.LiveRoomItem
import erl.bilico.data.repository.LiveRepository
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 让列表/动态卡片在可见时按需补齐高能观众人数，并共享仓库内存缓存。 */
internal val LocalLiveRepository = staticCompositionLocalOf<LiveRepository?> { null }

@Composable
internal fun rememberOnlineRankCount(
    roomId: Long,
    uid: Long,
    initial: Long?,
    active: Boolean = true,
): Long? {
    val repository = LocalLiveRepository.current
    var count by remember(roomId, initial) {
        mutableStateOf(initial ?: repository?.cachedOnlineRankCount(roomId))
    }
    LaunchedEffect(roomId, uid, active, repository) {
        if (!active || repository == null || roomId <= 0L || uid <= 0L || count != null) return@LaunchedEffect
        repository.onlineRankCount(roomId, uid).onSuccess { count = it }
    }
    return count
}

/**
 * 直播间的封面卡片：直播首页与动态页的直播筛选共用。
 * 封面按两列布局的控件宽度请求缩略图，避免拉整张原图。
 */
@Composable
internal fun LiveRoomCard(room: LiveRoomItem, onClick: () -> Unit) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val density = LocalDensity.current
    val coverUrl = remember(room.cover, screenWidthDp, density) {
        val widthPx = with(density) { ((screenWidthDp.dp - 34.dp) / 2).roundToPx() }
        Formatters.sizedImageUrl(room.cover, widthPx, widthPx * 10 / 16)
    }
    val onlineCount = rememberOnlineRankCount(
        roomId = room.roomId,
        uid = room.uid,
        initial = room.onlineCount,
    )
    val highEnergyText = onlineCount?.let { "高能观众(${Formatters.formatStat(it)})" }.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(6.dp),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(SquircleShape(10.dp)),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "直播中", fontSize = 11.sp, color = Color.White)
                Box(Modifier.padding(start = 6.dp).weight(1f))
                if (highEnergyText.isNotEmpty()) {
                    Text(text = highEnergyText, fontSize = 11.sp, color = Color.White, maxLines = 1)
                }
            }
        }
        Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            Text(
                text = room.title,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onSurface,
                // 固定按两行排版：单行标题也占两行高，两列卡片才不会一高一低。
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(room.anchor.ifBlank { "未知主播" })
                    if (room.areaName.isNotBlank()) append(" · ").append(room.areaName)
                },
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 11.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
