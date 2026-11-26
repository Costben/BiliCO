package erl.bilico.feature.video

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import erl.bilico.data.model.FeedVideo
import erl.bilico.di.AppContainer
import erl.bilico.platform.uptimeMillis
import erl.bilico.player.BiliPlayer
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.PlayerStates
import erl.bilico.ui.NavState
import kotlinx.coroutines.delay
import kotlin.math.abs

@Stable
internal class VideoPlaybackClock {
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    private var pendingSeekTargetMs: Long = -1L
    private var pendingSeekDeadlineMs: Long = 0L

    fun seekTo(player: BiliPlayer, targetMs: Long) {
        val clamped = if (durationMs > 0L) targetMs.coerceIn(0L, durationMs) else targetMs.coerceAtLeast(0L)
        positionMs = clamped
        pendingSeekTargetMs = clamped
        pendingSeekDeadlineMs = uptimeMillis() + SEEK_HOLD_WINDOW_MS
        player.seekTo(clamped)
    }

    fun syncPolledPosition(polledMs: Long) {
        val target = pendingSeekTargetMs
        if (target >= 0L) {
            val arrived = abs(polledMs - target) <= SEEK_SETTLE_TOLERANCE_MS
            val expired = uptimeMillis() >= pendingSeekDeadlineMs
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

@Composable
internal fun VideoPlaybackEffects(
    player: BiliPlayer,
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
    onError: (String) -> Unit,
    onClearError: () -> Unit,
    onVideoAspectChanged: (Float) -> Unit,
    onPromptAutoNextRecommend: ((FeedVideo) -> Unit)? = null,
    onCancelAutoNext: (() -> Unit)? = null,
) {
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by container.settings.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sleepTimerTriggerVersion by container.sleepTimer.triggerVersion.collectAsStateWithLifecycle()
    val currentIsTop by rememberUpdatedState(isTop)
    val headers = remember(bvid) { container.client.mediaRequestHeaders(bvid) }

    var lastSleepTrigger by remember { mutableStateOf(sleepTimerTriggerVersion) }
    LaunchedEffect(sleepTimerTriggerVersion) {
        if (sleepTimerTriggerVersion > lastSleepTrigger) {
            lastSleepTrigger = sleepTimerTriggerVersion
            onCancelAutoNext?.invoke()
            if (player.isPlaying) player.pause()
        }
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
                if (container.sleepTimer.consumeAfterCurrentVideo(player)) {
                    onCancelAutoNext?.invoke()
                } else {
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
                }
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

    LaunchedEffect(player, bvid, isTop) {
        if (!isTop) return@LaunchedEffect
        if (PlaybackBridge.consumeRouteSwitchMute(player)) {
            player.stop()
            player.clearMediaItems()
            PlaybackBridge.activeMediaId = ""
        }
    }

    var loadedCid by remember(bvid) { mutableStateOf<Long?>(null) }
    LaunchedEffect(player, bvid, state.source, isTop) {
        if (!isTop) return@LaunchedEffect
        val source = state.source ?: return@LaunchedEffect
        val currentAttempts = attempts ?: return@LaunchedEffect
        val mediaId = playbackMediaId(bvid, state.selectedCid, source.videoUrl)
        if (player.playbackState != PlayerStates.STATE_IDLE &&
            PlaybackBridge.activeMediaId == mediaId
        ) {
            loadedCid = state.selectedCid
            currentAttempts.adopt(source, headers, mediaId)
            return@LaunchedEffect
        }
        if (state.resumePositionMs <= 0L && source.lastPlayTimeMs >= 3_000L) {
            viewModel.applyServerResumeHint(source.lastPlayCid, source.lastPlayTimeMs)
        }
        val resumeAt = viewModel.state.value.resumePositionMs
        val shouldPlay = when {
            loadedCid == null || player.playbackState == PlayerStates.STATE_IDLE ->
                container.settings.detailAutoPlay.value
            loadedCid != state.selectedCid && player.playbackState == PlayerStates.STATE_ENDED ->
                true
            else -> player.playWhenReady
        }
        loadedCid = state.selectedCid
        onClearError()
        PlaybackBridge.activeMediaId = mediaId
        currentAttempts.start(source, headers, resumeAt, mediaId, playWhenReady = shouldPlay)
        if (resumeAt > 0L) viewModel.consumeResumePosition()
    }

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
