package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player

/** 播放器处于主动播放意图（含缓冲与切集加载，排除暂停与播完）。 */
internal fun Player.hasPlayIntent(): Boolean =
    playWhenReady && playbackState != Player.STATE_ENDED

/** 前台未受抑制的主动播放意图：用于维持屏幕常亮。 */
internal fun Player.hasActivePlayIntent(): Boolean =
    hasPlayIntent() && playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

/** 监听播放器播放意图变化；后台、画中画或未处于主动播放时释放屏幕常亮。 */
@Composable
internal fun rememberPlaybackScreenOn(player: Player): Boolean {
    var keepScreenOn by remember(player) { mutableStateOf(player.hasActivePlayIntent()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                keepScreenOn = player.hasActivePlayIntent()
            }
        }
        player.addListener(listener)
        keepScreenOn = player.hasActivePlayIntent()
        onDispose { player.removeListener(listener) }
    }
    return keepScreenOn
}
