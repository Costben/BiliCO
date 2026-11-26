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
import erl.bilico.player.hasPlayIntent

@Composable
internal fun rememberPlaybackScreenOn(player: BiliPlayer): Boolean {
    var active by remember(player) { mutableStateOf(player.hasPlayIntent) }
    DisposableEffect(player) {
        active = player.hasPlayIntent
        val listener = object : BiliPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                active = player.hasPlayIntent || isPlaying
            }

            override fun onPlayIntentChanged(hasPlayIntent: Boolean) {
                active = hasPlayIntent
            }

            override fun onPlaybackStateChanged(state: Int) {
                active = player.playWhenReady && state != PlayerStates.STATE_ENDED && state != PlayerStates.STATE_IDLE
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return active
}

@Composable
internal fun KeepScreenOnEffect(enabled: Boolean) {
    val context = LocalPlatformContext.current
    DisposableEffect(context, enabled) {
        keepScreenOn(context, enabled)
        onDispose { keepScreenOn(context, false) }
    }
}
