package erl.bilico.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlay
import io.github.suqi8.coui.kmp.basic.LocalEdgeToEdge
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop

/**
 * 玻璃效果总开关。BiliCO 没有把它做成设置项：Cobo 的观感靠顶栏与底栏的模糊和高光撑着，
 * 关掉之后整条栏会退回平涂，和设计稿对不上。
 */
const val BlurEffectsEnabled = true

/** 模糊关闭时连 backdrop 的录制一起省掉；没人采样它，每层每帧重录都是白费。 */
fun Modifier.layerBackdropIf(enabled: Boolean, backdrop: LayerBackdrop): Modifier =
    if (enabled) layerBackdrop(backdrop) else this

/**
 * 自定义顶栏的材质层：模糊开着走 [AppBarBlurOverlay]，关掉就画整块实色。
 * 两条路在关掉模糊后的观感不同，统一成实色才不会忽深忽浅。
 *
 * 材质只在 `Scaffold(edgeToEdge = true)` 里才画，而这里常常挂在 Scaffold 之外（浮在整页列表上），
 * 所以本组件自己把 [LocalEdgeToEdge] 置真；否则 [AppBarBlurOverlay] 会退化成一块什么都没有的壳，
 * 顶栏变成一条硬边实色。
 *
 * @param platformRenderer 交给系统窗口材质去采样。页面里有自己录 backdrop 的图层时传 `false`，
 *   走 COUI 的引擎采样那一层：系统材质采的是整个窗口，会把自己的输出吃回去，滚动时丢帧发黑。
 */
@Composable
fun AppBarSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = pageContainerColor(),
    scrollFraction: () -> Float = { 1f },
    platformRenderer: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    if (BlurEffectsEnabled) {
        CompositionLocalProvider(LocalEdgeToEdge provides true) {
            AppBarBlurOverlay(
                modifier = modifier,
                containerColor = containerColor,
                scrollFraction = scrollFraction,
                platformRenderer = platformRenderer,
                content = content,
            )
        }
        return
    }
    Box(
        modifier.drawBehind {
            val alpha = scrollFraction().coerceIn(0f, 1f)
            if (alpha > 0f) drawRect(containerColor, alpha = alpha)
        },
        content = content,
    )
}
