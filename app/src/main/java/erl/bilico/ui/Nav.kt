package erl.bilico.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

val ExpandEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
const val ExpandEnterMillis = 420
const val ExpandExitMillis = 220
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

fun Modifier.expandOrigin(origin: ExpandOrigin): Modifier =
    onGloballyPositioned {
        origin.bounds = Rect(
            it.positionInRoot(),
            Size(it.size.width.toFloat(), it.size.height.toFloat()),
        )
    }
        .drawWithContent {
            // 读一下 key：请求刷新时这个 draw 失效，就会用卡片此刻的样子重录。
            origin.refreshKey
            origin.layer.record { this@drawWithContent.drawContent() }
            drawLayer(origin.layer)
        }

sealed interface Route {
    data object Main : Route
    data class Video(val bvid: String, val startSec: Long = 0L, val cid: Long = 0L) : Route
    data class LiveRoom(val roomId: Long) : Route
    data class Space(val mid: Long) : Route
    data object History : Route
    data object Favorites : Route
    data class FavoriteFolder(val mediaId: Long, val title: String) : Route
    data object WatchLater : Route
    data object Following : Route
    data object Settings : Route
    data class Pgc(val seasonId: Long, val epId: Long = 0L, val startSec: Long = 0L) : Route
    data object Messages : Route
    data object BangumiFollow : Route
    data object WebLogin : Route
    data object Update : Route
    data class Article(val cvId: Long, val title: String = "") : Route
    data class Opus(val dynamicId: String, val title: String = "") : Route
}

class NavEntry(
    val route: Route,
    val origin: ExpandOrigin? = null,
    initialProgress: Float = 1f,
    initialFade: Float = 1f,
) {
    val id = nextId++
    val progress = Animatable(initialProgress)
    val fade = Animatable(initialFade)
    var exiting by mutableStateOf(false)

    /** 返回手势把它拉出去的过程中为 true，还没真的出栈。 */
    var previewing by mutableStateOf(false)

    /** 视频页从卡片长大；列表页和其余二级页由宿主整页滑入。 */
    val expand: Boolean get() = route is Route.Video && origin?.bounds != null

    private companion object {
        var nextId = 0
    }
}

@Stable
class NavState {
    val stack = mutableStateListOf(NavEntry(Route.Main))

    private var host: CoroutineScope? = null

    var mainTab by mutableIntStateOf(0)
        private set

    val current: Route get() = stack.last().route

    fun attach(scope: CoroutineScope) {
        host = scope
    }

    fun setTab(index: Int) {
        mainTab = index
    }

    fun push(route: Route, origin: ExpandOrigin? = null) {
        val entry = NavEntry(route, origin, initialProgress = 0f, initialFade = 0f)
        stack.add(entry)
        val scope = host ?: return
        scope.launch {
            withFrameNanos { }
            val expand = entry.expand
            if (!expand) entry.fade.snapTo(1f)
            val millis = if (expand) ExpandEnterMillis else RouteSlideMillis
            coroutineScope {
                launch {
                    entry.progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(millis, easing = ExpandEasing),
                    )
                }
                if (expand) {
                    launch {
                        entry.fade.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(ExpandEnterMillis, easing = LinearEasing),
                        )
                    }
                }
            }
            // 这时详情页已经把列表盖住了，挪行看不见；返回时卡片已经在栏下方。
            alignCardBelowBar(entry.origin)
        }
    }

    fun pop() {
        if (stack.size <= 1) return
        val entry = stack.last()
        if (entry.exiting) return
        entry.exiting = true
        entry.origin?.let { it.refreshKey++ }
        val scope = host
        if (scope == null) {
            stack.remove(entry)
            return
        }
        scope.launch {
            alignCardBelowBar(entry.origin)
            val expand = entry.expand
            val baseMillis = if (expand) ExpandExitMillis else RouteSlideMillis
            val remaining = entry.progress.value.coerceIn(0f, 1f)
            val millis = (baseMillis * remaining).toInt().coerceAtLeast(1)
            coroutineScope {
                launch {
                    entry.progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            millis,
                            easing = if (expand) LinearEasing else ExpandEasing,
                        ),
                    )
                }
                if (expand) {
                    launch {
                        entry.fade.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(millis, easing = LinearEasing),
                        )
                    }
                }
            }
            stack.remove(entry)
        }
    }

    /** 未登录空态的「去登录」：退出当前二级页并切到「我的」。 */
    fun popToMe() {
        mainTab = ME_TAB_INDEX
        while (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }

    /** 播放页的「首页」：退出所有二级页，回到第一个标签。 */
    fun popToMain() {
        mainTab = 0
        while (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }

    fun reset(route: Route) {
        stack.clear()
        stack.add(NavEntry(route))
    }

    /** 返回手势已经把进度拉到 0 的条目：直接出栈，不再播一次动画。 */
    fun completePredictivePop(entry: NavEntry) {
        if (stack.lastOrNull() === entry) {
            entry.exiting = true
            stack.remove(entry)
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
 * 把卡片所在的列表上移刚好够让卡片顶边落到栏下沿之下。
 *
 * 详情页的框画在页面自己的图层上，在栏之上；半截藏在栏底的卡片回来时会画到栏上面。
 * 挪列表而不是重排图层，栏本身不动，卡片落在一个栏盖不到的位置。
 *
 * 列表回滚可能把栏的折叠标题行带回来，栏的边线随之改变，所以量几轮直到两边对齐。
 */
internal suspend fun alignCardBelowBar(origin: ExpandOrigin?) {
    if (origin == null) return
    val state = origin.listState ?: return
    val bar = origin.topBar ?: return
    repeat(4) {
        val line = bar.bottom
        if (line <= 0f) return
        // 刷新可能已经把这行从列表里换掉，旧矩形会把列表滚到不知道哪里去。
        val onScreen = origin.rowKey?.let { key -> state.visibleKeys().any { it == key } } ?: false
        if (!onScreen) return
        val top = origin.bounds?.top ?: return
        val delta = line + origin.clearancePx - top
        if (delta <= 0.5f) return
        if (state.scrollBy(-delta) == 0f) return
        // 一帧让列表重新布局，一帧让栏报出新下沿。
        withFrameNanos { }
        withFrameNanos { }
    }
}
