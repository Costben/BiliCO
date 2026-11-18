package erl.bilico.feature.live

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.model.LiveCandidate
import erl.bilico.di.AppContainer
import erl.bilico.feature.video.DanmakuActionCallbacks
import erl.bilico.feature.video.DanmakuOverlay
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.feature.video.DanmakuTimeline
import erl.bilico.feature.video.FIXED_DURATION_MS
import erl.bilico.feature.video.VideoFullscreenStage
import erl.bilico.feature.video.VideoMessageToast
import erl.bilico.feature.video.VideoPlayer
import erl.bilico.feature.video.copyDanmakuToClipboard
import erl.bilico.feature.video.describePlaybackError
import erl.bilico.feature.video.findActivity
import erl.bilico.feature.video.rememberLongPressSpeedController
import erl.bilico.feature.video.rememberPlayerFullscreenController
import erl.bilico.feature.video.rememberStableNavBarBottomDp
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.buildConfiguredExoPlayer
import erl.bilico.player.releasePlaybackPlayer
import erl.bilico.player.rememberPlaybackPlayer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.NavState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveRoomScreen(
    container: AppContainer,
    nav: NavState,
    roomId: Long,
) {
    val viewModel: LiveRoomViewModel = viewModel(key = "live-$roomId") {
        LiveRoomViewModel(container.live, container.client.okHttp, roomId, container.session.mid())
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val advancedPlusOne by container.settings.advancedPlusOne.collectAsStateWithLifecycle()
    val speedLocked by container.settings.speedLocked.collectAsStateWithLifecycle()
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val longPressSpeedPercent by container.settings.longPressSpeed.collectAsStateWithLifecycle()
    val adjustableLongPressSpeed by container.settings.adjustableLongPressSpeed.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val player = rememberPlaybackPlayer("live-$roomId") { buildConfiguredExoPlayer(context) }
    // 页面被弹出（最小化不算）时释放播放器：一台准备好的播放器会占住几十 MB 的加载缓冲。
    val playbackEntry = LocalNavEntry.current
    DisposableEffect(playbackEntry, roomId) {
        playbackEntry?.onDiscard = { releasePlaybackPlayer("live-$roomId") }
        onDispose { }
    }

    // 视频本身的宽高比（宽 / 高），来自解码器上报的首帧尺寸；0 表示还没拿到。
    var videoAspect by remember(roomId) { mutableFloatStateOf(0f) }
    val fsController = rememberPlayerFullscreenController(
        videoAspect = videoAspect,
        autoRotateMode = autoRotateMode,
        inPipMode = inPipMode,
    )
    val fullscreen = fsController.fullscreen
    val deviceSide = fsController.deviceSide
    val applyFullscreen = fsController.applyFullscreen

    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    val longPressSpeed = rememberLongPressSpeedController(
        baseSpeed = speed,
        boostPercent = longPressSpeedPercent,
    )
    val effectiveSpeed = longPressSpeed.effectiveSpeed

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var errorEvent by remember { mutableStateOf<PlaybackException?>(null) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var refetched by remember { mutableIntStateOf(0) }

    val overlayItems = remember { mutableStateListOf<DanmakuItem>() }
    var consumedDanmakuSeq by remember { mutableLongStateOf(0L) }
    var lastOverlayStamp by remember { mutableLongStateOf(0L) }
    var nextSelfDanmakuId by remember { mutableLongStateOf(-1L) }
    var liveMessage by remember { mutableStateOf<String?>(null) }
    if (liveMessage != null) {
        LaunchedEffect(liveMessage) {
            delay(2600)
            liveMessage = null
        }
    }
    // 直播弹幕走自己的时间轴：播放器在 FLV 直播里 currentPosition 会来回跳，
    // 拿它当时间轴会让同一秒进来的弹幕挤在同一时刻、入轨互相顶掉。
    val overlayTimeline = remember {
        DanmakuTimeline().apply { source = { SystemClock.elapsedRealtime() } }
    }

    fun clearDanmakuOverlay() {
        overlayItems.clear()
        consumedDanmakuSeq = viewModel.state.value.danmakuSeq
    }

    fun mediaSourceFor(candidate: LiveCandidate): MediaSource {
        val headers = container.client.mediaRequestHeaders().toMutableMap().apply {
            put("Referer", "https://live.bilibili.com/")
            put("Origin", "https://live.bilibili.com")
        }
        val dataSourceFactory = OkHttpDataSource.Factory(container.client.mediaClient())
            .setDefaultRequestProperties(headers)
        return if (candidate.isHls) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(candidate.url))
        } else {
            ProgressiveMediaSource.Factory(
                dataSourceFactory,
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
            ).createMediaSource(MediaItem.fromUri(candidate.url))
        }
    }

    fun prepareCandidate(candidate: LiveCandidate) {
        playerError = null
        clearDanmakuOverlay()
        player.setMediaSource(mediaSourceFor(candidate))
        player.prepare()
        // 直播从最新位置起播，避免一进房就落后到窗口外。
        player.seekToDefaultPosition()
        player.setPlaybackSpeed(effectiveSpeed)
        player.playWhenReady = true
    }

    DisposableEffect(player) {
        val initialSize = player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            videoAspect = (initialSize.width * initialSize.pixelWidthHeightRatio) / initialSize.height
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) refetched = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                errorEvent = error
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = state.detail?.anchorName.orEmpty(),
        artworkUrl = state.detail?.cover.orEmpty(),
        isPlaying = isPlaying,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
    )

    LaunchedEffect(state.playback, state.playbackGeneration) {
        val playback = state.playback ?: return@LaunchedEffect
        candidateIndex = 0
        prepareCandidate(playback.candidates.first())
    }

    // 换线路：403/404/5xx 或网络错误时按候选表顺延，全试完再重取一次地址（token 可能过期）。
    LaunchedEffect(errorEvent) {
        val error = errorEvent ?: return@LaunchedEffect
        errorEvent = null
        val playback = state.playback
        val failed = playback?.candidates?.getOrNull(candidateIndex)
        Log.w(
            "BiliLive",
            "player error code=${error.errorCode} name=${error.errorCodeName} " +
                "http=${httpStatus(error)} index=$candidateIndex/${playback?.candidates?.size} " +
                "url=${failed?.url?.take(120)}",
            error,
        )
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            clearDanmakuOverlay()
            player.prepare()
            player.seekToDefaultPosition()
            player.playWhenReady = true
            return@LaunchedEffect
        }
        val next = playback?.candidates?.getOrNull(candidateIndex + 1)
        if (next != null) {
            candidateIndex += 1
            prepareCandidate(next)
            return@LaunchedEffect
        }
        if (refetched < 1) {
            refetched += 1
            viewModel.retryPlayback()
            return@LaunchedEffect
        }
        playerError = describePlaybackError(error)
    }

    LaunchedEffect(effectiveSpeed) {
        player.setPlaybackSpeed(effectiveSpeed)
    }

    // 直播弹幕直接按到达时的播放位置入轨，和视频弹幕共用同一套 120Hz 渲染。
    LaunchedEffect(state.danmakuSeq) {
        val pending = state.danmakuSeq - consumedDanmakuSeq
        if (pending <= 0L) return@LaunchedEffect
        val count = pending.coerceAtMost(state.danmaku.size.toLong()).toInt()
        if (count <= 0) return@LaunchedEffect
        // 暂停期间收到的弹幕只留在列表面板里，恢复播放时不会一次性糊满屏幕。
        if (!player.isPlaying) {
            consumedDanmakuSeq = state.danmakuSeq
            return@LaunchedEffect
        }
        val nowMs = overlayTimeline.nowMs.coerceAtLeast(lastOverlayStamp + 1L).toInt()
        lastOverlayStamp = nowMs.toLong()
        val firstId = state.danmakuSeq - count
        state.danmaku.takeLast(count).forEachIndexed { index, message ->
            if (!viewModel.isBlocked(message.mid, message.text)) {
                val medal = if (message.medalName.isNotBlank()) {
                    "${message.medalName} ${message.medalLevel}"
                } else {
                    ""
                }
                overlayItems += DanmakuItem(
                    id = firstId + index,
                    timeMs = nowMs,
                    type = DanmakuType.SCROLL,
                    content = message.text,
                    color = 0xFFFFFF,
                    fontSize = 25,
                    midHash = message.mid.takeIf { it > 0L }?.toString().orEmpty(),
                    senderName = message.name,
                    senderMedal = medal,
                    emotes = message.emotes,
                )
            }
        }
        val retainWindowMs = danmakuSettings.scrollDurationMs.coerceAtLeast(FIXED_DURATION_MS) + 2_000L
        val pruneBeforeMs = (nowMs.toLong() - retainWindowMs).coerceAtLeast(0L)
        while (overlayItems.isNotEmpty() && overlayItems.first().timeMs < pruneBeforeMs) {
            overlayItems.removeAt(0)
        }
        while (overlayItems.size > MAX_OVERLAY_DANMAKU) overlayItems.removeAt(0)
        consumedDanmakuSeq = state.danmakuSeq
    }

    BackHandler(enabled = fullscreen) { applyFullscreen(false) }

    @Composable
    fun RenderLiveDialogs(inlineInStage: Boolean) {
        if (danmakuSettingsDialogVisible) {
            DanmakuSettingsDialog(
                settings = danmakuSettings,
                repository = container.settings,
                onDismiss = { danmakuSettingsDialogVisible = false },
                inlineInStage = inlineInStage,
            )
        }
    }

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = deviceSide,
        inPipMode = inPipMode,
        activity = activity,
        darkMode = darkMode,
        videoAspect = videoAspect,
        playerContent = {
            val stageBackdrop = rememberLayerBackdrop()
            val anyStageDialogOpen = fullscreen && danmakuSettingsDialogVisible
            VideoPlayer(
                player = player,
                title = state.detail?.title.orEmpty(),
                isPlaying = isPlaying,
                positionMs = 0L,
                durationMs = 0L,
                isBuffering = state.isPlaybackLoading || isBuffering,
                errorMessage = state.playbackError ?: playerError,
                qualityLabel = state.qualityLabel(),
                speed = speed,
                fullscreen = fullscreen,
                portraitFullscreen = fullscreen && fsController.portraitStage,
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeek = {},
                onToggleFullscreen = { applyFullscreen(!fullscreen) },
                onFullscreenChange = applyFullscreen,
                hapticEnabled = gestureHaptic,
                volumeBrightnessEnabled = gestureVolBright,
                fullscreenHintEnabled = fullscreenGestureHint,
                qualities = state.playback?.qualities.orEmpty(),
                activeQuality = state.activeQuality,
                onSelectQuality = viewModel::selectQuality,
                speedLocked = speedLocked,
                onToggleSpeedLock = { container.settings.setSpeedLock(!speedLocked, speed) },
                onSelectSpeed = { value ->
                    speed = value
                    if (speedLocked) container.settings.setLockedSpeed(value)
                },
                onBack = {
                    if (fullscreen) applyFullscreen(false) else nav.pop()
                },
                onRetry = viewModel::retryPlayback,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdropIf(BlurEffectsEnabled && anyStageDialogOpen, stageBackdrop),
                inPipMode = inPipMode,
                liveMode = true,
                statusText = state.statusText(),
                danmakuEnabled = danmakuEnabled,
                onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                onDanmakuSettingsClick = { danmakuSettingsDialogVisible = true },
                onEnterPip = if (PlaybackBridge.canEnterPip(activity)) {
                    { PlaybackBridge.enterPipNow(activity) }
                } else {
                    null
                },
                onHome = { nav.popToMain() },
                longPressSpeedActive = longPressSpeed.active,
                longPressSpeedValue = longPressSpeed.speed,
                adjustableLongPressSpeed = adjustableLongPressSpeed,
                onLongPressSpeedChange = longPressSpeed.onActiveChange,
                onLongPressSpeedAdjust = longPressSpeed.onAdjust,
                overlay = {
                    // 弹幕视图按引用判断列表有没有换过：这里必须给一份新列表，
                    // 直接传可变的 SnapshotStateList 会让它永远认为「没变」，直播弹幕一条都画不出来。
                    DanmakuOverlay(
                        player = player,
                        items = overlayItems.toList(),
                        isPlaying = isPlaying,
                        settings = danmakuSettings,
                        timeline = overlayTimeline,
                    )
                },
                danmakuActions = run {
                    fun sendLivePlusOne(text: String, emotes: Map<String, EmoteImage>) {
                        val realRoomId = state.detail?.roomId?.takeIf { it > 0L } ?: roomId
                        if (!container.session.isLoggedIn()) {
                            liveMessage = "请先登录后再操作"
                            return
                        }
                        scope.launch {
                            container.live.sendDanmaku(realRoomId, text, container.session.csrf())
                                .onSuccess {
                                    val stamp = overlayTimeline.nowMs.coerceAtLeast(lastOverlayStamp + 1L)
                                    lastOverlayStamp = stamp
                                    overlayItems += DanmakuItem(
                                        id = nextSelfDanmakuId--,
                                        timeMs = stamp.toInt(),
                                        type = DanmakuType.SCROLL,
                                        content = text,
                                        color = 0xFFFFFF,
                                        fontSize = 25,
                                        isSelf = true,
                                        emotes = emotes,
                                    )
                                    liveMessage = "弹幕已发送"
                                }
                                .onFailure { liveMessage = it.message ?: "发送弹幕失败" }
                        }
                    }
                    DanmakuActionCallbacks(
                        onCopy = { item ->
                            copyDanmakuToClipboard(context, item.content)
                            liveMessage = "已复制弹幕"
                        },
                        onPlusOne = { item -> sendLivePlusOne(item.content, item.emotes) },
                        onAdvancedPlusOne = if (advancedPlusOne) {
                            { item ->
                                val prefix = buildString {
                                    if (item.senderMedal.isNotBlank()) append("[${item.senderMedal}]")
                                    if (item.senderName.isNotBlank()) {
                                        if (isNotEmpty()) append(' ')
                                        append("@${item.senderName}")
                                    }
                                    if (isNotEmpty()) append(": ")
                                }
                                sendLivePlusOne(prefix + item.content, item.emotes)
                            }
                        } else {
                            null
                        },
                        onBlock = { item ->
                            val mid = item.midHash.toLongOrNull() ?: 0L
                            viewModel.blockTemporary(mid, item.content)
                            overlayItems.removeAll { viewModel.isBlocked(it.midHash.toLongOrNull() ?: 0L, it.content) }
                            liveMessage = "已临时屏蔽该弹幕及发送者"
                        },
                    )
                },
            )
            if (fullscreen) {
                VideoMessageToast(message = liveMessage, inPipMode = inPipMode, inFullscreen = true)
                CompositionLocalProvider(
                    LocalBackdrop provides if (BlurEffectsEnabled) stageBackdrop else null,
                ) {
                    RenderLiveDialogs(inlineInStage = true)
                }
            }
        },
        portraitOverlay = {
            VideoMessageToast(message = liveMessage, inPipMode = inPipMode, inFullscreen = false)
        },
        detailContent = {
            val navBarBottom = rememberStableNavBarBottomDp()
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> LoadingState(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )

                    state.error != null -> ErrorState(
                        message = state.error.orEmpty(),
                        modifier = Modifier.weight(1f),
                        onRetry = viewModel::load,
                    )

                    else -> state.detail?.let { detail ->
                        LiveRoomInfo(
                            detail = detail,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        HorizontalDivider(color = COUITheme.colorScheme.dividerLine)
                        LiveDanmakuPanel(
                            messages = state.danmaku,
                            firstSeq = state.danmakuSeq - state.danmaku.size,
                            state = state.danmakuState,
                            error = state.danmakuError,
                            danmakuEnabled = danmakuEnabled,
                            onToggleDanmaku = {
                                container.settings.setDanmakuEnabled(!danmakuEnabled)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(bottom = navBarBottom),
                            onOpenDanmakuSettings = {
                                danmakuSettingsDialogVisible = true
                            },
                        )
                    }
                }
            }
        },
    )

    if (!fullscreen) {
        RenderLiveDialogs(inlineInStage = false)
    }
}

private fun LiveRoomUiState.statusText(): String {
    val detail = detail ?: return ""
    if (!detail.isLive) return "未开播"
    return if (playback == null) "加载中" else "直播中"
}

private fun LiveRoomUiState.qualityLabel(): String? {
    val playback = playback ?: return null
    val label = playback.qualities
        .firstOrNull { it.quality == activeQuality }
        ?.label
        ?.takeIf { it.isNotBlank() }
    return label ?: activeQuality.takeIf { it > 0 }?.let { "${it}P" }
}

/** 从异常链里掏出 HTTP 状态码，日志里能直接看出是 403 还是超时。 */
private fun httpStatus(error: PlaybackException): Int? {
    var cause: Throwable? = error.cause
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}

private const val MAX_OVERLAY_DANMAKU = 600
