package erl.bilico.ui

import android.content.res.Configuration
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import erl.bilico.feature.video.findActivity
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.squircle.SquircleDefaults
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow

internal class ExpandShape(
    private val heightPx: Float,
    private val radiusPx: Float,
    private val weight: Float = SquircleDefaults.WeightNormal,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val height = heightPx.coerceIn(0f, size.height)
        // 使用与屏幕 G2 圆角和卡片一致的超椭圆轮廓，避免转场起止瞬间在圆形角与超椭圆角之间跳变。
        val path = Path()
        path.addSquircleRect(
            width = size.width,
            height = height,
            cornerRadius = radiusPx.coerceAtLeast(0f),
            weight = weight,
        )
        return Outline.Generic(path)
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

/**
 * 仿照系统桌面返回图标的速率与柔和回弹：约 310ms 落回卡片，
 * 随后以极轻柔的单次过冲微缩偏出并在约 600ms 内平滑归位。
 */
internal val ExpandReturnSpringSpec: SpringSpec<Float> = spring(
    dampingRatio = 0.78f,
    stiffness = 160f,
    visibilityThreshold = 0.001f,
)

/** 预测性返回中途松手未达阈值时，柔和弹回全屏。 */
internal val ExpandCancelSpringSpec: SpringSpec<Float> = spring(
    dampingRatio = 0.88f,
    stiffness = 280f,
    visibilityThreshold = 0.001f,
)

/** 回弹时的固定收缩量与沿飞行方向的最大超界偏移（柔和桌面图标级，与卡片大小无关）。 */
private val BounceShrinkDp = 2.5.dp
private val BounceShiftDp = 3.5.dp

/**
 * 转场展开、回缩与回弹期间隐藏底层列表里的原卡片，避免缩小回弹时后面还垫着一张 100% 静止的卡片出现重影。
 * 进场前两帧 progress 尚未起步时不提前隐藏，避免卡片原地闪出空洞；缩进融合球时原卡片照常显示。
 */
internal class ExpandOriginVisibility(
    private val stack: List<NavEntry>,
) {
    fun isHidden(origin: ExpandOrigin, currentEntry: NavEntry?): Boolean {
        val startIndex = if (currentEntry != null) {
            val idx = stack.indexOfFirst { it === currentEntry }
            if (idx >= 0) idx + 1 else 1
        } else {
            1
        }
        for (i in stack.lastIndex downTo startIndex) {
            val entry = stack[i]
            if (!entry.expand || entry.minimizingToOrb) continue
            if (entry.progress.value <= 0.0005f && !entry.exiting && !entry.previewing) continue
            val active = entry.origin ?: continue
            val sameRowBox = active.rowKey != null &&
                active.rowKey == origin.rowKey &&
                abs((active.bounds?.width ?: 0f) - (origin.bounds?.width ?: 0f)) < 2f
            if (active === origin || sameRowBox) {
                return true
            }
        }
        return false
    }
}

internal val LocalExpandOriginVisibility = staticCompositionLocalOf<ExpandOriginVisibility?> { null }

@Composable
internal fun ExpandLayer(
    entry: NavEntry,
    screenCorner: Dp,
    progress: () -> Float,
    fade: () -> Float,
    offset: () -> Offset = { Offset.Zero },
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
    // 退场收进融合球保留尾段淡出映射；回卡片直接使用 1..0 连续弹簧，去掉 0.2..0 死区停顿。
    fun frameProgress(p: Float): Float = when {
        !exiting -> p
        entry.minimizingToOrb -> if (p < 0f) p else ((p - LandedProgress) / (1f - LandedProgress)).coerceIn(0f, 1f)
        else -> p
    }
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
                val pClamped = p.coerceIn(0f, 1f)
                val dragOffset = offset()
                val width: Float
                val height: Float
                val left: Float
                val top: Float
                if (p < 0f) {
                    // 按固定物理距离做轻量收缩与沿飞行方向的柔和超界回弹，与卡片落入速度平滑衔接。
                    val bounceFraction = (-p / 0.0189f).coerceIn(0f, 1.3f)
                    val shrinkPx = BounceShrinkDp.toPx() * bounceFraction
                    val maxShiftPx = BounceShiftDp.toPx() * bounceFraction
                    width = (bounds.width - shrinkPx).coerceAtLeast(bounds.width * 0.85f)
                    height = (bounds.height - shrinkPx).coerceAtLeast(bounds.height * 0.85f)
                    val dirX = if (size.width > 0f) {
                        ((bounds.center.x - size.width * 0.5f) / (size.width * 0.5f)).coerceIn(-1f, 1f)
                    } else {
                        0f
                    }
                    val dirY = if (size.height > 0f) {
                        ((bounds.center.y - size.height * 0.5f) / (size.height * 0.5f)).coerceIn(-1f, 1f)
                    } else {
                        0f
                    }
                    left = bounds.left + (bounds.width - width) * 0.5f + dirX * maxShiftPx
                    top = bounds.top + (bounds.height - height) * 0.5f + dirY * maxShiftPx
                } else {
                    width = lerp(bounds.width, size.width, p)
                    val cardH = if (bounds.width > 0f) bounds.height * (width / bounds.width) else lerp(bounds.height, size.height, p)
                    height = if (exiting) {
                        val t = ((p - 0.24f) / 0.76f).coerceIn(0f, 1f)
                        lerp(cardH, size.height, t.pow(1.6f))
                    } else {
                        lerp(bounds.height, size.height, p)
                    }
                    left = lerp(bounds.left, 0f, p) + dragOffset.x
                    top = lerp(bounds.top, 0f, p) + dragOffset.y
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
                val radius = lerp(origin.cornerRadius.toPx(), screenCorner.toPx(), pClamped) / scale
                val originWeight = if (entry.minimizingToOrb) SquircleDefaults.WeightNormal else CardDefaults.Weight
                val cornerWeight = lerp(originWeight, SquircleDefaults.WeightNormal, pClamped)
                if (p < 1f || dragOffset != Offset.Zero) {
                    shape = ExpandShape(height / scale, radius, cornerWeight)
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
                    val cardAlpha = ((0.24f - frameProgress(progress()).coerceIn(0f, 1f)) / 0.18f).coerceIn(0f, 1f)
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
                        val pClamped = frameProgress(progress()).coerceIn(0f, 1f)
                        alpha = if (exiting) 1f else enterContentAlpha()
                        translationY = -statusBarTop * (1f - pClamped)
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

/** 跟手甩出后让页面偏移顺着甩出方向弹回原点。 */
internal suspend fun NavEntry.returnFlingOffset(initialVelocity: Offset = Offset.Zero) {
    offset.animateTo(
        targetValue = Offset.Zero,
        animationSpec = PredictiveBackMotionDefaults.flingReturnSpringSpec(),
        initialVelocity = initialVelocity,
    )
}

internal fun finishPredictivePop(
    nav: NavState,
    entry: NavEntry,
    lastProgress: Float,
    moved: Boolean,
    offsetVelocity: Offset = Offset.Zero,
) {
    val flinging = entry.offset.value != Offset.Zero || offsetVelocity != Offset.Zero
    if (entry.expand) {
        nav.pop(
            initialProgressVelocity = if (!entry.minimizingToOrb) {
                if (lastProgress <= 0.02f) -0.55f else -0.35f
            } else {
                0f
            },
            initialOffsetVelocity = offsetVelocity,
        )
    } else if (lastProgress <= 0.02f && moved && !flinging) {
        nav.completePredictivePop(entry)
    } else {
        nav.pop(initialOffsetVelocity = offsetVelocity)
    }
}

/**
 * 退场动画首帧绘制后立刻把页面快照锁进图层并卸掉可交互子树占位（仍保活组合与播放器会话），
 * 让底层列表在回缩与回弹期间立刻响应滑动、点击和再次返回，不必干等动画播完。
 */
@Composable
internal fun ExitSnapshotHost(
    exiting: Boolean,
    hasSurfaceView: Boolean,
    content: @Composable () -> Unit,
) {
    val exitLayer = rememberGraphicsLayer()
    var snapshotReady by remember { mutableStateOf(false) }
    if ((!exiting || hasSurfaceView) && snapshotReady) {
        snapshotReady = false
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(
                when {
                    snapshotReady -> Modifier.drawBehind { drawLayer(exitLayer) }
                    exiting && !hasSurfaceView -> Modifier.drawWithContent {
                        exitLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(exitLayer)
                        snapshotReady = true
                    }
                    else -> Modifier
                },
            ),
    ) {
        Box(
            if (snapshotReady) {
                Modifier.layout { measurable, constraints ->
                    measurable.measure(constraints)
                    layout(0, 0) { }
                }
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            content()
        }
    }
}

/** 把 [origin] 指到它所在的列表与浮动栏上，返回时才能把那行挪出栏底。 */
fun ExpandOrigin.attachToRow(listState: RowScrollState?, rowKey: Any?, topBar: TopBarBounds?) {
    this.listState = listState
    this.rowKey = rowKey
    this.topBar = topBar
}

/**
 * 把卡片所在的列表挪到顶栏之下、底栏之上的安全可视区内。
 *
 * 详情页的框画在页面自己的图层上，在顶栏与底栏之上；半截藏在顶栏或底栏底下的卡片回来时会画到栏上面，
 * 落定后栏再盖回来会显得突兀。挪列表而不是重排图层，栏本身不动，卡片落在一个栏盖不到的完整位置。
 *
 * 列表滚动可能把栏的折叠标题行带回来，栏的边线随之改变，所以量几轮直到两边对齐。
 */
internal suspend fun alignCardBelowBar(origin: ExpandOrigin?) {
    if (origin == null) return
    val state = origin.listState ?: return
    val bar = origin.topBar ?: return
    repeat(4) {
        // 刷新可能已经把这行从列表里换掉，旧矩形会把列表滚到不知道哪里去。
        val onScreen = origin.rowKey?.let { key -> state.visibleKeys().any { it == key } } ?: false
        if (!onScreen) return
        val bounds = origin.bounds ?: return
        val topLine = bar.bottom
        val topSafe = topLine + origin.clearancePx
        val topDelta = if (topLine > 0f) topSafe - bounds.top else 0f

        val bottomLine = bar.bottomBarTop
        val bottomSafe = bottomLine - origin.clearancePx
        val rawBottomDelta = if (bottomLine > 0f) bounds.bottom - bottomSafe else 0f
        // 卡片高度若超过安全区高度，优先保证顶边不撞进顶栏下沿。
        val maxUpShift = if (topLine > 0f) (bounds.top - topSafe).coerceAtLeast(0f) else Float.MAX_VALUE
        val bottomDelta = rawBottomDelta.coerceAtMost(maxUpShift)

        val scrollDelta = when {
            topDelta > 0.5f -> -topDelta
            bottomDelta > 0.5f -> bottomDelta
            else -> return
        }
        if (state.scrollBy(scrollDelta) == 0f) return
        // 一帧让列表重新布局，一帧让栏报出新边线。
        withFrameNanos { }
        withFrameNanos { }
    }
}

/**
 * 窗口状态栏图标颜色的唯一真源：只根据当前非退场栈顶路由、图片浮层与深色主题同步，
 * 避免退场动画被打断、多页重叠或转屏协程回写把状态栏图标卡死在白色。
 */
@Composable
internal fun SyncStatusBarAppearance(
    lightStatusBars: Boolean,
    dark: Boolean,
    orientation: Int,
    syncTick: Int,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity, lightStatusBars, orientation, syncTick) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = lightStatusBars
        }
        onDispose { }
    }
    LaunchedEffect(activity, dark, lightStatusBars) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (controller != null && controller.isAppearanceLightStatusBars != lightStatusBars) {
            controller.isAppearanceLightStatusBars = lightStatusBars
        }
    }
}
