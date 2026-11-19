package erl.bilico.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NavEntry(
    route: Route,
    val origin: ExpandOrigin? = null,
    initialProgress: Float = 1f,
    initialFade: Float = 1f,
) {
    val id = nextId++
    var route by mutableStateOf(route)
    var replaced by mutableStateOf(false)
    val progress = Animatable(initialProgress)
    val fade = Animatable(initialFade)
    var exiting by mutableStateOf(false)

    /** 返回手势把它拉出去的过程中为 true，还没真的出栈。 */
    var previewing by mutableStateOf(false)

    /** 最小化或从融合球还原期间，临时接管展开/收起锚点，不覆盖卡片原始 origin。 */
    var orbOverrideOrigin by mutableStateOf<ExpandOrigin?>(null)
    var minimizingToOrb by mutableStateOf(false)

    /** 页面里挂着 SurfaceView（HDR 播放），转场期间不能整页离屏，否则视频层会被离屏层吃掉。 */
    var hasSurfaceView by mutableStateOf(false)

    /** 播放页竖屏收到底部只剩顶栏时为 true，状态栏下方露出浅色底，需切换深色图标。 */
    var collapsedBarVisible by mutableStateOf(false)

    /**
     * 这一页从栈里彻底丢掉时执行（最小化不算，收进球的页面还在）。
     * 播放页用它释放播放器：一台准备好的播放器会占住几十 MB 的加载缓冲，不释放就得等 Activity 销毁。
     */
    var onDiscard: (() -> Unit)? = null

    fun discard() {
        val callback = onDiscard
        onDiscard = null
        callback?.invoke()
    }

    val activeOrigin: ExpandOrigin? get() = orbOverrideOrigin ?: origin

    /** 是否最初挂载在展开容器中；切集后保持槽位不变以免重建播放器舞台。 */
    val hasExpandOrigin: Boolean
        get() = (route.isPlayback() || route is Route.Opus) && activeOrigin?.bounds != null

    /** 视频页与动态详情页从卡片长大；原地连播切到下一条后退场改为整页右滑，最小化进融合球时强制走缩放。 */
    val expand: Boolean
        get() = (orbOverrideOrigin != null && activeOrigin?.bounds != null) || (!replaced && hasExpandOrigin)

    private companion object {
        var nextId = 0
    }
}

/** 当前正在组合的这一页；播放页拿它登记「页面被丢掉时释放播放器」。 */
val LocalNavEntry = compositionLocalOf<NavEntry?> { null }
