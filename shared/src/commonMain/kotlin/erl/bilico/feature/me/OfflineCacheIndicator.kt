package erl.bilico.feature.me

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

private enum class CacheIndicatorPhase {
    IDLE_ARROW,
    DOWNLOADING,
    COMPLETING,
    COMPLETED,
}

/**
 * 「我的」页离线缓存行右侧状态槽（方案 B：真实进度环 → 勾号一闪 → 淡切为已缓存数量）。
 *
 * 外层槽宽固定为 `12.dp x 24.dp`（与 COUI [ArrowPreference] 右侧箭头槽完全一致），
 * 内部 `24.dp` 进度环与完成数字均以该槽中心为锚点绘制，确保与上下行 `>` 箭头严格同轴居中对齐。
 */
@Composable
internal fun RowScope.OfflineCacheEndAction(
    activeCount: Int,
    completedCount: Int,
    progress: Float,
) {
    val scheme = COUITheme.colorScheme
    val arrowTint = remember(scheme.onSurfaceVariantActions) {
        ColorFilter.tint(scheme.onSurfaceVariantActions)
    }

    var phase by remember {
        mutableStateOf(
            when {
                activeCount > 0 -> CacheIndicatorPhase.DOWNLOADING
                completedCount > 0 -> CacheIndicatorPhase.COMPLETED
                else -> CacheIndicatorPhase.IDLE_ARROW
            },
        )
    }
    var displayActiveCount by remember { mutableIntStateOf(activeCount.coerceAtLeast(1)) }
    if (activeCount > 0) {
        displayActiveCount = activeCount
    }

    val ringSweep = remember { Animatable(if (activeCount > 0) progress.coerceIn(0f, 1f) else 0f) }
    val ringAlpha = remember { Animatable(if (activeCount > 0) 1f else 0f) }
    val ringScale = remember { Animatable(1f) }
    val activeNumAlpha = remember { Animatable(if (activeCount > 0) 1f else 0f) }
    val activeNumScale = remember { Animatable(1f) }
    val checkProgress = remember { Animatable(0f) }
    val checkAlpha = remember { Animatable(0f) }
    val doneAlpha = remember { Animatable(if (activeCount == 0 && completedCount > 0) 1f else 0f) }
    val doneScale = remember { Animatable(1f) }

    val hasActive = activeCount > 0
    val hasCompleted = completedCount > 0

    // 下载中实时平滑推进真实字节进度
    LaunchedEffect(phase, progress) {
        if (phase == CacheIndicatorPhase.DOWNLOADING) {
            ringSweep.animateTo(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
            )
        }
    }

    // 状态切换与方案 B 完成动画（满圈 → 画勾停留 → 缩环淡出并弹出已缓存数）
    LaunchedEffect(hasActive, hasCompleted) {
        when {
            hasActive -> {
                phase = CacheIndicatorPhase.DOWNLOADING
                ringAlpha.snapTo(1f)
                ringScale.snapTo(1f)
                activeNumAlpha.snapTo(1f)
                activeNumScale.snapTo(1f)
                checkAlpha.snapTo(0f)
                checkProgress.snapTo(0f)
                doneAlpha.snapTo(0f)
                doneScale.snapTo(0.7f)
            }

            hasCompleted && phase == CacheIndicatorPhase.DOWNLOADING -> {
                phase = CacheIndicatorPhase.COMPLETING
                coroutineScope {
                    launch {
                        ringSweep.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                        )
                    }
                    launch {
                        activeNumAlpha.animateTo(0f, tween(durationMillis = 150))
                    }
                    launch {
                        activeNumScale.animateTo(0.5f, tween(durationMillis = 150))
                    }
                }

                checkAlpha.snapTo(1f)
                checkProgress.snapTo(0f)
                checkProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                )
                delay(420L)

                coroutineScope {
                    launch {
                        ringScale.animateTo(
                            targetValue = 0.65f,
                            animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing),
                        )
                    }
                    launch {
                        ringAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing),
                        )
                    }
                    launch {
                        checkAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing),
                        )
                    }
                    launch {
                        delay(110L)
                        doneScale.snapTo(0.7f)
                        coroutineScope {
                            launch {
                                doneAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                )
                            }
                            launch {
                                doneScale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = keyframes {
                                        durationMillis = 280
                                        0.7f at 0 using FastOutSlowInEasing
                                        1.08f at 196 using FastOutSlowInEasing
                                        1f at 280
                                    },
                                )
                            }
                        }
                    }
                }
                phase = CacheIndicatorPhase.COMPLETED
            }

            hasCompleted -> {
                phase = CacheIndicatorPhase.COMPLETED
                ringAlpha.snapTo(0f)
                ringScale.snapTo(1f)
                activeNumAlpha.snapTo(0f)
                checkAlpha.snapTo(0f)
                checkProgress.snapTo(0f)
                doneAlpha.snapTo(1f)
                doneScale.snapTo(1f)
            }

            else -> {
                phase = CacheIndicatorPhase.IDLE_ARROW
                ringAlpha.snapTo(0f)
                activeNumAlpha.snapTo(0f)
                checkAlpha.snapTo(0f)
                doneAlpha.snapTo(0f)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(width = 12.dp, height = 24.dp)
            .align(Alignment.CenterVertically),
        contentAlignment = Alignment.Center,
    ) {
        if (phase == CacheIndicatorPhase.IDLE_ARROW) {
            Image(
                imageVector = COUIIcons.Basic.ArrowRight,
                contentDescription = null,
                colorFilter = arrowTint,
                modifier = Modifier.size(width = 12.dp, height = 24.dp),
            )
            return@Box
        }

        // 24dp 真实进度环 + 中心下载数 + 完成勾号
        if (ringAlpha.value > 0.01f || checkAlpha.value > 0.01f || activeNumAlpha.value > 0.01f) {
            val trackColor = scheme.onSurface.copy(alpha = 0.12f)
            val primaryColor = scheme.primary
            val sweep = if (phase == CacheIndicatorPhase.DOWNLOADING) {
                (ringSweep.value * 360f).coerceIn(10f, 360f)
            } else {
                (ringSweep.value * 360f).coerceIn(0f, 360f)
            }

            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.Center, unbounded = true)
                    .size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (ringAlpha.value > 0.01f) {
                    Canvas(
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                alpha = ringAlpha.value
                                scaleX = ringScale.value
                                scaleY = ringScale.value
                            },
                    ) {
                        val strokePx = 2.2.dp.toPx()
                        val inset = strokePx / 2f + 1.dp.toPx()
                        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                        val topLeft = Offset(inset, inset)
                        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                    }
                }

                if (activeNumAlpha.value > 0.01f) {
                    Text(
                        text = displayActiveCount.toString(),
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            alpha = activeNumAlpha.value
                            scaleX = activeNumScale.value
                            scaleY = activeNumScale.value
                        },
                    )
                }

                if (checkAlpha.value > 0.01f && checkProgress.value > 0.01f) {
                    Canvas(
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                alpha = checkAlpha.value
                                scaleX = ringScale.value
                                scaleY = ringScale.value
                            },
                    ) {
                        val w = size.width
                        val h = size.height
                        val p0 = Offset(w * 0.28f, h * 0.51f)
                        val p1 = Offset(w * 0.44f, h * 0.67f)
                        val p2 = Offset(w * 0.73f, h * 0.36f)
                        val len1 = hypot(p1.x - p0.x, p1.y - p0.y)
                        val len2 = hypot(p2.x - p1.x, p2.y - p1.y)
                        val split = len1 / (len1 + len2)
                        val t = checkProgress.value.coerceIn(0f, 1f)
                        val path = Path().apply {
                            moveTo(p0.x, p0.y)
                            if (t <= split) {
                                val f = (t / split).coerceIn(0f, 1f)
                                lineTo(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f)
                            } else {
                                lineTo(p1.x, p1.y)
                                val f = ((t - split) / (1f - split)).coerceIn(0f, 1f)
                                lineTo(p1.x + (p2.x - p1.x) * f, p1.y + (p2.y - p1.y) * f)
                            }
                        }
                        drawPath(
                            path = path,
                            color = primaryColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
        }

        // 已缓存数量（与上下箭头严格同轴居中）
        if (doneAlpha.value > 0.01f && completedCount > 0) {
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.Center, unbounded = true)
                    .graphicsLayer {
                        alpha = doneAlpha.value
                        scaleX = doneScale.value
                        scaleY = doneScale.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = completedCount.toString(),
                    style = COUITheme.textStyles.body2,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
