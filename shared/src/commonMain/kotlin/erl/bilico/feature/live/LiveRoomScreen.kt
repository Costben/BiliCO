package erl.bilico.feature.live

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import erl.bilico.feature.video.rememberPlayerFullscreenController
import erl.bilico.feature.video.rememberStableNavBarBottomDp
import erl.bilico.platform.KeyValueStore
import erl.bilico.platform.LocalPlatformContext
import erl.bilico.platform.PlatformBackHandler
import erl.bilico.platform.canEnterPip
import erl.bilico.platform.enterPipNow
import erl.bilico.platform.uptimeMillis
import erl.bilico.player.BiliPlayerListener
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlayerStates
import erl.bilico.player.buildConfiguredBiliPlayer
import erl.bilico.player.releasePlaybackPlayer
import erl.bilico.player.rememberPlaybackPlayer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.feature.video.rememberStageVideoBackdrop
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LiveRoomScreen(
    container: AppContainer,
    nav: NavState,
    roomId: Long,
) {
    val context = LocalPlatformContext.current
    val streamPrefs = remember(context) {
        KeyValueStore(context, "live_stream_prefs")
    }
    val viewModel: LiveRoomViewModel = viewModel(key = "live-$roomId") {
        LiveRoomViewModel(
            container.live,
            container.client.http,
            roomId,
            container.session.mid(),
            streamPrefs,
            blockSnapshotProvider = { container.blocks.snapshot.value },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val blockSnapshot by container.blocks.snapshot.collectAsStateWithLifecycle()

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

    val scope = rememberCoroutineScope()
    val player = rememberPlaybackPlayer("live-$roomId") { buildConfiguredBiliPlayer(context) }
    val playbackEntry = LocalNavEntry.current
    DisposableEffect(playbackEntry, roomId) {
        playbackEntry?.onDiscard = {
            val hasOtherSameRoom = nav.stack.any { it !== playbackEntry && (it.route as? Route.LiveRoom)?.roomId == roomId } ||
                (nav.minimizedEntry != null && nav.minimizedEntry !== playbackEntry && (nav.minimizedEntry?.route as? Route.LiveRoom)?.roomId == roomId)
            if (!hasOtherSameRoom) releasePlaybackPlayer("live-$roomId")
        }
        onDispose { }
    }

    var videoAspect by remember(roomId) { mutableFloatStateOf(0f) }
    var screenLocked by remember { mutableStateOf(false) }
    val fsController = rememberPlayerFullscreenController(videoAspect, autoRotateMode, inPipMode || screenLocked)
    val fullscreen = fsController.fullscreen
    val deviceSide = fsController.deviceSide
    val applyFullscreen = fsController.applyFullscreen
    val onScreenLockedChange: (Boolean) -> Unit = { locked ->
        if (screenLocked && !locked && fullscreen && !inPipMode && deviceSide == 0) applyFullscreen(true)
        screenLocked = locked
    }

    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var castDialogVisible by remember { mutableStateOf(false) }
    var lineDialogVisible by remember { mutableStateOf(false) }
    var scDrawerVisible by remember { mutableStateOf(false) }
    var danmakuDraft by rememberSaveable(roomId) { mutableStateOf("") }

    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var errorEvent by remember { mutableStateOf<String?>(null) }
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
        DanmakuTimeline().apply {
            source = { uptimeMillis() }
            nowMs = uptimeMillis()
        }
    }

    fun clearDanmakuOverlay() {
        overlayItems.clear()
        consumedDanmakuSeq = viewModel.state.value.danmakuSeq
    }

    fun prepareCandidate(candidate: LiveCandidate) {
        playerError = null
        clearDanmakuOverlay()
        player.setPlaybackSpeed(1f)
        player.setMedia(
            videoUrl = candidate.url,
            headers = container.client.liveStreamHeaders(),
            startPositionMs = 0L,
            playWhenReady = true,
            isLive = true,
        )
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
                    val stamp = overlayTimeline.source().coerceAtLeast(lastOverlayStamp + 1L)
                    overlayTimeline.nowMs = stamp
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

    DisposableEffect(player) {
        val initialSize = player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            videoAspect = initialSize.width.toFloat() / initialSize.height.toFloat()
        }
        val updatePlaying: (Boolean) -> Unit = { isPlaying = it }
        val listener = object : BiliPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaying(isPlaying)
            }

            override fun onVideoSizeChanged(width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    videoAspect = width.toFloat() / height.toFloat()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == PlayerStates.STATE_BUFFERING
                if (state == PlayerStates.STATE_READY) refetched = 0
            }

            override fun onPlayerError(message: String) {
                errorEvent = message
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

    LaunchedEffect(blockSnapshot) {
        viewModel.pruneBlocked()
        overlayItems.removeAll { !it.isSelf && blockSnapshot.matchesDanmaku(it.content) }
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
        val nowMs = overlayTimeline.source().coerceAtLeast(lastOverlayStamp + 1L).toInt()
        overlayTimeline.nowMs = nowMs.toLong()
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

    PlatformBackHandler(enabled = fullscreen && !screenLocked) { applyFullscreen(false) }

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

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = deviceSide,
        inPipMode = inPipMode,
        darkMode = darkMode,
        onExitFullscreen = { applyFullscreen(false) },
        videoAspect = videoAspect,
        screenLocked = screenLocked,
        onScreenLockedChange = onScreenLockedChange,
        playerContent = {
            val returnHome = LocalStageReturnHome.current ?: { nav.popToMain() }
            val captureStageBackdrop = BlurEffectsEnabled && fullscreen
            val stageBackdropActive = captureStageBackdrop && (
                danmakuSettingsDialogVisible || lineDialogVisible || castDialogVisible || scDrawerVisible
                )
            val stageBackdrop = rememberStageVideoBackdrop(
                player = player,
                active = stageBackdropActive,
                isPlaying = isPlaying,
            )
            VideoPlayer(
                player = player,
                title = state.detail?.title.orEmpty(),
                isPlaying = isPlaying,
                positionMs = 0L,
                durationMs = 0L,
                isBuffering = state.isPlaybackLoading || isBuffering,
                errorMessage = state.playbackError ?: playerError,
                qualityLabel = state.qualityLabel(),
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
                    .layerBackdropIf(captureStageBackdrop, stageBackdrop),
                inPipMode = inPipMode,
                liveMode = true,
                statusText = if (state.onlyAudio) "黑听中" else state.statusText(),
                onRefreshLive = {
                    refetched = 0
                    playerError = null
                    errorEvent = null
                    viewModel.reconnect()
                    liveMessage = "正在重连直播间与弹幕"
                },
                audioOnly = state.onlyAudio,
                onToggleAudioOnly = {
                    viewModel.toggleOnlyAudio()
                    liveMessage = if (!state.onlyAudio) "已开启黑听（仅播放音频）" else "已恢复视频画面"
                },
                onSelectLine = if (!state.playback?.streams.isNullOrEmpty()) ({ lineDialogVisible = true }) else null,
                superChatCount = state.superChatHistory.size,
                onSuperChatsClick = { scDrawerVisible = true },
                audioOnlyOverlay = { LiveAudioOnlyOverlay(detail = state.detail) },
                danmakuEnabled = danmakuEnabled,
                onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                onDanmakuSettingsClick = { danmakuSettingsDialogVisible = true },
                onEnterPip = if (canEnterPip(context)) ({ enterPipNow(context) }) else null,
                onHome = returnHome,
                onCast = if (castStreamUrl.isNotBlank()) ({ castDialogVisible = true }) else null,
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
                        modifier = Modifier.fillMaxSize(),
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
                    LocalBackdrop provides if (BlurEffectsEnabled) stageBackdrop else null,
                ) {
                    RenderLiveDialogs(inlineInStage = true)
                    RenderSuperChatDrawer(inlineInStage = true)
                }
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

private const val MAX_OVERLAY_DANMAKU = 600
