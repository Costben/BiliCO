package erl.bilico.feature.dynamic

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.util.Formatters
import erl.bilico.ui.components.LoadingImage
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val originalPages = remember { mutableStateMapOf<Int, Boolean>() }
        var downloading by remember { mutableStateOf(false) }

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
                val url = remember(images[page].url, widthPx, originalPages[page]) {
                    if (originalPages[page] == true) {
                        Formatters.normalizeImageUrl(images[page].url)
                    } else {
                        Formatters.widthImageUrl(images[page].url, widthPx)
                    }
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
                    LoadingImage(
                        url = url,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Fit,
                        backgroundColor = Color.Transparent,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerIconButton(icon = COUIIcons.Back, label = "返回", onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                val isOriginal = originalPages[currentPage] == true
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isOriginal) COUITheme.colorScheme.primary else Color(0x33FFFFFF)
                        )
                        .clickable { originalPages[currentPage] = !isOriginal }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "原图",
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.size(8.dp))
                ViewerIconButton(
                    icon = COUIIcons.Download,
                    label = "下载",
                    onClick = {
                        if (!downloading) {
                            downloading = true
                            val saveUrl = Formatters.normalizeImageUrl(images[currentPage].url)
                            scope.launch {
                                val ok = saveImageToGallery(context, saveUrl)
                                downloading = false
                                Toast.makeText(
                                    context,
                                    if (ok) "已保存到相册" else "保存失败",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                )
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

@Composable
private fun ViewerIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        tint = Color.White,
    )
}

/** 把原图解码成 Bitmap 后写进相册，失败返回 false。 */
private suspend fun saveImageToGallery(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext false
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(Size.ORIGINAL)
        .build()
    val bitmap = try {
        val result = SingletonImageLoader.get(context).execute(request)
        (result as? SuccessResult)?.image?.let { (it as? BitmapImage)?.bitmap }
    } catch (_: Throwable) {
        null
    } ?: return@withContext false

    val name = "bilico_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/BiliCO",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = try {
        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } catch (_: Throwable) {
        null
    } ?: return@withContext false
    return@withContext try {
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        } ?: return@withContext false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (_: Throwable) {
        resolver.delete(uri, null, null)
        false
    }
}
