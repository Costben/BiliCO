package erl.bilico.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import erl.bilico.di.AppContainer
import erl.bilico.di.LocalAppContainer
import erl.bilico.feature.dynamic.ImageViewerOverlay
import erl.bilico.feature.live.LocalLiveRepository
import erl.bilico.feature.update.UpdateDialog
import erl.bilico.feature.video.PlayerWindowBrightnessEffect
import erl.bilico.platform.BackDispatcher
import erl.bilico.platform.LocalBackDispatcher
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.LocalScreenConfig
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.PlatformToastHost
import erl.bilico.platform.ScreenConfig
import erl.bilico.platform.SyncPictureInPictureParams
import erl.bilico.platform.configurePlatformImageLoader
import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.imageCacheDirectoryPath
import erl.bilico.platform.openExternalUrl
import erl.bilico.platform.platformNeedsBackdropHoleFill
import erl.bilico.platform.showToast
import erl.bilico.platform.useEdgeSwipeBackOverlay
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

private val BareBvid = Regex("BV[0-9A-Za-z]{10}")
private val AidTarget = Regex("""(?:^av|bilibili\.com/video/av)(\d+)""", RegexOption.IGNORE_CASE)

private fun openVideoTarget(
    target: String,
    nav: NavState,
    container: AppContainer,
    context: PlatformContext,
    scope: CoroutineScope,
    startSec: Long = 0L,
) {
    val value = target.trim()
    fun pushOrSeek(bvid: String) {
        val current = nav.stack.lastOrNull { !it.exiting }?.route as? Route.Video
        if (current?.bvid != bvid) {
            nav.push(Route.Video(bvid, startSec = startSec))
        } else if (startSec > 0L) {
            val active = PlaybackBridge.activePlayer
            if (active != null) {
                active.seekTo(startSec * 1000L)
                if (!active.isPlaying) active.play()
            } else {
                nav.replaceTop(Route.Video(bvid, startSec = startSec))
            }
        }
    }
    if (BareBvid.matches(value)) {
        pushOrSeek(value)
        return
    }
    val aid = AidTarget.find(value)?.groupValues?.getOrNull(1)?.toLongOrNull()
    if (aid != null && aid > 0L) {
        scope.launch {
            val bvid = container.messages.resolveBvid(aid).getOrNull()
            if (bvid != null) {
                pushOrSeek(bvid)
            } else {
                showToast(context, "无法打开此视频链接")
            }
        }
        return
    }
    scope.launch {
        val bvid = container.video.resolveShortLink(value)
        if (bvid != null) {
            pushOrSeek(bvid)
        } else if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            nav.push(Route.WebPage(value))
        } else {
            openExternalUrl(context, value)
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun BiliCoRoot(
    container: AppContainer,
    externalBackDispatcher: BackDispatcher? = null,
    pendingVideoTarget: String? = null,
    pendingVideoStartSec: Long = 0L,
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
            .configurePlatformImageLoader(container.client)
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

    LaunchedEffect(pendingVideoTarget, pendingVideoStartSec) {
        val target = pendingVideoTarget?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        openVideoTarget(target, nav, container, context, scope, startSec = pendingVideoStartSec)
        onPendingVideoHandled()
    }
    val inAppBackgroundPlay by container.settings.inAppBackgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
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

    // Android 预测性返回：挂在 RouteLayer 之前，确保子页内更深层的返回处理器（如全屏、回复详情、菜单、看图浮层）优先响应；
    // 根页面（栈深 1）且无任何浮层打开时不启用任何回调，把退回桌面的预测性返回动画交还系统。
    BiliCoPredictiveBackHandler(nav, enabled = viewerHost.active == null)

    val pageSurface = pageContainerColor()
    val rootBackdropBg = if (nav.stack.lastOrNull { !it.exiting }?.route?.isPlayback() == true) {
        Color.Black
    } else {
        pageSurface
    }
    // iOS 需显式传 onDraw 保持两趟录制并用 DstOver 填实 UIKitView 留下的透明洞；
    // Android 保持单趟默认录制，避免全应用每帧双趟录制开销。
    val rootBackdrop = if (platformNeedsBackdropHoleFill) {
        rememberLayerBackdrop {
            drawContent()
            drawRect(rootBackdropBg, blendMode = BlendMode.DstOver)
        }
    } else {
        rememberLayerBackdrop()
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

                // 仅 iOS 挂载左侧边缘跟手返回浮层，Android 由系统预测性返回驱动。
                if (useEdgeSwipeBackOverlay && nav.stack.count { !it.exiting } > 1 && viewerHost.active == null) {
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
                val activePlayback = topRoute?.isPlayback() == true
                PlayerWindowBrightnessEffect(active = activePlayback && !inPipMode)
                SyncPictureInPictureParams(
                    activePlayback = activePlayback,
                    isPlaying = PlaybackBridge.currentIsPlaying,
                    autoPip = autoPip,
                )
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
                    (nav.minimizedEntry != null || nav.isMinimizingToOrb) &&
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

                val suppressPopupBackdrop = topEntry?.hasSurfaceView == true
                Box(Modifier.fillMaxSize().zIndex(5f)) {
                    CompositionLocalProvider(
                        LocalBackdrop provides if (BlurEffectsEnabled && !suppressPopupBackdrop) rootBackdrop else null,
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
