package erl.bilico.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicLiveCard
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.LoadingImage
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 卡片内可用宽度：屏宽减去列表留白（24dp）与卡片内边距（28dp）。 */
@Composable
internal fun cardContentWidthDp(): Dp = LocalConfiguration.current.screenWidthDp.dp - 52.dp

/**
 * 开播动态：封面右上角是「N人看过」与「直播中」，左下角是分区名，封面下面是直播间标题。
 * 整块点进直播间。
 */
@Composable
internal fun DynamicLiveCardView(
    live: DynamicLiveCard,
    onOpenLive: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val contentWidth = cardContentWidthDp()
    val widthPx = remember(density, contentWidth) {
        with(density) { contentWidth.roundToPx() }
    }
    val coverUrl = remember(live.cover, widthPx) {
        Formatters.sizedImageUrl(live.cover, widthPx, widthPx * 9 / 16)
    }
    val onClick = remember(live.roomId, onOpenLive) { { onOpenLive(live.roomId) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            LoadingImage(
                url = coverUrl,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (live.watchedText.isNotBlank()) {
                    Text(
                        text = live.watchedText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x66000000))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                if (live.living) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFFB7299))
                            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiveSignalBars()
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "直播中",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (live.areaName.isNotBlank()) {
                Text(
                    text = live.areaName,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    style = TextStyle(
                        shadow = Shadow(color = Color(0xCC000000), offset = Offset(0f, 1f), blurRadius = 4f),
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (live.title.isNotBlank()) {
            Text(
                text = live.title,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「直播中」角标左边那三根信号格：由矮到高，纯装饰。 */
@Composable
private fun LiveSignalBars() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        listOf(4.dp, 7.dp, 10.dp).forEach { height ->
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White),
            )
        }
    }
}

/**
 * 长文型动态的正文图还在取时占位：形状和后面的真实九宫格一致（三列方格），
 * 取到图直接换成同一块，卡片不会先闪一串「[图片]」再重排。
 */
@Composable
internal fun OpusImagePlaceholder(modifier: Modifier = Modifier) {
    val cell = COUITheme.colorScheme.surfaceContainer
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cell),
                    )
                }
            }
        }
    }
}

/** feed 里长文图片的占位写法。 */
private const val IMAGE_PLACEHOLDER = "[图片]"

/** 去掉占位标记，顺便把因此空出来的行收掉。 */
internal fun String.withoutImagePlaceholders(): String =
    replace(IMAGE_PLACEHOLDER, "")
        .lines()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")

@Composable
internal fun DynamicImageGrid(
    images: List<DynamicImage>,
    onOpenImage: (List<DynamicImage>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val contentWidth = cardContentWidthDp()

    if (images.size == 1) {
        val image = images.first()
        val ratio = if (image.width > 0 && image.height > 0) {
            (image.width.toFloat() / image.height.toFloat()).coerceIn(0.65f, 1.85f)
        } else {
            4f / 3f
        }
        val singleWidthDp = (contentWidth * 0.68f).coerceAtLeast(160.dp)
        val singleWidthPx = remember(density, singleWidthDp) {
            with(density) { singleWidthDp.roundToPx() }
        }
        val url = remember(image.url, singleWidthPx) {
            Formatters.widthImageUrl(image.url, singleWidthPx)
        }
        Box(
            modifier = modifier
                .width(singleWidthDp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenImage(images, 0) },
        ) {
            LoadingImage(
                url = url,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        return
    }

    val columns = if (images.size == 2 || images.size == 4) 2 else 3
    val cellPx = remember(density, columns, contentWidth) {
        with(density) { ((contentWidth - 8.dp) / columns).roundToPx() }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        images.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEachIndexed { columnIndex, image ->
                    val index = rowIndex * columns + columnIndex
                    val url = remember(image.url, cellPx) {
                        Formatters.sizedImageUrl(image.url, cellPx, cellPx)
                    }
                    val onClick = remember(images, index, onOpenImage) {
                        { onOpenImage(images, index) }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClick),
                    ) {
                        LoadingImage(
                            url = url,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
