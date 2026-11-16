package erl.bilico.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import erl.bilico.ui.LocalRouteIsTop
import erl.bilico.ui.LocalRouteMinimized

val LocalInPipMode = compositionLocalOf { false }

/**
 * 统一管理当前活跃播放器的后台播放、通知栏控制与画中画状态。
 */
object PlaybackBridge {
    const val ACTION_TOGGLE_PLAY = "erl.bilico.action.TOGGLE_PLAY"
    const val ACTION_STOP_BG = "erl.bilico.action.STOP_BG"

    @Volatile
    var activePlayer: Player? = null
        private set

    @Volatile
    var activeTitle: String = ""
        private set

    @Volatile
    var activeSubtitle: String = ""
        private set

    @Volatile
    var activeArtworkUrl: String = ""
        private set

    @Volatile
    var autoPipEnabled: Boolean = true
        private set

    var currentTitle by mutableStateOf("")
        private set

    var currentSubtitle by mutableStateOf("")
        private set

    var currentArtworkUrl by mutableStateOf("")
        private set

    var currentIsPlaying by mutableStateOf(false)
        private set

    var stopVersion by mutableIntStateOf(0)
        private set

    fun bind(
        player: Player,
        title: String,
        subtitle: String,
        artworkUrl: String,
        isPlaying: Boolean,
        autoPip: Boolean,
    ) {
        activePlayer = player
        activeTitle = title
        activeSubtitle = subtitle
        activeArtworkUrl = artworkUrl
        autoPipEnabled = autoPip
        currentTitle = title
        currentSubtitle = subtitle
        currentArtworkUrl = artworkUrl
        currentIsPlaying = isPlaying
    }

    fun unbind(player: Player, context: Context) {
        if (activePlayer === player) {
            activePlayer = null
            activeTitle = ""
            activeSubtitle = ""
            activeArtworkUrl = ""
            currentTitle = ""
            currentSubtitle = ""
            currentArtworkUrl = ""
            currentIsPlaying = false
            PlaybackService.stop(context)
        }
    }

    fun canMinimizeSession(): Boolean {
        val p = activePlayer ?: return false
        return p.playbackState != Player.STATE_IDLE &&
            p.playbackState != Player.STATE_ENDED &&
            (p.isPlaying || p.playWhenReady)
    }

    fun refreshSession(context: Context) {
        PlaybackService.refreshIfRunning(context)
    }

    fun togglePlayPause(context: Context) {
        val p = activePlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
        currentIsPlaying = p.playWhenReady
        PlaybackService.refreshIfRunning(context)
    }

    fun pauseAndStopSession(context: Context) {
        activePlayer?.pause()
        currentIsPlaying = false
        PlaybackService.stop(context)
    }

    fun stopPlayback(context: Context) {
        pauseAndStopSession(context)
        stopVersion++
    }

    fun canEnterPip(activity: Activity?): Boolean {
        if (activity == null) return false
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun buildPipParams(context: Context, isPlaying: Boolean, autoEnter: Boolean): PictureInPictureParams {
        val actionIntent = Intent(ACTION_TOGGLE_PLAY).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context,
            101,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val iconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val label = if (isPlaying) "暂停" else "播放"
        val action = RemoteAction(Icon.createWithResource(context, iconRes), label, label, pending)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(action))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter && isPlaying)
        }
        return builder.build()
    }

    fun enterPipNow(activity: Activity?): Boolean {
        if (!canEnterPip(activity) || activity == null) return false
        val playing = activePlayer?.isPlaying == true
        val params = buildPipParams(activity, playing, autoPipEnabled)
        return runCatching { activity.enterPictureInPictureMode(params) }.getOrDefault(false)
    }

    fun onUserLeaveHint(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (autoPipEnabled && activePlayer?.isPlaying == true && canEnterPip(activity)) {
                enterPipNow(activity)
            }
        }
    }
}

/**
 * 播放器跟着页面走，不跟某一次组合走。
 * 点媒体条封面会让系统重建 Activity，组合里的 remember 会重新建一个播放器并把旧的释放掉，
 * 媒体条还指着旧的，一点就崩。同一个页面的 key 不变，重建后还是原来那个播放器。
 */
@Composable
fun rememberPlaybackPlayer(key: Any, factory: () -> ExoPlayer): ExoPlayer {
    val holder: PlaybackPlayerHolder = viewModel(
        key = "playback-player-$key",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PlaybackPlayerHolder(factory()) as T
            }
        },
    )
    return holder.player
}

private class PlaybackPlayerHolder(val player: ExoPlayer) : ViewModel() {
    override fun onCleared() {
        player.release()
    }
}

fun buildConfiguredExoPlayer(context: Context): ExoPlayer {
    val attrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    return ExoPlayer.Builder(context)
        .setAudioAttributes(attrs, true)
        .setHandleAudioBecomingNoisy(true)
        .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
        .build()
}

@Composable
fun BindPlaybackSession(
    player: Player,
    title: String,
    subtitle: String,
    artworkUrl: String = "",
    isPlaying: Boolean,
    backgroundPlay: Boolean,
    autoPip: Boolean,
) {
    val context = LocalContext.current
    val activity = LocalContext.current.findPipActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTop = LocalRouteIsTop.current
    val isMinimized = LocalRouteMinimized.current
    val activeSession = isTop || isMinimized
    val effectiveAutoPip = autoPip && isTop

    val currentBackgroundPlay = rememberUpdatedState(backgroundPlay)

    LaunchedEffect(activeSession, player, title, subtitle, artworkUrl, isPlaying, effectiveAutoPip) {
        if (!activeSession) return@LaunchedEffect
        PlaybackBridge.bind(player, title, subtitle, artworkUrl, isPlaying, effectiveAutoPip)
        PlaybackService.start(context)
        if (activity != null && PlaybackBridge.canEnterPip(activity)) {
            runCatching {
                activity.setPictureInPictureParams(
                    PlaybackBridge.buildPipParams(activity, isPlaying, effectiveAutoPip),
                )
            }
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val inPip = activity?.isInPictureInPictureMode == true
                    if (!inPip) {
                        if (currentBackgroundPlay.value && player.isPlaying) {
                            PlaybackService.promote(context)
                        } else if (player.isPlaying) {
                            player.pause()
                            PlaybackService.stop(context)
                        }
                    }
                }

                Lifecycle.Event.ON_START -> Unit

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity != null && PlaybackBridge.canEnterPip(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.setPictureInPictureParams(
                        PlaybackBridge.buildPipParams(activity, isPlaying = false, autoEnter = false),
                    )
                }
            }
            PlaybackBridge.unbind(player, context)
        }
    }
}

private fun Context.findPipActivity(): Activity? {
    var cur: Context? = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}
