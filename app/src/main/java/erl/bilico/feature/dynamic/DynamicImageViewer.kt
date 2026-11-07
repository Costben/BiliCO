package erl.bilico.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs

/** 全屏看图：支持左右滑动切图、双指缩放拖拽、双击放大还原、单击关闭。 */
@Composable
fun DynamicImageViewer(
    images: List<DynamicImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, images.lastIndex),
            pageCount = { images.size },
        )
        var currentPage by remember { mutableIntStateOf(pagerState.currentPage) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { currentPage = it }
        }

        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val density = LocalDensity.current
        val widthPx = remember(screenWidthDp, density) {
            with(density) { (screenWidthDp.dp * 1.5f).roundToPx().coerceAtLeast(1080) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offsetX by remember(page) { mutableFloatStateOf(0f) }
                var offsetY by remember(page) { mutableFloatStateOf(0f) }
                LaunchedEffect(currentPage) {
                    if (currentPage != page) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
                val url = remember(images[page].url, widthPx) {
                    Formatters.widthImageUrl(images[page].url, widthPx)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = { onDismiss() },
                                onDoubleTap = {
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.4f
                                    }
                                },
                            )
                        }
                        .pointerInput(page) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val multiTouch = event.changes.size > 1
                                    if (multiTouch || scale > 1.02f) {
                                        val nextScale = (scale * zoomChange).coerceIn(1f, 4.5f)
                                        val maxX = (size.width * (nextScale - 1f)) * 0.5f
                                        val maxY = (size.height * (nextScale - 1f)) * 0.5f
                                        scale = nextScale
                                        if (nextScale > 1.01f) {
                                            offsetX = (offsetX + panChange.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + panChange.y).coerceIn(-maxY, maxY)
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        if (multiTouch || abs(zoomChange - 1f) * centroidSize > 2f) {
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Text(
                text = "${currentPage + 1} / ${images.size}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                style = COUITheme.textStyles.footnote1,
                color = Color.White,
            )
        }
    }
}
