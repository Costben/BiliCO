package erl.bilico.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.keepScreenOn
import erl.bilico.player.BiliPlayer
import erl.bilico.player.BiliPlayerListener
import erl.bilico.player.PlayerStates

private fun BiliPlayer.hasActivePlaybackForScreenOn(): Boolean =
    isPlaying || (playWhenReady && playbackState != PlayerStates.STATE_ENDED && playbackState != PlayerStates.STATE_IDLE)

@Composable
internal fun rememberPlaybackScreenOn(player: BiliPlayer): Boolean {
    var active by remember(player) { mutableStateOf(player.hasActivePlaybackForScreenOn()) }
    DisposableEffect(player) {
        active = player.hasActivePlaybackForScreenOn()
        val listener = object : BiliPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                active = player.hasActivePlaybackForScreenOn()
            }

            override fun onPlayIntentChanged(hasPlayIntent: Boolean) {
                active = player.hasActivePlaybackForScreenOn()
            }

            override fun onPlaybackStateChanged(state: Int) {
                active = player.hasActivePlaybackForScreenOn()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return active
}

private var keepScreenOnHolders = 0

@Composable
internal fun KeepScreenOnEffect(enabled: Boolean) {
    val context = LocalPlatformContext.current
    DisposableEffect(context, enabled) {
        if (enabled) {
            if (keepScreenOnHolders++ == 0) keepScreenOn(context, true)
        }
        onDispose {
            if (enabled) {
                keepScreenOnHolders = (keepScreenOnHolders - 1).coerceAtLeast(0)
                if (keepScreenOnHolders == 0) keepScreenOn(context, false)
            }
        }
    }
}
