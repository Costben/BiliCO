package erl.bilico.feature.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.isDarkTheme
import erl.bilico.ui.pageContainerColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ColorOS 17 视频转屏动画参数（`com.oplus.animation.OplusVideoRotateAnimation`）：
 * 450ms，COUIMoveEase (0.3, 0, 0.1, 1)。
 */
internal const val COLOROS_VIDEO_ROTATE_DURATION_MS = 450

internal val ColorOSMoveEase = CubicBezierEasing(0.3f, 0.0f, 0.1f, 1.0f)

/** 等系统真正转完屏的上限，超时就按当前窗口方向直接收尾。 */
private const val ORIENTATION_SETTLE_TIMEOUT_MS = 1500L

/** 退出全屏时缩回到这个进度就让系统栏开始滑入，不等动画完全结束。 */
private const val SYSTEM_BARS_REVEAL_PROGRESS = 0.5f


/**
 * 读取忽略显隐状态的固定状态栏像素高度，避免全屏切换隐藏/显示系统栏时触发整页逐帧重排。
 */
@Composable
internal fun rememberStableStatusBarHeightPx(): Float {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
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
}

/**
 * 读取忽略显隐状态的固定导航栏底部内边距，防止进出全屏时因系统栏动画引发详情列表逐帧重测。
 */
@Composable
internal fun rememberStableNavBarBottomDp(): androidx.compose.ui.unit.Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val bottomPx = ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?: 0
        with(density) { bottomPx.toDp() }
    }
}


/**
 * 播放页的横竖屏舞台：系统真横屏 + 自绘视频旋转动画。
 *
 * ColorOS 只对白名单里的应用（B 站、腾讯视频等，见 `sys_video_rotate_white_list.xml`）
 * 在转屏时把视频区域单独转出来，其他应用拿到的都是整窗淡入淡出。这里自己补上同一套动画：
 *
 * - 窗口的系统转屏动画设成 `SEAMLESS`：系统在新方向上直接接着合成当前画面，
 *   不再先截一张静止图盖住屏幕等 App 出首帧，转屏那一帧不会看到画面定住；
 * - 进入全屏：竖屏窗口里先把播放器从 16:9 位置放大并旋转 ±90° 铺满整屏，
 *   动画落稳后再向系统申请对应方向的横屏。最后一帧和横屏窗口的画面完全重合；
 * - 退出全屏：先申请竖屏，等窗口真的转回竖屏，从「旋转铺满」那一帧倒着缩回 16:9 原位。
 *
 * [videoAspect] 是视频本身的宽高比（宽 / 高）。小于 1 的竖屏视频不转窗口：
 * 全屏时窗口保持竖屏，播放器从 16:9 槽位等比放大到整屏，退出时倒着缩回去。
 *
 * 播放器始终在同一个组合槽位里，PlayerView 不会被重建。
 */
@Composable
internal fun VideoFullscreenStage(
    fullscreen: Boolean,
    landscapeSide: Int,
    inPipMode: Boolean,
    activity: Activity?,
    darkMode: Int,
    playerContent: @Composable BoxScope.() -> Unit,
    detailContent: @Composable BoxScope.() -> Unit,
    portraitOverlay: @Composable BoxScope.() -> Unit = {},
    portraitCollapsedBar: @Composable BoxScope.() -> Unit = {},
    /** 竖屏视频的槽位可以上下滑动改高度（只有视频详情页开）。 */
    portraitResizable: Boolean = false,
    /** 正在播放：槽位收不到只剩顶栏，至少留一段能看的高度。 */
    playing: Boolean = false,
    /**
     * 详情页的列表是不是已经滚到顶。收到只剩横栏之后，只有列表到顶了再往下拉才把视频拉回来；
     * 列表还在半路时，下拉先滚列表，视频不动，否则一下滑视频就整个弹出来。
     */
    canExpand: () -> Boolean = { true },
    videoAspect: Float = 0f,
) {
    val dark = isDarkTheme(darkMode)
    val windowLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentWindowLandscape by rememberUpdatedState(windowLandscape)

    // 竖屏视频的全屏方向跟着视频走：窗口不转，只在竖屏里把画面放到最大。
    val aspect = if (videoAspect > 0f) videoAspect else DEFAULT_VIDEO_ASPECT
    val portraitStage = aspect < 1f
    val currentPortraitStage by rememberUpdatedState(portraitStage)
    val currentAspect by rememberUpdatedState(aspect)

    // 详情页里视频槽位的状态：[0] 完整高度、[1] 当前高度（都在 measure 里写，composition 不订阅）。
    // slotHeight 直接存当前高度（px），0 表示「还没量过 = 完整高度」。
    // 存高度而不是收起比例：下限会随播放状态变（播放中一屏的 30%，暂停才是顶栏那一条），
    // 存比例的话下限一变，同一个比例换算出另一个高度，画面会「啪」地跳一下。
    val slotMetrics = remember { FloatArray(2) }
    val slotFloor = remember { FloatArray(1) }
    val slotHeight = remember { mutableFloatStateOf(0f) }
    // 用户自己拖过槽位之后，槽位高度就归他管，别被宽高比的变化重新按下去。
    var slotDragged by remember { mutableStateOf(false) }
    // 进页面时还不知道真实宽高比，先按 16:9 量了一版槽位。真实宽高比到了要把它放回完整高度，
    // 否则竖屏视频会被下限夹在「播放中最小高度」上，再也长不满。
    LaunchedEffect(aspect) {
        if (!slotDragged) slotHeight.floatValue = 0f
    }
    val minSlotPx = with(LocalDensity.current) { CollapsedBarHeight.toPx() }
    // 横栏跟着视频一起进退：视频长到（横栏高度 + 这一段）就完全淡掉，
    // 中途不硬切，所以不会「一闪」；淡完就整个撤掉组合，不再挡下面的点击。
    val barFadePx = with(LocalDensity.current) { 56.dp.toPx() }
    // 只有真的收到顶栏那一条才画它。位置比的是高度而不是比例：
    // 播放中下限抬高，那时画出来的是「一条横栏压住视频上半截」，不是这个状态。
    val barVisible by remember {
        derivedStateOf {
            val h = slotHeight.floatValue
            h > 0f && h <= minSlotPx + 0.5f
        }
    }
    val barFading by remember {
        derivedStateOf {
            val h = slotHeight.floatValue
            h > 0f && h < minSlotPx + barFadePx
        }
    }

    // 手动全屏时手机可能还竖着拿，沿用最近一次的横屏方向，默认听筒朝左。
    var preferredSide by remember { mutableIntStateOf(1) }
    if (landscapeSide != 0) preferredSide = landscapeSide
    val currentSide by rememberUpdatedState(preferredSide)

    val progress = remember { Animatable(if (fullscreen) 1f else 0f) }
    val angle = remember { Animatable(sideToDegrees(preferredSide)) }

    DisposableEffect(activity) {
        val window = activity?.window
        val oldRotation = window?.attributes?.rotationAnimation
        val oldCutout = window?.attributes?.layoutInDisplayCutoutMode
        window?.attributes = window.attributes.apply {
            rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
            // 横屏窗口要和竖屏整屏严格对调，刘海那一侧不能被系统让出去，否则转屏那一帧对不齐。
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (window != null && oldRotation != null && oldCutout != null) {
                window.attributes = window.attributes.apply {
                    rotationAnimation = oldRotation
                    layoutInDisplayCutoutMode = oldCutout
                }
            }
        }
    }

    // 页面栈里的页面都常驻，状态栏图标颜色只由栈顶这一页接管：
    // 从上一个播放页返回时，它退场会把颜色还给主题，这一页重新成为栈顶要再定回白色。
    // 视频收到底之后状态栏下面就是页面底色，白图标会看不见，这时换成深色图标。
    val isTop = LocalRouteIsTop.current
    val darkStatusIcons = barVisible && !dark
    DisposableEffect(activity, dark, isTop, darkStatusIcons) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (isTop) {
            controller?.isAppearanceLightStatusBars = darkStatusIcons
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (isTop) {
                controller?.isAppearanceLightStatusBars = !dark
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(fullscreen, portraitStage, activity) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            // 和 ColorOS 白名单转屏一样：开转前先收起状态栏和导航条，旋转途中画面上只剩视频。
            // 舞台用的是固定的系统栏高度，收起系统栏不会让播放器和详情页重排。
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            // 起跳点就是当前槽位高度（用户拖到哪就从哪长起来），这里不改高度：
            // 改回去会让竖屏视频先跳一下再放大。转场本身按 slotMetrics[1] 换算缩放。
            if (portraitStage) {
                // 竖屏视频：不转窗口，播放器从当前槽位等比放大到整屏。
                angle.snapTo(0f)
                if (currentWindowLandscape) {
                    // 上一个视频还是横屏全屏时切到竖屏视频：先转回竖屏，直接落位。
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    progress.snapTo(1f)
                    withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
                        snapshotFlow { currentWindowLandscape }.first { !it }
                    }
                    repeat(2) { withFrameNanos { } }
                } else {
                    if (progress.value <= 0.001f) {
                        // 头一帧播放器要按整屏尺寸重排一次，画完再起动画。
                        repeat(2) { withFrameNanos { } }
                    }
                    progress.animateTo(
                        1f,
                        tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                    )
                }
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else if (!currentWindowLandscape) {
                if (progress.value <= 0.001f) {
                    angle.snapTo(sideToDegrees(currentSide))
                    // 头一帧播放器要按横屏尺寸重排一次，画完再起动画。
                    repeat(2) { withFrameNanos { } }
                    progress.animateTo(
                        1f,
                        tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase),
                    )
                } else {
                    // 从竖屏全屏直接换成横屏视频：画面已经在整屏，直接把朝向摆正再转窗口。
                    angle.snapTo(sideToDegrees(currentSide))
                    progress.snapTo(1f)
                }
                activity?.requestedOrientation = degreesToOrientation(angle.value)
            } else {
                progress.snapTo(1f)
                activity?.requestedOrientation = degreesToOrientation(angle.value)
            }
        } else {
            if (portraitStage) {
                // 竖屏全屏退出：窗口本来就是竖屏，直接缩回 16:9 槽位。
                angle.snapTo(0f)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else if (currentWindowLandscape) {
                // 从系统真横屏退出：先记下当前朝向，转回竖屏后从「旋转铺满」那一帧倒着放。
                angle.snapTo(displayRotationDegrees(activity) ?: angle.value)
                progress.snapTo(1f)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                withTimeoutOrNull(ORIENTATION_SETTLE_TIMEOUT_MS) {
                    snapshotFlow { currentWindowLandscape }.first { !it }
                }
                // 转回竖屏后的头一帧要整页重排，等它画完再起动画，否则前半段会被这一帧吞掉。
                repeat(2) { withFrameNanos { } }
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            // 缩回途中就放出系统栏：转过一半（约 140ms）开始滑入，和画面落位大致同时结束。
            var barsShown = false
            progress.animateTo(0f, tween(COLOROS_VIDEO_ROTATE_DURATION_MS, easing = ColorOSMoveEase)) {
                if (!barsShown && value <= SYSTEM_BARS_REVEAL_PROGRESS) {
                    barsShown = true
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            if (!barsShown) controller?.show(WindowInsetsCompat.Type.systemBars())
            // 转屏时窗口属性会被系统重新下发一遍，状态栏图标颜色可能被打回成跟随主题的深色，
            // 在纯黑的播放页顶部就看不见了。落回竖屏后再按「视频有没有收到底」定一次。
            controller?.isAppearanceLightStatusBars = barVisible && !dark
        }
    }

    // 横屏里把手机掉个头：直接申请对侧横屏，系统硬切即可，画面本来就铺满。
    LaunchedEffect(landscapeSide, fullscreen, windowLandscape, portraitStage) {
        if (!portraitStage && fullscreen && windowLandscape &&
            landscapeSide != 0 && progress.value >= 0.999f
        ) {
            angle.snapTo(sideToDegrees(landscapeSide))
            activity?.requestedOrientation = degreesToOrientation(angle.value)
        }
    }

    // 转屏让窗口重新量 inset，横屏落定后再收一遍系统栏，防止被顶出来。
    LaunchedEffect(fullscreen, windowLandscape, activity) {
        if (fullscreen && windowLandscape) {
            activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                ?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    if (inPipMode) {
        Box(Modifier.fillMaxSize().background(Color.Black), content = playerContent)
        return
    }

    val inStage by remember { derivedStateOf { fullscreen || progress.value > 0.0005f } }
    val pageSurface = pageContainerColor()
    val statusBarPx = rememberStableStatusBarHeightPx()
    // 横屏窗口里详情页仍按竖屏尺寸测量，退出全屏时不用重排，滚动位置也不会跳。
    var portraitWidth by remember { mutableIntStateOf(0) }
    var portraitHeight by remember { mutableIntStateOf(0) }
    // 最近一次按「舞台尺寸」量出来的播放器大小（宽 = 竖屏高，高 = 竖屏宽）。
    // 播放器那一层拿它和实测尺寸比对来决定用不用转场矩阵，不靠 inStage：
    // 进度归零那一帧画面已经要按竖屏画了，可重排要等到下一帧，
    // 按 inStage 切分支就会拿竖屏的变换去画横屏尺寸的画面，收尾闪一帧特别大。
    val stageBox = remember { IntArray(2) }
    val currentLandscapeForLayer by rememberUpdatedState(windowLandscape)
    val currentCanExpand by rememberUpdatedState(canExpand)
    // 上滑先收视频、收干净了列表才滚；已经收起来的视频再下滑就长回去。
    // 两个方向都在 onPreScroll 里接：列表滚到顶后的下拉会被自身的回弹吃掉，
    // 留不到 onPostScroll，只有抢在前面才收得到。
    val collapseScroll = remember(minSlotPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 甩出去的惯性（SideEffect）也要接着收：只认手指的话，用力一推视频会停在松手那一刻，
                // 后面的惯性全喂给列表，看起来就是卡住不动了。收到底之后再原样交还，列表照常甩。
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
                // 视频还是完整高度时，下拉照旧交给列表，别抢它的回弹。
                if (dy > 0f && current >= full - 0.5f) return Offset.Zero
                // 收起来之后要拉回来，得先让列表滚到顶：列表还在半路时先滚列表，
                // 不然随便一下滑视频就弹出来，看着像闪了一下。
                if (dy > 0f && !currentCanExpand()) return Offset.Zero
                val next = (current + dy).coerceIn(floor, full)
                if (next == current) return Offset.Zero
                slotHeight.floatValue = next
                slotDragged = true
                return Offset(0f, next - current)
            }
        }
    }
    // 播放器图层当前的缩放；null = 没在转场，按原尺寸平铺。
    // 只有确实按舞台尺寸量出来的那一帧才算转场：切换发生在尺寸真正变掉之后，首尾两帧都不会用错坐标系。
    val stageScale: (Size) -> Float? = remember {
        { size ->
            val stageW = stageBox[0]
            val stageH = stageBox[1]
            val measured = stageW > 0 &&
                size.width.roundToInt() == stageW &&
                size.height.roundToInt() == stageH
            when {
                !measured || currentLandscapeForLayer -> null
                currentPortraitStage -> {
                    // 竖屏全屏：FIT 后的画面在两个尺寸里比例相同，等比缩放就能严丝合缝地对上。
                    // 起点用详情页当前的槽位高度：视频被滑得很小时，放大也从那个大小开始。
                    val portW = stageW.toFloat()
                    val slotW = fitContentWidth(currentAspect, portW, slotMetrics[1])
                    val fullW = fitContentWidth(currentAspect, size.width, size.height)
                    val from = (slotW / fullW.coerceAtLeast(1f)).coerceAtLeast(0.01f)
                    lerp(from, 1f, progress.value)
                }
                else -> {
                    val portW = size.height
                    val portH = size.width
                    // 起点就是当前槽位高度：4:3 这类非 16:9 视频拉满宽度时槽位更高，
                    // 按 16:9 算会让退出全屏的动画收回到一个比原位更小的尺寸。
                    val videoH = slotMetrics[1].takeIf { it > 0f }
                        ?: min(portW * 9f / 16f, portH * 0.7f)
                    lerp(videoH / portW.coerceAtLeast(1f), 1f, progress.value).coerceAtLeast(0.01f)
                }
            }
        }
    }

    Layout(
        content = {
            // 0: 简介 / 评论，只在竖屏窗口且没铺满时绘制。
            // 竖向滑动先经过这里：竖屏视频的槽位高度就是被它推着变的。
            Box(
                modifier = Modifier
                    .background(pageSurface)
                    .nestedScroll(collapseScroll)
                    .drawWithContent {
                        if (!windowLandscape && progress.value < 0.9995f) drawContent()
                    },
                content = detailContent,
            )

            // 1: 竖屏视频槽 + 全屏渐黑遮罩，压在详情页上面，只画纯色不开离屏缓冲。
            Box(
                modifier = Modifier.drawBehind {
                    val p = if (windowLandscape) 1f else progress.value
                    if (p < 0.9995f) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(0f, statusBarPx),
                            size = Size(size.width, slotMetrics[1]),
                        )
                    }
                    if (p > 0.0005f) drawRect(Color.Black.copy(alpha = p.coerceIn(0f, 1f)))
                },
            )

            // 2: 播放器。竖屏窗口转屏途中按横屏尺寸布局一次，由 GPU 矩阵完成缩放 + ±90° 旋转；
            //    竖屏视频则按整屏尺寸布局，等比缩放回 16:9 槽位。
            Box(
                modifier = Modifier.graphicsLayer {
                    val s = stageScale(size)
                    if (s == null) {
                        transformOrigin = TransformOrigin.Center
                        translationX = 0f
                        translationY = if (windowLandscape) 0f else statusBarPx
                        scaleX = 1f
                        scaleY = 1f
                        rotationZ = 0f
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
                        clip = false
                    }
                },
                content = {
                    CompositionLocalProvider(
                        LocalStageScale provides stageScale,
                        // 竖屏全屏是等比放大，黑底会跟着缩放露在画面外；横屏旋转仍需要它撑满窗口。
                        LocalStageOpaquePlayer provides !portraitStage,
                    ) { playerContent() }
                },
            )

            // 3: 竖屏附加层（返回按钮等）。视频收到底时，顶栏那一行也画在这里。
            Box(
                modifier = Modifier.drawWithContent { if (!windowLandscape && !inStage) drawContent() },
                content = {
                    if (!windowLandscape && !inStage) {
                        CompositionLocalProvider(LocalPortraitSlotHeight provides slotHeight) {
                            portraitOverlay()
                            if (portraitResizable) {
                                // 横栏不是「在/不在」两种状态，而是跟着槽位高度淡进淡出：
                                // 视频被拉下来时它一起退场，拉回收起时才慢慢回来。
                                CollapsedBarSlot(barFading) {
                                    Box(
                                        modifier = Modifier.graphicsLayer {
                                            alpha = (
                                                (minSlotPx + barFadePx - slotHeight.floatValue) /
                                                    barFadePx
                                                ).coerceIn(0f, 1f)
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
                    drawRect(Color.Black, size = Size(size.width, statusBarPx))
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
        // 详情页里的视频槽位：高度跟着滑动变。竖屏视频收到顶栏那一条为止；
        // 横屏视频同样能收，但播放中至少留一屏的 30%，暂停了才推得干净。
        val videoH = if (portraitResizable) {
            val full = min(portW / aspect, portH * 0.7f)
            // 进详情页统一落在 16:9 高度：竖屏视频不再一进来就顶到最高。
            val initial = min(portW * 9f / 16f, full)
            slotMetrics[0] = full
            val floor = if (playing) {
                maxOf(minSlotPx, min(initial, portH * 0.3f))
            } else {
                minSlotPx
            }.coerceAtMost(full)
            slotFloor[0] = floor
            // slotHeight 为 0 表示还没量过。下限变了（开始/暂停播放）只夹紧当前高度，
            // 不会按比例换算成别的高度，画面就不会在暂停那一刻跳一下。
            val wanted = slotHeight.floatValue.takeIf { it > 0f } ?: initial
            val clamped = wanted.coerceIn(floor, full)
            if (clamped != slotHeight.floatValue) slotHeight.floatValue = clamped
            clamped.also { slotMetrics[1] = it }
        } else {
            slotMetrics[0] = 0f
            slotFloor[0] = minSlotPx
            min(portW * 9f / 16f, portH * 0.7f).also { slotMetrics[1] = it }
        }
        val detailTop = (statusBarPx + videoH).roundToInt()

        val detail = measurables[0].measure(
            Constraints.fixed(portW, (portH - detailTop).coerceAtLeast(1)),
        )
        val backdrop = measurables[1].measure(Constraints.fixed(winW, winH))
        val playerConstraints = when {
            windowLandscape -> Constraints.fixed(winW, winH)
            inStage -> {
                if (portraitStage) {
                    // 竖屏全屏：播放器按整屏尺寸布局，交给 GPU 等比缩回槽位。
                    stageBox[0] = winW
                    stageBox[1] = winH
                    Constraints.fixed(winW, winH)
                } else {
                    // 转场期间播放器按「竖屏高 × 竖屏宽」量一次，交给 GPU 矩阵缩放旋转。
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

