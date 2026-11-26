package erl.bilico.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.GlassChipDefaults
import io.github.suqi8.coui.kmp.basic.drawGlassChipHighlight

/**
 * 分段胶囊的玻璃材质参数。
 *
 * 底槽和选中块都用便签那套模糊材质（[GlassChipDefaults]）。这个对象只补它缺的东西：
 * 选中块自身的受光梯度与「只在上沿有光」的边线。
 */
internal object SegmentGlass {

    /**
     * 底槽顶边细线的强度。便签那套线在深色底上只有 +17/255，几乎看不见，
     * 深色底要用更足的 alpha 才立得住胶囊的轮廓。
     */
    fun trackHairline(dark: Boolean): Float = if (dark) 0.75f else 0.55f
}

/** 底槽的顶边/底边细线，画在内容之上。 */
internal fun DrawScope.drawSegmentTrackGlass(dark: Boolean) {
    drawGlassChipHighlight(SegmentGlass.trackHairline(dark), dark)
}

/**
 * 选中块的受光与边线，画在模糊背景之上。
 *
 * 选中块不能是平涂：便签那块是「顶亮、往下沉」的。这里用一条竖向白洗做出这个梯度。
 * [washed] 为 true 表示底下是真实的模糊采样，这一层同时要承担「选中比未选亮」的差值；
 * 平涂路径的底色（[GlassChipDefaults.flatFill]）已经含了那个差值，这里只补很轻的受光。
 */
internal fun DrawScope.drawSegmentPillGlass(dark: Boolean, washed: Boolean) {
    val radius = CornerRadius(size.height / 2f, size.height / 2f)
    val brush = if (washed) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.25f else 0.12f),
            0.5f to Color.White.copy(alpha = if (dark) 0.20f else 0.08f),
            1f to Color.White.copy(alpha = if (dark) 0.14f else 0.05f),
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.055f else 0.10f),
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (dark) 0.045f else 0.03f),
        )
    }
    drawRoundRect(brush = brush, size = size, cornerRadius = radius)
    drawPillRim(dark)
}

/**
 * 只在上沿有光的边线。
 *
 * [drawGlassChipHighlight] 会同时画顶边和底边，而这块本身已经「上亮下沉」，
 * 底边再压一道亮线就把受光方向做反了，所以这里单独画一条到 45% 高度就淡出的线。
 */
private fun DrawScope.drawPillRim(dark: Boolean) {
    val stroke = 1.dp.toPx()
    val inset = stroke / 2f
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.23f else 0.90f),
            0.45f to Color.Transparent,
            1f to Color.Transparent,
        ),
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius((size.height / 2f - inset).coerceAtLeast(0f)),
        style = Stroke(width = stroke),
    )
}
