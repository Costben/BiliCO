package erl.bilico.ui

import android.content.res.Configuration
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import kotlin.math.pow

internal class ExpandShape(
    private val heightPx: Float,
    private val radiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val height = heightPx.coerceIn(0f, size.height)
        val r = radiusPx.coerceAtLeast(0f)
        // 用硬件 RoundRect 裁剪替代逐帧重算通用 Path，避免过渡期 RenderThread 反复生成蒙版。
        return Outline.Rounded(RoundRect(0f, 0f, size.width, height, r, r))
    }
}

@Composable
internal fun rememberScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val radius = context.display
                    ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                    ?.radius ?: 0
                with(density) { radius.toDp() }
            }.getOrDefault(0.dp)
        } else {
            0.dp
        }
    }
}

/** 最近一次竖屏时的配置与窗口尺寸，横屏期间给被盖住的页面用。只在组合里读写，不需要快照状态。 */
internal class FrozenPortrait {
    var configuration: Configuration? = null
        private set
    var width = 0
        private set
    var height = 0
        private set
    val ready: Boolean get() = configuration != null && width > 0 && height > 0

    fun capture(configuration: Configuration, width: Int, height: Int) {
        this.configuration = configuration
        this.width = width
        this.height = height
    }
}

/** 手势要拉过这里才算「已经落回卡片」，松手即走；再弹回来会像 bug。 */
internal const val LandedProgress = 0.2f

@Composable
internal fun ExpandLayer(
    entry: NavEntry,
    screenCorner: Dp,
    progress: () -> Float,
    fade: () -> Float,
    content: @Composable () -> Unit,
) {
    val origin = entry.activeOrigin
    val bounds = origin?.bounds
    if (origin == null || bounds == null) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBarTop = remember(view, density) {
        val fromInsets = ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: 0
        if (fromInsets > 0) {
            fromInsets.toFloat()
        } else {
            val resId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) view.resources.getDimensionPixelSize(resId).toFloat() else with(density) { 28.dp.toPx() }
        }
    }
    val surface = pageContainerColor()
    val exiting = entry.exiting || entry.previewing
    // 退场时框在 LandedProgress 之前就落回卡片，手指停在末尾时看到的就是松手后的样子。
    fun frameProgress(p: Float): Float =
        if (exiting) ((p - LandedProgress) / (1f - LandedProgress)).coerceIn(0f, 1f) else p
    fun enterContentAlpha(): Float = ((fade() - 0.02f) / 0.24f).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (entry.expand && !exiting && progress() > 0.0005f) {
                    drawRect(Color.Black, alpha = 0.3f * progress())
                }
            }
            .graphicsLayer {
                if (!entry.expand) {
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    alpha = 1f
                    clip = false
                    return@graphicsLayer
                }
                val p = frameProgress(progress())
                val left = lerp(bounds.left, 0f, p)
                val top = lerp(bounds.top, 0f, p)
                val width = lerp(bounds.width, size.width, p)
                val cardH = if (bounds.width > 0f) bounds.height * (width / bounds.width) else lerp(bounds.height, size.height, p)
                val height = if (exiting) {
                    val t = ((p - 0.24f) / 0.76f).coerceIn(0f, 1f)
                    lerp(cardH, size.height, t.pow(1.6f))
                } else {
                    lerp(bounds.height, size.height, p)
                }
                val scale = if (size.width > 0f && width > 0f) width / size.width else 1f
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = left
                translationY = top
                scaleX = scale
                scaleY = scale
                alpha = if (entry.minimizingToOrb && exiting) {
                    (progress() / LandedProgress).coerceIn(0f, 1f)
                } else {
                    1f
                }
                val radius = lerp(origin.cornerRadius.toPx(), screenCorner.toPx(), p) / scale
                if (p < 1f) {
                    shape = ExpandShape(height / scale, radius)
                    clip = true
                } else {
                    clip = false
                }
            }
            .drawWithContent {
                if (entry.expand && !exiting && frameProgress(progress()) <= 0.0005f) return@drawWithContent
                drawContent()
            },
    ) {
        // 转场期间把整页先画进一张离屏图层，再整体缩放、圆角裁剪后贴上去。
        // 直接在圆角裁剪和缩放底下逐个绘制，GPU 要为每种「控件 × 圆角裁剪」组合现编一条渲染管线，
        // 冷启动后头几次开、退视频会因此连掉几帧；离屏后页面内容按平常的方式绘制，裁剪只作用在一次贴图上。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val transitioning = entry.expand && (exiting || progress() < 1f || enterContentAlpha() < 0.999f)
                    compositingStrategy = if (transitioning && !entry.hasSurfaceView) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                }
                .drawBehind {
                    if (!entry.expand) return@drawBehind
                    val p = frameProgress(progress())
                    if (exiting) {
                        if (p < 1f) drawRect(surface)
                        return@drawBehind
                    }
                    val ca = enterContentAlpha()
                    if (p < 1f || ca < 0.999f) {
                        drawRect(surface)
                        if (ca < 0.999f) drawCardSnapshot(origin.layer, bounds, alpha = 1f, surface = null)
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (!entry.expand || !exiting) return@drawWithContent
                    val cardAlpha = ((0.24f - frameProgress(progress())) / 0.18f).coerceIn(0f, 1f)
                    if (cardAlpha > 0f) drawCardSnapshot(origin.layer, bounds, alpha = cardAlpha, surface = surface)
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // 淡入用普通的图层透明度。ModulateAlpha 会把每个绘制换成另一套着色器组合，
                        // 冷启动后第一次展开要为此现编七八条管线，正好压在动画上。
                        if (!entry.expand) {
                            alpha = 1f
                            translationY = 0f
                            return@graphicsLayer
                        }
                        val p = frameProgress(progress())
                        alpha = if (exiting) 1f else enterContentAlpha()
                        translationY = -statusBarTop * (1f - p)
                    },
            ) {
                Box(Modifier.fillMaxSize().background(surface)) { content() }
            }
        }
    }
}

/** 把点击时拍下的卡片按页面宽度放大画上来；[surface] 非空时先垫一层底色并整体按 [alpha] 淡出。 */
private fun DrawScope.drawCardSnapshot(layer: GraphicsLayer, bounds: Rect, alpha: Float, surface: Color?) {
    val layerSize = layer.size
    if (layerSize.width <= 0 || layerSize.height <= 0 || bounds.width <= 0f) return
    val k = size.width / bounds.width
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        clipRect(0f, 0f, bounds.width, bounds.height) {
            if (surface == null) {
                drawLayer(layer)
            } else {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(0f, 0f, bounds.width, bounds.height), Paint().apply { this.alpha = alpha })
                    drawRect(surface, size = bounds.size)
                    drawLayer(layer)
                    canvas.restore()
                }
            }
        }
    }
}


/**
 * 转场时页面要先画进离屏图层，离屏目标用的渲染管线和直接上屏的是两套，
 * 进程里第一次用到时 GPU 要现编十来条（8 至尊版上约 90ms），正好压在第一次开视频的展开动画上。
 * 首页内容画好、用户还没点进去时，把整棵界面离屏绘制两三帧（画面不变），让这批管线提前编好。
 */
@Stable
internal class LayerPipelineWarmup {
    var active by mutableStateOf(false)
        internal set
}

@Composable
internal fun rememberLayerPipelineWarmup(nav: NavState): LayerPipelineWarmup {
    val warmup = remember { LayerPipelineWarmup() }
    LaunchedEffect(warmup) {
        delay(WarmupDelayMillis)
        // 已经点进别的页面就不必了：那次转场自己会把管线编出来，这时再离屏一帧只是白费。
        if (nav.stack.size != 1 || nav.minimizedEntry?.hasSurfaceView == true) return@LaunchedEffect
        warmup.active = true
        repeat(WarmupFrames) {
            withFrameNanos { }
            // 这几帧里用户可能已经点进视频页：那次转场自己会编管线，继续离屏只是白画。
            if (nav.stack.size != 1) {
                warmup.active = false
                return@LaunchedEffect
            }
        }
        warmup.active = false
    }
    return warmup
}

private const val WarmupDelayMillis = 1500L
private const val WarmupFrames = 3
