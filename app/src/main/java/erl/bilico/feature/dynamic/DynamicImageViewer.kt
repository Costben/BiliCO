package erl.bilico.feature.dynamic

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.util.Formatters
import erl.bilico.feature.video.findActivity
import erl.bilico.ui.ExpandEasing
import erl.bilico.ui.ImageViewerHost
import erl.bilico.ui.ImageViewerSession
import erl.bilico.ui.ImageZoomMillis
import erl.bilico.ui.LocalImageOrigins
import erl.bilico.ui.LocalImageViewerHost
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val ChromeScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.55f),
    0.62f to Color.Black.copy(alpha = 0.32f),
    1f to Color.Transparent,
)

/** 挂到根节点看图宿主：与列表、详情、评论弹窗同处一个窗口坐标系，才能从原缩略图原地长出并缩回。 */
@Composable
fun DynamicImageViewer(
    images: List<DynamicImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    val host = LocalImageViewerHost.current ?: return
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val session = remember(images, initialIndex) {
        ImageViewerSession(
            images = images,
            initialIndex = initialIndex,
            onDismiss = { currentOnDismiss() },
        )
    }
    DisposableEffect(host, session) {
        host.show(session)
        onDispose { host.release(session) }
    }
}

@Composable
internal fun ImageViewerOverlay(
    host: ImageViewerHost,
    session: ImageViewerSession,
) {
    val images = session.images
    if (images.isEmpty()) return

    val origins = LocalImageOrigins.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    val widthPx = remember(configuration.screenWidthDp, density) {
        (configuration.screenWidthDp * density * 1.5f).toInt().coerceAtLeast(1080)
    }

    val urls = remember(images) { images.map { Formatters.normalizeImageUrl(it.url) } }
    val frame = remember(session) {
        ViewerFrame().apply {
            images.forEach { image ->
                if (image.width > 0 && image.height > 0) {
                    val key = Formatters.normalizeImageUrl(image.url)
                    aspects[key] = image.width.toFloat() / image.height.toFloat()
                }
            }
        }
    }
    val progress: () -> Float = remember(session) { { session.progress.value } }

    LaunchedEffect(session) {
        if (!session.exiting && !session.previewing) {
            session.progress.animateTo(1f, tween(ImageZoomMillis, easing = ExpandEasing))
        }
    }

    var chrome by remember(session) { mutableStateOf(true) }
    val originalPages = remember(session) { mutableStateMapOf<Int, Boolean>() }
    var downloading by remember(session) { mutableStateOf(false) }

    val start = session.initialIndex.coerceIn(0, urls.lastIndex)
    val pagerState = rememberPagerState(initialPage = start) { urls.size }
    val current = urls.getOrNull(pagerState.currentPage).orEmpty()

    SideEffect { origins.viewing = current }
    DisposableEffect(session) {
        onDispose { origins.viewing = null }
    }

    ViewerSystemBars(visible = chrome)

    // 拖拽退出动画播放期间拦截返回键，防止重复触发收起。
    BackHandler(enabled = frame.dismiss.exiting) { }

    // 预测返回跟手随手指进度实时缩回原缩略图。
    PredictiveBackHandler(enabled = !frame.dismiss.exiting) { events ->
        var lastProgress = 1f
        var moved = false
        session.previewing = true
        try {
            events.collect { event ->
                val value = (1f - event.progress).coerceIn(0f, 1f)
                if (value < 0.98f) moved = true
                lastProgress = value
                session.progress.snapTo(value)
            }
            if (lastProgress <= 0.02f && moved) {
                host.completePredictiveClose(session)
            } else {
                host.close(session)
            }
        } catch (cancelled: CancellationException) {
            if (lastProgress <= 0.2f && moved) {
                host.completePredictiveClose(session)
                return@PredictiveBackHandler
            }
            withContext(NonCancellable) {
                session.progress.animateTo(1f, tween(180, easing = ExpandEasing))
                session.previewing = false
            }
            throw cancelled
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { frame.host = it }
            .onSizeChanged { frame.dismiss.boxSize = it },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val d = frame.dismiss
                    val bgAlpha = when {
                        d.exiting -> d.exitBgAlpha.value
                        d.active -> d.currentBgAlpha()
                        else -> progress()
                    }
                    if (bgAlpha > 0f) drawRect(Color.Black, alpha = bgAlpha)
                },
        )

        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            beyondViewportPageCount = 1,
            userScrollEnabled = urls.size > 1 && !frame.dismiss.active && !frame.dismiss.exiting,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val keyUrl = urls[page]
            val requestUrl = if (originalPages[page] == true) {
                keyUrl
            } else {
                Formatters.widthImageUrl(images[page].url, widthPx)
            }
            ZoomableImage(
                keyUrl = keyUrl,
                requestUrl = requestUrl,
                thumbModel = origins.modelOf(keyUrl),
                original = originalPages[page] == true,
                isCurrent = pagerState.currentPage == page,
                isSettledElsewhere = pagerState.settledPage != page,
                canPage = urls.size > 1,
                isPagerScrolling = { abs(pagerState.currentPageOffsetFraction) > 0.01f },
                canStartDismiss = {
                    !session.exiting && !session.previewing && progress() >= 0.98f
                },
                progress = progress,
                origins = origins,
                frame = frame,
                onTap = { if (!frame.dismiss.exiting) chrome = !chrome },
                onDismissRelease = { velocity ->
                    handleDismissRelease(
                        velocity = velocity,
                        dismiss = frame.dismiss,
                        frame = frame,
                        origins = origins,
                        url = urls.getOrNull(pagerState.currentPage),
                        density = density,
                        scope = scope,
                        onCloseImmediate = { host.closeImmediate(session) },
                    )
                },
            )
        }

        if (chrome) {
            val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout)
            val dismissChromeAlpha by animateFloatAsState(
                targetValue = if (frame.dismiss.dragging || frame.dismiss.exiting) 0f else 1f,
                animationSpec = tween(150),
                label = "dismissChrome",
            )
            val chromeFade: () -> Float = {
                ((progress() - ChromeFadeStart) / (1f - ChromeFadeStart)).coerceIn(0f, 1f) * dismissChromeAlpha
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeFade() }
                    .background(ChromeScrim)
                    .windowInsetsPadding(topInsets)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerIconButton(
                    icon = COUIIcons.Back,
                    label = "返回",
                    onClick = {
                        if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                            frame.dismiss.cancelActive()
                            host.close(session)
                        }
                    },
                )
                Spacer(Modifier.weight(1f))
                val currentPage = pagerState.currentPage
                val isOriginal = originalPages[currentPage] == true
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isOriginal) COUITheme.colorScheme.primary else Color(0x33FFFFFF),
                        )
                        .clickable {
                            if (!frame.dismiss.exiting && !frame.dismiss.dragging) {
                                originalPages[currentPage] = !isOriginal
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "原图", fontSize = 13.sp, color = Color.White)
                }
                Spacer(Modifier.size(8.dp))
                ViewerIconButton(
                    icon = COUIIcons.Download,
                    label = "下载",
                    onClick = {
                        if (!downloading && !frame.dismiss.exiting && !frame.dismiss.dragging) {
                            downloading = true
                            val saveUrl = urls[currentPage]
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
                text = "${pagerState.currentPage + 1} / ${urls.size}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = chromeFade() }
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
private fun ViewerSystemBars(visible: Boolean) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val window = remember(context) { context.findActivity()?.window }
    val controller = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }
    LaunchedEffect(controller, visible) {
        controller ?: return@LaunchedEffect
        controller.isAppearanceLightStatusBars = false
        if (visible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }
    DisposableEffect(controller, systemDark) {
        val wasLight = controller?.isAppearanceLightStatusBars ?: !systemDark
        onDispose {
            controller?.let {
                it.isAppearanceLightStatusBars = wasLight
                it.show(WindowInsetsCompat.Type.statusBars())
            }
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
