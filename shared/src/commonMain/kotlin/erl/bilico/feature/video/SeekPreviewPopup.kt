package erl.bilico.feature.video

import erl.bilico.platform.PlatformContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import coil3.PlatformContext as CoilPlatformContext
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext
import erl.bilico.platform.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import coil3.size.Size
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.model.VideoPreviewFrame
import erl.bilico.data.model.VideoPreviewShot
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CoroutineScope
import erl.bilico.platform.ioDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 进度条预览雪碧图控制器：
 * - 按需加载（呼出控件或开始拖动进度/手势快进时触发），不拖慢视频起播；
 * - 在控制器生命周期内复用已解码的雪碧图（上限 4 张，单边 ≤ 2400px 避免超 4096 纹理上限）。
 */
@Stable
internal class SeekPreviewController(
    private val context: CoilPlatformContext,
    private val repository: VideoRepository,
    private val scope: CoroutineScope,
) {
    var shot: VideoPreviewShot? by mutableStateOf(null)
        private set

    private val sheetCache = mutableStateMapOf<String, ImageBitmap>()
    private val cacheOrder = ArrayDeque<String>()
    private val loadingSheets = HashSet<String>()

    private var activeBvid: String = ""
    private var activeCid: Long = 0L
    private var attempted: Boolean = false
    private var loadJob: Job? = null

    fun attach(bvid: String, cid: Long) {
        if (bvid == activeBvid && cid == activeCid) return
        loadJob?.cancel()
        loadJob = null
        activeBvid = bvid
        activeCid = cid
        attempted = false
        shot = null
        sheetCache.clear()
        cacheOrder.clear()
        loadingSheets.clear()
    }

    fun ensureLoaded(positionMs: Long = 0L, durationMs: Long = 0L) {
        val currentShot = shot
        if (currentShot != null) {
            currentShot.resolveFrame(positionMs, durationMs)?.sheetUrl?.let(::requestSheet)
            return
        }
        if (attempted || activeBvid.isBlank() || activeCid <= 0L) return
        attempted = true
        val targetBvid = activeBvid
        val targetCid = activeCid
        loadJob = scope.launch {
            val loaded = repository.videoshot(targetBvid, targetCid)
            if (targetBvid == activeBvid && targetCid == activeCid) {
                shot = loaded
                loaded?.resolveFrame(positionMs, durationMs)?.sheetUrl?.let(::requestSheet)
            }
        }
    }

    fun bitmapFor(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        sheetCache[url]?.let { return it }
        requestSheet(url)
        return null
    }

    private fun requestSheet(url: String) {
        if (url.isBlank() || sheetCache.containsKey(url) || url in loadingSheets) return
        val targetBvid = activeBvid
        val targetCid = activeCid
        loadingSheets.add(url)
        scope.launch {
            val decoded = withContext(ioDispatcher) {
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(Size(MAX_SHEET_DIMENSION_PX, MAX_SHEET_DIMENSION_PX))
                        .scale(Scale.FIT)
                        .build()
                    val result = SingletonImageLoader.get(context).execute(request)
                    ((result as? SuccessResult)?.image as? BitmapImage)?.bitmap?.toComposeImageBitmap()
                }.getOrNull()
            }
            loadingSheets.remove(url)
            if (decoded != null && targetBvid == activeBvid && targetCid == activeCid) {
                if (cacheOrder.size >= MAX_CACHED_SHEETS) {
                    val evicted = cacheOrder.removeFirstOrNull()
                    if (evicted != null) sheetCache.remove(evicted)
                }
                sheetCache[url] = decoded
                cacheOrder.addLast(url)
            }
        }
    }

    private companion object {
        const val MAX_CACHED_SHEETS = 4
        const val MAX_SHEET_DIMENSION_PX = 2400
    }
}

@Composable
internal fun rememberSeekPreviewController(
    repository: VideoRepository,
    bvid: String,
    cid: Long,
): SeekPreviewController {
    val context = LocalCoilPlatformContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context, repository, scope) { SeekPreviewController(context, repository, scope) }
    LaunchedEffect(controller, bvid, cid) {
        controller.attach(bvid, cid)
    }
    return controller
}

/** 进度条拖拽时悬停在滑块正上方的预览浮窗（自动贴边防溢出）。 */
@Composable
internal fun SeekPreviewFloatingOverlay(
    scrubbingPositionMs: Long,
    durationMs: Long,
    preview: SeekPreviewController?,
    chapters: List<VideoChapter>,
    fullscreen: Boolean,
    bottomPadding: Dp,
    horizontalPad: Dp,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0L) return
    val cardWidth = if (fullscreen) 168.dp else 136.dp
    val fraction = (scrubbingPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
    ) {
        val density = LocalDensity.current
        val totalPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val padPx = with(density) { horizontalPad.toPx() }
        val trackPadPx = with(density) { 6.dp.toPx() }
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val trackStart = padPx + trackPadPx
        val trackSpan = (totalPx - trackStart * 2f).coerceAtLeast(1f)
        val anchorX = trackStart + fraction * trackSpan
        val maxLeft = (totalPx - padPx - cardWidthPx).coerceAtLeast(padPx)
        val offsetX = (anchorX - cardWidthPx / 2f).coerceIn(padPx, maxLeft).roundToInt()

        SeekPreviewCard(
            positionMs = scrubbingPositionMs,
            durationMs = durationMs,
            preview = preview,
            chapters = chapters,
            deltaSec = null,
            compact = !fullscreen,
            modifier = Modifier
                .width(cardWidth)
                .offset { IntOffset(offsetX, 0) },
        )
    }
}

/**
 * 进度预览卡片：有雪碧图时按 [VideoPreviewFrame] 裁切对应缩略图帧，无雪碧图或加载中时退回紧凑时间提示。
 */
@Composable
internal fun SeekPreviewCard(
    positionMs: Long,
    durationMs: Long,
    preview: SeekPreviewController?,
    chapters: List<VideoChapter> = emptyList(),
    deltaSec: Long? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shot = preview?.shot
    val frame = remember(shot, positionMs, durationMs) {
        shot?.resolveFrame(positionMs, durationMs)
    }
    val chapterTitle = remember(chapters, positionMs) {
        if (chapters.isEmpty()) {
            null
        } else {
            chapters.firstOrNull { positionMs in it.fromMs..it.toMs }?.title
                ?: chapters.lastOrNull { positionMs >= it.fromMs }?.title
        }?.takeIf { it.isNotBlank() }
    }
    val sheetBitmap = preview?.bitmapFor(frame?.sheetUrl)

    val timeLabel = remember(positionMs, durationMs, deltaSec) {
        val base = "${Formatters.formatDuration(positionMs / 1000)} / ${Formatters.formatDuration(durationMs / 1000)}"
        if (deltaSec != null) {
            val sign = if (deltaSec >= 0L) "+" else ""
            "$base (${sign}${deltaSec}s)"
        } else {
            base
        }
    }

    if (frame == null) {
        Column(
            modifier = modifier
                .clip(SquircleShape(10.dp))
                .background(Color(0xD9141416))
                .border(0.8.dp, Color.White.copy(alpha = 0.14f), SquircleShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = timeLabel,
                style = COUITheme.textStyles.footnote1,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
            )
            if (chapterTitle != null) {
                Text(
                    text = chapterTitle,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }

    val aspect = (frame.cellWidth.toFloat() / frame.cellHeight.toFloat()).coerceIn(0.6f, 2.4f)
    Column(
        modifier = modifier
            .clip(SquircleShape(12.dp))
            .background(Color(0xE6141416))
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), SquircleShape(12.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(SquircleShape(8.dp))
                .background(Color(0xFF1F1F23)),
        ) {
            if (sheetBitmap != null) {
                SpriteFrameCanvas(
                    bitmap = sheetBitmap,
                    frame = frame,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = timeLabel,
            fontSize = if (compact) 11.sp else 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (chapterTitle != null) {
            Text(
                text = chapterTitle,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SpriteFrameCanvas(
    bitmap: ImageBitmap,
    frame: VideoPreviewFrame,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val bmpW = bitmap.width
        val bmpH = bitmap.height
        if (bmpW <= 0 || bmpH <= 0) return@Canvas
        val cellW = bmpW.toFloat() / frame.xLen.coerceAtLeast(1)
        val cellH = bmpH.toFloat() / frame.yLen.coerceAtLeast(1)
        val srcX = (frame.col * cellW).roundToInt().coerceIn(0, (bmpW - 1).coerceAtLeast(0))
        val srcY = (frame.row * cellH).roundToInt().coerceIn(0, (bmpH - 1).coerceAtLeast(0))
        val drawW = cellW.roundToInt().coerceIn(1, (bmpW - srcX).coerceAtLeast(1))
        val drawH = cellH.roundToInt().coerceIn(1, (bmpH - srcY).coerceAtLeast(1))
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(drawW, drawH),
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
        )
    }
}
