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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

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
    var autoPipEnabled: Boolean = true
        private set

    fun bind(
        player: Player,
        title: String,
        subtitle: String,
        autoPip: Boolean,
    ) {
        activePlayer = player
        activeTitle = title
        activeSubtitle = subtitle
        autoPipEnabled = autoPip
    }

    fun unbind(player: Player, context: Context) {
        if (activePlayer === player) {
            activePlayer = null
            activeTitle = ""
            activeSubtitle = ""
            PlaybackService.stop(context)
        }
    }

    fun togglePlayPause(context: Context) {
        val p = activePlayer ?: return
        if (p.isPlaying) p.pause() else p.play()
        PlaybackService.refreshIfRunning(context)
    }

    fun stopPlayback(context: Context) {
        activePlayer?.pause()
        PlaybackService.stop(context)
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

fun buildConfiguredExoPlayer(context: Context): ExoPlayer {
    val attrs = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    return ExoPlayer.Builder(context)
        .setAudioAttributes(attrs, true)
        .setHandleAudioBecomingNoisy(true)
        .build()
}

@Composable
fun BindPlaybackSession(
    player: Player,
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    backgroundPlay: Boolean,
    autoPip: Boolean,
) {
    val context = LocalContext.current
    val activity = LocalContext.current.findPipActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentBackgroundPlay = rememberUpdatedState(backgroundPlay)
    val currentTitle = rememberUpdatedState(title)
    val currentSubtitle = rememberUpdatedState(subtitle)

    LaunchedEffect(player, title, subtitle, isPlaying, autoPip) {
        PlaybackBridge.bind(player, title, subtitle, autoPip)
        if (activity != null && PlaybackBridge.canEnterPip(activity)) {
            runCatching {
                activity.setPictureInPictureParams(
                    PlaybackBridge.buildPipParams(activity, isPlaying, autoPip),
                )
            }
        }
        PlaybackService.refreshIfRunning(context)
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val inPip = activity?.isInPictureInPictureMode == true
                    if (!inPip) {
                        if (currentBackgroundPlay.value && player.isPlaying) {
                            PlaybackService.start(
                                context = context,
                                title = currentTitle.value.ifBlank { "BiliCO 正在播放" },
                                subtitle = currentSubtitle.value,
                            )
                        } else if (!currentBackgroundPlay.value && player.isPlaying) {
                            player.pause()
                        }
                    }
                }

                Lifecycle.Event.ON_START -> {
                    PlaybackService.stop(context)
                }

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
