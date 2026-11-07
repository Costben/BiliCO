package erl.bilico.feature.live

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import coil3.compose.AsyncImage
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.model.DanmakuType
import erl.bilico.data.model.LiveCandidate
import erl.bilico.data.model.LiveRoomDetail
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.video.DanmakuOverlay
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.feature.video.MANUAL_FULLSCREEN_COOLDOWN_MS
import erl.bilico.feature.video.QualityDialog
import erl.bilico.feature.video.SpeedDialog
import erl.bilico.feature.video.VideoFullscreenStage
import erl.bilico.feature.video.VideoPlayer
import erl.bilico.feature.video.describePlaybackError
import erl.bilico.feature.video.findActivity
import erl.bilico.feature.video.rememberDeviceOrientationSide
import erl.bilico.feature.video.rememberStableNavBarBottomDp
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.buildConfiguredExoPlayer
import erl.bilico.ui.NavState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = remember { buildConfiguredExoPlayer(context) }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val deviceSide = rememberDeviceOrientationSide(autoRotateMode)
    var manualFullscreenExit by rememberSaveable { mutableStateOf(false) }
    var lastManualActionTime by remember { mutableLongStateOf(0L) }
    var manualEnterAwaitingLandscape by remember { mutableStateOf(false) }
    val applyFullscreen: (Boolean) -> Unit = { value ->
        lastManualActionTime = SystemClock.uptimeMillis()
        if (value) {
            manualFullscreenExit = false
            manualEnterAwaitingLandscape = deviceSide == 0
            fullscreen = true
        } else {
            manualFullscreenExit = true
            manualEnterAwaitingLandscape = false
            fullscreen = false
        }
    }

    var qualityDialogVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }
    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var longPressSpeed by remember { mutableStateOf(false) }
    val boostSpeed = if (speed >= 2f) 3f else 2f
    val effectiveSpeed = if (longPressSpeed) boostSpeed else speed

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var errorEvent by remember { mutableStateOf<PlaybackException?>(null) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var refetched by remember { mutableIntStateOf(0) }

    val overlayItems = remember { mutableStateListOf<DanmakuItem>() }
    var consumedDanmakuSeq by remember { mutableLongStateOf(0L) }

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
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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
            player.release()
        }
    }

    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = state.detail?.anchorName.orEmpty(),
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
        val nowMs = player.currentPosition.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val firstId = state.danmakuSeq - count
        state.danmaku.takeLast(count).forEachIndexed { index, message ->
            overlayItems += DanmakuItem(
                id = firstId + index,
                timeMs = nowMs,
                type = DanmakuType.SCROLL,
                content = message.text,
                color = 0xFFFFFF,
                fontSize = 25,
            )
        }
        while (overlayItems.size > MAX_OVERLAY_DANMAKU) overlayItems.removeAt(0)
        consumedDanmakuSeq = state.danmakuSeq
    }

    var wasLandscape by remember { mutableStateOf(deviceSide != 0) }
    LaunchedEffect(deviceSide, manualFullscreenExit, manualEnterAwaitingLandscape) {
        if (inPipMode) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        val inCooldown = now - lastManualActionTime < MANUAL_FULLSCREEN_COOLDOWN_MS
        if (deviceSide != 0) {
            if (manualEnterAwaitingLandscape) manualEnterAwaitingLandscape = false
            if (manualFullscreenExit && !inCooldown) manualFullscreenExit = false
            if (!manualFullscreenExit && !inCooldown && !fullscreen) fullscreen = true
        } else {
            if (manualFullscreenExit) manualFullscreenExit = false
            if (wasLandscape && !inCooldown && !manualEnterAwaitingLandscape && fullscreen) {
                fullscreen = false
            }
        }
        wasLandscape = deviceSide != 0
    }

    BackHandler(enabled = fullscreen) { applyFullscreen(false) }

    @Composable
    fun RenderLiveDialogs(inlineInStage: Boolean) {
        if (speedDialogVisible) {
            SpeedDialog(
                active = speed,
                onSelect = { value ->
                    speed = value
                    speedDialogVisible = false
                },
                onDismiss = { speedDialogVisible = false },
                inlineInStage = inlineInStage,
            )
        }
        if (danmakuSettingsDialogVisible) {
            DanmakuSettingsDialog(
                settings = danmakuSettings,
                repository = container.settings,
                onDismiss = { danmakuSettingsDialogVisible = false },
                inlineInStage = inlineInStage,
            )
        }
        val playback = state.playback
        if (qualityDialogVisible && playback != null) {
            QualityDialog(
                qualities = playback.qualities,
                activeQuality = state.activeQuality,
                onSelect = { qn ->
                    qualityDialogVisible = false
                    viewModel.selectQuality(qn)
                },
                onDismiss = { qualityDialogVisible = false },
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
        playerContent = {
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
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeek = {},
                onToggleFullscreen = { applyFullscreen(!fullscreen) },
                onFullscreenChange = applyFullscreen,
                hapticEnabled = gestureHaptic,
                volumeBrightnessEnabled = gestureVolBright,
                fullscreenHintEnabled = fullscreenGestureHint,
                onQualityClick = { qualityDialogVisible = true },
                onSpeedClick = { speedDialogVisible = true },
                onBack = {
                    if (fullscreen) applyFullscreen(false) else nav.pop()
                },
                onRetry = viewModel::retryPlayback,
                modifier = Modifier.fillMaxSize(),
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
                longPressSpeedActive = longPressSpeed,
                longPressSpeedValue = boostSpeed,
                onLongPressSpeedChange = { longPressSpeed = it },
                overlay = {
                    DanmakuOverlay(
                        player = player,
                        items = overlayItems,
                        isPlaying = isPlaying,
                        settings = danmakuSettings,
                    )
                },
            )
            if (fullscreen) {
                RenderLiveDialogs(inlineInStage = true)
            }
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

@Composable
private fun LiveRoomInfo(detail: LiveRoomDetail, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val avatarPx = with(density) { 36.dp.roundToPx() }
    val avatarUrl = remember(detail.anchorFace) {
        Formatters.sizedImageUrl(detail.anchorFace, avatarPx, avatarPx)
    }

    Column(modifier) {
        Text(
            text = detail.title,
            style = COUITheme.textStyles.title3,
            maxLines = 2,
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (detail.anchorFace.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(
                    text = detail.anchorName.ifBlank { "未知主播" },
                    style = COUITheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = buildString {
                        if (detail.areaName.isNotBlank()) append(detail.areaName).append(" · ")
                        if (detail.isLive) {
                            append(Formatters.formatStat(detail.online)).append("人气")
                        } else {
                            append("未开播")
                        }
                    },
                    style = COUITheme.textStyles.footnote2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            }
        }

        if (detail.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail.description,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 2,
            )
        }
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

private const val MAX_OVERLAY_DANMAKU = 120
