package erl.bilico.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * 浮在列表上方的顶栏下沿，根坐标。
 *
 * 推荐页的标题行会随滚动折起来，栏的下沿在滚动中一直在动。这个值从栏自己的布局里报出来，
 * 需要避让栏的卡片（展开转场回落的落点）读到的就是栏此刻真正的边，而不是列表构图时的估算值。
 */
@Stable
class TopBarBounds {
    var bottom by mutableFloatStateOf(0f)
        private set

    fun report(top: Float, height: Float) {
        bottom = top + height
    }
}

/** 当前页浮在列表上方的栏；没有浮动栏的页面为 null。 */
val LocalTopBarBounds = staticCompositionLocalOf<TopBarBounds?> { null }

/** 只要这个栏还在布局，就持续把 [bounds] 更新到它的下沿。 */
fun Modifier.reportTopBarBottom(bounds: TopBarBounds): Modifier =
    onGloballyPositioned {
        bounds.report(it.positionInRoot().y, it.size.height.toFloat())
    }
