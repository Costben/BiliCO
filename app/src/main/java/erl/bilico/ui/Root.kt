package erl.bilico.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import erl.bilico.di.AppContainer
import erl.bilico.feature.article.ArticleScreen
import erl.bilico.feature.chat.ChatScreen
import erl.bilico.feature.dynamic.DynamicDetailScreen
import erl.bilico.feature.dynamic.DynamicScreen
import erl.bilico.feature.dynamic.ImageViewerOverlay
import erl.bilico.feature.favorite.FavoriteFolderScreen
import erl.bilico.feature.favorite.FavoriteFoldersScreen
import erl.bilico.feature.following.FollowingScreen
import erl.bilico.feature.feed.FeedScreen
import erl.bilico.feature.history.HistoryScreen
import erl.bilico.feature.live.LiveListScreen
import erl.bilico.feature.live.LiveRoomScreen
import erl.bilico.feature.login.LoginScreen
import erl.bilico.feature.login.WebLoginScreen
import erl.bilico.feature.me.MeScreen
import erl.bilico.feature.message.MessageScreen
import erl.bilico.feature.pgc.BangumiFollowScreen
import erl.bilico.feature.pgc.PgcScreen
import erl.bilico.feature.settings.AboutScreen
import erl.bilico.feature.settings.SettingsScreen
import erl.bilico.feature.space.SpaceScreen
import erl.bilico.feature.update.UpdateDialog
import erl.bilico.feature.update.UpdateScreen
import erl.bilico.feature.video.VideoScreen
import erl.bilico.feature.watchlater.WatchLaterScreen
import erl.bilico.ui.components.LocalOpenBvid
import erl.bilico.update.AppUpdater
import erl.bilico.update.UpdateCheckInterval
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.squircle.addSquircleRect
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

/** 手势要拉过这里才算「已经落回卡片」，松手即走；再弹回来会像 bug。 */
private const val LandedProgress = 0.2f

@Composable
fun BiliCoRoot(container: AppContainer) {
    val nav = remember { NavState() }
    val origins = remember { ImageOrigins() }
    val viewerHost = remember { ImageViewerHost() }
    val scope = rememberCoroutineScope()
    SideEffect {
        nav.attach(scope)
        viewerHost.attach(scope)
    }

    // 启动时静默查一次更新：等本地配置读完，开关打开且距上次检查超过 12 小时才发请求。
    androidx.compose.runtime.LaunchedEffect(Unit) {
        container.settings.loaded.first { it }
        if (!container.settings.autoCheckUpdate.value) return@LaunchedEffect
        val elapsed = System.currentTimeMillis() - container.settings.updateCheckedAt.value
        if (elapsed < UpdateCheckInterval) return@LaunchedEffect
        container.updater.checkUpdate(manual = false)
    }

    // 搜索框带着焦点跳页时，输入法的候选栏会浮在新页面上；每次切页都清一次焦点。
    val focusManager = LocalFocusManager.current
    androidx.compose.runtime.LaunchedEffect(nav.stack.size) { focusManager.clearFocus(force = true) }

    BiliCoPredictiveBackHandler(nav, enabled = viewerHost.active == null)

    // 弹窗宿主采这块 backdrop：没有它，COUI 的玻璃面板会退回不透明底色。
    val rootBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalImageOrigins provides origins,
        LocalImageViewerHost provides viewerHost,
        LocalOpenBvid provides { bvid -> nav.push(Route.Video(bvid)) },
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
        Box(Modifier.fillMaxSize().layerBackdropIf(BlurEffectsEnabled, rootBackdrop)) {
            Box(Modifier.fillMaxSize().background(pageSurface))
            nav.stack.forEachIndexed { index, entry ->
                key(entry.id) {
                    RouteLayer(
                        entry = entry,
                        index = index,
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
        // 窗口缩小时四角会露出窗口底色，补一层页面底色，只画在超级椭圆外侧。
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(3f)
                .drawBehind {
                    if (nav.stack.all { it.progress.value >= 1f }) return@drawBehind
                    val cut = when (val outline = cornerShape.createOutline(size, layoutDirection, this)) {
                        is Outline.Generic -> outline.path
                        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                        else -> return@drawBehind
                    }
                    clipPath(cut, ClipOp.Difference) { drawRect(pageSurface) }
                },
        )

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
    PredictiveBackHandler(enabled = enabled && nav.stack.size > 1) { events ->
        val entry = nav.stack.lastOrNull()
        if (entry == null || nav.stack.size <= 1) {
            events.collect { }
            return@PredictiveBackHandler
        }
        if (entry.exiting) {
            events.collect { }
            return@PredictiveBackHandler
        }

        var lastProgress = 1f
        var moved = false
        entry.previewing = true
        // 快照是点卡片时录的，现在重录一次，页面交回去的是卡片此时此刻的样子。
        entry.origin?.let { it.refreshKey++ }
        try {
            events.collect { event ->
                val value = (1f - event.progress).coerceIn(0f, 1f)
                if (value < 0.98f) moved = true
                lastProgress = value
                entry.progress.snapTo(value)
                if (entry.expand) entry.fade.snapTo(value)
            }
            if (lastProgress <= 0.02f && moved) {
                nav.completePredictivePop(entry)
            } else {
                nav.pop()
            }
        } catch (cancelled: CancellationException) {
            // 已经落回卡片了：系统把这叫取消，但没什么可预览的，直接走。
            if (lastProgress <= LandedProgress && moved) {
                nav.completePredictivePop(entry)
                return@PredictiveBackHandler
            }
            // 松手太早：让页面顺着手指弹回去。
            withContext(NonCancellable) {
                coroutineScope {
                    launch {
                        entry.progress.animateTo(1f, tween(180, easing = ExpandEasing))
                    }
                    if (entry.expand) {
                        launch {
                            entry.fade.animateTo(1f, tween(180, easing = LinearEasing))
                        }
                    }
                }
                entry.previewing = false
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
    val isTop = index == lastIndex
    val belowTop = index == lastIndex - 1
    val top = nav.stack.lastOrNull()
    val topProgress: () -> Float = { top?.progress?.value ?: 1f }
    // 展开页自己画生长，宿主别再从右边推它。
    val slide = top != null && !top.expand
    val cornerShape = remember(screenCorner) { SquircleShape(screenCorner) }

    val layer = Modifier
        .fillMaxSize()
        .zIndex(if (isTop) 2f else 1f)
        .then(
            if (isTop) {
                // 吃掉空白处的点击，别漏到下面那页；拖动仍然交给可滚动内容。
                Modifier.pointerInput(Unit) { detectTapGestures { } }
            } else {
                Modifier
            }
        )
        .graphicsLayer {
            if (slide) {
                translationX = when {
                    isTop -> (1f - progress()) * widthPx
                    belowTop -> -topProgress() * widthPx / RouteParallaxDivisor
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
            val covered = !isTop && (
                !belowTop || (
                    topProgress() >= 1f &&
                        top?.exiting != true &&
                        top?.previewing != true
                    )
                )
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
        LocalConfiguration provides layerConfiguration,
        LocalNavigationEventDispatcherOwner provides routeDispatcherOwner,
    ) {
        Box(layer.then(freezeLayout)) {
            if (entry.expand) {
                ExpandLayer(entry, screenCorner, progress, { entry.fade.value }) {
                    RouteContent(entry.route, nav, container)
                }
            } else {
                RouteContent(entry.route, nav, container)
            }
        }
    }
}

@Composable
private fun ExpandLayer(
    entry: NavEntry,
    screenCorner: Dp,
    progress: () -> Float,
    fade: () -> Float,
    content: @Composable () -> Unit,
) {
    val origin = entry.origin ?: return
    val bounds = origin.bounds ?: return
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
                if (!exiting && progress() > 0.0005f) {
                    drawRect(Color.Black, alpha = 0.3f * progress())
                }
            }
            .graphicsLayer {
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
                val radius = lerp(origin.cornerRadius.toPx(), screenCorner.toPx(), p) / scale
                if (p < 1f) {
                    shape = ExpandShape(height / scale, radius)
                    clip = true
                } else {
                    clip = false
                }
            }
            .drawWithContent {
                val p = frameProgress(progress())
                if (!exiting && p <= 0.0005f) return@drawWithContent
                drawContent()
                if (!exiting) return@drawWithContent
                val cardAlpha = ((0.24f - p) / 0.18f).coerceIn(0f, 1f)
                val layerSize = origin.layer.size
                if (cardAlpha > 0f && layerSize.width > 0 && layerSize.height > 0 && bounds.width > 0f) {
                    val k = size.width / bounds.width
                    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                        clipRect(0f, 0f, bounds.width, bounds.height) {
                            drawIntoCanvas { canvas ->
                                canvas.saveLayer(
                                    Rect(0f, 0f, bounds.width, bounds.height),
                                    Paint().apply { alpha = cardAlpha },
                                )
                                drawRect(surface, size = bounds.size)
                                drawLayer(origin.layer)
                                canvas.restore()
                            }
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = frameProgress(progress())
                    if (exiting) {
                        if (p < 1f) drawRect(surface)
                        return@drawBehind
                    }
                    val ca = enterContentAlpha()
                    if (p < 1f || ca < 0.999f) {
                        drawRect(surface)
                        if (ca < 0.999f) {
                            val layerSize = origin.layer.size
                            if (layerSize.width > 0 && layerSize.height > 0 && bounds.width > 0f) {
                                val k = size.width / bounds.width
                                withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                                    clipRect(0f, 0f, bounds.width, bounds.height) {
                                        drawLayer(origin.layer)
                                    }
                                }
                            }
                        }
                    }
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = frameProgress(progress())
                    alpha = if (exiting) 1f else enterContentAlpha()
                    translationY = -statusBarTop * (1f - p)
                },
        ) {
            Box(Modifier.fillMaxSize().background(surface)) { content() }
        }
    }
}

@Composable
private fun RouteContent(route: Route, nav: NavState, container: AppContainer) {
    when (route) {
        Route.Main -> MainShell(nav, container)
        is Route.Video -> VideoScreen(container, nav, route.bvid, route.startSec * 1000, route.cid)
        is Route.LiveRoom -> LiveRoomScreen(container, nav, route.roomId)
        is Route.Space -> SpaceScreen(nav, container, route.mid)
        Route.History -> HistoryScreen(container, nav)
        Route.Favorites -> FavoriteFoldersScreen(container, nav)
        is Route.FavoriteFolder -> FavoriteFolderScreen(container, nav, route.mediaId, route.title)
        Route.WatchLater -> WatchLaterScreen(container, nav)
        Route.Following -> FollowingScreen(container, nav)
        Route.Settings -> SettingsScreen(container, nav)
        Route.About -> AboutScreen(container, nav)
        is Route.Pgc -> PgcScreen(container, nav, route.seasonId, route.epId, route.startSec * 1000)
        Route.Messages -> MessageScreen(container, nav)
        Route.BangumiFollow -> BangumiFollowScreen(container, nav)
        Route.Login -> LoginScreen(container, nav)
        Route.WebLogin -> WebLoginScreen(container, nav)
        Route.Update -> UpdateScreen(container, nav)
        is Route.Article -> ArticleScreen(container, nav, route.cvId, route.title)
        is Route.Opus -> DynamicDetailScreen(container, nav, route.dynamicId)
        is Route.Chat -> ChatScreen(container, nav, route.talkerId, route.talkerName, route.talkerFace)
    }
}

private class ExpandShape(
    private val heightPx: Float,
    private val radiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val height = heightPx.coerceIn(0f, size.height)
        // 和静止时的框同一个超级椭圆，最后一段才不会从屏幕圆角上脱开。
        val path = Path()
        path.addSquircleRect(
            width = size.width,
            height = height,
            cornerRadius = radiusPx,
        )
        return Outline.Generic(path)
    }
}

@Composable
private fun rememberScreenCornerRadius(): Dp {
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
private class FrozenPortrait {
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
