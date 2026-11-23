package erl.bilico.feature.comment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import erl.bilico.ui.components.SECONDARY_ALPHA
import erl.bilico.ui.components.VectorLayer
import erl.bilico.ui.components.dualToneVector
import erl.bilico.ui.components.roundedRectPath
import erl.bilico.ui.components.unifiedStrokeIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.CommentSailing
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * 右上角装扮立绘 + 号码牌：
 * B 站 `cardbg.image` 统一为 `972×162`（6:1）透明横图，角色立绘固定位于水平 `[0.68, 0.83]` 槽位。
 * 用 `38dp×32dp` 视窗配合显式 `Layout` 精准对齐原图立绘中心（`0.758 * 192dp`），
 * 右侧紧邻绘制两行方折数码字体（`· NO.` 与 `032469`）。
 */
@Composable
internal fun CommentSailingBadge(
    sailing: CommentSailing,
    isDark: Boolean,
) {
    val density = LocalDensity.current
    val fullBannerWidthDp = 192.dp
    val bannerHeightDp = 32.dp
    val cropWidthDp = 38.dp
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
    unifiedStrokeIcon(
        name = "LikeOutline",
        paths = listOf(
            "M7.4,10.3V19.9H4.8C3.75,19.9 3.1,19.25 3.1,18.2V12C3.1,10.95 3.75,10.3 4.8,10.3H7.4Z",
            "M7.4,10.3L10.95,4.35C11.45,3.5 12.55,3.25 13.4,3.75C14.15,4.18 14.5,5.05 14.25,5.88" +
                "L13.35,9.1H18.1C19.75,9.1 20.9,10.28 20.72,11.92L20.02,17.65" +
                "C19.82,19.05 18.65,19.9 17.15,19.9H7.4",
        ),
    )
}

internal val LikeFilledIcon: ImageVector by lazy {
    dualToneVector(
        name = "LikeFilled",
        layers = listOf(
            VectorLayer(path = roundedRectPath(2.2f, 9.8f, 4.5f, 11.0f, 1.7f), alpha = SECONDARY_ALPHA),
            VectorLayer(
                path = "M8.3,10.1L11.55,3.85C12.05,2.9 13.25,2.62 14.15,3.18" +
                    "C14.95,3.68 15.32,4.65 15.02,5.55L13.92,8.95H18.45" +
                    "C20.42,8.95 21.85,10.42 21.58,12.38L20.82,17.95" +
                    "C20.56,19.68 19.15,20.8 17.4,20.8H10.2C8.95,20.8 8.3,20.15 8.3,18.9V10.1Z",
            ),
        ),
    )
}

internal val DislikeOutlineIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "DislikeOutline",
        paths = listOf(
            "M16.6,13.7V4.1H19.2C20.25,4.1 20.9,4.75 20.9,5.8V12C20.9,13.05 20.25,13.7 19.2,13.7H16.6Z",
            "M16.6,13.7L13.05,19.65C12.55,20.5 11.45,20.75 10.6,20.25C9.85,19.82 9.5,18.95 9.75,18.12" +
                "L10.65,14.9H5.9C4.25,14.9 3.1,13.72 3.28,12.08L3.98,6.35" +
                "C4.18,4.95 5.35,4.1 6.85,4.1H16.6",
        ),
    )
}

internal val DislikeFilledIcon: ImageVector by lazy {
    dualToneVector(
        name = "DislikeFilled",
        layers = listOf(
            VectorLayer(path = roundedRectPath(17.3f, 3.2f, 4.5f, 11.0f, 1.7f), alpha = SECONDARY_ALPHA),
            VectorLayer(
                path = "M15.7,13.9L12.45,20.15C11.95,21.1 10.75,21.38 9.85,20.82" +
                    "C9.05,20.32 8.68,19.35 8.98,18.45L10.08,15.05H5.55" +
                    "C3.58,15.05 2.15,13.58 2.42,11.62L3.18,6.05" +
                    "C3.44,4.32 4.85,3.2 6.6,3.2H13.8C15.05,3.2 15.7,3.85 15.7,5.1V13.9Z",
            ),
        ),
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

/**
 * 评论区单行昵称 + 等级/UP 徽章：
 * 默认 13.5sp；当昵称在剩余宽度内放不下时自动微调字号至刚好完整显示（下限 10sp，避免过小），不换行。
 */
@Composable
internal fun UserNameWithBadges(
    userName: String,
    level: Int,
    isUp: Boolean,
    nameColor: Color,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = COUITheme.textStyles.body2.copy(
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )
    val badgesWidthPx = with(density) {
        ((if (level > 0) 28.dp else 0.dp) + (if (isUp) 25.dp else 0.dp)).roundToPx()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxTextWidthPx = (constraints.maxWidth - badgesWidthPx - 2).coerceAtLeast(0)
        val fittedFontSp = remember(userName, baseStyle, maxTextWidthPx) {
            if (maxTextWidthPx <= 0 || userName.isEmpty()) {
                13.5f
            } else {
                val fitsAt = { sizeSp: Float ->
                    !textMeasurer.measure(
                        text = userName,
                        style = baseStyle.copy(fontSize = sizeSp.sp),
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = maxTextWidthPx),
                    ).hasVisualOverflow
                }
                val maxSp = 13.5f
                val minSp = 10.0f
                when {
                    fitsAt(maxSp) -> maxSp
                    !fitsAt(minSp) -> minSp
                    else -> {
                        var lo = minSp
                        var hi = maxSp
                        repeat(7) {
                            val mid = (lo + hi) * 0.5f
                            if (fitsAt(mid)) lo = mid else hi = mid
                        }
                        lo
                    }
                }
            }
        }

        Row(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = userName,
                modifier = Modifier.weight(1f, fill = false),
                style = baseStyle.copy(fontSize = fittedFontSp.sp),
                color = nameColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (level > 0) {
                Spacer(Modifier.width(4.dp))
                BiliLevelBadge(level = level)
            }
            if (isUp) {
                Spacer(Modifier.width(4.dp))
                BiliUpBadge()
            }
        }
    }
}
