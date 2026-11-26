package erl.bilico.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import okio.Path.Companion.toPath
import erl.bilico.di.AppContainer
import erl.bilico.di.LocalAppContainer
import erl.bilico.feature.article.ArticleScreen
import erl.bilico.feature.chat.ChatScreen
import erl.bilico.feature.comment.EmoteManagerScreen
import erl.bilico.feature.download.DownloadsScreen
import erl.bilico.feature.dynamic.DynamicDetailScreen
import erl.bilico.feature.dynamic.ImageViewerOverlay
import erl.bilico.feature.favorite.FavoriteFolderScreen
import erl.bilico.feature.favorite.FavoriteFoldersScreen
import erl.bilico.feature.following.FollowingScreen
import erl.bilico.feature.history.HistoryScreen
import erl.bilico.feature.live.LiveRoomScreen
import erl.bilico.feature.live.LocalLiveRepository
import erl.bilico.feature.login.LoginScreen
import erl.bilico.feature.login.WebLoginScreen
import erl.bilico.feature.me.WebProfileEditScreen
import erl.bilico.feature.message.MessageScreen
import erl.bilico.feature.pgc.BangumiFollowScreen
import erl.bilico.feature.pgc.PgcScreen
import erl.bilico.feature.search.SearchScreen
import erl.bilico.feature.settings.AboutScreen
import erl.bilico.feature.settings.BlockManagerScreen
import erl.bilico.feature.settings.SettingsScreen
import erl.bilico.feature.settings.StatsOverlaySettingsScreen
import erl.bilico.feature.space.SpaceCollectionScreen
import erl.bilico.feature.space.SpaceScreen
import erl.bilico.feature.update.UpdateDialog
import erl.bilico.feature.update.UpdateScreen
import erl.bilico.feature.video.PlayerWindowBrightnessEffect
import erl.bilico.feature.video.VideoScreen
import erl.bilico.feature.watchlater.WatchLaterScreen
import erl.bilico.platform.BackDispatcher
import erl.bilico.platform.LocalBackDispatcher
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.LocalScreenConfig
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.PlatformToastHost
import erl.bilico.platform.ScreenConfig
import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.imageCacheDirectoryPath
import erl.bilico.platform.openExternalUrl
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.ui.components.InAppWebScreen
import erl.bilico.ui.components.LocalOpenBvid
import erl.bilico.ui.components.LocalOpenTopic
import erl.bilico.ui.components.LocalOpenUrl
import erl.bilico.ui.components.LocalOpenUserName
import erl.bilico.update.AppUpdater
import erl.bilico.update.UpdateCheckInterval
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import io.github.suqi8.coui.kmp.utils.PredictiveBackDragTracker
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val BareBvid = Regex("BV[0-9A-Za-z]{10}")

private fun openVideoTarget(
    target: String,
    nav: NavState,
    container: AppContainer,
    context: PlatformContext,
    scope: CoroutineScope,
) {
    val value = target.trim()
    if (BareBvid.matches(value)) {
        nav.push(Route.Video(value))
        return
    }
    scope.launch {
        val bvid = container.video.resolveShortLink(value)
        if (bvid != null) {
            nav.push(Route.Video(bvid))
        } else if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            nav.push(Route.WebPage(value))
        } else {
            openExternalUrl(context, value)
        }
    }
}

@Composable
fun BiliCoRoot(
    container: AppContainer,
    externalBackDispatcher: BackDispatcher? = null,
    pendingVideoTarget: String? = null,
    onPendingVideoHandled: () -> Unit = {},
) {
    setSingletonImageLoaderFactory { platformContext ->
        ImageLoader.Builder(platformContext)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(platformContext, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDirectoryPath().toPath())
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            .components {
                add(KtorNetworkFetcherFactory(container.client.http))
            }
            .build()
    }

    val nav = remember { NavState() }
    val origins = remember { ImageOrigins() }
    val viewerHost = remember { ImageViewerHost() }
    val orbOrigin = rememberExpandOrigin(cornerRadius = 28.dp)
    val secondaryOrbOrigin = rememberExpandOrigin(cornerRadius = 28.dp)
    val backDispatcher = externalBackDispatcher ?: remember { BackDispatcher() }
    val scope = rememberCoroutineScope()
    val context = container.appContext

    LaunchedEffect(pendingVideoTarget) {
        val target = pendingVideoTarget?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        openVideoTarget(target, nav, container, context, scope)
        onPendingVideoHandled()
    }
    val inAppBackgroundPlay by container.settings.inAppBackgroundPlay.collectAsStateWithLifecycle()
    val returnBounce by container.settings.returnBounce.collectAsStateWithLifecycle()
    val predictiveBackFollow by container.settings.predictiveBackFollow.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val account by container.session.account.collectAsStateWithLifecycle()
    val dark = isDarkTheme(darkMode)

    val currentTopRoute = nav.stack.lastOrNull { !it.exiting }?.route
    var prevTopRoute by remember { mutableStateOf<Route?>(null) }
    LaunchedEffect(account?.mid) {
        container.messages.refreshUnread(force = true)
    }
    LaunchedEffect(currentTopRoute) {
        val prev = prevTopRoute
        prevTopRoute = currentTopRoute
        if (currentTopRoute == Route.Main && (prev == Route.Messages || prev is Route.Chat)) {
            container.messages.refreshUnread(force = true)
        }
    }
    LaunchedEffect(account?.mid, currentTopRoute) {
        if (account != null && currentTopRoute == Route.Main) {
            while (isActive) {
                delay(60_000L)
                container.messages.refreshUnread(force = true)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { container.messages.refreshUnread(force = false) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SideEffect {
        nav.attach(scope, context)
        nav.orbOrigin = orbOrigin
        nav.secondaryOrbOrigin = secondaryOrbOrigin
        nav.inAppBackgroundPlay = { inAppBackgroundPlay }
        nav.returnBounce = { returnBounce }
        nav.predictiveBackFollow = { predictiveBackFollow }
        viewerHost.attach(scope)
    }

    LaunchedEffect(PlaybackBridge.stopVersion) {
        if (PlaybackBridge.stopVersion > 0 && nav.minimizedEntry != null) {
            nav.dismissMinimized(pausePlayback = false)
        }
    }

    LaunchedEffect(inAppBackgroundPlay) {
        if (!inAppBackgroundPlay && nav.minimizedEntry != null) {
            nav.dismissMinimized(pausePlayback = true)
        }
    }

    LaunchedEffect(Unit) {
        container.settings.loaded.first { it }
        if (!container.settings.autoCheckUpdate.value) return@LaunchedEffect
        val elapsed = currentTimeMillis() - container.settings.updateCheckedAt.value
        if (elapsed < UpdateCheckInterval) return@LaunchedEffect
        container.updater.checkUpdate(manual = false)
    }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(nav.stack.count { !it.exiting }) { focusManager.clearFocus(force = true) }

    val pageSurface = pageContainerColor()
    val rootBackdropBg = if (nav.stack.lastOrNull { !it.exiting }?.route?.isPlayback() == true) {
        Color.Black
    } else {
        pageSurface
    }
    // 显式传 onDraw 保持两趟录制（usesDefaultOnDraw = false）：既避免把整个 App 与 UIKitView
    // 吞进单趟离屏层，又用 DstOver 填实 UIKitView 留下的 BlendMode.Clear 透明洞，防止弹窗模糊变成空洞或长条。
    val rootBackdrop = rememberLayerBackdrop {
        drawContent()
        drawRect(rootBackdropBg, blendMode = BlendMode.DstOver)
    }

    CompositionLocalProvider(
        LocalPlatformContext provides context,
        LocalBackDispatcher provides backDispatcher,
        LocalNavState provides nav,
        LocalExpandOriginVisibility provides remember(nav) { ExpandOriginVisibility(nav.stack) },
        LocalImageOrigins provides origins,
        LocalImageViewerHost provides viewerHost,
        LocalOpenBvid provides { target -> openVideoTarget(target, nav, container, context, scope) },
        LocalOpenUrl provides { url, title ->
            val trimmed = url.trim()
            val normalized = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
            if (normalized.startsWith("http://", ignoreCase = true) || normalized.startsWith("https://", ignoreCase = true)) {
                nav.push(Route.WebPage(normalized, title ?: "网页"))
            }
        },
        LocalOpenTopic provides { topic ->
            val query = topic.trim()
            if (query.isNotEmpty()) nav.push(Route.Search(query))
        },
        LocalOpenUserName provides { rawName ->
            openUserByName(rawName, nav, container, scope)
        },
        LocalLiveRepository provides container.live,
        LocalAppContainer provides container,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat()
            val widthDp = with(density) { maxWidth.value.toInt() }
            val heightDp = with(density) { maxHeight.value.toInt() }
            val screenConfig = remember(widthDp, heightDp) {
                ScreenConfig(
                    screenWidthDp = widthDp,
                    screenHeightDp = heightDp,
                    isLandscape = widthDp > heightDp,
                )
            }

            val screenCorner = rememberScreenCornerRadius()
            val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }
            val frozenPortrait = remember { FrozenPortrait() }
            val windowPortrait = !screenConfig.isLandscape
            if (windowPortrait) {
                frozenPortrait.capture(screenConfig, constraints.maxWidth, constraints.maxHeight)
            }

            val gpuWarmup = rememberLayerPipelineWarmup(nav)
            val cornerPx = with(density) { screenCorner.toPx() }
            val warmupExitLayer = rememberGraphicsLayer()

            CompositionLocalProvider(LocalScreenConfig provides screenConfig) {
                // 两层节点常驻，预热只切换图形层属性、不动组合树，提前编好 ExpandLayer / ExitSnapshotHost / Squircle 裁切管线，
                // 避免第一次看完视频返回时临时编译 Metal 离屏圆角管线掉帧。
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val warming = gpuWarmup.active && size.height > 0f
                            clip = warming
                            shape = if (warming) ExpandShape(size.height, cornerPx) else RectangleShape
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = if (gpuWarmup.active) {
                                    CompositingStrategy.Offscreen
                                } else {
                                    CompositingStrategy.Auto
                                }
                            }
                            .layerBackdropIf(BlurEffectsEnabled, rootBackdrop),
                    ) {
                        Box(Modifier.fillMaxSize().background(pageSurface))
                        val minimized = nav.minimizedEntry
                        val layers = buildList {
                            if (minimized != null) add(minimized)
                            addAll(nav.stack)
                        }
                        layers.forEach { entry ->
                            key(entry.id) {
                                val stackIndex = nav.stack.indexOf(entry)
                                RouteLayer(
                                    entry = entry,
                                    index = stackIndex,
                                    lastIndex = nav.stack.lastIndex,
                                    widthPx = widthPx,
                                    screenCorner = screenCorner,
                                    nav = nav,
                                    container = container,
                                    screenConfig = screenConfig,
                                    frozen = if (windowPortrait || !frozenPortrait.ready) null else frozenPortrait,
                                )
                            }
                        }
                    }
                }

                // 预热 ExitSnapshotHost 离屏录制与 Squircle 差集裁切路径（16x16 极低透明度，不可见）。
                if (gpuWarmup.active) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                warmupExitLayer.alpha = 0.004f
                                warmupExitLayer.record(size = IntSize(16, 16)) {
                                    drawRect(pageSurface, size = Size(16f, 16f))
                                }
                                drawLayer(warmupExitLayer)
                                val patchSize = Size(16f, 16f)
                                val outline = cornerShape.createOutline(patchSize, layoutDirection, this)
                                val cut = when (outline) {
                                    is Outline.Generic -> outline.path
                                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                                    else -> null
                                }
                                if (cut != null) {
                                    clipPath(cut, ClipOp.Difference) {
                                        drawRect(pageSurface.copy(alpha = 0.004f), size = patchSize)
                                    }
                                }
                            },
                    )
                }

                // Interactive iOS left-edge swipe back gesture
                if (nav.stack.count { !it.exiting } > 1 && viewerHost.active == null) {
                    EdgeSwipeBackOverlay(
                        widthPx = widthPx,
                        nav = nav,
                        backDispatcher = backDispatcher,
                        scope = scope,
                    )
                }

                val cornerMaskCache = remember(cornerShape) {
                    object {
                        var size = Size.Zero
                        var layoutDirection: LayoutDirection? = null
                        var path: Path? = null
                    }
                }
                val showCornerMask by remember(nav) {
                    derivedStateOf { nav.stack.any { !it.expand && it.progress.value < 1f } }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                        .drawBehind {
                            if (!showCornerMask) return@drawBehind
                            val cached = cornerMaskCache.path?.takeIf {
                                cornerMaskCache.size == size && cornerMaskCache.layoutDirection == layoutDirection
                            }
                            val cut = cached ?: when (val outline = cornerShape.createOutline(size, layoutDirection, this)) {
                                is Outline.Generic -> outline.path
                                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                                else -> return@drawBehind
                            }.also {
                                cornerMaskCache.size = size
                                cornerMaskCache.layoutDirection = layoutDirection
                                cornerMaskCache.path = it
                            }
                            clipPath(cut, ClipOp.Difference) { drawRect(pageSurface) }
                        },
                )

                val topEntry = nav.stack.lastOrNull { !it.exiting }
                val topRoute = topEntry?.route
                val inPipMode = LocalInPipMode.current
                PlayerWindowBrightnessEffect(active = topRoute?.isPlayback() == true && !inPipMode)
                val wantLightStatusBars = !dark &&
                    viewerHost.active == null &&
                    (topEntry == null || !topEntry.route.isPlayback() || inPipMode || topEntry.collapsedBarVisible)
                SyncStatusBarAppearance(
                    lightStatusBars = wantLightStatusBars,
                    dark = dark,
                    orientation = screenConfig.isLandscape,
                    syncTick = nav.statusBarSyncTick,
                )

                val showSecondaryOrb = !inPipMode &&
                    nav.minimizedEntry != null &&
                    topRoute != null &&
                    topRoute != Route.Main &&
                    topRoute.supportsOrbOverlay()
                if (showSecondaryOrb) {
                    Box(
                        modifier = Modifier.fillMaxSize().zIndex(4f),
                        contentAlignment = androidx.compose.ui.Alignment.BottomEnd,
                    ) {
                        CompositionLocalProvider(
                            LocalBackdrop provides if (BlurEffectsEnabled) rootBackdrop else null,
                        ) {
                            SecondaryPlaybackOrbOverlay(nav = nav, orbOrigin = secondaryOrbOrigin)
                        }
                    }
                }

                Box(Modifier.fillMaxSize().zIndex(5f)) {
                    CompositionLocalProvider(
                        LocalBackdrop provides if (BlurEffectsEnabled) rootBackdrop else null,
                    ) {
                        COUIPopupHost()
                    }
                }

                viewerHost.active?.let { session ->
                    Box(Modifier.fillMaxSize().zIndex(6f)) {
                        ImageViewerOverlay(host = viewerHost, session = session)
                    }
                }

                UpdatePrompt(container)

                Box(Modifier.fillMaxSize().zIndex(8f)) {
                    PlatformToastHost()
                }
            }
        }
    }
}

@Composable
private fun EdgeSwipeBackOverlay(
    widthPx: Float,
    nav: NavState,
    backDispatcher: BackDispatcher,
    scope: CoroutineScope,
) {
    val density = LocalDensity.current
    val maxFlingSpeedPx = with(density) { PredictiveBackMotionDefaults.MaxFlingSpeed.toPx() }
    val flingCommitVelocityPx = with(density) { 450.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .zIndex(4.5f)
            .pointerInput(nav, widthPx, maxFlingSpeedPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val startPos = down.position
                    var started = false
                    var lastChange = down

                    while (!started) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        lastChange = change
                        val dx = change.position.x - startPos.x
                        val dy = change.position.y - startPos.y
                        if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.15f) {
                            return@awaitEachGesture
                        }
                        if (dx > touchSlop && dx >= abs(dy) * 0.7f) {
                            started = true
                            change.consume()
                        }
                    }

                    if (backDispatcher.dispatch()) return@awaitEachGesture
                    val entry = nav.stack.lastOrNull { !it.exiting } ?: return@awaitEachGesture
                    if (nav.stack.count { !it.exiting } <= 1) return@awaitEachGesture

                    var lastProgress = 1f
                    var moved = false
                    entry.previewing = true
                    nav.preparePredictivePop(entry)
                    val cardCollapse = entry.expand && !entry.minimizingToOrb
                    val trackFinger = entry.expand && nav.predictiveBackFollow()
                    val dragTracker = if (trackFinger) PredictiveBackDragTracker() else null
                    dragTracker?.record(startPos.x, startPos.y, down.uptimeMillis)

                    var prevX = lastChange.position.x
                    var prevT = lastChange.uptimeMillis
                    var lastVx = 0f

                    fun applyPointer(pos: Offset, uptimeMs: Long) {
                        val dt = (uptimeMs - prevT).coerceAtLeast(1L)
                        val vx = (pos.x - prevX) * 1000f / dt
                        lastVx = lastVx * 0.4f + vx * 0.6f
                        prevX = pos.x
                        prevT = uptimeMs

                        val raw = ((pos.x - startPos.x) / (widthPx * 0.65f)).coerceIn(0f, 1f)
                        val value = if (cardCollapse) {
                            (1f - raw / (1f - LandedProgress)).coerceIn(0f, 1f)
                        } else {
                            (1f - raw).coerceIn(0f, 1f)
                        }
                        if (value < 0.98f) moved = true
                        lastProgress = value
                        val followOffset = dragTracker?.record(pos.x, pos.y, uptimeMs)
                        scope.launch {
                            entry.progress.snapTo(value)
                            if (entry.expand) entry.fade.snapTo(value)
                            if (followOffset != null) entry.offset.snapTo(followOffset)
                        }
                    }

                    applyPointer(lastChange.position, lastChange.uptimeMillis)

                    var canceled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null) {
                            canceled = true
                            break
                        }
                        if (!change.pressed) {
                            if (change.positionChanged()) {
                                applyPointer(change.position, change.uptimeMillis)
                                change.consume()
                            }
                            break
                        }
                        if (change.positionChanged()) {
                            applyPointer(change.position, change.uptimeMillis)
                            change.consume()
                        }
                    }

                    val commitVelocity = dragTracker?.velocityPxPerSec(maxFlingSpeedPx) ?: Offset.Zero
                    val shouldCommit = !canceled && (
                        lastProgress <= 0.68f ||
                            (moved && lastVx >= flingCommitVelocityPx)
                        )
                    val landed = if (cardCollapse) lastProgress <= 0.05f else lastProgress <= LandedProgress
                    if (shouldCommit || (landed && moved)) {
                        finishPredictivePop(nav, entry, lastProgress, moved, commitVelocity)
                    } else {
                        scope.launch {
                            coroutineScope {
                                launch { entry.progress.animateTo(1f, ExpandCancelSpringSpec) }
                                if (entry.expand) {
                                    launch { entry.fade.animateTo(1f, tween(200, easing = LinearEasing)) }
                                }
                                if (entry.offset.value != Offset.Zero) {
                                    launch { entry.returnFlingOffset() }
                                }
                            }
                            nav.cancelPredictivePop(entry)
                        }
                    }
                }
            },
    )
}

@Composable
private fun UpdatePrompt(container: AppContainer) {
    val context = LocalPlatformContext.current
    val info = container.updater.pendingUpdate ?: return
    UpdateDialog(
        container = container,
        info = info,
        preview = container.updater.previewingUpdate,
        onDismiss = { container.updater.dismissUpdate() },
        onSkip = {
            container.settings.setSkippedUpdateVersion(info.version)
            container.updater.dismissUpdate()
        },
        onDownload = {
            AppUpdater.openDownload(context, info)
            container.updater.dismissUpdate()
        },
    )
}

@Composable
private fun RouteLayer(
    entry: NavEntry,
    index: Int,
    lastIndex: Int,
    widthPx: Float,
    screenCorner: Dp,
    nav: NavState,
    container: AppContainer,
    screenConfig: ScreenConfig,
    frozen: FrozenPortrait?,
) {
    val progress: () -> Float = { entry.progress.value }
    val isMinimized = index < 0
    val activeTop = nav.stack.lastOrNull { !it.exiting }
    val activeBelowTop = nav.stack.filter { !it.exiting }.let { if (it.size >= 2) it[it.size - 2] else null }
    val isTop = !isMinimized && entry === activeTop
    val belowTop = !isMinimized && entry === activeBelowTop
    val slideDriver = nav.stack.lastOrNull { it.exiting } ?: activeTop
    val topProgress: () -> Float = { slideDriver?.progress?.value ?: 1f }
    val slide = !isMinimized && !entry.expand && (entry === slideDriver || (slideDriver != null && !slideDriver.expand))
    val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }

    val covered by remember(isMinimized, index, nav.stack.size) {
        derivedStateOf {
            isMinimized || (index >= 0 && nav.stack.drop(index + 1).any {
                !it.exiting && !it.previewing && it.progress.value >= 1f
            })
        }
    }

    val layer = Modifier
        .fillMaxSize()
        .zIndex(if (isMinimized) -1f else 1f + index * 0.01f)
        .then(
            if (isTop) {
                Modifier.pointerInput(Unit) { detectTapGestures { } }
            } else {
                Modifier
            },
        )
        .graphicsLayer {
            if (isMinimized) {
                alpha = 0f
                scaleX = 0f
                scaleY = 0f
                clip = false
                return@graphicsLayer
            }
            if (slide) {
                translationX = when {
                    entry === slideDriver -> (1f - progress()) * widthPx
                    isTop || belowTop -> -topProgress() * widthPx / RouteParallaxDivisor
                    else -> 0f
                }
                if (progress() < 1f || topProgress() < 1f) {
                    shape = cornerShape
                    clip = true
                } else {
                    clip = false
                }
            }
        }
        .drawWithContent {
            if (!covered) {
                drawContent()
            }
        }

    val transition = remember(entry) {
        RouteTransition(
            progress = progress,
            exiting = { entry.exiting || entry.previewing },
        )
    }

    val freeze = if (!isTop) frozen else null
    val layerConfig = freeze?.configuration ?: screenConfig
    val freezeLayout = Modifier.layout { measurable, constraints ->
        val inner = if (freeze != null) Constraints.fixed(freeze.width, freeze.height) else constraints
        val placeable = measurable.measure(inner)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

    val routeDispatcherOwner = rememberNavigationEventDispatcherOwner(
        enabled = isTop,
        parent = LocalNavigationEventDispatcherOwner.current,
    )
    SideEffect {
        routeDispatcherOwner.navigationEventDispatcher.isEnabled = isTop
    }

    CompositionLocalProvider(
        LocalRouteTransition provides transition,
        LocalRouteIsTop provides isTop,
        LocalRouteMinimized provides isMinimized,
        LocalNavEntry provides entry,
        LocalScreenConfig provides layerConfig,
        LocalNavigationEventDispatcherOwner provides routeDispatcherOwner,
    ) {
        Box(layer.then(freezeLayout)) {
            if (entry.hasExpandOrigin || entry.route.isPlayback() || entry.route is Route.Opus) {
                ExpandLayer(entry, screenCorner, progress, { entry.fade.value }, { entry.offset.value }) {
                    ExitSnapshotHost(exiting = entry.exiting, hasSurfaceView = entry.hasSurfaceView) {
                        RouteContent(entry.route, nav, container)
                    }
                }
            } else {
                ExitSnapshotHost(exiting = entry.exiting, hasSurfaceView = entry.hasSurfaceView) {
                    RouteContent(entry.route, nav, container)
                }
            }
        }
    }
}

@Composable
private fun RouteContent(route: Route, nav: NavState, container: AppContainer) {
    when (route) {
        Route.Main -> MainShell(nav, container)
        is Route.Video -> VideoScreen(
            container = container,
            nav = nav,
            bvid = route.bvid,
            startPositionMs = route.startSec * 1000,
            initialCid = route.cid,
            commentRootId = route.commentRootId,
            commentTargetId = route.commentTargetId,
            commentMatchAuthor = route.commentMatchAuthor,
            commentMatchText = route.commentMatchText,
            offline = route.offline,
            promoted = route.promoted,
        )
        is Route.LiveRoom -> LiveRoomScreen(container, nav, route.roomId)
        is Route.Space -> SpaceScreen(nav, container, route.mid)
        is Route.SpaceCollection -> SpaceCollectionScreen(
            nav = nav,
            container = container,
            mid = route.mid,
            collectionId = route.collectionId,
            isSeason = route.isSeason,
            title = route.title,
            total = route.total,
        )
        Route.History -> HistoryScreen(container, nav)
        Route.Favorites -> FavoriteFoldersScreen(container, nav)
        is Route.FavoriteFolder -> FavoriteFolderScreen(container, nav, route.mediaId, route.title)
        Route.WatchLater -> WatchLaterScreen(container, nav)
        Route.Downloads -> DownloadsScreen(container, nav)
        Route.Following -> FollowingScreen(container, nav)
        Route.Settings -> SettingsScreen(container, nav)
        Route.StatsOverlaySettings -> StatsOverlaySettingsScreen(container, nav)
        Route.BlockManager -> BlockManagerScreen(container, nav)
        Route.About -> AboutScreen(container, nav)
        is Route.Pgc -> PgcScreen(container, nav, route.seasonId, route.epId, route.startSec * 1000, route.offline)
        Route.Messages -> MessageScreen(container, nav)
        Route.BangumiFollow -> BangumiFollowScreen(container, nav)
        Route.Login -> LoginScreen(container, nav)
        Route.WebLogin -> WebLoginScreen(container, nav)
        Route.WebProfileEdit -> WebProfileEditScreen(container, nav)
        is Route.EmoteManager -> EmoteManagerScreen(container, nav, route.url)
        is Route.WebPage -> InAppWebScreen(container, nav, route.url, route.title)
        Route.Update -> UpdateScreen(container, nav)
        is Route.Article -> ArticleScreen(
            container = container,
            nav = nav,
            cvId = route.cvId,
            initialTitle = route.title,
            commentRootId = route.commentRootId,
            commentTargetId = route.commentTargetId,
            commentMatchAuthor = route.commentMatchAuthor,
            commentMatchText = route.commentMatchText,
        )
        is Route.Opus -> DynamicDetailScreen(
            container = container,
            nav = nav,
            dynamicId = route.dynamicId,
            commentRootId = route.commentRootId,
            commentTargetId = route.commentTargetId,
            commentMatchAuthor = route.commentMatchAuthor,
            commentMatchText = route.commentMatchText,
        )
        is Route.Chat -> ChatScreen(container, nav, route.talkerId, route.talkerName, route.talkerFace)
        is Route.Search -> SearchScreen(container, nav, route.query)
    }
}
