package erl.bilico.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import erl.bilico.ExternalVideoLink
import erl.bilico.di.AppContainer
import erl.bilico.di.LocalAppContainer
import erl.bilico.feature.article.ArticleScreen
import erl.bilico.feature.chat.ChatScreen
import erl.bilico.feature.comment.EmoteManagerScreen
import erl.bilico.feature.dynamic.DynamicDetailScreen
import erl.bilico.feature.dynamic.DynamicScreen
import erl.bilico.feature.dynamic.ImageViewerOverlay
import erl.bilico.feature.favorite.FavoriteFolderScreen
import erl.bilico.feature.favorite.FavoriteFoldersScreen
import erl.bilico.feature.feed.FeedScreen
import erl.bilico.feature.following.FollowingScreen
import erl.bilico.feature.history.HistoryScreen
import erl.bilico.feature.live.LiveListScreen
import erl.bilico.feature.live.LiveRoomScreen
import erl.bilico.feature.live.LocalLiveRepository
import erl.bilico.feature.login.LoginScreen
import erl.bilico.feature.login.WebLoginScreen
import erl.bilico.feature.me.MeScreen
import erl.bilico.feature.message.MessageScreen
import erl.bilico.feature.pgc.BangumiFollowScreen
import erl.bilico.feature.pgc.PgcScreen
import erl.bilico.feature.search.SearchScreen
import erl.bilico.feature.settings.AboutScreen
import erl.bilico.feature.settings.BlockManagerScreen
import erl.bilico.feature.settings.SettingsScreen
import erl.bilico.feature.space.SpaceScreen
import erl.bilico.feature.update.UpdateDialog
import erl.bilico.feature.update.UpdateScreen
import erl.bilico.feature.video.PlayerWindowBrightnessEffect
import erl.bilico.feature.video.VideoScreen
import erl.bilico.feature.watchlater.WatchLaterScreen
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.ui.components.LocalOpenBvid
import erl.bilico.update.AppUpdater
import erl.bilico.update.UpdateCheckInterval
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BareBvid = Regex("BV[0-9A-Za-z]{10}")

/**
 * 正文里点到的视频目标：BV 号直接进播放页，短链先解析出 BV 号再进。
 * 解析不到就交给浏览器打开原链接。
 */
private fun openVideoTarget(
    target: String,
    nav: NavState,
    container: AppContainer,
    context: Context,
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
        } else {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
            }
        }
    }
}

@Composable
internal fun BiliCoRoot(
    container: AppContainer,
    externalVideoLink: ExternalVideoLink? = null,
    onVideoLinkHandled: (ExternalVideoLink) -> Unit = {},
) {
    val nav = remember { NavState() }
    val origins = remember { ImageOrigins() }
    val viewerHost = remember { ImageViewerHost() }
    val orbOrigin = rememberExpandOrigin(cornerRadius = 28.dp)
    val secondaryOrbOrigin = rememberExpandOrigin(cornerRadius = 28.dp)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val inAppBackgroundPlay by container.settings.inAppBackgroundPlay.collectAsStateWithLifecycle()
    val returnBounce by container.settings.returnBounce.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val dark = isDarkTheme(darkMode)
    SideEffect {
        nav.attach(scope, context)
        nav.orbOrigin = orbOrigin
        nav.secondaryOrbOrigin = secondaryOrbOrigin
        nav.inAppBackgroundPlay = { inAppBackgroundPlay }
        nav.returnBounce = { returnBounce }
        viewerHost.attach(scope)
    }

    LaunchedEffect(externalVideoLink) {
        val link = externalVideoLink ?: return@LaunchedEffect
        val bvid = link.bvid ?: link.aid?.let { container.messages.resolveBvid(it).getOrNull() }
        if (bvid == null) {
            Toast.makeText(context, "无法打开此视频链接", Toast.LENGTH_SHORT).show()
        } else {
            val current = nav.stack.lastOrNull { !it.exiting }?.route as? Route.Video
            if (current?.bvid != bvid) {
                nav.push(Route.Video(bvid, startSec = link.startSec))
            } else if (link.startSec > 0L) {
                val active = PlaybackBridge.activePlayer
                if (active != null) {
                    active.seekTo(link.startSec * 1000L)
                    if (!active.isPlaying) active.play()
                } else {
                    nav.replaceTop(Route.Video(bvid, startSec = link.startSec))
                }
            }
        }
        onVideoLinkHandled(link)
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

    // 启动时静默查一次更新：等本地配置读完，开关打开且距上次检查超过 12 小时才发请求。
    LaunchedEffect(Unit) {
        container.settings.loaded.first { it }
        if (!container.settings.autoCheckUpdate.value) return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - container.settings.updateCheckedAt.value
        if (elapsed < UpdateCheckInterval) return@LaunchedEffect
        container.updater.checkUpdate(manual = false)
    }

    // 搜索框带着焦点跳页时，输入法的候选栏会浮在新页面上；每次切页都清一次焦点。
    val focusManager = LocalFocusManager.current
    LaunchedEffect(nav.stack.count { !it.exiting }) { focusManager.clearFocus(force = true) }

    BiliCoPredictiveBackHandler(nav, enabled = viewerHost.active == null)

    // 弹窗宿主采这块 backdrop：没有它，COUI 的玻璃面板会退回不透明底色。
    val rootBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalNavState provides nav,
        LocalExpandOriginVisibility provides remember(nav) { ExpandOriginVisibility(nav.stack) },
        LocalImageOrigins provides origins,
        LocalImageViewerHost provides viewerHost,
        LocalOpenBvid provides { target -> openVideoTarget(target, nav, container, context, scope) },
        LocalLiveRepository provides container.live,
        LocalAppContainer provides container,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val screenCorner = rememberScreenCornerRadius()
        val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }
        val pageSurface = pageContainerColor()
        val configuration = LocalConfiguration.current
        val frozenPortrait = remember { FrozenPortrait() }
        val windowPortrait = configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        if (windowPortrait) frozenPortrait.capture(configuration, constraints.maxWidth, constraints.maxHeight)

        // backdrop 只挂在这一层：整栈内容录一次就够，逐页各录一次是白费。
        val gpuWarmup = rememberLayerPipelineWarmup(nav)
        val cornerPx = with(LocalDensity.current) { screenCorner.toPx() }
        val stackContent: @Composable () -> Unit = {
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
                        frozen = if (windowPortrait || !frozenPortrait.ready) null else frozenPortrait,
                    )
                }
            }
        }
        // 这两层节点常驻，warmup 只改图层属性、不动组合结构。否则 1.5s 时父节点树变两次，
        // 整棵 stackContent 会被销毁重建两次：正在滑的首页断触、滚动位置复位、图片重新请求。
        // 结构和展开转场同构：外层圆角裁剪、内层离屏。全屏半径下画面看不出变化，
        // 但离屏 + 圆角裁剪这条路径会真的走一遍，退出转场要用的管线提前编好。
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
            ) { stackContent() }
        }
        // 滑动退场/进场且窗口缩小时四角会露出窗口底色，补一层页面底色，只画在超级椭圆外侧。
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
        // 播放页的亮度手势借的是宿主窗口，离开播放页必须还给系统，
        // 否则回到首页还停在调过的那一档，整个界面一直刺眼。
        PlayerWindowBrightnessEffect(active = topRoute?.isPlayback() == true && !inPipMode)
        val wantLightStatusBars = !dark &&
            viewerHost.active == null &&
            (topEntry == null || !topEntry.route.isPlayback() || inPipMode || topEntry.collapsedBarVisible)
        SyncStatusBarAppearance(
            lightStatusBars = wantLightStatusBars,
            dark = dark,
            orientation = configuration.orientation,
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

        // 弹窗宿主：COUI 的 OverlayDialog 只往 LocalDialogStates 里登记，没有这个宿主就什么都不画。
        Box(Modifier.fillMaxSize().zIndex(5f)) {
            CompositionLocalProvider(
                LocalBackdrop provides if (BlurEffectsEnabled) rootBackdrop else null,
            ) {
                COUIPopupHost()
            }
        }

        // 看图浮层挂在最上层且共享同一个根窗口，才能从任意页面或回复弹窗的缩略图原地生长和缩回。
        viewerHost.active?.let { session ->
            Box(Modifier.fillMaxSize().zIndex(6f)) {
                ImageViewerOverlay(host = viewerHost, session = session)
            }
        }

        // 更新提示挂在根节点：自动检查、设置页手动检查以及更新日志页预览均统一由此弹出。
        UpdatePrompt(container)
        }
    }
}

@Composable
private fun UpdatePrompt(container: AppContainer) {
    val context = LocalContext.current
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

/** 页面跟着手指往回收的返回动画；根页面不注册，交给系统播退回桌面的动画。 */
@Composable
fun BiliCoPredictiveBackHandler(nav: NavState, enabled: Boolean = true) {
    PredictiveBackHandler(enabled = enabled && nav.stack.count { !it.exiting } > 1) { events ->
        val entry = nav.stack.lastOrNull { !it.exiting }
        if (entry == null || nav.stack.count { !it.exiting } <= 1) {
            events.collect { }
            return@PredictiveBackHandler
        }

        var lastProgress = 1f
        var moved = false
        entry.previewing = true
        nav.preparePredictivePop(entry)
        val cardCollapse = entry.expand && !entry.minimizingToOrb
        try {
            events.collect { event ->
                val raw = event.progress
                val value = if (cardCollapse) (1f - raw / (1f - LandedProgress)).coerceIn(0f, 1f) else (1f - raw).coerceIn(0f, 1f)
                if (value < 0.98f) moved = true
                lastProgress = value
                entry.progress.snapTo(value)
                if (entry.expand) entry.fade.snapTo(value)
            }
            finishPredictivePop(nav, entry, lastProgress, moved)
        } catch (cancelled: CancellationException) {
            val landed = if (cardCollapse) lastProgress <= 0.05f else lastProgress <= LandedProgress
            if (landed && moved) {
                finishPredictivePop(nav, entry, lastProgress, moved)
                return@PredictiveBackHandler
            }
            // 松手太早：让页面顺着手指弹回去。
            withContext(NonCancellable) {
                coroutineScope {
                    launch { entry.progress.animateTo(1f, ExpandCancelSpringSpec) }
                    if (entry.expand) launch { entry.fade.animateTo(1f, tween(200, easing = LinearEasing)) }
                }
                nav.cancelPredictivePop(entry)
            }
            throw cancelled
        }
    }
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
    frozen: FrozenPortrait?,
) {
    // 只在图层和绘制块里读，手势进行时失效的是图层，不会重组整页。
    val progress: () -> Float = { entry.progress.value }
    val isMinimized = index < 0
    val activeTop = nav.stack.lastOrNull { !it.exiting }
    val activeBelowTop = nav.stack.filter { !it.exiting }.let { if (it.size >= 2) it[it.size - 2] else null }
    val isTop = !isMinimized && entry === activeTop
    val belowTop = !isMinimized && entry === activeBelowTop
    val slideDriver = nav.stack.lastOrNull { it.exiting } ?: activeTop
    val topProgress: () -> Float = { slideDriver?.progress?.value ?: 1f }
    // 展开页自己画生长，宿主别再从右边推它。
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
                // 吃掉空白处的点击，别漏到下面那页；拖动仍然交给可滚动内容。
                Modifier.pointerInput(Unit) { detectTapGestures { } }
            } else {
                Modifier
            }
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
                // 路径裁剪每帧都要光栅化一张蒙版，只有页面真的在移动时才需要。
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

    // 播放页全屏转成横屏时，压在下面的页面看不见，沿用竖屏的配置和尺寸，
    // 否则首页信息流等整栈页面会在转屏那一帧按横屏宽度重组、重排，把视频卡住几十毫秒。
    val freeze = if (!isTop) frozen else null
    val layerConfiguration = freeze?.configuration ?: LocalConfiguration.current
    // 修饰符常驻，转屏那一帧只换尺寸来源，不插拔节点；尺寸没变时下面的页面直接跳过测量。
    val freezeLayout = Modifier.layout { measurable, constraints ->
        val inner = if (freeze != null) Constraints.fixed(freeze.width, freeze.height) else constraints
        val placeable = measurable.measure(inner)
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

    // 非栈顶页仍留在组合树里，必须按 isTop 停用其返回分发器，避免后台搜索栏等抢走栈顶返回。
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
        LocalConfiguration provides layerConfiguration,
        LocalNavigationEventDispatcherOwner provides routeDispatcherOwner,
    ) {
        Box(layer.then(freezeLayout)) {
            if (entry.hasExpandOrigin || entry.route.isPlayback() || entry.route is Route.Opus) {
                ExpandLayer(entry, screenCorner, progress, { entry.fade.value }) {
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
        )
        is Route.LiveRoom -> LiveRoomScreen(container, nav, route.roomId)
        is Route.Space -> SpaceScreen(nav, container, route.mid)
        Route.History -> HistoryScreen(container, nav)
        Route.Favorites -> FavoriteFoldersScreen(container, nav)
        is Route.FavoriteFolder -> FavoriteFolderScreen(container, nav, route.mediaId, route.title)
        Route.WatchLater -> WatchLaterScreen(container, nav)
        Route.Following -> FollowingScreen(container, nav)
        Route.Settings -> SettingsScreen(container, nav)
        Route.BlockManager -> BlockManagerScreen(container, nav)
        Route.About -> AboutScreen(container, nav)
        is Route.Pgc -> PgcScreen(container, nav, route.seasonId, route.epId, route.startSec * 1000)
        Route.Messages -> MessageScreen(container, nav)
        Route.BangumiFollow -> BangumiFollowScreen(container, nav)
        Route.Login -> LoginScreen(container, nav)
        Route.WebLogin -> WebLoginScreen(container, nav)
        is Route.EmoteManager -> EmoteManagerScreen(container, nav, route.url)
        Route.Update -> UpdateScreen(container, nav)
        is Route.Article -> ArticleScreen(
            container = container,
            nav = nav,
            cvId = route.cvId,
            initialTitle = route.title,
            commentRootId = route.commentRootId,
            commentTargetId = route.commentTargetId,
        )
        is Route.Opus -> DynamicDetailScreen(
            container = container,
            nav = nav,
            dynamicId = route.dynamicId,
            commentRootId = route.commentRootId,
            commentTargetId = route.commentTargetId,
        )
        is Route.Chat -> ChatScreen(container, nav, route.talkerId, route.talkerName, route.talkerFace)
        is Route.Search -> SearchScreen(container, nav, route.query)
    }
}
