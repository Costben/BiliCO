package erl.bilico.feature.comment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import erl.bilico.data.model.CommentSailing
import erl.bilico.data.util.Formatters

/**
 * 右上角装扮立绘 + 号码牌：
 * B 站 `cardbg.image` 统一为 `972×162`（6:1）透明横图，角色立绘固定位于水平 `[0.68, 0.83]` 槽位。
 * 用 `44dp×36dp` 视窗配合显式 `Layout` 精准对齐原图立绘中心（`0.758 * 216dp`），
 * 右侧紧邻绘制两行方折数码字体（`· NO.` 与 `032469`）。
 */
@Composable
internal fun CommentSailingBadge(
    sailing: CommentSailing,
    isDark: Boolean,
) {
    val density = LocalDensity.current
    val fullBannerWidthDp = 216.dp
    val bannerHeightDp = 36.dp
    val cropWidthDp = 44.dp
    val requestWidthPx = with(density) { fullBannerWidthDp.roundToPx() }
    val sizedBannerUrl = remember(sailing.imageUrl, requestWidthPx) {
        Formatters.widthImageUrl(sailing.imageUrl, requestWidthPx)
    }
    val serialColor = remember(sailing.fanColorHex, isDark) {
        parseCommentColor(sailing.fanColorHex) ?: Color(0xFFE0584B)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Layout(
            content = {
                AsyncImage(
                    model = sizedBannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                )
            },
            modifier = Modifier
                .size(width = cropWidthDp, height = bannerHeightDp)
                .clipToBounds(),
        ) { measurables, constraints ->
            val bannerW = fullBannerWidthDp.roundToPx()
            val bannerH = bannerHeightDp.roundToPx()
            val placeable = measurables[0].measure(Constraints.fixed(bannerW, bannerH))
            val cropW = constraints.maxWidth
            val cropH = constraints.maxHeight
            val artCenterX = (bannerW * 0.758f).toInt()
            val placeX = cropW / 2 - artCenterX
            layout(cropW, cropH) {
                placeable.place(placeX, 0)
            }
        }

        if (sailing.numDesc.isNotBlank()) {
            Spacer(Modifier.width(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(2.5.dp)) {
                        drawCircle(color = serialColor.copy(alpha = 0.75f))
                    }
                    Spacer(Modifier.width(2.dp))
                    PixelSerialText(
                        text = sailing.numPrefix.ifBlank { "NO." }.uppercase(),
                        color = serialColor,
                        glyphHeight = 6.dp,
                    )
                }
                PixelSerialText(
                    text = sailing.numDesc.uppercase(),
                    color = serialColor,
                    glyphHeight = 6.5.dp,
                )
            }
        }
    }
}

/**
 * 实心圆角 `LV0`~`LV6` 几何等级徽章，复刻参考图中的红底白字方折 `LV6`。
 */
@Composable
internal fun BiliLevelBadge(level: Int, modifier: Modifier = Modifier) {
    val safeLevel = level.coerceIn(0, 6)
    val bgColor = when (safeLevel) {
        0, 1 -> Color(0xFFBFBFBF)
        2 -> Color(0xFF8BD29B)
        3 -> Color(0xFF7BCDEF)
        4 -> Color(0xFFFFB37C)
        5 -> Color(0xFFFF6C00)
        else -> Color(0xFFEC4D44)
    }
    Canvas(modifier.size(width = 24.dp, height = 12.dp)) {
        val r = 2.dp.toPx()
        drawRoundRect(color = bgColor, cornerRadius = CornerRadius(r, r))

        val strokeW = 1.35.dp.toPx()
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Square, join = StrokeJoin.Miter)
        val top = size.height * 0.24f
        val bottom = size.height * 0.76f
        val midY = (top + bottom) * 0.5f
        val glyphW = size.width * 0.19f
        val gap = size.width * 0.085f
        val startX = (size.width - glyphW * 3f - gap * 2f) * 0.5f

        val lx0 = startX
        val lx1 = lx0 + glyphW * 0.92f
        val lPath = Path().apply {
            moveTo(lx0, top)
            lineTo(lx0, bottom)
            lineTo(lx1, bottom)
        }
        drawPath(lPath, Color.White, style = stroke)

        val vx0 = startX + glyphW + gap
        val vxMid = vx0 + glyphW * 0.5f
        val vx1 = vx0 + glyphW
        val vShoulder = top + (bottom - top) * 0.52f
        val vPath = Path().apply {
            moveTo(vx0, top)
            lineTo(vx0, vShoulder)
            lineTo(vxMid, bottom)
            lineTo(vx1, vShoulder)
            lineTo(vx1, top)
        }
        drawPath(vPath, Color.White, style = stroke)

        drawBlockGlyph(
            ch = ('0' + safeLevel),
            left = vx1 + gap,
            top = top,
            width = glyphW,
            height = bottom - top,
            midY = midY,
            color = Color.White,
            stroke = stroke,
        )
    }
}

/**
 * 实心圆角 `UP` 几何徽章，与 `BiliLevelBadge` 严格同高居中对齐，避免字体 ascent/descent 偏移。
 */
@Composable
internal fun BiliUpBadge(modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 21.dp, height = 12.dp)) {
        val r = 2.dp.toPx()
        drawRoundRect(color = Color(0xFFFB7299), cornerRadius = CornerRadius(r, r))
        val strokeW = 1.35.dp.toPx()
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Square, join = StrokeJoin.Miter)
        val top = size.height * 0.24f
        val bottom = size.height * 0.76f
        val midY = (top + bottom) * 0.5f
        val glyphW = size.width * 0.25f
        val gap = size.width * 0.11f
        val startX = (size.width - glyphW * 2f - gap) * 0.5f

        val uPath = Path().apply {
            moveTo(startX, top)
            lineTo(startX, bottom)
            lineTo(startX + glyphW, bottom)
            lineTo(startX + glyphW, top)
        }
        drawPath(uPath, Color.White, style = stroke)

        val px0 = startX + glyphW + gap
        val px1 = px0 + glyphW
        val pPath = Path().apply {
            moveTo(px0, bottom)
            lineTo(px0, top)
            lineTo(px1, top)
            lineTo(px1, midY + strokeW * 0.15f)
            lineTo(px0, midY + strokeW * 0.15f)
        }
        drawPath(pPath, Color.White, style = stroke)
    }
}

@Composable
internal fun PixelSerialText(
    text: String,
    color: Color,
    glyphHeight: Dp,
) {
    val glyphWidth = glyphHeight * 0.74f
    val dotWidth = glyphHeight * 0.26f
    val spacing = glyphHeight * 0.24f
    val totalWidth = remember(text, glyphWidth, dotWidth, spacing) {
        var sum = 0.dp
        text.forEachIndexed { idx, ch ->
            sum += if (ch == '.') dotWidth else glyphWidth
            if (idx < text.lastIndex) sum += spacing
        }
        sum.coerceAtLeast(1.dp)
    }

    Canvas(Modifier.size(width = totalWidth, height = glyphHeight)) {
        val strokeW = (size.height * 0.19f).coerceAtLeast(1.5f)
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Square, join = StrokeJoin.Miter)
        val pad = strokeW * 0.5f
        val top = pad
        val h = (size.height - pad * 2f).coerceAtLeast(1f)
        val midY = top + h * 0.5f
        val gw = glyphWidth.toPx() - pad
        val dw = dotWidth.toPx()
        val sp = spacing.toPx()
        var cursorX = pad * 0.5f

        text.forEach { ch ->
            if (ch == '.') {
                drawRect(
                    color = color,
                    topLeft = Offset(cursorX, top + h - strokeW),
                    size = Size(strokeW * 1.15f, strokeW * 1.15f),
                )
                cursorX += dw + sp
            } else {
                drawBlockGlyph(
                    ch = ch,
                    left = cursorX,
                    top = top,
                    width = gw.coerceAtLeast(1f),
                    height = h,
                    midY = midY,
                    color = color,
                    stroke = stroke,
                )
                cursorX += glyphWidth.toPx() + sp
            }
        }
    }
}

private fun DrawScope.drawBlockGlyph(
    ch: Char,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    midY: Float,
    color: Color,
    stroke: Stroke,
) {
    val right = left + width
    val bottom = top + height
    val p = Path()
    when (ch) {
        '0', 'O' -> {
            p.moveTo(left, top); p.lineTo(right, top)
            p.lineTo(right, bottom); p.lineTo(left, bottom); p.close()
        }
        '1' -> {
            val cx = left + width * 0.58f
            p.moveTo(left + width * 0.2f, top)
            p.lineTo(cx, top); p.lineTo(cx, bottom)
            p.moveTo(left + width * 0.18f, bottom); p.lineTo(right - width * 0.08f, bottom)
        }
        '2' -> {
            p.moveTo(left, top); p.lineTo(right, top)
            p.lineTo(right, midY); p.lineTo(left, midY)
            p.lineTo(left, bottom); p.lineTo(right, bottom)
        }
        '3' -> {
            p.moveTo(left, top); p.lineTo(right, top)
            p.lineTo(right, bottom); p.lineTo(left, bottom)
            p.moveTo(left + width * 0.15f, midY); p.lineTo(right, midY)
        }
        '4' -> {
            p.moveTo(left, top); p.lineTo(left, midY); p.lineTo(right, midY)
            p.moveTo(right, top); p.lineTo(right, bottom)
        }
        '5' -> {
            p.moveTo(right, top); p.lineTo(left, top)
            p.lineTo(left, midY); p.lineTo(right, midY)
            p.lineTo(right, bottom); p.lineTo(left, bottom)
        }
        '6' -> {
            p.moveTo(right, top); p.lineTo(left, top)
            p.lineTo(left, bottom); p.lineTo(right, bottom)
            p.lineTo(right, midY); p.lineTo(left, midY)
        }
        '7' -> {
            p.moveTo(left, top); p.lineTo(right, top); p.lineTo(right, bottom)
        }
        '8' -> {
            p.moveTo(left, top); p.lineTo(right, top)
            p.lineTo(right, bottom); p.lineTo(left, bottom); p.close()
            p.moveTo(left, midY); p.lineTo(right, midY)
        }
        '9' -> {
            p.moveTo(left, bottom); p.lineTo(right, bottom)
            p.lineTo(right, top); p.lineTo(left, top)
            p.lineTo(left, midY); p.lineTo(right, midY)
        }
        'N' -> {
            p.moveTo(left, bottom); p.lineTo(left, top)
            p.lineTo(right, bottom); p.lineTo(right, top)
        }
        'C' -> {
            p.moveTo(right, top); p.lineTo(left, top)
            p.lineTo(left, bottom); p.lineTo(right, bottom)
        }
        'D' -> {
            p.moveTo(left, top); p.lineTo(right - width * 0.2f, top)
            p.lineTo(right, top + height * 0.2f); p.lineTo(right, bottom - height * 0.2f)
            p.lineTo(right - width * 0.2f, bottom); p.lineTo(left, bottom); p.close()
        }
        else -> {
            p.moveTo(left, top); p.lineTo(right, top)
            p.lineTo(right, bottom); p.lineTo(left, bottom); p.close()
        }
    }
    drawPath(p, color = color, style = stroke)
}

internal fun parseCommentColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val parsed = clean.toLongOrNull(16) ?: return null
    val argb = if (clean.length == 6) 0xFF000000L or parsed else parsed
    return Color(argb.toInt())
}

internal val ReplyCurveIcon: ImageVector by lazy {
    commentVector(
        name = "ReplyCurve",
        path = "M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z",
    )
}

internal val LikeOutlineIcon: ImageVector by lazy {
    commentVector(
        name = "LikeOutline",
        path = "M9,21h9c0.83,0 1.54,-0.5 1.84,-1.22l3.02,-7.05c0.09,-0.23 0.14,-0.47 0.14,-0.73v-2" +
            "c0,-1.1 -0.9,-2 -2,-2h-6.31l0.95,-4.57 0.03,-0.32c0,-0.41 -0.17,-0.79 -0.44,-1.06L14.17,1 " +
            "7.58,7.59C7.22,7.95 7,8.45 7,9v10c0,1.1 0.9,2 2,2zM9,9l4.34,-4.34L12,10h9v2l-3,7H9V9z" +
            "M1,9h4v12H1V9z",
    )
}

internal val LikeFilledIcon: ImageVector by lazy {
    commentVector(
        name = "LikeFilled",
        path = "M1,21h4V9H1v12zm22,-11c0,-1.1 -0.9,-2 -2,-2h-6.31l0.95,-4.57 0.03,-0.32" +
            "c0,-0.41 -0.17,-0.79 -0.44,-1.06L14.17,1 7.59,7.59C7.22,7.95 7,8.45 7,9v10" +
            "c0,1.1 0.9,2 2,2h9c0.83,0 1.54,-0.5 1.84,-1.22l3.02,-7.05c0.09,-0.23 0.14,-0.47 0.14,-0.73v-2z",
    )
}

internal val DislikeOutlineIcon: ImageVector by lazy {
    commentVector(
        name = "DislikeOutline",
        path = "M15,3H6c-0.83,0 -1.54,0.5 -1.84,1.22l-3.02,7.05c-0.09,0.23 -0.14,0.47 -0.14,0.73v2" +
            "c0,1.1 0.9,2 2,2h6.31l-0.95,4.57 -0.03,0.32c0,0.41 0.17,0.79 0.44,1.06L9.83,23" +
            "l6.58,-6.59c0.37,-0.36 0.59,-0.86 0.59,-1.41V5c0,-1.1 -0.9,-2 -2,-2zm0,12l-4.34,4.34L12,14H3v-2" +
            "l3,-7h9v10zm4,-12h4v12h-4V3z",
    )
}

internal val DislikeFilledIcon: ImageVector by lazy {
    commentVector(
        name = "DislikeFilled",
        path = "M15,3H6c-0.83,0 -1.54,0.5 -1.84,1.22l-3.02,7.05c-0.09,0.23 -0.14,0.47 -0.14,0.73v2" +
            "c0,1.1 0.9,2 2,2h6.31l-0.95,4.57 -0.03,0.32c0,0.41 0.17,0.79 0.44,1.06L9.83,23" +
            "l6.58,-6.59c0.37,-0.36 0.59,-0.86 0.59,-1.41V5c0,-1.1 -0.9,-2 -2,-2zm4,0v12h4V3h-4z",
    )
}

private fun commentVector(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(path),
        fill = SolidColor(Color.Black),
    ).build()
