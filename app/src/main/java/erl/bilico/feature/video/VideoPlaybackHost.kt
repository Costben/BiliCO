package erl.bilico.feature.video

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import erl.bilico.data.model.FeedVideo
import erl.bilico.di.AppContainer
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.PlaybackBridge
import erl.bilico.ui.NavState
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 播放进度只在这里存一份。页面自己绝不读这两个值，只把实例交给真正要画进度的子组件：
 * 每 500ms 写一次状态，读的人越少，跟着重排重绘的范围就越小。
 */
@Stable
internal class VideoPlaybackClock {
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    private var pendingSeekTargetMs: Long = -1L
    private var pendingSeekDeadlineMs: Long = 0L

    /** 主动跳转时立即同步时钟，并在 ExoPlayer 落稳前屏蔽轮询回写旧位置，防止进度条松手回弹。 */
    fun seekTo(player: ExoPlayer, targetMs: Long) {
        val clamped = if (durationMs > 0L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        positionMs = clamped
        pendingSeekTargetMs = clamped
        pendingSeekDeadlineMs = SystemClock.uptimeMillis() + SEEK_HOLD_WINDOW_MS
        player.seekTo(clamped)
    }

    fun syncPolledPosition(polledMs: Long) {
        val target = pendingSeekTargetMs
        if (target >= 0L) {
            val arrived = abs(polledMs - target) <= SEEK_SETTLE_TOLERANCE_MS
            val expired = SystemClock.uptimeMillis() >= pendingSeekDeadlineMs
            if (!arrived && !expired) return
            pendingSeekTargetMs = -1L
        }
        positionMs = polledMs
    }

    fun reset() {
        positionMs = 0L
        durationMs = 0L
        bufferedMs = 0L
        pendingSeekTargetMs = -1L
        pendingSeekDeadlineMs = 0L
    }

    private companion object {
        const val SEEK_HOLD_WINDOW_MS = 1200L
        const val SEEK_SETTLE_TOLERANCE_MS = 1500L
    }
}

/**
 * 播放器会话、片源装载与进度轮询。只在 `player != null` 时组合：
 * 进视频页的展开动画期间播放器还没挂上，这些订阅与副作用也就还没开始跑。
 */
@Composable
internal fun VideoPlaybackEffects(
    player: ExoPlayer,
    bvid: String,
    isTop: Boolean,
    isActiveRoute: Boolean,
    isPlaying: Boolean,
    state: VideoUiState,
    viewModel: VideoViewModel,
    container: AppContainer,
    nav: NavState,
    clock: VideoPlaybackClock,
    attempts: PlaybackAttempts?,
    effectiveSpeed: Float,
    danmakuController: DanmakuController,
    sponsorController: SponsorBlockController,
    onPlayingChanged: (Boolean) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onPlayIntentChanged: (Boolean) -> Unit,
    onError: (PlaybackException) -> Unit,
    onClearError: () -> Unit,
    onVideoAspectChanged: (Float) -> Unit,
    onPromptAutoNextRecommend: ((FeedVideo) -> Unit)? = null,
    onCancelAutoNext: (() -> Unit)? = null,
) {
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by container.settings.sponsorBlockEnabled.collectAsStateWithLifecycle()
    // 轮询协程不随 isTop 重启，读的是最新值。
    val currentIsTop by rememberUpdatedState(isTop)
    // 片源请求头与数据源只在这里构造：调用方只有拿到播放器才会组合进来，不进场就不碰 OkHttp。
    val context = LocalContext.current
    val headers = remember(bvid) { container.client.mediaRequestHeaders(bvid) }
    val dataSourceFactory = remember(context, headers) {
        val httpFactory = OkHttpDataSource.Factory(container.client.mediaClient())
            .setDefaultRequestProperties(headers)
        DefaultDataSource.Factory(context, httpFactory)
    }

    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = state.detail?.upName.orEmpty(),
        artworkUrl = state.detail?.cover.orEmpty(),
        isPlaying = isPlaying,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
    )

    if (isActiveRoute) {
        BindPlayerEvents(
            player = player,
            viewModel = viewModel,
            container = container,
            onPlayingChanged = onPlayingChanged,
            onBufferingChanged = onBufferingChanged,
            onPlayIntentChanged = onPlayIntentChanged,
            onError = onError,
            onVideoAspectChanged = onVideoAspectChanged,
            onPlaybackEnded = {
                handleVideoPlaybackEnded(
                    mode = playbackEndMode,
                    player = player,
                    viewModel = viewModel,
                    nav = nav,
                    state = state,
                    bvid = bvid,
                    blocks = container.blocks.snapshot.value,
                    onPromptAutoNextRecommend = onPromptAutoNextRecommend,
                )
            },
            onPlaybackLeaveEnded = onCancelAutoNext,
        )
    }

    LaunchedEffect(player, bvid, state.selectedCid, sponsorBlockEnabled, isActiveRoute) {
        if (isActiveRoute) {
            sponsorController.attach(bvid, state.selectedCid, sponsorBlockEnabled, player)
        }
    }

    DisposableEffect(player) {
        onDispose { sponsorController.restoreMuteIfNeeded(player) }
    }

    // 带球换到下一条：先收尾上一次的静音（恢复音量、停掉旧片源），再让下面这个副作用装载新片源。
    // 必须排在装载之前：两个 effect 同一帧启动，谁先声明谁先跑完。
    LaunchedEffect(player, bvid, isTop) {
        if (!isTop) return@LaunchedEffect
        if (PlaybackBridge.consumeRouteSwitchMute(player)) {
            player.stop()
            player.clearMediaItems()
        }
    }

    LaunchedEffect(player, bvid, state.source, isTop) {
        // 只让栈顶这一页往共享播放器里装片源：压在下面的播放页还留在组合里，
        // 一旦它们也跟着状态变化重新装载，就会把正在播的那条顶掉、从头再来一遍。
        if (!isTop) return@LaunchedEffect
        val source = state.source ?: return@LaunchedEffect
        val currentAttempts = attempts ?: return@LaunchedEffect
        val mediaId = playbackMediaId(bvid, state.selectedCid, source.videoUrl)
        // 缩进融合球、再从球里回来时这一页会被重建一次，播放器本身没换；
        // 此时再 setMediaSource 一次就会从 0 重新播。片源没变且播放器还装着它，就只登记候选表。
        if (player.playbackState != Player.STATE_IDLE &&
            player.currentMediaItem?.mediaId == mediaId
        ) {
            currentAttempts.adopt(source, dataSourceFactory, mediaId)
            return@LaunchedEffect
        }
        if (state.resumePositionMs <= 0L && source.lastPlayTimeMs >= 3_000L) {
            viewModel.applyServerResumeHint(source.lastPlayCid, source.lastPlayTimeMs)
        }
        val resumeAt = viewModel.state.value.resumePositionMs
        // 换线之前先把上一条的错误清掉：不清的话，换线成功也一直挂着错误卡片。
        onClearError()
        currentAttempts.start(source, dataSourceFactory, resumeAt, mediaId)
        if (resumeAt > 0L) viewModel.consumeResumePosition()
    }

    // 切号 / 登录态变了：按当前清晰度重新取流，让刚生效的大会员档位回到清晰度菜单里。
    // 首帧拿到的登录态不重取，免得一进页面就白闪一下。
    val playbackAccount by container.session.account.collectAsStateWithLifecycle()
    var playbackAccountKey by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    LaunchedEffect(playbackAccount, isTop) {
        val key = playbackAccount?.let { it.mid to it.vip }
        val previous = playbackAccountKey
        playbackAccountKey = key
        if (isTop && previous != null && previous != key) viewModel.refreshPlayback(clock.positionMs)
    }

    LaunchedEffect(player, effectiveSpeed, isTop) {
        if (isTop) player.setPlaybackSpeed(effectiveSpeed)
    }

    LaunchedEffect(player, isActiveRoute) {
        if (!isActiveRoute) return@LaunchedEffect
        while (true) {
            val position = player.currentPosition.coerceAtLeast(0L)
            attempts?.rememberPosition(position)
            viewModel.reportHeartbeat(position / 1000, player.isPlaying)
            sponsorController.onPosition(
                positionMs = position,
                player = player,
                enabled = container.settings.sponsorBlockEnabled.value,
                skipMode = container.settings.sponsorSkipMode.value,
                enabledCategories = container.settings.sponsorCategories.value,
            )
            // 收进融合球之后界面不画进度，别再让每 500ms 的一拍去动 UI 状态。
            if (currentIsTop) {
                clock.syncPolledPosition(position)
                clock.durationMs = player.duration.coerceAtLeast(0L)
                clock.bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
                danmakuController.onPosition(clock.positionMs)
            }
            delay(500)
        }
    }
}
