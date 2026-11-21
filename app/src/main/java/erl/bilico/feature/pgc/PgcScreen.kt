package erl.bilico.feature.pgc

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.model.isHdr
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.ReplyThreadController
import erl.bilico.feature.video.CollapsedVideoBar
import erl.bilico.feature.video.DanmakuController
import erl.bilico.feature.video.DanmakuOverlay
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.feature.video.FullscreenDanmakuComposer
import erl.bilico.feature.video.LocalStageReturnHome
import erl.bilico.feature.video.LocalPortraitSlotHeight
import erl.bilico.feature.video.PlaybackAttempts
import erl.bilico.feature.video.SubtitleController
import erl.bilico.feature.video.SubtitleOverlay
import erl.bilico.feature.video.VideoFullscreenStage
import erl.bilico.feature.video.VideoMessageToast
import erl.bilico.feature.video.VideoPlayer
import erl.bilico.feature.video.describePlaybackError
import erl.bilico.feature.video.findActivity
import erl.bilico.feature.video.playbackMediaId
import erl.bilico.feature.video.rememberDanmakuActions
import erl.bilico.feature.video.rememberLongPressSpeedController
import erl.bilico.feature.video.resolveLongPressSpeedMode
import erl.bilico.feature.video.rememberPlayerFullscreenController
import erl.bilico.feature.video.rememberVideoBarBackdrops
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.buildConfiguredExoPlayer
import erl.bilico.player.releasePlaybackPlayer
import erl.bilico.player.rememberPlaybackPlayer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.LocalNavEntry
import erl.bilico.ui.LocalRouteTransition
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.layerBackdropIf
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PgcScreen(
    container: AppContainer,
    nav: NavState,
    seasonId: Long,
    epId: Long = 0L,
    startPositionMs: Long = 0L,
) {
    val viewModel: PgcViewModel = viewModel(key = "pgc-$seasonId-$epId-$startPositionMs") {
        PgcViewModel(
            repository = container.pgc,
            heartbeat = container.heartbeat,
            session = container.session,
            settings = container.settings,
            seasonId = seasonId,
            epId = epId,
            startPositionMs = startPositionMs,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val playbackEndMode by container.settings.playbackEndMode.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val longPressSpeedPercent by container.settings.longPressSpeed.collectAsStateWithLifecycle()
    val adjustableLongPressSpeed by container.settings.adjustableLongPressSpeed.collectAsStateWithLifecycle()
    val swipeDownLockLongPressSpeed by container.settings.swipeDownLockLongPressSpeed.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val speedLocked by container.settings.speedLocked.collectAsStateWithLifecycle()
    val lockedSpeed by container.settings.lockedSpeed.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope) }
    val subtitleController = remember { SubtitleController(container.subtitles, scope) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = rememberPlaybackPlayer("pgc-$seasonId") { buildConfiguredExoPlayer(context) }
    // 页面被弹出（最小化不算）时释放播放器：一台准备好的播放器会占住几十 MB 的加载缓冲。
    val playbackEntry = LocalNavEntry.current
    DisposableEffect(playbackEntry, seasonId) {
        playbackEntry?.onDiscard = { releasePlaybackPlayer("pgc-$seasonId") }
        onDispose { }
    }

    // 播放器解码上报的首帧宽高比（换集时同步重置），再回退到 dash 视频轨预设比例；0 表示仍未知。
    val episode = viewModel.currentEpisode
    var playerVideoAspect by remember(episode?.cid) { mutableFloatStateOf(0f) }
    val effectiveVideoAspect = playerVideoAspect.takeIf { it > 0f }
        ?: state.source?.aspect?.takeIf { it > 0f }
        ?: 0f
    val fsController = rememberPlayerFullscreenController(
        videoAspect = effectiveVideoAspect,
        autoRotateMode = autoRotateMode,
        inPipMode = inPipMode,
    )
    val fullscreen = fsController.fullscreen
    val deviceSide = fsController.deviceSide
    val applyFullscreen = fsController.applyFullscreen
    val routeTransition = LocalRouteTransition.current
    val blurActive by remember(routeTransition) {
        derivedStateOf {
            routeTransition == null ||
                (!routeTransition.exiting() && routeTransition.progress() >= 0.999f)
        }
    }
    val detailPager = rememberPagerState(pageCount = { 2 })
    val infoListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    val replyThread = remember(seasonId) { ReplyThreadController() }
    val replyListState = rememberLazyListState()
    val barBackdrops = rememberVideoBarBackdrops(pageContainerColor())
    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var castDialogVisible by remember { mutableStateOf(false) }
    var danmakuComposing by rememberSaveable { mutableStateOf(false) }
    var danmakuDraft by rememberSaveable { mutableStateOf("") }
    var speed by rememberSaveable {
        mutableFloatStateOf(if (speedLocked) lockedSpeed else 1f)
    }
    val longPressSpeed = rememberLongPressSpeedController(
        baseSpeed = speed,
        boostPercent = longPressSpeedPercent,
    )
    val effectiveSpeed = longPressSpeed.effectiveSpeed

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val playbackAttempts = remember(player) { PlaybackAttempts(player) { effectiveSpeed } }

    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = episode?.title.orEmpty(),
        artworkUrl = episode?.cover?.ifBlank { null } ?: state.detail?.cover.orEmpty(),
        isPlaying = isPlaying,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
    )

    DisposableEffect(player) {
        val initialSize = player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            playerVideoAspect = (initialSize.width * initialSize.pixelWidthHeightRatio) / initialSize.height
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    playerVideoAspect = (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    viewModel.markPlaybackStarted()
                } else if (playbackState == Player.STATE_ENDED) {
                    viewModel.flushHeartbeat(player.duration.coerceAtLeast(0L) / 1000, container.scope)
                    when (playbackEndMode) {
                        PlaybackEndMode.OFF -> Unit
                        PlaybackEndMode.SINGLE_LOOP -> {
                            player.seekTo(0L)
                            player.play()
                        }
                        PlaybackEndMode.LIST_LOOP -> {
                            if (!viewModel.playNextEpisode(loop = true)) {
                                player.seekTo(0L)
                                player.play()
                            }
                        }
                        PlaybackEndMode.AUTO_NEXT -> viewModel.playNextEpisode()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("BiliPlayer", describePlaybackError(error), error)
                if (!playbackAttempts.next()) playerError = describePlaybackError(error)
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
            player.removeListener(listener)
        }
    }

    LaunchedEffect(episode?.cid, episode?.aid, episode?.bvid) {
        val ep = episode ?: return@LaunchedEffect
        if (ep.cid <= 0L) return@LaunchedEffect
        danmakuController.attach(ep.cid)
        danmakuController.onPosition(player.currentPosition)
        subtitleController.attach(bvid = ep.bvid, aid = ep.aid, cid = ep.cid)
    }

    LaunchedEffect(state.source) {
        val source = state.source ?: return@LaunchedEffect
        val currentEp = viewModel.currentEpisode
        val referer = if (currentEp != null && currentEp.epId > 0L) {
            "https://www.bilibili.com/bangumi/play/ep${currentEp.epId}"
        } else {
            "https://www.bilibili.com/bangumi/play/ss$seasonId"
        }
        val headers = container.client.mediaRequestHeaders().toMutableMap().apply {
            put("Referer", referer)
        }
        val dataSourceFactory = OkHttpDataSource.Factory(container.client.mediaClient())
            .setDefaultRequestProperties(headers)
        val resumeAt = viewModel.state.value.resumePositionMs
        val mediaId = playbackMediaId(currentEp?.bvid.orEmpty(), currentEp?.cid ?: 0L, source.videoUrl)
        // 页面被重建（最小化进融合球/复原）时播放器没换，重复装载会让番剧从 0 重新播。
        if (player.playbackState != Player.STATE_IDLE &&
            player.currentMediaItem?.mediaId == mediaId
        ) {
            playbackAttempts.adopt(source, dataSourceFactory, mediaId)
            return@LaunchedEffect
        }
        playerError = null
        playbackAttempts.start(source, dataSourceFactory, resumeAt, mediaId)
        if (resumeAt > 0L) viewModel.consumeResumePosition()
    }

    // 切号 / 登录态变了：按当前清晰度重新取流，让刚生效的大会员档位回到清晰度菜单里。
    // 首帧拿到的登录态不重取，免得一进页面就白闪一下。
    val playbackAccount by container.session.account.collectAsStateWithLifecycle()
    var playbackAccountKey by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    LaunchedEffect(playbackAccount) {
        val key = playbackAccount?.let { it.mid to it.vip }
        val previous = playbackAccountKey
        playbackAccountKey = key
        if (previous != null && previous != key) viewModel.refreshPlayback(positionMs)
    }

    LaunchedEffect(effectiveSpeed) {
        player.setPlaybackSpeed(effectiveSpeed)
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            playbackAttempts.rememberPosition(positionMs)
            viewModel.reportHeartbeat(positionMs / 1000, player.isPlaying)
            danmakuController.onPosition(positionMs)
            delay(500)
        }
    }

    BackHandler(enabled = fullscreen) { applyFullscreen(false) }

    var danmakuActionMessage by remember { mutableStateOf<String?>(null) }
    val message = danmakuActionMessage ?: state.message
    val sendDanmaku: () -> Unit = {
        val text = danmakuDraft.trim()
        val aid = episode?.aid ?: 0L
        val cid = episode?.cid ?: 0L
        when {
            text.isEmpty() || cid <= 0L -> Unit
            !container.session.isLoggedIn() -> danmakuActionMessage = "请先登录后再操作"
            else -> scope.launch {
                val position = player.currentPosition.coerceAtLeast(0L)
                container.danmaku.send(aid, cid, text, position)
                    .onSuccess { dmid ->
                        danmakuDraft = ""
                        danmakuComposing = false
                        val showAtMs = maxOf(position, player.currentPosition.coerceAtLeast(0L))
                        danmakuController.appendSelf(content = text, timeMs = showAtMs, serverId = dmid)
                        danmakuController.reload(position)
                        danmakuActionMessage = "弹幕已发送"
                    }
                    .onFailure { danmakuActionMessage = it.message ?: "发送弹幕失败" }
            }
        }
    }
    if (danmakuActionMessage != null) {
        LaunchedEffect(danmakuActionMessage) {
            delay(2600)
            danmakuActionMessage = null
        }
    }
    if (state.message != null) {
        LaunchedEffect(state.message) {
            delay(2600)
            viewModel.consumeMessage()
        }
    }

    @Composable
    fun RenderPgcDialogs(inlineInStage: Boolean) {
        if (danmakuSettingsDialogVisible) {
            DanmakuSettingsDialog(
                settings = danmakuSettings,
                repository = container.settings,
                onDismiss = { danmakuSettingsDialogVisible = false },
                inlineInStage = inlineInStage,
            )
        }
        val currentEp = viewModel.currentEpisode
        val castEpId = currentEp?.epId ?: epId
        val castCid = currentEp?.cid ?: 0L
        if (castDialogVisible && (castEpId > 0L || castCid > 0L)) {
            erl.bilico.feature.cast.CastSheet(
                request = erl.bilico.feature.cast.CastMediaRequest(
                    title = state.detail?.title.orEmpty(),
                    epId = castEpId,
                    cid = castCid,
                    qn = state.activeQuality,
                    initialPositionMs = player.currentPosition.coerceAtLeast(0L),
                ),
                inlineInStage = inlineInStage,
                onDismiss = { castDialogVisible = false },
            )
        }
    }

    // HDR 档位必须走 SurfaceView：TextureView 由 GPU 合成，HDR 画面会被当成 SDR 铺到屏幕上（发暗）。
    val hdrSurface = state.source?.isHdr == true

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = deviceSide,
        inPipMode = inPipMode,
        activity = activity,
        darkMode = darkMode,
        onExitFullscreen = { applyFullscreen(false) },
        videoAspect = effectiveVideoAspect,
        portraitResizable = true,
        playing = isPlaying || isBuffering,
        surfaceViewPlayer = hdrSurface,
        canExpand = {
            val list = when {
                replyThread.root != null -> replyListState
                detailPager.currentPage == 0 -> infoListState
                else -> commentListState
            }
            list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
        },
        playerContent = {
            val stageBackdrop = rememberLayerBackdrop()
            val anyStageDialogOpen = fullscreen && (danmakuSettingsDialogVisible || castDialogVisible)
            val returnHome = LocalStageReturnHome.current ?: { nav.popToMain() }
            VideoPlayer(
                player = player,
                title = state.detail?.title.orEmpty(),
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs.takeIf { it > 0L } ?: state.source?.durationMs ?: 0L,
                isBuffering = state.isPlaybackLoading || isBuffering,
                errorMessage = state.playbackError ?: playerError,
                qualityLabel = state.qualityLabel(),
                source = state.source,
                hdrVideo = hdrSurface,
                speed = speed,
                fullscreen = fullscreen,
                portraitFullscreen = fullscreen && fsController.portraitStage,
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeek = { player.seekTo(it) },
                onToggleFullscreen = { applyFullscreen(!fullscreen) },
                onFullscreenChange = applyFullscreen,
                hapticEnabled = gestureHaptic,
                volumeBrightnessEnabled = gestureVolBright,
                fullscreenHintEnabled = fullscreenGestureHint,
                qualities = state.source?.qualities.orEmpty(),
                activeQuality = state.activeQuality,
                onSelectQuality = { qn -> viewModel.selectQuality(qn, player.currentPosition) },
                speedLocked = speedLocked,
                onToggleSpeedLock = { container.settings.setSpeedLock(!speedLocked, speed) },
                onSelectSpeed = { value ->
                    longPressSpeed.onActiveChange(false)
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
                danmakuEnabled = danmakuEnabled,
                onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                onDanmakuSettingsClick = { danmakuSettingsDialogVisible = true },
                subtitleLabel = subtitleController.buttonLabel,
                subtitleTracks = subtitleController.tracks,
                selectedSubtitleKey = subtitleController.selectedTrackKey,
                onSelectSubtitle = subtitleController::selectTrack,
                onEnterPip = if (PlaybackBridge.canEnterPip(activity)) {
                    { PlaybackBridge.enterPipNow(activity) }
                } else {
                    null
                },
                onHome = returnHome,
                onCast = if ((viewModel.currentEpisode?.epId ?: epId) > 0L || (viewModel.currentEpisode?.cid ?: 0L) > 0L) {
                    { castDialogVisible = true }
                } else {
                    null
                },
                playbackEndMode = playbackEndMode,
                onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
                longPressSpeed = longPressSpeed,
                longPressSpeedMode = resolveLongPressSpeedMode(
                    adjustableLongPressSpeed,
                    swipeDownLockLongPressSpeed,
                ),
                subtitleOverlay = { chromeVisible ->
                    SubtitleOverlay(
                        cues = subtitleController.cues,
                        positionMs = positionMs,
                        fullscreen = fullscreen,
                        controlsVisible = chromeVisible,
                        inPip = inPipMode,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                danmakuComposer = { onFocusChange ->
                    FullscreenDanmakuComposer(
                        draft = danmakuDraft,
                        onDraftChange = { danmakuDraft = it },
                        onSend = sendDanmaku,
                        onFocusChange = onFocusChange,
                    )
                },
                overlay = {
                    DanmakuOverlay(
                        player = player,
                        items = danmakuController.items,
                        isPlaying = isPlaying,
                        settings = danmakuSettings,
                    )
                },
                danmakuActions = rememberDanmakuActions(
                    aid = episode?.aid ?: 0L,
                    cid = episode?.cid ?: 0L,
                    fullscreen = fullscreen,
                    positionMs = { player.currentPosition.coerceAtLeast(0L) },
                    isLoggedIn = { container.session.isLoggedIn() },
                    repository = container.danmaku,
                    controller = danmakuController,
                    scope = scope,
                    onShowMessage = { danmakuActionMessage = it },
                    onShowLoginHint = { danmakuActionMessage = "请先登录后再操作" },
                ),
            )
            if (fullscreen) {
                VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = true)
                CompositionLocalProvider(
                    LocalBackdrop provides if (BlurEffectsEnabled) stageBackdrop else null,
                ) {
                    RenderPgcDialogs(inlineInStage = true)
                }
            }
        },
        detailContent = {
            PgcDetailSection(
                container = container,
                nav = nav,
                state = state,
                aid = episode?.aid ?: 0L,
                detailPager = detailPager,
                infoListState = infoListState,
                commentListState = commentListState,
                replyThread = replyThread,
                replyListState = replyListState,
                danmakuEnabled = danmakuEnabled,
                danmakuComposing = danmakuComposing,
                danmakuDraft = danmakuDraft,
                onOpenDanmakuSettings = { danmakuSettingsDialogVisible = true },
                onDanmakuDraftChange = { danmakuDraft = it },
                onDanmakuComposingChange = {
                    danmakuComposing = it
                    if (!it) danmakuDraft = ""
                },
                onSendDanmaku = sendDanmaku,
                onSelectPage = { page -> scope.launch { detailPager.animateScrollToPage(page) } },
                onToggleFollow = viewModel::toggleFollow,
                onRetry = viewModel::load,
                onSelectEpisode = { index ->
                    viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
                    viewModel.selectEpisode(index)
                },
                onSelectSeason = { id -> nav.push(Route.Pgc(id, 0L)) },
                onSeekCommentMs = { ms ->
                    player.seekTo(ms)
                    if (!player.isPlaying) player.play()
                },
                barBackdrops = barBackdrops,
                blurActive = blurActive,
                backEnabled = !fullscreen,
            )
        },
        portraitOverlay = {
            VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = false)
        },
        portraitCollapsedBar = {
            val slotHeight = LocalPortraitSlotHeight.current
            val returnHome = LocalStageReturnHome.current ?: { nav.popToMain() }
            CollapsedVideoBar(
                isPlaying = isPlaying,
                onBack = { nav.pop() },
                onHome = returnHome,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onExpand = { slotHeight.floatValue = 0f },
                playbackEndMode = playbackEndMode,
                onSelectPlaybackEndMode = container.settings::setPlaybackEndMode,
                onCast = if ((viewModel.currentEpisode?.epId ?: epId) > 0L || (viewModel.currentEpisode?.cid ?: 0L) > 0L) {
                    { castDialogVisible = true }
                } else {
                    null
                },
                backdrop = barBackdrops.barMaterial,
            )
        },
    )

    if (!fullscreen) {
        RenderPgcDialogs(inlineInStage = false)
    }
}

private fun PgcUiState.qualityLabel(): String? {
    val source = source ?: return null
    val active = source.qualities.firstOrNull { it.quality == activeQuality }
    return active?.label?.takeIf { it.isNotBlank() } ?: "${activeQuality}P"
}
