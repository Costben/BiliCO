package erl.bilico.feature.comment

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

private const val SORT_FADE_OUT_MS = 110
private const val SORT_FADE_IN_MS = 150

@Stable
internal class SortFadeState<T>(
    val currentItems: List<T>,
    val itemAlpha: () -> Float,
)

/**
 * 切换评论/楼中楼排序时的顺序淡出淡入：
 * 新排序数据就绪后，先将旧列表淡出（1f -> 0f），再替换数据并淡入新列表（0f -> 1f），
 * 避免新旧两层卡片叠加导致缝隙消失与画面亮度突增。
 */
@Composable
internal fun <T, S> rememberSortFadeTransition(
    renderedSort: S,
    items: List<T>,
    resetKey: Any = Unit,
): SortFadeState<T> {
    var displayedSort by remember(resetKey) { mutableStateOf(renderedSort) }
    var displayedItems by remember(resetKey) { mutableStateOf(items) }
    val alphaAnim = remember(resetKey) { Animatable(1f) }
    val latestItems by rememberUpdatedState(items)

    LaunchedEffect(items, renderedSort, resetKey) {
        if (displayedSort == renderedSort) {
            displayedItems = items
        }
    }

    LaunchedEffect(renderedSort, resetKey) {
        if (displayedSort == renderedSort) {
            displayedItems = latestItems
            if (alphaAnim.value < 1f) {
                val inMs = (SORT_FADE_IN_MS * (1f - alphaAnim.value)).toInt().coerceAtLeast(1)
                alphaAnim.animateTo(1f, tween(durationMillis = inMs, easing = FastOutSlowInEasing))
            }
            return@LaunchedEffect
        }
        val currentAlpha = alphaAnim.value
        if (displayedItems.isNotEmpty() && currentAlpha > 0.01f) {
            val outMs = (SORT_FADE_OUT_MS * currentAlpha).toInt().coerceAtLeast(1)
            alphaAnim.animateTo(0f, tween(durationMillis = outMs, easing = FastOutSlowInEasing))
        } else {
            alphaAnim.snapTo(0f)
        }
        displayedSort = renderedSort
        displayedItems = latestItems
        alphaAnim.animateTo(1f, tween(durationMillis = SORT_FADE_IN_MS, easing = FastOutSlowInEasing))
    }

    return SortFadeState(
        currentItems = displayedItems,
        itemAlpha = { alphaAnim.value },
    )
}
