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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    val popularity = when {
        room.online > 0L -> "${Formatters.formatStat(room.online)}人气"
        else -> room.popularityText
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(6.dp),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(10.dp)),
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
                if (popularity.isNotBlank()) {
                    Text(text = popularity, fontSize = 11.sp, color = Color.White)
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
