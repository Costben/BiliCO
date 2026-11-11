package erl.bilico.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.ChipTabDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.BlendColorEntry
import io.github.suqi8.coui.kmp.blur.BlurBlendMode
import io.github.suqi8.coui.kmp.blur.BlurColors
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.textureBlur
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

@Immutable
data class GlassChipItem(
    val label: String,
    val icon: ImageVector? = null,
)

private val ChipHeight = 32.dp
private val ChipMinWidth = 52.dp
private val ChipGap = 8.dp
private val ChipPadding = 12.dp
private val ChipIconSize = 16.dp
private val ChipIconGap = 4.dp

/** `ChipMaskHorizontalScrollView.FADING_EDGE_WIDTH`：滚到头部就不再渐隐。 */
private val ChipRowFadeWidth = ChipTabDefaults.FadeWidth

/**
 * 便签的胶囊材质：`COUIMaterialBlurEffect.TYPE_CONTENT_CHIP_TAB*` 给的 RenderEffect 是
 * `blur(40px)` 再叠两层 blend 色。40 是 HWUI 的 radius，sigma = 0.57735 × radius；本引擎的
 * radius 又按 sigma / 0.45 反推，所以引擎半径 = 40 × 0.57735 / 0.45 像素。
 */
private const val CHIP_HWUI_BLUR_RADIUS_PX = 40f
private const val HWUI_RADIUS_TO_SIGMA = 0.57735f
private const val ENGINE_SIGMA_PER_RADIUS = 0.45f

/**
 * 便签顶栏下面那排分类胶囊。
 *
 * 材质跟顶栏共用同一个 [Backdrop]：整页只有一份「背后内容」的录制，胶囊和顶栏各画一层模糊，
 * 而不是让每个胶囊自己再录一遍层。便签本身也是这个结构——胶囊的模糊是挂在 View 上的
 * background render effect，背后内容只有窗口那一份。
 *
 * 叠色与选中态过渡取便签的常量（浅色未选中 `#144E4E4E` ColorDodge + `#14606060` Luminosity，
 * 选中 `#66A3A1A1` + `#99F5F5F5`；深色未选中 `#33B2B2B2` Luminosity + `#33303030` Overlay，
 * 选中 `#A6505050` + `#4CA7A7A7`），选中进度用临界阻尼弹簧推进，跟 native 一致。
 */
@Composable
fun GlassChipRow(
    items: List<GlassChipItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalBackdrop.current,
    leading: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // 胶囊在滚动内容里的位置（不含 scrollX），用于把选中的那颗滚进视野。
    val tabBounds = remember(items.size) { mutableStateMapOf<Int, Rect>() }
    // 便签只在点击 / 程序化选中后滚，首次组合不动。
    var firstSelection by remember { mutableStateOf(true) }
    LaunchedEffect(selectedIndex) {
        if (firstSelection) {
            firstSelection = false
            return@LaunchedEffect
        }
        val bounds = tabBounds[selectedIndex] ?: return@LaunchedEffect
        with(density) {
            scrollState.scrollTabIntoView(bounds, ChipRowFadeWidth.toPx())
        }
    }
    Row(
        modifier = modifier.padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(ChipGap))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .edgeFade(scrollState)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                GlassChip(
                    label = item.label,
                    icon = item.icon,
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) },
                    backdrop = backdrop,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        tabBounds[index] = coordinates.boundsInParent()
                    },
                )
            }
        }
    }
}

/** 便签左上那颗只有图标的圆胶囊。 */
@Composable
fun GlassChipButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalBackdrop.current,
) {
    GlassChipSurface(
        selected = true,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(ChipHeight),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = chipContentColor(dark = true, fraction = 1f),
        )
    }
}

@Composable
private fun GlassChip(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    GlassChipSurface(
        selected = selected,
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier
            .height(ChipHeight)
            .widthIn(min = ChipMinWidth, max = 300.dp),
        contentPadding = PaddingValues(horizontal = ChipPadding),
    ) { contentColor ->
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ChipIconSize),
                tint = contentColor,
            )
            Spacer(Modifier.width(ChipIconGap))
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun GlassChipSurface(
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    val fraction by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 986.96f,
        ),
        visibilityThreshold = 1f / 256f,
        label = "glassChipFraction",
    )
    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    val shape = remember { RoundedCornerShape(percent = 50) }
    val density = LocalDensity.current
    val blurRadius: Dp = remember(density) {
        with(density) {
            (CHIP_HWUI_BLUR_RADIUS_PX * HWUI_RADIUS_TO_SIGMA / ENGINE_SIGMA_PER_RADIUS).toDp()
        }
    }
    val colors = remember(dark, fraction) { chipBlurColors(dark, fraction) }
    val flatFill = remember(dark, fraction) {
        if (dark) {
            lerp(Color(0xFF373737), Color(0xFF4A4A4A), fraction)
        } else {
            lerp(Color(0x2E4E4E4E), Color(0x8FE8E8E8), fraction)
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = shape,
                        blurRadius = blurRadius.value,
                        colors = colors,
                    )
                } else {
                    Modifier.background(flatFill)
                },
            )
            .drawWithContent {
                drawContent()
                drawCheckedHighlight(fraction, dark)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content(chipContentColor(dark, fraction))
        }
    }
}

/**
 * 把 [bounds] 那颗胶囊滚进未被渐隐遮住的窗口 `[fadePx, viewport - fadePx]`，
 * 对应 `COUIChipTabGroup.checkOcclusion` / `scrollToChipTabWithAnimation`。
 */
private suspend fun ScrollState.scrollTabIntoView(bounds: Rect, fadePx: Float) {
    val range = maxValue
    if (range <= 0) return
    val scrollX = value
    val visibleLeft = if (scrollX > 0) scrollX + fadePx else scrollX.toFloat()
    val visibleRight =
        if (scrollX < range) scrollX + viewportSize - fadePx else (scrollX + viewportSize).toFloat()
    val delta = when {
        bounds.left < visibleLeft -> bounds.left - visibleLeft
        bounds.right > visibleRight -> bounds.right - visibleRight
        else -> return
    }
    animateScrollTo(
        value = (scrollX + delta).roundToInt().coerceIn(0, range),
        animationSpec = ChipTabDefaults.ScrollSpring,
    )
}

/**
 * 便签滚不动就用 `ChipMaskHorizontalScrollView.dispatchDraw` 里的 `PorterDuff.DST_OUT` 渐变把边缘擦掉，
 * 且只在还能往那一侧滚的时候擦。这里照搬：离屏层上面叠两条 12dp 的横向渐变。
 */
private fun Modifier.edgeFade(scrollState: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (scrollState.maxValue <= 0) return@drawWithContent
        val fadePx = ChipRowFadeWidth.toPx().coerceAtMost(size.width / 2f)
        if (fadePx <= 0f) return@drawWithContent
        if (scrollState.canScrollBackward) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    startX = 0f,
                    endX = fadePx,
                ),
                size = Size(fadePx, size.height),
                blendMode = BlendMode.DstOut,
            )
        }
        if (scrollState.canScrollForward) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White),
                    startX = size.width - fadePx,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - fadePx, 0f),
                size = Size(fadePx, size.height),
                blendMode = BlendMode.DstOut,
            )
        }
    }

/**
 * 选中的上下高光线：ColorOS 17 的选中胶囊只在上、下弧边点光，左右两边不描，底部再补一层很薄的内发光。
 * 数值与 COUI 自己移植 `COUIChipTabDrawable` 的 `ChipTab` 一致（深色 0.23 / 0.14 白，浅色纯白）。
 */
private fun DrawScope.drawCheckedHighlight(fraction: Float, dark: Boolean) {
    val alpha = fraction.coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val strokeWidth = 1.dp.toPx()
    val inset = strokeWidth / 2f
    val strokeBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = (if (dark) 0.23f else 1f) * alpha),
        0.30f to Color.Transparent,
        0.70f to Color.Transparent,
        1f to Color.White.copy(alpha = (if (dark) 0.14f else 1f) * alpha),
        startY = 0f,
        endY = size.height,
    )
    val radius = (size.minDimension / 2f - inset).coerceAtLeast(0f)
    drawRoundRect(
        brush = strokeBrush,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = strokeWidth),
    )
    val glowHeight = 2.dp.toPx()
    val glowAlpha = (if (dark) 0.06f else 0.85f) * alpha
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = glowAlpha)),
            startY = size.height - glowHeight,
            endY = size.height,
        ),
        topLeft = Offset(0f, size.height - glowHeight),
        size = Size(size.width, glowHeight),
    )
}

private fun chipContentColor(dark: Boolean, fraction: Float): Color {
    val idle = if (dark) Color(0xC7FFFFFF) else Color(0xE6000000)
    val active = if (dark) Color(0xF2FFFFFF) else Color(0xF2000000)
    return lerp(idle, active, fraction)
}

private fun chipBlurColors(dark: Boolean, fraction: Float): BlurColors {
    val f = fraction.coerceIn(0f, 1f)
    return if (dark) {
        BlurColors(
            blendColors = listOf(
                BlendColorEntry(lerp(Color(0x33B2B2B2), Color(0xA6505050), f), BlurBlendMode.Luminosity),
                BlendColorEntry(lerp(Color(0x33303030), Color(0x4CA7A7A7), f), BlurBlendMode.Overlay),
            ),
        )
    } else {
        BlurColors(
            blendColors = listOf(
                BlendColorEntry(lerp(Color(0x144E4E4E), Color(0x66A3A1A1), f), BlurBlendMode.ColorDodge),
                BlendColorEntry(lerp(Color(0x14606060), Color(0x99F5F5F5), f), BlurBlendMode.Luminosity),
            ),
        )
    }
}
