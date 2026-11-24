package erl.bilico.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.player.PlaybackBridge
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.utils.PredictiveBackMotionDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

val ExpandEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/** 进场曲线与时长参考系统桌面应用展开节奏，起止柔和不抢跳。 */
val ExpandInEasing = CubicBezierEasing(0.32f, 0.08f, 0.18f, 1f)
const val ExpandEnterMillis = 420
const val ExpandExitMillis = 320
const val RouteSlideMillis = 320
const val RouteParallaxDivisor = 4f
const val FEED_TAB_INDEX = 0
const val DYNAMIC_TAB_INDEX = 1
const val ME_TAB_INDEX = 2

/**
 * 当前路由在自己的登场动画里走了多远：0 还没到，1 已就位。
 * 只给图层和绘制块读，动画进行时不会让整页重组。
 */
@Stable
class RouteTransition(val progress: () -> Float, val exiting: () -> Boolean)

val LocalRouteTransition = staticCompositionLocalOf<RouteTransition?> { null }

/** 当前路由是否在栈顶。窗口级的状态（比如状态栏图标颜色）只该由栈顶页面接管。 */
val LocalRouteIsTop = compositionLocalOf { true }

/** 当前路由是否已最小化收进底栏融合球。收起期间只保活音频与会话，跳过画布与视频纹理渲染。 */
val LocalRouteMinimized = compositionLocalOf { false }

/**
 * 详情页从哪张卡片长出来。卡片在 [expandOrigin] 里把这一帧录进图层，
 * [NavState.push] 带着它走，详情页就从卡片矩形长大，而不是从右边滑进来。
 */
@Stable
class ExpandOrigin(
    val layer: GraphicsLayer,
    val cornerRadius: Dp = 17.dp,
) {
    var bounds by mutableStateOf<Rect?>(null)

    /** 拍一下让卡片重画并重录 [layer]。快照取的是点击那一刻的卡片样子。 */
    var refreshKey by mutableIntStateOf(0)

    /** 卡片所在的列表。返回时用它把行挪出浮动栏，落点才不会被栏压住。 */
    var listState: RowScrollState? = null

    /** 浮在 [listState] 上方的栏，它的下沿就是卡片要让开的线。 */
    var topBar: TopBarBounds? = null

    /** 卡片在 [listState] 里的 key，用来确认它还在屏幕上。 */
    var rowKey: Any? = null

    /** 栏自身的材质往下多画的像素。 */
    var clearancePx: Float = 0f
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

fun LazyListState.asRowScrollState(): RowScrollState = RowScrollState(
    visibleKeys = { layoutInfo.visibleItemsInfo.map { it.key } },
    scrollBy = { scrollBy(it) },
)

fun LazyGridState.asRowScrollState(): RowScrollState = RowScrollState(
    visibleKeys = { layoutInfo.visibleItemsInfo.map { it.key } },
    scrollBy = { scrollBy(it) },
)

@Composable
fun rememberExpandOrigin(cornerRadius: Dp = 17.dp): ExpandOrigin {
    val layer = rememberGraphicsLayer()
    val clearance = with(LocalDensity.current) { AppBarBlurOverlayDefaults.ExtraHeight.toPx() }
    return remember(layer, cornerRadius, clearance) {
        ExpandOrigin(layer, cornerRadius).apply { clearancePx = clearance }
    }
}

@Composable
fun Modifier.expandOrigin(origin: ExpandOrigin): Modifier {
    val visibility = LocalExpandOriginVisibility.current
    val currentEntry = LocalNavEntry.current
    return onGloballyPositioned {
        origin.bounds = Rect(
            it.positionInRoot(),
            Size(it.size.width.toFloat(), it.size.height.toFloat()),
        )
    }
        .drawWithContent {
            // 读一下 key：请求刷新时这个 draw 失效，就会用卡片此刻的样子重录。
            origin.refreshKey
            origin.layer.record { this@drawWithContent.drawContent() }
            if (visibility?.isHidden(origin, currentEntry) != true) {
                drawLayer(origin.layer)
            }
        }
}

sealed interface Route {
    data object Main : Route
    data class Video(
        val bvid: String,
        val startSec: Long = 0L,
        val cid: Long = 0L,
        val commentRootId: Long = 0L,
        val commentTargetId: Long = 0L,
        val offline: Boolean = false,
        /** 推荐流下发的商业推广（小火箭）内容，只在详情页标题前标注。 */
        val promoted: Boolean = false,
    ) : Route
    data class LiveRoom(val roomId: Long) : Route
    data class Space(val mid: Long) : Route
    data object History : Route
    data object Favorites : Route
    data class FavoriteFolder(val mediaId: Long, val title: String) : Route
    data object WatchLater : Route
    data object Downloads : Route
    data object Following : Route
    data object Settings : Route
    data object StatsOverlaySettings : Route
    data object BlockManager : Route
    data object About : Route
    data class Pgc(
        val seasonId: Long,
        val epId: Long = 0L,
        val startSec: Long = 0L,
        val offline: Boolean = false,
    ) : Route
    data object Messages : Route
    data object BangumiFollow : Route
    data object Login : Route
    data object WebLogin : Route
    data object WebProfileEdit : Route
    data class EmoteManager(val url: String) : Route
    data class WebPage(val url: String, val title: String = "网页") : Route
    data object Update : Route
    data class Article(
        val cvId: Long,
        val title: String = "",
        val commentRootId: Long = 0L,
        val commentTargetId: Long = 0L,
    ) : Route
    data class Opus(
        val dynamicId: String,
        val title: String = "",
        val commentRootId: Long = 0L,
        val commentTargetId: Long = 0L,
    ) : Route
    data class Chat(val talkerId: Long, val talkerName: String = "", val talkerFace: String = "") : Route
    data class Search(val query: String = "") : Route
}

fun Route.isPlayback(): Boolean = this is Route.Video || this is Route.Pgc || this is Route.LiveRoom

/** 该页面上允许展示或退回右下角播放融合球（排除其他播放页及登录/聊天等独立模态页）。 */
fun Route.supportsOrbOverlay(): Boolean =
    this == Route.Main || (
        !isPlayback() &&
            this !is Route.Chat &&
            this !is Route.Login &&
            this !is Route.WebLogin &&
            this !is Route.WebProfileEdit &&
            this !is Route.EmoteManager &&
            this !is Route.WebPage
        )

val LocalNavState = staticCompositionLocalOf<NavState?> { null }

@Stable
class NavState {
    val stack = mutableStateListOf(NavEntry(Route.Main))

    val playQueue = PlayQueue()

    var minimizedEntry by mutableStateOf<NavEntry?>(null)
        private set

    var orbOrigin: ExpandOrigin? = null
    var secondaryOrbOrigin: ExpandOrigin? = null
    var inAppBackgroundPlay: () -> Boolean = { true }
    var returnBounce: () -> Boolean = { true }

    /** 预测性返回时是否让页面跟随手指偏移；缩回卡片与缩进融合球都适用。 */
    var predictiveBackFollow: () -> Boolean = { true }

    private var host: CoroutineScope? = null
    private var appContext: Context? = null

    var mainTab by mutableIntStateOf(0)
        private set

    /** 供全屏转屏收尾后通知根节点按当前真栈顶重新对齐一次状态栏图标。 */
    var statusBarSyncTick by mutableIntStateOf(0)
        private set

    fun requestStatusBarSync() {
        statusBarSyncTick++
    }

    val current: Route get() = stack.last().route

    val isMinimizingToOrb: Boolean get() = stack.any { it.exiting && it.minimizingToOrb }

    fun attach(scope: CoroutineScope, context: Context? = null) {
        host = scope
        if (context != null) appContext = context.applicationContext
    }

    fun setTab(index: Int) {
        mainTab = index
    }

    private fun canMinimizeSession(entry: NavEntry): Boolean =
        entry.route.isPlayback() && inAppBackgroundPlay() && PlaybackBridge.canMinimizeSession()

    /** 只要下一层不是另一个播放页（如设置、关注、首页等支持悬浮球的页面），返回时缩进右下角融合球。 */
    fun shouldMinimizeOnPop(entry: NavEntry): Boolean {
        if (!canMinimizeSession(entry)) return false
        val index = stack.indexOf(entry)
        return index > 0 && stack[index - 1].route.supportsOrbOverlay()
    }

    private fun minimizeTargetOrigin(entry: NavEntry): ExpandOrigin? {
        val index = stack.indexOf(entry)
        val prevRoute = if (index > 0) stack[index - 1].route else null
        return if (prevRoute == Route.Main) {
            orbOrigin
        } else {
            secondaryOrbOrigin?.takeIf { it.bounds != null } ?: orbOrigin
        }
    }

    fun push(route: Route, origin: ExpandOrigin? = null) {
        // 球里那条视频要在这里让位。先停掉会话、把新页压进栈，再决定旧页什么时候释放：
        // 新页还没进栈时就不算「还有别的视频页」，旧页出栈会当场同步 release 播放器，
        // 那一下在真机上要卡三四百毫秒，正好压在手势起手的那一帧上。
        val prevMinimized = if (route.isPlayback()) {
            minimizedEntry ?: stack.lastOrNull { it.exiting && it.minimizingToOrb }
        } else {
            null
        }
        val sameVideoPlayer = route is Route.Video && prevMinimized?.route is Route.Video
        // 点的就是球里那一条：什么都不用停，播放器接着放，新页拿到的还是同一台。
        val sameBvid = sameVideoPlayer &&
            (route as Route.Video).bvid == (prevMinimized!!.route as Route.Video).bvid
        if (prevMinimized != null && !sameBvid) {
            if (sameVideoPlayer) {
                // 同一台播放器接着用：只瞬间静音（0 次 Binder），旧片源等新页落位再停。
                PlaybackBridge.muteForRouteSwitch()
            } else {
                appContext?.let(PlaybackBridge::pauseAndStopSession)
            }
        }
        val entry = NavEntry(route, origin, initialProgress = 0f, initialFade = 0f)
        stack.add(entry)
        var deferredDiscard: NavEntry? = null
        if (prevMinimized != null) {
            val wasInFlightOrb = prevMinimized !== minimizedEntry
            prevMinimized.minimizingToOrb = false
            if (sameVideoPlayer) {
                // 这时新页已在栈里，旧页出栈不会再把播放器一起放掉，可以立刻撤掉它的组合。
                if (wasInFlightOrb) {
                    stack.remove(prevMinimized)
                    prevMinimized.discard()
                } else {
                    dismissMinimized(pausePlayback = false)
                }
            } else {
                // 跨类型切换要换掉另一台播放器：把释放挪到进场动画之外。
                if (wasInFlightOrb) stack.remove(prevMinimized) else minimizedEntry = null
                deferredDiscard = prevMinimized
            }
        }
        val scope = host
        if (scope == null) {
            deferredDiscard?.discard()
            return
        }
        scope.launch {
            repeat(2) { withFrameNanos { } }
            val expand = entry.expand
            if (!expand) entry.fade.snapTo(1f)
            val millis = if (expand) ExpandEnterMillis else RouteSlideMillis
            try {
                coroutineScope {
                    launch {
                        entry.progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(millis, easing = ExpandInEasing),
                        )
                    }
                    if (expand) {
                        launch {
                            entry.fade.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(ExpandEnterMillis, easing = ExpandInEasing),
                            )
                        }
                    }
                }
            } finally {
                // 进场中途被返回或被别的页切走：这一轮静音的旧视频没有被新页接手，
                // 必须在这里停掉，否则球里那条会静音着在后台继续播。
                if (sameVideoPlayer && !sameBvid && (entry.exiting || stack.lastOrNull() !== entry)) {
                    appContext?.let(PlaybackBridge::stopForRouteSwitch)
                }
            }
            // 这时详情页已经把列表盖住了，挪行看不见；返回时卡片已经在栏下方。
            alignCardBelowBar(entry.origin)
            deferredDiscard?.discard()
        }
    }

    /** 连续播放换下一条：同为视频页时原地更新路由，保留全屏舞台与横竖屏朝向；后台最小化期间也同步更新。 */
    fun replaceTop(route: Route) {
        if (stack.isEmpty()) return
        val top = stack.last()
        if (top.route is Route.Video && route is Route.Video) {
            top.route = route
            top.replaced = true
            return
        }
        val minimized = minimizedEntry
        if (minimized != null && minimized.route is Route.Video && route is Route.Video) {
            minimized.route = route
            minimized.replaced = true
            return
        }
        stack[stack.lastIndex] = NavEntry(route)
    }

    fun pop(initialProgressVelocity: Float = 0f, initialOffsetVelocity: Offset = Offset.Zero) {
        if (stack.count { !it.exiting } <= 1) return
        val entry = stack.lastOrNull { !it.exiting } ?: return
        val targetOrb = if (shouldMinimizeOnPop(entry)) minimizeTargetOrigin(entry) else null
        val minimize = targetOrb?.bounds != null
        if (minimize) {
            targetOrb.refreshKey++
            entry.orbOverrideOrigin = targetOrb
            entry.minimizingToOrb = true
        } else {
            if (entry.route.isPlayback()) {
                appContext?.let(PlaybackBridge::pauseAndStopSession)
            }
            if (entry.expand) entry.origin?.let { it.refreshKey++ }
        }
        entry.exiting = true
        val scope = host
        if (scope == null) {
            if (minimize) {
                entry.exiting = false
                entry.minimizingToOrb = false
                entry.orbOverrideOrigin = null
                minimizedEntry = entry
            }
            stack.remove(entry)
            if (!minimize) entry.discard()
            return
        }
        scope.launch {
            if (!minimize && entry.expand) alignCardBelowBar(entry.origin)
            val expand = entry.expand
            val baseMillis = if (expand) ExpandExitMillis else RouteSlideMillis
            val remaining = entry.progress.value.coerceIn(0f, 1f)
            val baseRemaining = (baseMillis * remaining).toInt().coerceAtLeast(1)
            val hasFlingOffset = entry.offset.value != Offset.Zero || initialOffsetVelocity != Offset.Zero
            // 跟手甩出时至少留够回缩时间，避免偏移还没收回页面就落位。
            val millis = if (expand && hasFlingOffset) {
                maxOf(baseRemaining, PredictiveBackMotionDefaults.MIN_CARD_FLING_EXIT_MILLIS)
            } else {
                baseRemaining
            }
            coroutineScope {
                launch {
                    // 允许回弹时走桌面图标回弹弹簧，关闭或收进融合球时走平滑淡出。
                    val bounce = expand && !minimize && returnBounce()
                    val spec: AnimationSpec<Float> = if (bounce) {
                        ExpandReturnSpringSpec
                    } else {
                        tween(millis, easing = ExpandEasing)
                    }
                    entry.progress.animateTo(
                        targetValue = 0f,
                        animationSpec = spec,
                        initialVelocity = if (bounce) initialProgressVelocity else 0f,
                    )
                }
                if (expand) {
                    launch {
                        entry.fade.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(millis, easing = ExpandEasing),
                        )
                    }
                }
                if (hasFlingOffset) {
                    launch { entry.returnFlingOffset(initialOffsetVelocity) }
                }
            }
            if (minimize && entry.minimizingToOrb) {
                finalizeMinimize(entry)
            } else {
                stack.remove(entry)
                entry.discard()
            }
        }
    }

    fun preparePredictivePop(entry: NavEntry) {
        val targetOrb = if (shouldMinimizeOnPop(entry)) minimizeTargetOrigin(entry) else null
        if (targetOrb?.bounds != null) {
            targetOrb.refreshKey++
            entry.orbOverrideOrigin = targetOrb
            entry.minimizingToOrb = true
        } else if (entry.expand) {
            entry.origin?.let { it.refreshKey++ }
        }
    }

    fun cancelPredictivePop(entry: NavEntry) {
        entry.previewing = false
        entry.minimizingToOrb = false
        entry.orbOverrideOrigin = null
    }

    fun restoreMinimized() {
        val entry = minimizedEntry ?: return
        if (entry.exiting || stack.lastOrNull() === entry) return
        val origin = if (stack.lastOrNull()?.route == Route.Main) {
            orbOrigin
        } else {
            secondaryOrbOrigin ?: orbOrigin
        }
        origin?.let { it.refreshKey++ }
        minimizedEntry = null
        entry.orbOverrideOrigin = origin
        entry.minimizingToOrb = false
        entry.exiting = false
        entry.previewing = false
        stack.add(entry)
        val scope = host ?: return
        scope.launch {
            entry.progress.snapTo(0f)
            entry.fade.snapTo(0f)
            repeat(2) { withFrameNanos { } }
            val expand = entry.expand
            if (!expand) entry.fade.snapTo(1f)
            val millis = if (expand) ExpandEnterMillis else RouteSlideMillis
            coroutineScope {
                launch {
                    entry.progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(millis, easing = ExpandInEasing),
                    )
                }
                if (expand) {
                    launch {
                        entry.fade.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(ExpandEnterMillis, easing = ExpandInEasing),
                        )
                    }
                }
            }
            entry.orbOverrideOrigin = null
        }
    }

    fun dismissMinimized(pausePlayback: Boolean = true) {
        val entry = minimizedEntry ?: return
        if (pausePlayback) {
            appContext?.let(PlaybackBridge::pauseAndStopSession)
        }
        minimizedEntry = null
        entry.discard()
    }

    private suspend fun finalizeMinimize(entry: NavEntry) {
        entry.exiting = false
        entry.previewing = false
        entry.minimizingToOrb = false
        entry.orbOverrideOrigin = null
        entry.progress.snapTo(0f)
        entry.fade.snapTo(0f)
        entry.offset.snapTo(Offset.Zero)
        minimizedEntry = entry
        stack.remove(entry)
    }

    /** 未登录空态的「去登录」：退出当前二级页并切到「我的」。 */
    fun popToMe() {
        mainTab = ME_TAB_INDEX
        while (stack.size > 1) {
            val removed = stack.removeAt(stack.lastIndex)
            if (removed.route.isPlayback() && minimizedEntry == null) {
                appContext?.let(PlaybackBridge::pauseAndStopSession)
            }
            removed.discard()
        }
    }

    /** 播放页的「首页」：退出所有二级页，回到第一个标签；符合应用内后台播放时缩进融合球，否则走标准退场动画。 */
    fun popToMain() {
        val prevTab = mainTab
        mainTab = 0
        val entry = stack.lastOrNull { !it.exiting } ?: return
        val entryIndex = stack.indexOf(entry)
        if (entryIndex <= 0) return
        val hadIntermediate = entryIndex > 1
        while (stack.indexOf(entry) > 1) {
            stack.removeAt(1).discard()
        }
        val willMinimize = canMinimizeSession(entry) && orbOrigin?.bounds != null
        // 切回首页第一个标签或清掉中间页时，原卡片锚点已不在可见首页上；不缩进融合球就改为平滑右滑退场。
        if (!willMinimize && (hadIntermediate || prevTab != 0)) {
            entry.replaced = true
        }
        pop()
    }

    fun reset(route: Route) {
        dismissMinimized(pausePlayback = true)
        stack.forEach { it.discard() }
        stack.clear()
        stack.add(NavEntry(route))
    }

    /** 返回手势已经把进度拉到 0 的条目：直接出栈，不再播一次动画。 */
    fun completePredictivePop(entry: NavEntry) {
        if (stack.lastOrNull() === entry) {
            if (entry.minimizingToOrb) {
                entry.exiting = false
                entry.previewing = false
                entry.minimizingToOrb = false
                entry.orbOverrideOrigin = null
                minimizedEntry = entry
                stack.remove(entry)
                host?.launch {
                    entry.progress.snapTo(0f)
                    entry.fade.snapTo(0f)
                }
            } else {
                entry.exiting = true
                if (entry.route.isPlayback()) {
                    appContext?.let(PlaybackBridge::pauseAndStopSession)
                }
                stack.remove(entry)
                entry.discard()
            }
        }
    }
}
