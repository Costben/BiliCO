package erl.bilico.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.anim.folmeSpring
import io.github.suqi8.coui.kmp.basic.Checkbox
import kotlin.math.roundToInt

/**
 * 多选行的整行平移容器：
 * - [body] 始终按完整行宽测量与排版，不会因左侧复选框出现而触发文字换行重排或逐字缩短；
 * - 进入多选时，复选框从左侧滑入，[body] 整体向右平移 [checkboxSlotWidth]；
 * - 右侧超出的部分在末端通过「渐变高斯模糊 + 透明度淡出」柔化隐去，避免硬裁切。
 */
@Composable
fun MultiSelectSlideRow(
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    checkboxTag: String,
    modifier: Modifier = Modifier,
    checkboxSlotWidth: Dp = 36.dp,
    edgeFadeWidth: Dp = 44.dp,
    body: @Composable () -> Unit,
) {
    val progressState = animateFloatAsState(
        targetValue = if (selectionMode) 1f else 0f,
        animationSpec = folmeSpring(damping = 1f, response = 0.32f),
        label = "multiSelectSlide",
    )
    val showCheckbox by remember(selectionMode) {
        derivedStateOf { selectionMode || progressState.value > 0.001f }
    }
    val density = LocalDensity.current
    val slotWidthPx = with(density) { checkboxSlotWidth.toPx() }
    val maxFadeWidthPx = with(density) { edgeFadeWidth.toPx() }
    val maxBlurPx = with(density) { 5.dp.toPx() }

    val contentLayer = rememberGraphicsLayer()
    val sharpLayer = rememberGraphicsLayer().apply {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    val blurLayer = rememberGraphicsLayer()
    val blurMaskedLayer = rememberGraphicsLayer().apply {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    var lastBlurRadius by remember { mutableFloatStateOf(-1f) }

    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier
                    .width(checkboxSlotWidth)
                    .graphicsLayer {
                        val p = progressState.value.coerceIn(0f, 1f)
                        alpha = p
                        scaleX = 0.85f + 0.15f * p
                        scaleY = 0.85f + 0.15f * p
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (showCheckbox) {
                    Checkbox(
                        state = if (selected) ToggleableState.On else ToggleableState.Off,
                        onClick = if (selectionMode) onToggleSelect else null,
                        modifier = Modifier
                            .padding(start = 2.dp, end = 10.dp)
                            .testTag(checkboxTag),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clipToBounds()
                    .drawWithContent {
                        val p = progressState.value.coerceIn(0f, 1f)
                        if (p <= 0.001f) {
                            drawContent()
                            return@drawWithContent
                        }
                        val w = size.width
                        val h = size.height
                        if (w <= 0f || h <= 0f) return@drawWithContent

                        val shiftPx = slotWidthPx * p
                        contentLayer.record {
                            translate(left = shiftPx) {
                                this@drawWithContent.drawContent()
                            }
                        }

                        val fadeW = maxFadeWidthPx.coerceAtMost(w * 0.45f) * p
                        if (fadeW <= 1f) {
                            drawLayer(contentLayer)
                            return@drawWithContent
                        }
                        val fadeStart = (w - fadeW).coerceAtLeast(0f)
                        val sharpFadeEnd = (fadeStart + fadeW * 0.7f).coerceAtMost(w)

                        sharpLayer.record {
                            drawLayer(contentLayer)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.White),
                                    startX = fadeStart,
                                    endX = sharpFadeEnd.coerceAtLeast(fadeStart + 1f),
                                ),
                                topLeft = Offset(fadeStart, 0f),
                                size = Size(w - fadeStart, h),
                                blendMode = BlendMode.DstOut,
                            )
                        }
                        drawLayer(sharpLayer)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val blurRadius = (maxBlurPx * p).coerceAtLeast(0.5f)
                            if (blurRadius != lastBlurRadius) {
                                blurLayer.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                                lastBlurRadius = blurRadius
                            }
                            blurLayer.record {
                                drawLayer(contentLayer)
                            }
                            blurMaskedLayer.record {
                                clipRect(left = fadeStart, top = 0f, right = w, bottom = h) {
                                    drawLayer(blurLayer)
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colorStops = arrayOf(
                                                0f to Color.Transparent,
                                                0.45f to Color.White.copy(alpha = 0.9f * p),
                                                1f to Color.Transparent,
                                            ),
                                            startX = fadeStart,
                                            endX = w,
                                        ),
                                        topLeft = Offset(fadeStart, 0f),
                                        size = Size(w - fadeStart, h),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                            drawLayer(blurMaskedLayer)
                        }
                    },
            ) {
                body()
            }
        },
    ) { measurables, constraints ->
        val bodyConstraints = constraints.copy(minWidth = constraints.maxWidth)
        val bodyPlaceable = measurables[1].measure(bodyConstraints)
        val checkPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val height = maxOf(bodyPlaceable.height, checkPlaceable.height)
        val p = progressState.value.coerceIn(0f, 1f)
        layout(constraints.maxWidth, height) {
            bodyPlaceable.placeRelative(0, (height - bodyPlaceable.height) / 2)
            if (showCheckbox) {
                val checkX = ((p - 1f) * slotWidthPx * 0.45f).roundToInt()
                checkPlaceable.placeRelative(checkX, (height - checkPlaceable.height) / 2)
            }
        }
    }
}
