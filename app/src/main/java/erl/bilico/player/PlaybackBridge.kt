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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    private var activeOwner: Any? = null

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
        owner: Any? = null,
    ) {
        activeOwner = owner
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

    fun isOwner(owner: Any): Boolean = activeOwner === owner

    fun unbind(player: Player, context: Context, owner: Any? = null) {
        if (activePlayer === player && (owner == null || activeOwner === owner)) {
            detach(player, owner)
            PlaybackService.stop(context)
        }
    }

    /** 播放器要被释放了：先把它从桥上下掉，免得媒体条还指着一台已经销毁的播放器。 */
    internal fun detach(player: Player, owner: Any? = null) {
        if (activePlayer !== player) return
        if (owner != null && activeOwner !== owner) return
        activeOwner = null
        activePlayer = null
        activeTitle = ""
        activeSubtitle = ""
        activeArtworkUrl = ""
        currentTitle = ""
        currentSubtitle = ""
        currentArtworkUrl = ""
        currentIsPlaying = false
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
 *
 * 释放由页面自己负责（`NavEntry.onDiscard` → [releasePlaybackPlayer]）：一台准备好的播放器
 * 会在堆上占住几十 MB 的加载缓冲，只看不释放的话，每看一个视频就留下一台，几分钟就把堆吃满。
 */
@Composable
fun rememberPlaybackPlayer(key: Any, factory: () -> ExoPlayer): ExoPlayer {
    val registryKey = key.toString()
    val holder: PlaybackPlayerHolder = viewModel(
        key = "playback-player-$registryKey",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PlaybackPlayerHolder(registryKey, factory) as T
            }
        },
    )
    // 连播下一条时同一页换了 key：上一台播放器不会再被用到，立刻放掉。
    val lastKey = remember { arrayOfNulls<String>(1) }
    SideEffect {
        val previous = lastKey[0]
        if (previous != null && previous != registryKey) releasePlaybackPlayer(previous)
        lastKey[0] = registryKey
    }
    DisposableEffect(holder, registryKey) {
        playerRegistry[registryKey] = holder
        onDispose { }
    }
    // 只有真正进入这一页（首次组合或换 key）才去取播放器：退场那几帧的重组不复活已经释放的那台。
    return remember(holder, registryKey) { holder.obtain() }
}

/** 页面从栈里被丢掉（不是最小化）时调用：释放播放器，下次点进同一页再建一台。 */
fun releasePlaybackPlayer(key: Any) {
    playerRegistry.remove(key.toString())?.release()
}

private val playerRegistry = HashMap<String, PlaybackPlayerHolder>()

private class PlaybackPlayerHolder(
    private val registryKey: String,
    private val factory: () -> ExoPlayer,
) : ViewModel() {
    private var active: ExoPlayer = factory()
    private var released = false

    /** 取当前这台；被释放过就换一台新的。 */
    fun obtain(): ExoPlayer {
        if (released) {
            released = false
            active = factory()
        }
        return active
    }

    fun release() {
        if (released) return
        released = true
        PlaybackBridge.detach(active)
        active.release()
    }

    override fun onCleared() {
        release()
        playerRegistry.remove(registryKey)
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

    val sessionOwner = remember { Any() }
    val routeEntry = erl.bilico.ui.LocalNavEntry.current
    val navState = erl.bilico.ui.LocalNavState.current
    val currentBackgroundPlay = rememberUpdatedState(backgroundPlay)

    LaunchedEffect(activeSession, player, title, subtitle, artworkUrl, isPlaying, effectiveAutoPip) {
        if (!activeSession) return@LaunchedEffect
        PlaybackBridge.bind(
            player = player,
            title = title,
            subtitle = subtitle,
            artworkUrl = artworkUrl,
            isPlaying = isPlaying,
            autoPip = effectiveAutoPip,
            owner = sessionOwner,
        )
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
                    if (!PlaybackBridge.isOwner(sessionOwner)) return@LifecycleEventObserver
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
            // 收进融合球的那一页动画期间会被拆掉重建，重建出来的一版 stageReady 归零、播放器槽位消失，
            // 于是这一次解绑发生在「球已经接手」之后。此时清掉桥，球就会变成无封面 + 暂停图标，
            // 菜单里的「继续播放」也因为拿不到播放器点不动。收进球里的页面仍在，绑定交给它新的一版。
            val entry = routeEntry
            if (entry != null && (entry.minimizingToOrb || navState?.minimizedEntry === entry)) {
                return@onDispose
            }
            // 多层播放页共享同一台 ExoPlayer：只有当前会话真正拥有者被销毁时才解绑并清画中画参数，
            // 否则中间历史播放页出栈时会把正在最小化播放的栈顶视频封面与播放器句柄一起抹掉。
            if (!PlaybackBridge.isOwner(sessionOwner)) return@onDispose
            if (activity != null && PlaybackBridge.canEnterPip(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.setPictureInPictureParams(
                        PlaybackBridge.buildPipParams(activity, isPlaying = false, autoEnter = false),
                    )
                }
            }
            PlaybackBridge.unbind(player, context, owner = sessionOwner)
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
