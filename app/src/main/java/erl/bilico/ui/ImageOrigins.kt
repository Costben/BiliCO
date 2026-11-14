package erl.bilico.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.DynamicImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

const val ImageZoomMillis = 420

/**
 * 缩略图在根宿主坐标系下的信息：位置、圆角、真实宽高比以及卡片实际绘制的模型地址。
 * 看图页借此在首帧直接复用缩略图已解码的位图并完成原地生长/缩回转场。
 */
internal class ThumbBounds(
    val rect: Rect,
    val cornerRadius: Float,
    val aspect: Float?,
    val model: String?,
)

internal class ThumbSlot {
    var coordinates: LayoutCoordinates? = null
    var cornerRadius: Dp = 0.dp
    var aspect: Float? = null
    var model: String? = null
}

/** 屏幕上各图片缩略图的位置索引（按规范化图片 URL 建索引）。 */
@Stable
class ImageOrigins {
    private val slots = HashMap<String, ThumbSlot>()

    /** 看图页正在展示或拖拽的那一张图片 URL；为空表示看图页未打开。 */
    var viewing by mutableStateOf<String?>(null)

    internal fun put(url: String, slot: ThumbSlot) {
        slots[url] = slot
    }

    internal fun remove(url: String, slot: ThumbSlot) {
        if (slots[url] === slot) slots.remove(url)
    }

    /** 卡片缩略图实际请求并已解码的模型地址，首帧先由它顶上避免空白闪屏。 */
    internal fun modelOf(url: String): String? = slots[url]?.model

    /** 实时读取缩略图当前屏幕位置，退场时即使列表滚过也能准确缩回目标位置。 */
    internal fun boundsOf(url: String, host: LayoutCoordinates, density: Float): ThumbBounds? {
        val slot = slots[url] ?: return null
        val coordinates = slot.coordinates?.takeIf { it.isAttached } ?: return null
        if (!host.isAttached) return null
        val rect = host.localBoundingBoxOf(coordinates, clipBounds = false)
        if (rect.width <= 0f || rect.height <= 0f) return null
        val radius = (slot.cornerRadius.value * density)
            .coerceAtMost(minOf(rect.width, rect.height) / 2f)
        return ThumbBounds(rect, radius, slot.aspect, slot.model)
    }
}

val LocalImageOrigins = staticCompositionLocalOf { ImageOrigins() }

/**
 * 标记该元素为 [url] 对应的缩略图：点开看图页从此处长出、关闭或甩出时缩回此处；
 * 当看图页正在查看同一张图时自动隐藏底层缩略图，避免半透明拖拽背景透出重影。
 */
@Composable
fun Modifier.imageOrigin(
    url: String,
    cornerRadius: Dp,
    aspect: Float? = null,
    model: String? = null,
): Modifier {
    val origins = LocalImageOrigins.current
    val normalizedUrl = url
    val slot = remember(normalizedUrl) { ThumbSlot() }
    SideEffect {
        slot.cornerRadius = cornerRadius
        slot.aspect = aspect
        slot.model = model
    }
    DisposableEffect(origins, normalizedUrl, slot) {
        onDispose { origins.remove(normalizedUrl, slot) }
    }
    return this
        .onPlaced {
            slot.coordinates = it
            origins.put(normalizedUrl, slot)
        }
        .graphicsLayer {
            alpha = if (origins.viewing == normalizedUrl) 0f else 1f
        }
}

@Stable
class ImageViewerSession(
    val images: List<DynamicImage>,
    val initialIndex: Int,
    internal val onDismiss: () -> Unit,
) {
    val progress = Animatable(0f)
    var exiting by mutableStateOf(false)
    var previewing by mutableStateOf(false)
}

/** 根节点的看图浮层宿主：与页面栈、回复弹窗共享同一窗口坐标系。 */
@Stable
class ImageViewerHost {
    var active by mutableStateOf<ImageViewerSession?>(null)
        private set

    private var scope: CoroutineScope? = null

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    internal fun show(session: ImageViewerSession) {
        session.exiting = false
        session.previewing = false
        active = session
    }

    internal fun release(session: ImageViewerSession) {
        if (active === session) {
            active = null
        }
    }

    fun close(session: ImageViewerSession? = active) {
        val target = session ?: return
        if (active !== target || target.exiting) return
        val coroutineScope = scope ?: run {
            closeImmediate(target)
            return
        }
        target.exiting = true
        coroutineScope.launch {
            try {
                target.progress.animateTo(0f, tween(ImageZoomMillis, easing = ExpandEasing))
            } catch (_: CancellationException) {
                // 让 finally 完成状态收口。
            } finally {
                withContext(NonCancellable) {
                    if (active === target) active = null
                    target.onDismiss()
                }
            }
        }
    }

    fun completePredictiveClose(session: ImageViewerSession) {
        if (active !== session || session.exiting) return
        val coroutineScope = scope ?: run {
            closeImmediate(session)
            return
        }
        session.exiting = true
        val remaining = session.progress.value.coerceIn(0f, 1f)
        val duration = (remaining * ImageZoomMillis).roundToInt().coerceIn(80, 260)
        coroutineScope.launch {
            try {
                if (remaining > 0.005f) {
                    session.progress.animateTo(0f, tween(duration, easing = ExpandEasing))
                }
            } finally {
                withContext(NonCancellable) {
                    if (active === session) active = null
                    session.onDismiss()
                }
            }
        }
    }

    fun closeImmediate(session: ImageViewerSession? = active) {
        val target = session ?: return
        if (active === target) active = null
        target.onDismiss()
    }
}

val LocalImageViewerHost = staticCompositionLocalOf<ImageViewerHost?> { null }
