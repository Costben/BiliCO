package erl.bilico.feature.live

import android.content.Context
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
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
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
import erl.bilico.feature.video.FullscreenDanmakuComposer
import erl.bilico.feature.video.LocalStageReturnHome
import erl.bilico.feature.video.VideoFullscreenStage
import erl.bilico.feature.video.VideoMessageToast
import erl.bilico.feature.video.VideoPlayer
import erl.bilico.feature.video.copyDanmakuToClipboard
import erl.bilico.feature.video.describePlaybackError
import erl.bilico.feature.video.findActivity
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
import erl.bilico.ui.Route
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
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
    val context = LocalContext.current
    val streamPrefs = remember(context) {
        context.getSharedPreferences("live_stream_prefs", Context.MODE_PRIVATE)
    }
    val viewModel: LiveRoomViewModel = viewModel(key = "live-$roomId") {
        LiveRoomViewModel(container.live, container.client.okHttp, roomId, container.session.mid(), streamPrefs)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val advancedPlusOne by container.settings.advancedPlusOne.collectAsStateWithLifecycle()
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val player = rememberPlaybackPlayer("live-$roomId") { buildConfiguredExoPlayer(context) }
    val playbackEntry = LocalNavEntry.current
    DisposableEffect(playbackEntry, roomId) {
        playbackEntry?.onDiscard = { releasePlaybackPlayer("live-$roomId") }
        onDispose { }
    }

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
    var castDialogVisible by remember { mutableStateOf(false) }
    var lineDialogVisible by remember { mutableStateOf(false) }
    var scDrawerVisible by remember { mutableStateOf(false) }
    var danmakuDraft by rememberSaveable(roomId) { mutableStateOf("") }

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
    val overlayTimeline = remember {
        DanmakuTimeline().apply { source = { SystemClock.elapsedRealtime() } }
    }

    fun clearDanmakuOverlay() {
        overlayItems.clear()
        consumedDanmakuSeq = viewModel.state.value.danmakuSeq
    }

    fun prepareCandidate(candidate: LiveCandidate) {
        playerError = null
        clearDanmakuOverlay()
        player.setPlaybackSpeed(1f)
        player.setMediaSource(createLiveMediaSource(container.client, candidate))
        player.prepare()
        player.seekToDefaultPosition()
        player.playWhenReady = true
    }

    fun sendLiveDanmaku(text: String, emotes: Map<String, EmoteImage> = emptyMap(), onSent: (() -> Unit)? = null) {
        val realRoomId = state.detail?.roomId?.takeIf { it > 0L } ?: roomId
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (!container.session.isLoggedIn()) {
            liveMessage = "请先登录后再操作"
            return
        }
        scope.launch {
            container.live.sendDanmaku(realRoomId, trimmed, container.session.csrf())
                .onSuccess {
                    onSent?.invoke()
                    val stamp = overlayTimeline.nowMs.coerceAtLeast(lastOverlayStamp + 1L)
                    lastOverlayStamp = stamp
                    overlayItems += DanmakuItem(
                        id = nextSelfDanmakuId--,
                        timeMs = stamp.toInt(),
                        type = DanmakuType.SCROLL,
                        content = trimmed,
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

    // 黑听模式：同步禁用视频轨道解码与渲染，切回视频模式时立即恢复。
    LaunchedEffect(player, state.onlyAudio) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, state.onlyAudio)
            .build()
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
        onDispose { player.removeListener(listener) }
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

    LaunchedEffect(state.playbackGeneration) {
        val playback = state.playback ?: return@LaunchedEffect
        val first = playback.candidates.firstOrNull() ?: return@LaunchedEffect
        candidateIndex = 0
        viewModel.updateActiveSelection(first.selection)
        prepareCandidate(first)
    }

    LaunchedEffect(errorEvent) {
        val error = errorEvent ?: return@LaunchedEffect
        errorEvent = null
        val playback = state.playback
        val failed = playback?.candidates?.getOrNull(candidateIndex)
        Log.w(
            "BiliLive",
            "player error code=${error.errorCode} name=${error.errorCodeName} " +
                "http=${liveHttpStatus(error)} index=$candidateIndex/${playback?.candidates?.size} " +
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
            viewModel.updateActiveSelection(next.selection)
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

    LaunchedEffect(state.danmakuSeq) {
        val pending = state.danmakuSeq - consumedDanmakuSeq
        if (pending <= 0L) return@LaunchedEffect
        val count = pending.coerceAtMost(state.danmaku.size.toLong()).toInt()
        if (count <= 0) return@LaunchedEffect
        if (!player.isPlaying) {
            consumedDanmakuSeq = state.danmakuSeq
            return@LaunchedEffect
        }
        val nowMs = overlayTimeline.nowMs.coerceAtLeast(lastOverlayStamp + 1L).toInt()
        lastOverlayStamp = nowMs.toLong()
        val firstId = state.danmakuSeq - count
        state.danmaku.takeLast(count).forEachIndexed { index, message ->
            if (!viewModel.isBlocked(message.mid, message.text)) {
                val medal = if (message.medalName.isNotBlank()) "${message.medalName} ${message.medalLevel}" else ""
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

    val castStreamUrl = remember(state.playback, candidateIndex) {
        val list = state.playback?.candidates.orEmpty()
        list.firstOrNull { it.url.contains(".m3u8", ignoreCase = true) }?.url
            ?: list.getOrNull(candidateIndex)?.url
            ?: list.firstOrNull()?.url.orEmpty()
    }

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
        if (lineDialogVisible) {
            LiveStreamDialog(
                streams = state.playback?.streams.orEmpty(),
                current = state.lineSelection,
                inlineInStage = inlineInStage,
                onSelect = { sel ->
                    viewModel.selectLine(sel)
                    liveMessage = "已切换直播线路"
                },
                onDismiss = { lineDialogVisible = false },
            )
        }
        if (castDialogVisible && castStreamUrl.isNotBlank()) {
            erl.bilico.feature.cast.CastSheet(
                request = erl.bilico.feature.cast.CastMediaRequest(
                    title = state.detail?.title.orEmpty(),
                    liveStreamUrl = castStreamUrl,
                ),
                inlineInStage = inlineInStage,
                onDismiss = { castDialogVisible = false },
            )
        }
    }

    @Composable
    fun RenderSuperChatDrawer(inlineInStage: Boolean) {
        LiveSuperChatDrawer(
            visible = scDrawerVisible,
            items = state.superChatHistory,
            nowSec = state.nowSec,
            inlineInStage = inlineInStage,
            onReplay = { sc ->
                scDrawerVisible = false
                viewModel.replaySuperChat(sc)
                liveMessage = "正在回放 ${sc.userName} 的 SC"
            },
            onCopy = { sc ->
                copyDanmakuToClipboard(context, sc.message)
                liveMessage = "已复制 SC 留言"
            },
            onDismiss = { scDrawerVisible = false },
        )
    }

    // 直播优先走 SurfaceView：避免 60fps 直播流在 TextureView GPU 合成链路里丢帧/抖动。
    val useSurfaceView = true

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = deviceSide,
        inPipMode = inPipMode,
        activity = activity,
        darkMode = darkMode,
        onExitFullscreen = { applyFullscreen(false) },
        videoAspect = videoAspect,
        surfaceViewPlayer = useSurfaceView,
        playerContent = {
            val returnHome = LocalStageReturnHome.current ?: { nav.popToMain() }
            val stageBackdrop = rememberLayerBackdrop()
            val anyStageDialogOpen = !useSurfaceView && fullscreen && (danmakuSettingsDialogVisible || castDialogVisible || lineDialogVisible || scDrawerVisible)
            VideoPlayer(
                player = player,
                title = state.detail?.title.orEmpty(),
                isPlaying = isPlaying,
                positionMs = 0L,
                durationMs = 0L,
                isBuffering = state.isPlaybackLoading || isBuffering,
                errorMessage = state.playbackError ?: playerError,
                qualityLabel = state.qualityLabel(),
                hdrVideo = useSurfaceView,
                speed = 1f,
                fullscreen = fullscreen,
                portraitFullscreen = fullscreen && fsController.portraitStage,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onSeek = {},
                onToggleFullscreen = { applyFullscreen(!fullscreen) },
                onFullscreenChange = applyFullscreen,
                hapticEnabled = gestureHaptic,
                volumeBrightnessEnabled = gestureVolBright,
                fullscreenHintEnabled = fullscreenGestureHint,
                qualities = state.playback?.qualities.orEmpty(),
                activeQuality = state.activeQuality,
                onSelectQuality = viewModel::selectQuality,
                onBack = { if (fullscreen) applyFullscreen(false) else nav.pop() },
                onRetry = viewModel::retryPlayback,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdropIf(BlurEffectsEnabled && anyStageDialogOpen, stageBackdrop),
                inPipMode = inPipMode,
                liveMode = true,
                statusText = if (state.onlyAudio) "黑听中" else state.statusText(),
                audioOnly = state.onlyAudio,
                onToggleAudioOnly = {
                    viewModel.toggleOnlyAudio()
                    liveMessage = if (!state.onlyAudio) "已开启黑听（仅播放音频）" else "已恢复视频画面"
                },
                onSelectLine = if (!state.playback?.streams.isNullOrEmpty()) {
                    { lineDialogVisible = true }
                } else {
                    null
                },
                superChatCount = state.superChatHistory.size,
                onSuperChatsClick = { scDrawerVisible = true },
                audioOnlyOverlay = { LiveAudioOnlyOverlay(detail = state.detail) },
                danmakuEnabled = danmakuEnabled,
                onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                onDanmakuSettingsClick = { danmakuSettingsDialogVisible = true },
                onEnterPip = if (PlaybackBridge.canEnterPip(activity)) {
                    { PlaybackBridge.enterPipNow(activity) }
                } else {
                    null
                },
                onHome = returnHome,
                onCast = if (castStreamUrl.isNotBlank()) {
                    { castDialogVisible = true }
                } else {
                    null
                },
                danmakuComposer = { onFocusChange ->
                    FullscreenDanmakuComposer(
                        draft = danmakuDraft,
                        onDraftChange = { danmakuDraft = it },
                        onSend = {
                            sendLiveDanmaku(danmakuDraft) { danmakuDraft = "" }
                        },
                        onFocusChange = onFocusChange,
                    )
                },
                overlay = {
                    DanmakuOverlay(
                        player = player,
                        items = overlayItems.toList(),
                        isPlaying = isPlaying,
                        settings = danmakuSettings,
                        timeline = overlayTimeline,
                    )
                },
                danmakuActions = DanmakuActionCallbacks(
                    onCopy = { item ->
                        copyDanmakuToClipboard(context, item.content)
                        liveMessage = "已复制弹幕"
                    },
                    onPlusOne = { item -> sendLiveDanmaku(item.content, item.emotes) },
                    onAdvancedPlusOne = if (advancedPlusOne) {
                        { item ->
                            sendLiveDanmaku(formatAdvancedPlusOneText(item.senderMedal, item.senderName, item.content), item.emotes)
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
                ),
            )
            val fsSc = state.fullscreenSuperChat
            if ((fullscreen || state.fullscreenSuperChatIsReplay) && !inPipMode && fsSc != null && (danmakuEnabled || state.fullscreenSuperChatIsReplay)) {
                FullscreenSuperChatOverlay(
                    sc = fsSc,
                    endSec = state.fullscreenSuperChatEndSec,
                    nowSec = state.nowSec,
                    onDismiss = viewModel::dismissFullscreenSuperChat,
                    isReplay = state.fullscreenSuperChatIsReplay,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = if (fullscreen) 24.dp else 12.dp,
                            bottom = if (fullscreen) 58.dp else 12.dp,
                        ),
                )
            }
            if (fullscreen) {
                VideoMessageToast(message = liveMessage, inPipMode = inPipMode, inFullscreen = true)
                CompositionLocalProvider(
                    LocalBackdrop provides if (BlurEffectsEnabled && !useSurfaceView) stageBackdrop else null,
                ) {
                    RenderLiveDialogs(inlineInStage = true)
                }
                RenderSuperChatDrawer(inlineInStage = true)
            }
        },
        portraitOverlay = {
            if (!fullscreen) RenderSuperChatDrawer(inlineInStage = false)
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
                            entries = state.chatEntries,
                            activeSuperChats = state.activeSuperChats,
                            nowSec = state.nowSec,
                            state = state.danmakuState,
                            error = state.danmakuError,
                            danmakuEnabled = danmakuEnabled,
                            draft = danmakuDraft,
                            onDraftChange = { danmakuDraft = it },
                            onSendDanmaku = { sendLiveDanmaku(danmakuDraft) { danmakuDraft = "" } },
                            onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                            onPlusOne = { text, emotes -> sendLiveDanmaku(text, emotes) },
                            onAdvancedPlusOne = { medal, name, text, emotes ->
                                sendLiveDanmaku(formatAdvancedPlusOneText(medal, name, text), emotes)
                            },
                            onCopyDanmaku = { msg -> liveMessage = msg },
                            onBlockUser = { mid, name ->
                                viewModel.blockUserTemporary(mid)
                                overlayItems.removeAll { (it.midHash.toLongOrNull() ?: 0L) == mid }
                                liveMessage = "已临时屏蔽 $name"
                            },
                            onBlockContent = { text ->
                                viewModel.blockContentTemporary(text)
                                val trimmed = text.trim()
                                overlayItems.removeAll { it.content.trim() == trimmed }
                                liveMessage = "已临时屏蔽该弹幕"
                            },
                            onOpenSpace = { mid -> nav.push(Route.Space(mid)) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(bottom = navBarBottom),
                            onOpenDanmakuSettings = { danmakuSettingsDialogVisible = true },
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

private const val MAX_OVERLAY_DANMAKU = 600
