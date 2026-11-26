package erl.bilico.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.suqi8.coui.kmp.basic.AppBarBlurOverlayDefaults
import io.github.suqi8.coui.kmp.utils.rememberGalleryDensity

/**
 * 顶栏、底栏钉在 360dp 短边，不受系统「显示大小」影响；页面内容仍然跟着系统走。
 * 只把这一层套在栏上，别套到页面上。
 */
@Composable
fun PinnedBarDensity(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides rememberGalleryDensity(), content = content)
}

/** 状态栏高度加上若干「按钉住密度量出来的行高」，换算成当前密度下的 dp。 */
@Composable
fun pinnedHeight(row: Dp): Dp = pinnedHeight(listOf(row))

@Composable
fun pinnedHeight(row1: Dp, row2: Dp): Dp = pinnedHeight(listOf(row1, row2))

@Composable
private fun pinnedHeight(rows: List<Dp>): Dp {
    val local = LocalDensity.current
    val statusPx = WindowInsets.statusBars.getTop(local)
    val rowsPx = with(rememberGalleryDensity()) { rows.fold(0f) { acc, row -> acc + row.toPx() } }
    return with(local) { (statusPx + rowsPx).toDp() }
}

/** 玻璃顶栏下首屏内容的上沿：栏高加上材质往下多画的渐隐模糊带，再留出 [gap]。 */
fun glassBarContentTop(topBarHeight: Dp, gap: Dp): Dp =
    topBarHeight + AppBarBlurOverlayDefaults.ExtraHeight + gap
