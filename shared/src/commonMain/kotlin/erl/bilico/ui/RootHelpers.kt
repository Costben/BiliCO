package erl.bilico.ui

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import erl.bilico.di.AppContainer
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.ScreenConfig
import erl.bilico.platform.platformUsesOffscreenExpandCompositing
import erl.bilico.platform.rememberPlatformScreenCornerRadius
import erl.bilico.platform.rememberPlatformStatusBarTopPx
import erl.bilico.platform.setStatusBarDarkIcons
import erl.bilico.player.LocalPlayerSurfaceAlpha
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.squircle.SquircleDefaults
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

internal data class ExpandShape(
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
internal fun rememberScreenCornerRadius(): Dp = rememberPlatformScreenCornerRadius()

internal class FrozenPortrait {
    var configuration: ScreenConfig? = null
        private set
    var width = 0
        private set
    var height = 0
        private set
    val ready: Boolean get() = configuration != null && width > 0 && height > 0

    fun capture(configuration: ScreenConfig, width: Int, height: Int) {
        this.configuration = configuration
        this.width = width
        this.height = height
    }
}

internal const val LandedProgress = 0.2f

internal val ExpandReturnSpringSpec: SpringSpec<Float> = spring(
    dampingRatio = 0.78f,
    stiffness = 160f,
    visibilityThreshold = 0.001f,
)

internal val ExpandCancelSpringSpec: SpringSpec<Float> = spring(
    dampingRatio = 0.88f,
    stiffness = 280f,
    visibilityThreshold = 0.001f,
)

private val BounceShrinkDp = 2.5.dp
private val BounceShiftDp = 3.5.dp

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
            val active = entry.activeOrigin ?: continue
            if (active === origin) return true
            val sameRow = active.rowKey != null && active.rowKey == origin.rowKey
            if (!sameRow) continue
            val sameItem = if (active.itemKey != null || origin.itemKey != null) {
                active.itemKey == origin.itemKey
            } else {
                val activeBounds = active.bounds
                val originBounds = origin.bounds
                activeBounds != null &&
                    originBounds != null &&
                    abs(activeBounds.left - originBounds.left) < 2f &&
                    abs(activeBounds.width - originBounds.width) < 2f &&
                    abs(activeBounds.height - originBounds.height) < 2f
            }
            if (sameItem) return true
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
    val statusBarTop = rememberPlatformStatusBarTopPx()
    val surface = pageContainerColor()
    val exiting = entry.exiting || entry.previewing
    val parentSurfaceAlpha = LocalPlayerSurfaceAlpha.current
    val surfaceAlphaProvider: () -> Float = remember(entry, parentSurfaceAlpha) {
        {
            val routeAlpha = if (entry.minimizingToOrb && (entry.exiting || entry.previewing)) {
                (progress() / LandedProgress).coerceIn(0f, 1f)
            } else 1f
            (parentSurfaceAlpha() * routeAlpha).coerceIn(0f, 1f)
        }
    }
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
                if (!entry.expand) {
                    drawContent()
                    return@drawWithContent
                }
                val p = frameProgress(progress())
                if (!exiting && p <= 0.0005f) return@drawWithContent
                drawContent()
                if (!exiting || platformUsesOffscreenExpandCompositing) return@drawWithContent
                // iOS Metal 零 saveLayer：直接设置 origin.layer.alpha，避免每帧分配/释放离屏纹理。
                val cardAlpha = ((0.24f - p.coerceIn(0f, 1f)) / 0.18f).coerceIn(0f, 1f)
                if (cardAlpha > 0f) drawCardSnapshot(origin.layer, bounds, alpha = cardAlpha, surface = surface)
            },
    ) {
        if (platformUsesOffscreenExpandCompositing) {
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
                    CompositionLocalProvider(LocalPlayerSurfaceAlpha provides surfaceAlphaProvider) {
                        Box(Modifier.fillMaxSize().background(surface)) { content() }
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
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
                    },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
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
                CompositionLocalProvider(LocalPlayerSurfaceAlpha provides surfaceAlphaProvider) {
                    Box(Modifier.fillMaxSize().background(surface)) { content() }
                }
            }
        }
    }
}

private fun DrawScope.drawCardSnapshot(layer: GraphicsLayer, bounds: Rect, alpha: Float, surface: Color?) {
    val layerSize = layer.size
    if (layerSize.width <= 0 || layerSize.height <= 0 || bounds.width <= 0f) return
    val k = size.width / bounds.width
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        clipRect(0f, 0f, bounds.width, bounds.height) {
            if (platformUsesOffscreenExpandCompositing) {
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
            } else {
                if (surface != null) {
                    drawRect(surface.copy(alpha = alpha), size = bounds.size)
                }
                val prevAlpha = layer.alpha
                layer.alpha = alpha
                try {
                    drawLayer(layer)
                } finally {
                    layer.alpha = prevAlpha
                }
            }
        }
    }
}

@Stable
internal class LayerPipelineWarmup {
    var active by mutableStateOf(false)
        internal set
}

@Composable
internal fun rememberLayerPipelineWarmup(nav: NavState): LayerPipelineWarmup {
    val warmup = remember { LayerPipelineWarmup() }
    LaunchedEffect(warmup) {
        delay(450L)
        if (nav.stack.size != 1) return@LaunchedEffect
        warmup.active = true
        repeat(2) {
            withFrameNanos { }
            if (nav.stack.size != 1) {
                warmup.active = false
                return@LaunchedEffect
            }
        }
        warmup.active = false
    }
    return warmup
}

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

/**
 * 卡片所在列表的取用面：单列是 [LazyListState]，双列是 [LazyGridState]。
 * 返回落点只用得到「这行还在不在屏幕上」和「滚一点」，两边各自实现一份。
 */
@Stable
class RowScrollState(
    val visibleKeys: () -> List<Any?>,
    val scrollBy: suspend (Float) -> Float,
)

fun LazyListState.asRowScrollState(): RowScrollState =
    RowScrollState({ layoutInfo.visibleItemsInfo.map { it.key } }, { scrollBy(it) })

fun LazyGridState.asRowScrollState(): RowScrollState =
    RowScrollState({ layoutInfo.visibleItemsInfo.map { it.key } }, { scrollBy(it) })

fun LazyStaggeredGridState.asRowScrollState(): RowScrollState =
    RowScrollState({ layoutInfo.visibleItemsInfo.map { it.key } }, { scrollBy(it) })

fun ExpandOrigin.attachToRow(
    listState: RowScrollState?,
    rowKey: Any?,
    topBar: TopBarBounds?,
    itemKey: Any? = null,
) {
    this.listState = listState
    this.rowKey = rowKey
    this.topBar = topBar
    this.itemKey = itemKey
}

internal suspend fun alignCardBelowBar(origin: ExpandOrigin?) {
    if (origin == null) return
    val state = origin.listState ?: return
    val bar = origin.topBar ?: return
    repeat(4) {
        val onScreen = origin.rowKey?.let { key -> state.visibleKeys().any { it == key } } ?: false
        if (!onScreen) return
        val bounds = origin.bounds ?: return
        val topLine = bar.bottom
        val topSafe = topLine + origin.clearancePx
        val topDelta = if (topLine > 0f) topSafe - bounds.top else 0f

        val bottomLine = bar.bottomBarTop
        val bottomSafe = bottomLine - origin.clearancePx
        val rawBottomDelta = if (bottomLine > 0f) bounds.bottom - bottomSafe else 0f
        val maxUpShift = if (topLine > 0f) (bounds.top - topSafe).coerceAtLeast(0f) else Float.MAX_VALUE
        val bottomDelta = rawBottomDelta.coerceAtMost(maxUpShift)

        val scrollDelta = when {
            topDelta > 0.5f -> -topDelta
            bottomDelta > 0.5f -> bottomDelta
            else -> return
        }
        if (state.scrollBy(scrollDelta) == 0f) return
        withFrameNanos { }
        withFrameNanos { }
    }
}

@Composable
internal fun SyncStatusBarAppearance(
    lightStatusBars: Boolean,
    dark: Boolean,
    orientation: Boolean,
    syncTick: Int,
) {
    val context = LocalPlatformContext.current
    DisposableEffect(context, lightStatusBars, dark, orientation, syncTick) {
        setStatusBarDarkIcons(context, lightStatusBars)
        onDispose { }
    }
}

internal fun openUserByName(
    rawName: String,
    nav: NavState,
    container: AppContainer,
    scope: CoroutineScope,
) {
    val name = rawName.trim().removePrefix("@").ifEmpty { return }
    scope.launch {
        val exactMid = container.search.searchUsers(name, page = 1).getOrNull()?.items
            ?.firstOrNull { it.name.equals(name, ignoreCase = true) }?.mid
        if (exactMid != null && exactMid > 0L) nav.push(Route.Space(exactMid)) else nav.push(Route.Search(name))
    }
}
