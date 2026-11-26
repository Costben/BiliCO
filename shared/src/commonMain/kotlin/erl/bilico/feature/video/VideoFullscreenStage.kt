package erl.bilico.feature.video

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.LocalScreenConfig
import erl.bilico.platform.setSystemBarsVisible
import erl.bilico.platform.useNativeFullscreenRotation
import erl.bilico.player.LocalPlayerSurfaceAlpha
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.LocalNavState
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.pageContainerColor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.min
import kotlin.math.roundToInt

internal const val COLOROS_VIDEO_ROTATE_DURATION_MS = 450

internal val ColorOSMoveEase = CubicBezierEasing(0.3f, 0.0f, 0.1f, 1.0f)

internal const val ORIENTATION_SETTLE_TIMEOUT_MS = 1500L

internal fun isPortraitVideoStage(videoAspect: Float, inLandscapeFullscreen: Boolean): Boolean =
    (videoAspect > 0f && videoAspect < 1f) || (videoAspect <= 0f && !inLandscapeFullscreen)

@Composable
internal fun VideoFullscreenStage(
    fullscreen: Boolean,
    landscapeSide: Int,
    inPipMode: Boolean,
    darkMode: Int,
    playerContent: @Composable BoxScope.() -> Unit,
    detailContent: @Composable BoxScope.() -> Unit,
    portraitOverlay: @Composable BoxScope.() -> Unit = {},
    portraitCollapsedBar: @Composable BoxScope.() -> Unit = {},
    onExitFullscreen: () -> Unit = {},
    portraitResizable: Boolean = false,
    playing: Boolean = false,
    canExpand: () -> Boolean = { true },
    videoAspect: Float = 0f,
    surfaceViewPlayer: Boolean = false,
    screenLocked: Boolean = false,
    onScreenLockedChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val windowLandscape = LocalScreenConfig.current.isLandscape
    val currentWindowLandscape by rememberUpdatedState(windowLandscape)

    val aspect = if (videoAspect > 0f) videoAspect else DEFAULT_VIDEO_ASPECT
    val portraitStage = isPortraitVideoStage(videoAspect, fullscreen && windowLandscape)
    val currentPortraitStage by rememberUpdatedState(portraitStage)
    val currentAspect by rememberUpdatedState(aspect)
    val currentSurfaceViewPlayer by rememberUpdatedState(surfaceViewPlayer)

    val slotMetrics = remember { FloatArray(2) }
    val slotFloor = remember { FloatArray(1) }
    val slotHeight = remember { mutableFloatStateOf(0f) }
    var slotDragged by remember { mutableStateOf(false) }
    LaunchedEffect(aspect) {
        if (!slotDragged) slotHeight.floatValue = 0f
    }
    val minSlotPx = with(LocalDensity.current) { CollapsedBarHeight.toPx() }
    val barFadePx = with(LocalDensity.current) { 56.dp.toPx() }
    val barFading by remember {
        derivedStateOf {
            val h = slotHeight.floatValue
            h > 0f && h < minSlotPx + barFadePx
        }
    }
    val collapseAlpha: () -> Float = {
        if (!portraitResizable || slotHeight.floatValue <= 0f) {
            0f
        } else {
            ((minSlotPx + barFadePx - slotHeight.floatValue) / barFadePx).coerceIn(0f, 1f)
        }
    }

    var preferredSide by remember { mutableIntStateOf(1) }
    if (!screenLocked && landscapeSide != 0) preferredSide = landscapeSide

    val progress = remember { Animatable(if (fullscreen) 1f else 0f) }
    val angle = remember { Animatable(sideToDegrees(preferredSide)) }
    val reorient = remember { Animatable(0f) }
    val frameHolder = rememberStageFrameHolder()
    val currentOnScreenLockedChange by rememberUpdatedState(onScreenLockedChange)

    StageWindowAttributesEffect(context)
    StagePlatformWindowAttributesEffect()

    val isTop = LocalRouteIsTop.current
    val nav = LocalNavState.current
    val navEntry = LocalNavEntry.current
    val currentFullscreen by rememberUpdatedState(fullscreen)
    LaunchedEffect(navEntry, minSlotPx) {
        if (navEntry == null) return@LaunchedEffect
        snapshotFlow { slotHeight.floatValue > 0f && slotHeight.floatValue <= minSlotPx + 0.5f }
            .distinctUntilChanged()
            .collect { collapsed -> navEntry.collapsedBarVisible = collapsed }
    }
    DisposableEffect(context, isTop) {
        onDispose {
            if (isTop && (currentFullscreen || currentWindowLandscape)) {
                setSystemBarsVisible(context, true)
            }
        }
    }

    StageScreenLockOrientationEffect(
        fullscreen = fullscreen,
        screenLocked = screenLocked,
        portraitStage = portraitStage,
        angle = angle,
        context = context,
        onSyncLockedSide = { preferredSide = it },
    )

    StageOrientationTransitionEffect(
        fullscreen = fullscreen,
        portraitStage = portraitStage,
        screenLocked = screenLocked,
        progress = progress,
        angle = angle,
        frameHolder = frameHolder,
        context = context,
        windowLandscape = { currentWindowLandscape },
        sideDegrees = { sideToDegrees(preferredSide) },
        surfaceViewPlayer = { currentSurfaceViewPlayer },
        onSyncStatusBar = { nav?.requestStatusBarSync() },
    )

    LandscapeFlipEffect(
        landscapeSide = landscapeSide,
        fullscreen = fullscreen,
        windowLandscape = windowLandscape,
        portraitStage = portraitStage,
        surfaceViewPlayer = surfaceViewPlayer,
        screenLocked = screenLocked,
        progress = progress,
        angle = angle,
        reorient = reorient,
        context = context,
        frameHolder = frameHolder,
    )

    LaunchedEffect(fullscreen, windowLandscape, context) {
        if (fullscreen && windowLandscape) {
            setSystemBarsVisible(context, false)
        }
    }

    val currentOnExitFullscreen by rememberUpdatedState(onExitFullscreen)
    val inStage by remember(portraitStage, windowLandscape) {
        derivedStateOf {
            if (useNativeFullscreenRotation && !portraitStage) {
                windowLandscape
            } else {
                fullscreen || progress.value > 0.0005f
            }
        }
    }
    var pendingReturnHome by remember { mutableStateOf(false) }
    LaunchedEffect(pendingReturnHome, fullscreen, windowLandscape, inStage) {
        if (pendingReturnHome && !fullscreen && !windowLandscape && !inStage) {
            pendingReturnHome = false
            nav?.popToMain()
        }
    }
    val returnHome: () -> Unit = {
        if (fullscreen || windowLandscape || inStage) {
            pendingReturnHome = true
            if (fullscreen) currentOnExitFullscreen()
        } else {
            nav?.popToMain()
        }
    }

    if (inPipMode) {
        Box(Modifier.fillMaxSize().background(Color.Black), content = playerContent)
        return
    }

    val pageSurface = pageContainerColor()
    val statusBarPx = rememberStableStatusBarHeightPx()
    var portraitWidth by remember { mutableIntStateOf(0) }
    var portraitHeight by remember { mutableIntStateOf(0) }
    val stageBox = remember { IntArray(2) }
    val currentLandscapeForLayer by rememberUpdatedState(windowLandscape)
    val currentCanExpand by rememberUpdatedState(canExpand)

    val collapseScroll = remember(minSlotPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput &&
                    source != NestedScrollSource.SideEffect
                ) {
                    return Offset.Zero
                }
                val full = slotMetrics[0]
                val floor = slotFloor[0].takeIf { it > 0f } ?: minSlotPx
                val dy = available.y
                if (dy == 0f || full <= floor) return Offset.Zero
                val current = slotMetrics[1].takeIf { it > 0f } ?: full
                if (dy > 0f && current >= full - 0.5f) return Offset.Zero
                if (dy > 0f && !currentCanExpand()) return Offset.Zero
                val next = (current + dy).coerceIn(floor, full)
                if (next == current) return Offset.Zero
                slotHeight.floatValue = next
                slotDragged = true
                return Offset(0f, next - current)
            }
        }
    }

    val stageScale: (Size) -> Float? = remember {
        { size ->
            val stageW = stageBox[0]
            val stageH = stageBox[1]
            val measured = stageW > 0 &&
                size.width.roundToInt() == stageW &&
                size.height.roundToInt() == stageH
            when {
                currentSurfaceViewPlayer && !frameHolder.frozen -> null
                !measured || currentLandscapeForLayer || (useNativeFullscreenRotation && !currentPortraitStage) -> null
                currentPortraitStage -> {
                    val portW = stageW.toFloat()
                    val slotW = fitContentWidth(currentAspect, portW, slotMetrics[1])
                    val fullW = fitContentWidth(currentAspect, size.width, size.height)
                    val from = (slotW / fullW.coerceAtLeast(1f)).coerceAtLeast(0.01f)
                    lerp(from, 1f, progress.value)
                }
                else -> {
                    val portW = size.height
                    val portH = size.width
                    val videoH = slotMetrics[1].takeIf { it > 0f }
                        ?: min(portW * 9f / 16f, portH * 0.7f)
                    lerp(videoH / portW.coerceAtLeast(1f), 1f, progress.value).coerceAtLeast(0.01f)
                }
            }
        }
    }

    Layout(
        content = {
            // 0: 简介 / 评论。视频未折叠时裁剪掉视频槽位背后的绘制，防止评论/卡片与 UIKitView 视频抢顶层。
            Box(
                modifier = Modifier
                    .background(pageSurface)
                    .graphicsLayer {}
                    .nestedScroll(collapseScroll)
                    .drawWithContent {
                        if (!windowLandscape && progress.value < 0.9995f) {
                            val hideTop = if (portraitResizable && collapseAlpha() <= 0.001f) {
                                val currentVideoH = slotMetrics[1]
                                val currentDetailTop = (currentVideoH - minSlotPx).roundToInt().coerceAtLeast(0)
                                (statusBarPx + currentVideoH - currentDetailTop).coerceIn(0f, size.height)
                            } else {
                                0f
                            }
                            if (hideTop > 0f) {
                                clipRect(left = 0f, top = hideTop, right = size.width, bottom = size.height) {
                                    this@drawWithContent.drawContent()
                                }
                            } else {
                                drawContent()
                            }
                        }
                    },
                content = detailContent,
            )

            // 1: 竖屏视频槽 + 全屏渐黑遮罩
            Box(
                modifier = Modifier.drawBehind {
                    val p = if (windowLandscape) 1f else progress.value
                    if (p < 0.9995f) {
                        val slotAlpha = if (portraitResizable) (1f - collapseAlpha()).coerceIn(0f, 1f) else 1f
                        if (slotAlpha > 0.001f) {
                            drawRect(
                                color = Color.Black.copy(alpha = slotAlpha),
                                topLeft = Offset(0f, statusBarPx),
                                size = Size(size.width, slotMetrics[1]),
                            )
                        }
                    }
                    if (p > 0.0005f) drawRect(Color.Black.copy(alpha = p.coerceIn(0f, 1f)))
                },
            )

            // 2: 播放器
            Box(
                modifier = Modifier.graphicsLayer {
                    val s = stageScale(size)
                    if (s == null) {
                        transformOrigin = TransformOrigin.Center
                        translationX = 0f
                        translationY = if (windowLandscape) 0f else statusBarPx
                        scaleX = 1f
                        scaleY = 1f
                        rotationZ = if (windowLandscape &&
                            (!currentSurfaceViewPlayer || frameHolder.frozen)
                        ) {
                            reorient.value
                        } else {
                            0f
                        }
                        alpha = if (!windowLandscape && portraitResizable) (1f - collapseAlpha()).coerceIn(0f, 1f) else 1f
                        clip = !windowLandscape
                    } else if (currentPortraitStage) {
                        val portH = stageBox[1].toFloat()
                        val slotH = slotMetrics[1]
                        val p = progress.value
                        transformOrigin = TransformOrigin.Center
                        translationX = 0f
                        translationY = (statusBarPx + slotH * 0.5f - portH * 0.5f) * (1f - p)
                        scaleX = s
                        scaleY = s
                        rotationZ = 0f
                        alpha = 1f
                        clip = false
                    } else {
                        val portW = size.height
                        val portH = size.width
                        val videoH = slotMetrics[1].takeIf { it > 0f }
                            ?: min(portW * 9f / 16f, portH * 0.7f)
                        val p = progress.value
                        transformOrigin = TransformOrigin.Center
                        translationX = portW * 0.5f - portH * 0.5f
                        translationY = lerp(statusBarPx + videoH * 0.5f, portH * 0.5f, p) - portW * 0.5f
                        scaleX = s
                        scaleY = s
                        rotationZ = p * angle.value
                        alpha = 1f
                        clip = false
                    }
                },
                content = {
                    val playerVisible by remember {
                        derivedStateOf {
                            windowLandscape || !portraitResizable || collapseAlpha() < 0.99f
                        }
                    }
                    val playerAlphaProvider: () -> Float = remember {
                        {
                            if (!windowLandscape && portraitResizable) {
                                (1f - collapseAlpha()).coerceIn(0f, 1f)
                            } else {
                                1f
                            }
                        }
                    }
                    CompositionLocalProvider(
                        LocalStageScale provides stageScale,
                        LocalStageOpaquePlayer provides !portraitStage,
                        LocalStageVideoAspect provides aspect,
                        LocalStageFrozenVideo provides frameHolder.frozen,
                        LocalStageFrameSource provides frameHolder.source,
                        LocalStagePlayerVisible provides playerVisible,
                        LocalPlayerSurfaceAlpha provides playerAlphaProvider,
                        LocalStageReturnHome provides returnHome,
                        LocalStageScreenLockObserver provides currentOnScreenLockedChange,
                    ) {
                        playerContent()
                        StageFrozenFrame(frameHolder)
                    }
                },
            )

            // 3: 竖屏附加层与折叠顶栏
            Box(
                modifier = Modifier.drawWithContent { if (!windowLandscape && !inStage) drawContent() },
                content = {
                    if (!windowLandscape && !inStage) {
                        CompositionLocalProvider(
                            LocalPortraitSlotHeight provides slotHeight,
                            LocalStageReturnHome provides returnHome,
                        ) {
                            portraitOverlay()
                            if (portraitResizable) {
                                CollapsedBarSlot(barFading) {
                                    Box(
                                        modifier = Modifier.graphicsLayer {
                                            alpha = collapseAlpha()
                                        },
                                    ) { portraitCollapsedBar() }
                                }
                            }
                        }
                    }
                },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (windowLandscape || progress.value >= 0.9995f) {
                    drawRect(Color.Black)
                } else {
                    drawRect(pageSurface)
                    val barBgAlpha = if (portraitResizable) (1f - collapseAlpha()).coerceIn(0f, 1f) else 1f
                    if (barBgAlpha > 0.001f) {
                        drawRect(Color.Black.copy(alpha = barBgAlpha), size = Size(size.width, statusBarPx))
                    }
                }
            },
    ) { measurables, constraints ->
        val winW = constraints.maxWidth.coerceAtLeast(1)
        val winH = constraints.maxHeight.coerceAtLeast(1)
        if (!windowLandscape) {
            portraitWidth = winW
            portraitHeight = winH
        }
        val portW = if (portraitWidth > 0) portraitWidth else min(winW, winH)
        val portH = if (portraitHeight > 0) portraitHeight else maxOf(winW, winH)
        val videoH = if (portraitResizable) {
            val sixteenNine = min(portW * 9f / 16f, portH * 0.7f)
            val full = maxOf(sixteenNine, min(portW / aspect, portH * 0.7f))
            val initial = sixteenNine
            slotMetrics[0] = full
            val floor = if (playing) {
                maxOf(minSlotPx, min(initial, portH * 0.3f))
            } else {
                minSlotPx
            }.coerceAtMost(full)
            slotFloor[0] = floor
            val wanted = slotHeight.floatValue.takeIf { it > 0f } ?: initial
            val clamped = wanted.coerceIn(floor, full)
            if (clamped != slotHeight.floatValue) slotHeight.floatValue = clamped
            clamped.also { slotMetrics[1] = it }
        } else {
            slotMetrics[0] = 0f
            slotFloor[0] = minSlotPx
            min(portW * 9f / 16f, portH * 0.7f).also { slotMetrics[1] = it }
        }
        val detailTop = if (portraitResizable) {
            (videoH - minSlotPx).roundToInt().coerceAtLeast(0)
        } else {
            (statusBarPx + videoH).roundToInt()
        }

        val detail = measurables[0].measure(
            Constraints.fixed(portW, (portH - detailTop).coerceAtLeast(1)),
        )
        val backdrop = measurables[1].measure(Constraints.fixed(winW, winH))
        val playerConstraints = when {
            windowLandscape -> Constraints.fixed(winW, winH)
            surfaceViewPlayer && !frameHolder.frozen && !(inStage && portraitStage) ->
                Constraints.fixed(portW, videoH.roundToInt().coerceAtLeast(1))
            surfaceViewPlayer && frameHolder.frozen && !windowLandscape && !portraitStage -> {
                stageBox[0] = portH
                stageBox[1] = portW
                Constraints.fixed(portH, portW)
            }
            inStage -> {
                if (portraitStage) {
                    stageBox[0] = winW
                    stageBox[1] = winH
                    Constraints.fixed(winW, winH)
                } else {
                    stageBox[0] = portH
                    stageBox[1] = portW
                    Constraints.fixed(portH, portW)
                }
            }
            else -> Constraints.fixed(portW, videoH.roundToInt().coerceAtLeast(1))
        }
        val player = measurables[2].measure(playerConstraints)
        val overlay = measurables[3].measure(Constraints.fixed(winW, winH))
        layout(winW, winH) {
            detail.place(0, if (windowLandscape) 0 else detailTop)
            backdrop.place(0, 0)
            player.place(0, 0)
            overlay.place(0, 0)
        }
    }
}
