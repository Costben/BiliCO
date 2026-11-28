package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import erl.bilico.ui.components.COUISmoothCapsuleShape
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 长按倍速的附加手势模式：「可调变速」与「下滑锁定」互斥。 */
internal enum class LongPressSpeedMode {
    FIXED,
    ADJUSTABLE,
    SWIPE_DOWN_LOCK,
}

internal fun resolveLongPressSpeedMode(
    adjustable: Boolean,
    swipeDownLock: Boolean,
): LongPressSpeedMode = when {
    adjustable -> LongPressSpeedMode.ADJUSTABLE
    swipeDownLock -> LongPressSpeedMode.SWIPE_DOWN_LOCK
    else -> LongPressSpeedMode.FIXED
}

internal class LongPressSpeedController(
    val active: Boolean,
    val locked: Boolean,
    val speed: Float,
    val effectiveSpeed: Float,
    val onActiveChange: (Boolean) -> Unit,
    val onAdjust: (Float) -> Unit,
    val onLockChange: (Boolean) -> Unit,
)

@Composable
internal fun rememberLongPressSpeedController(
    baseSpeed: Float,
    boostPercent: Int,
): LongPressSpeedController {
    var active by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var adjustedSpeed by remember { mutableStateOf<Float?>(null) }
    val defaultBoost = boostPercent / 100f
    val activeBoost = if (active) (adjustedSpeed ?: defaultBoost) else defaultBoost
    val effective = if (active) activeBoost else baseSpeed
    return LongPressSpeedController(
        active = active,
        locked = locked,
        speed = activeBoost,
        effectiveSpeed = effective,
        onActiveChange = { next ->
            active = next
            if (!next) {
                locked = false
                adjustedSpeed = null
            } else {
                locked = false
            }
        },
        onAdjust = { adjustedSpeed = it },
        onLockChange = { nextLocked ->
            locked = nextLocked
            if (nextLocked) active = true
        },
    )
}

@Composable
internal fun LongPressSpeedBadge(
    speed: Float,
    mode: LongPressSpeedMode,
    locked: Boolean,
    onUnlock: () -> Unit,
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val label = when {
        locked -> "已锁定 ${speedLabel(speed)} · 长按解锁"
        mode == LongPressSpeedMode.SWIPE_DOWN_LOCK -> "倍速播放中 ${speedLabel(speed)} · 下滑锁定"
        else -> "倍速播放中 ${speedLabel(speed)}"
    }
    Text(
        text = label,
        modifier = modifier
            .padding(top = 14.dp + topInset)
            .clip(COUISmoothCapsuleShape)
            .background(Color(0x55000000))
            .then(if (locked) Modifier.clickable(onClick = onUnlock) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        style = COUITheme.textStyles.footnote1,
        fontWeight = FontWeight.Medium,
        color = Color.White,
    )
}
