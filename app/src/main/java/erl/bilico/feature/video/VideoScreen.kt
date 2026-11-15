package erl.bilico.feature.video

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.di.AppContainer
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.buildConfiguredExoPlayer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(
    container: AppContainer,
    nav: NavState,
    bvid: String,
    startPositionMs: Long = 0L,
    initialCid: Long = 0L,
) {
    val viewModel: VideoViewModel = viewModel(key = "video-$bvid-$startPositionMs-$initialCid") {
        VideoViewModel(
            repository = container.video,
            heartbeat = container.heartbeat,
            interactions = container.interactions,
            session = container.session,
            settings = container.settings,
            bvid = bvid,
            startPositionMs = startPositionMs,
            initialCid = initialCid,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val danmakuEnabled by container.settings.danmakuEnabled.collectAsStateWithLifecycle()
    val danmakuSettings by container.settings.danmakuSettings.collectAsStateWithLifecycle()
    val backgroundPlay by container.settings.backgroundPlay.collectAsStateWithLifecycle()
    val autoPip by container.settings.autoPip.collectAsStateWithLifecycle()
    val autoPlayNext by container.settings.autoPlayNext.collectAsStateWithLifecycle()
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val longPressSpeedPercent by container.settings.longPressSpeed.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by container.settings.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val sponsorSkipMode by container.settings.sponsorSkipMode.collectAsStateWithLifecycle()
    val sponsorCategories by container.settings.sponsorCategories.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope) }
    val subtitleController = remember { SubtitleController(container.subtitles, scope) }
    val sponsorController = remember { SponsorBlockController(container.sponsorBlock, scope) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = remember { buildConfiguredExoPlayer(context) }

    // 播放器解码上报的首帧宽高比（分 P 切换时同步重置），再回退到接口预设的 dimension / dash 尺寸；0 表示仍未知。
    var playerVideoAspect by remember(state.selectedCid) { mutableFloatStateOf(0f) }
    val effectiveVideoAspect = playerVideoAspect.takeIf { it > 0f }
        ?: state.detail?.aspectFor(state.selectedCid)?.takeIf { it > 0f }
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
    // 简介 / 评论两页横向翻。直接读 detailPager.currentPage 同步驱动页签，
    // 跟手无延迟，与私信页完全一致；点标签时开翻页动画。
    val detailPager = rememberPagerState(pageCount = { DETAIL_PAGES.size })
    val infoListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var queueSheetVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }
    var subtitleDialogVisible by remember { mutableStateOf(false) }
    var chapterDialogVisible by remember { mutableStateOf(false) }
    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var coinDialogVisible by remember { mutableStateOf(false) }
    var favoriteFolders by remember { mutableStateOf<List<FavoriteFolder>?>(null) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var longPressSpeed by remember { mutableStateOf(false) }
    var danmakuComposing by rememberSaveable { mutableStateOf(false) }
    var danmakuDraft by rememberSaveable { mutableStateOf("") }

    val sendDanmaku: () -> Unit = {
        val text = danmakuDraft.trim()
        val aid = state.detail?.aid ?: 0L
        val cid = state.selectedCid
        when {
            text.isEmpty() -> Unit
            !container.session.isLoggedIn() -> viewModel.showLoginHint()
            else -> scope.launch {
                val position = player.currentPosition.coerceAtLeast(0L)
                container.danmaku.send(aid, cid, text, position)
                    .onSuccess {
                        danmakuDraft = ""
                        danmakuComposing = false
                        danmakuController.reload(position)
                        viewModel.showMessage("弹幕已发送")
                    }
                    .onFailure { viewModel.showMessage(it.message ?: "发送弹幕失败") }
            }
        }
    }

    val boostSpeed = longPressSpeedPercent / 100f
    val effectiveSpeed = if (longPressSpeed) boostSpeed else speed
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val headers = remember(bvid) { container.client.mediaRequestHeaders(bvid) }
    val dataSourceFactory = remember(headers) {
        OkHttpDataSource.Factory(container.client.mediaClient()).setDefaultRequestProperties(headers)
    }
    val playbackAttempts = remember(player) { PlaybackAttempts(player) { effectiveSpeed } }

    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = state.detail?.upName.orEmpty(),
        isPlaying = isPlaying,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
    )

    BindPlayerEvents(
        player = player,
        viewModel = viewModel,
        container = container,
        onPlayingChanged = { isPlaying = it },
        onBufferingChanged = { isBuffering = it },
        onError = { error ->
            Log.w("BiliPlayer", describePlaybackError(error), error)
            // 线路坏了先换一条，候选表走完才把错误抛给界面。
            if (!playbackAttempts.next()) playerError = describePlaybackError(error)
        },
        onVideoAspectChanged = { playerVideoAspect = it },
        onPlaybackEnded = {
            // 下一 P 和「播放全部」的队列优先；都没有时，开着自动连播就接着播「其他推荐视频」的第一条。
            if (!viewModel.playNextPart()) {
                val queued = nav.playQueue.next()
                when {
                    queued != null -> nav.replaceTop(Route.Video(queued.video.bvid, queued.startSec))
                    autoPlayNext -> state.related.firstOrNull()?.let { next ->
                        nav.replaceTop(Route.Video(next.bvid, 0L))
                    }
                }
            }
        },
    )

    LaunchedEffect(bvid) { nav.playQueue.sync(bvid) }

    LaunchedEffect(bvid, state.selectedCid, sponsorBlockEnabled) {
        sponsorController.attach(bvid, state.selectedCid, sponsorBlockEnabled, player)
    }

    DisposableEffect(player) {
        onDispose { sponsorController.restoreMuteIfNeeded(player) }
    }

    LaunchedEffect(state.selectedCid, state.detail?.aid) {
        val cid = state.selectedCid
        if (cid > 0L) {
            danmakuController.attach(cid)
            danmakuController.onPosition(player.currentPosition)
            subtitleController.attach(
                bvid = bvid,
                aid = state.detail?.aid ?: 0L,
                cid = cid,
                onAuxiliaryLoaded = { info ->
                    val seekMs = viewModel.applyServerResumeHint(info.lastPlayCid, info.lastPlayTimeMs)
                    if (seekMs > 0L && player.playbackState != Player.STATE_IDLE && player.currentPosition < 3_000L) {
                        player.seekTo(seekMs)
                        viewModel.consumeResumePosition()
                    }
                },
            )
        }
    }

    LaunchedEffect(state.source) {
        val source = state.source ?: return@LaunchedEffect
        playerError = null
        if (state.resumePositionMs <= 0L && source.lastPlayTimeMs >= 3_000L) {
            viewModel.applyServerResumeHint(source.lastPlayCid, source.lastPlayTimeMs)
        }
        val resumeAt = viewModel.state.value.resumePositionMs
        playbackAttempts.start(source, dataSourceFactory, resumeAt)
        if (resumeAt > 0L) viewModel.consumeResumePosition()
    }

    LaunchedEffect(effectiveSpeed) {
        player.setPlaybackSpeed(effectiveSpeed)
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            viewModel.reportHeartbeat(positionMs / 1000, player.isPlaying)
            danmakuController.onPosition(positionMs)
            sponsorController.onPosition(
                positionMs = positionMs,
                player = player,
                enabled = container.settings.sponsorBlockEnabled.value,
                skipMode = container.settings.sponsorSkipMode.value,
                enabledCategories = container.settings.sponsorCategories.value,
            )
            delay(500)
        }
    }

    // 宿主窗口的方向由 VideoFullscreenStage 统一接管：竖屏锁死，全屏时真正转成横屏。
    BackHandler(enabled = fullscreen) { applyFullscreen(false) }

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            delay(2600)
            viewModel.consumeMessage()
        }
    }

    VideoFullscreenStage(
        fullscreen = fullscreen,
        landscapeSide = deviceSide,
        inPipMode = inPipMode,
        activity = activity,
        darkMode = darkMode,
        videoAspect = effectiveVideoAspect,
        portraitResizable = true,
        playing = isPlaying,
        canExpand = {
            val list = if (detailPager.currentPage == 0) infoListState else commentListState
            list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
        },
        playerContent = {
            val stageBackdrop = rememberLayerBackdrop()
            val anyStageDialogOpen = fullscreen && (
                speedDialogVisible || subtitleDialogVisible || qualityDialogVisible ||
                    danmakuSettingsDialogVisible || chapterDialogVisible
                )
            VideoPlayer(
                player = player,
                title = state.detail?.title.orEmpty(),
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs.takeIf { it > 0L } ?: state.source?.durationMs ?: 0L,
                isBuffering = state.isPlaybackLoading || isBuffering,
                errorMessage = state.playbackError ?: playerError,
                qualityLabel = state.qualityLabel(),
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
                onQualityClick = { qualityDialogVisible = true },
                onSpeedClick = { speedDialogVisible = true },
                onBack = {
                    if (fullscreen) applyFullscreen(false) else nav.pop()
                },
                onRetry = viewModel::load,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdropIf(BlurEffectsEnabled && anyStageDialogOpen, stageBackdrop),
                inPipMode = inPipMode,
                danmakuEnabled = danmakuEnabled,
                onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                onDanmakuSettingsClick = { danmakuSettingsDialogVisible = true },
                subtitleLabel = subtitleController.buttonLabel,
                onSubtitleClick = { subtitleDialogVisible = true },
                onEnterPip = if (PlaybackBridge.canEnterPip(activity)) {
                    { PlaybackBridge.enterPipNow(activity) }
                } else {
                    null
                },
                onHome = { nav.popToMain() },
                onWatchLater = viewModel::addToWatchLater,
                autoPlayNext = autoPlayNext,
                onToggleAutoPlayNext = { container.settings.setAutoPlayNext(!autoPlayNext) },
                onShare = {
                    shareVideo(
                        context = context,
                        title = state.detail?.title.orEmpty(),
                        bvid = bvid,
                    )
                },
                longPressSpeedActive = longPressSpeed,
                longPressSpeedValue = boostSpeed,
                onLongPressSpeedChange = { longPressSpeed = it },
                chapters = subtitleController.chapters,
                onChapterClick = { chapterDialogVisible = true },
                sponsorSegments = if (sponsorBlockEnabled) {
                    sponsorController.segments.filter { it.category.id in sponsorCategories }
                } else {
                    emptyList()
                },
                sponsorOverlay = { bottomPad, startPad ->
                    if (sponsorBlockEnabled && sponsorSkipMode >= 0) {
                        SponsorBlockPromptOverlay(
                            undoNotice = sponsorController.undoNotice,
                            promptSegment = sponsorController.promptSegment,
                            poiSegment = sponsorController.poiSegment,
                            bottomPad = bottomPad,
                            startPad = startPad,
                            onUndoSkip = { sponsorController.undoSkip(player) },
                            onSkipPrompt = { sponsorController.skipPromptSegment(player) },
                            onDismissPrompt = { sponsorController.dismissPromptSegment() },
                            onJumpPoi = { sponsorController.jumpToPoi(player) },
                            onDismissPoi = { sponsorController.dismissPoi() },
                        )
                    }
                },
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
                overlay = {
                    DanmakuOverlay(
                        player = player,
                        items = danmakuController.items,
                        isPlaying = isPlaying,
                        settings = danmakuSettings,
                    )
                },
            )
            if (fullscreen) {
                VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = true)
                CompositionLocalProvider(
                    LocalBackdrop provides if (BlurEffectsEnabled) stageBackdrop else null,
                ) {
                    VideoScreenDialogs(
                        speedDialogVisible = speedDialogVisible,
                        speed = speed,
                        onSpeedChange = { speed = it; speedDialogVisible = false },
                        onDismissSpeed = { speedDialogVisible = false },
                        subtitleDialogVisible = subtitleDialogVisible,
                        subtitleController = subtitleController,
                        onDismissSubtitle = { subtitleDialogVisible = false },
                        coinDialogVisible = false,
                        coined = state.coined,
                        onSelectCoin = {},
                        onDismissCoin = {},
                        favoriteFolders = null,
                        onSelectFavorite = {},
                        onDismissFavorite = {},
                        qualityDialogVisible = qualityDialogVisible,
                        state = state,
                        onSelectQuality = { qn -> qualityDialogVisible = false; viewModel.selectQuality(qn, player.currentPosition) },
                        onDismissQuality = { qualityDialogVisible = false },
                        danmakuSettingsDialogVisible = danmakuSettingsDialogVisible,
                        danmakuSettings = danmakuSettings,
                        settingsRepository = container.settings,
                        onDismissDanmakuSettings = { danmakuSettingsDialogVisible = false },
                        chapterDialogVisible = chapterDialogVisible,
                        chapters = subtitleController.chapters,
                        positionMs = positionMs,
                        onSelectChapter = { chapter ->
                            chapterDialogVisible = false
                            player.seekTo(chapter.fromMs)
                            if (!player.isPlaying) player.play()
                        },
                        onDismissChapter = { chapterDialogVisible = false },
                        inlineInStage = true,
                    )
                }
            }
        },
        detailContent = {
            VideoDetailSection(
                container = container,
                nav = nav,
                state = state,
                detailPager = detailPager,
                infoListState = infoListState,
                commentListState = commentListState,
                danmakuEnabled = danmakuEnabled,
                danmakuComposing = danmakuComposing,
                danmakuDraft = danmakuDraft,
                onDanmakuDraftChange = { danmakuDraft = it },
                onDanmakuComposingChange = {
                    danmakuComposing = it
                    if (!it) danmakuDraft = ""
                },
                onSendDanmaku = sendDanmaku,
                onSelectPage = { page -> scope.launch { detailPager.animateScrollToPage(page) } },
                onRetry = viewModel::load,
                onSelectPart = { index ->
                    viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
                    viewModel.selectPart(index, player.currentPosition)
                },
                onSelectEpisode = { episode ->
                    val partIndex = state.detail?.pages?.indexOfFirst { it.cid == episode.cid } ?: -1
                    if (episode.bvid == bvid && partIndex >= 0) {
                        viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
                        viewModel.selectPart(partIndex, player.currentPosition)
                    } else {
                        nav.push(Route.Video(episode.bvid, 0L, episode.cid))
                    }
                },
                onOpenQueue = { queueSheetVisible = true },
                onOpenCoin = { coinDialogVisible = true },
                onOpenFavorite = {
                    if (!container.session.isLoggedIn()) {
                        viewModel.showLoginHint()
                    } else {
                        scope.launch {
                            container.favorites.folders(container.session.mid())
                                .onSuccess { favoriteFolders = it }
                                .onFailure { viewModel.showMessage(it.message ?: "收藏夹加载失败") }
                        }
                    }
                },
                onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
                onSeekCommentMs = { ms ->
                    player.seekTo(ms)
                    if (!player.isPlaying) player.play()
                },
                onLike = viewModel::toggleLike,
                onDislike = viewModel::toggleDislike,
                onWatchLater = viewModel::addToWatchLater,
                onFollow = viewModel::toggleFollow,
                onOpenUser = { mid -> if (mid > 0L) nav.push(Route.Space(mid)) },
                onOpenVideo = { next -> nav.push(Route.Video(next)) },
                chapters = subtitleController.chapters,
                positionMs = positionMs,
                onSelectChapter = { chapter ->
                    player.seekTo(chapter.fromMs)
                    if (!player.isPlaying) player.play()
                },
                onOpenChapters = { chapterDialogVisible = true },
            )
        },
        portraitOverlay = {
            VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = false)
        },
        portraitCollapsedBar = {
            val slotHeight = LocalPortraitSlotHeight.current
            CollapsedVideoBar(
                isPlaying = isPlaying,
                onBack = { nav.pop() },
                onHome = { nav.popToMain() },
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onExpand = { slotHeight.floatValue = 0f },
                onDanmakuSettings = { danmakuSettingsDialogVisible = true },
                onWatchLater = viewModel::addToWatchLater,
                autoPlayNext = autoPlayNext,
                onToggleAutoPlayNext = { container.settings.setAutoPlayNext(!autoPlayNext) },
                onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
            )
        },
    )

    if (!fullscreen) {
        VideoScreenDialogs(
            speedDialogVisible = speedDialogVisible,
            speed = speed,
            onSpeedChange = { speed = it; speedDialogVisible = false },
            onDismissSpeed = { speedDialogVisible = false },
            subtitleDialogVisible = subtitleDialogVisible,
            subtitleController = subtitleController,
            onDismissSubtitle = { subtitleDialogVisible = false },
            coinDialogVisible = coinDialogVisible,
            coined = state.coined,
            onSelectCoin = { count -> coinDialogVisible = false; viewModel.coin(count) },
            onDismissCoin = { coinDialogVisible = false },
            favoriteFolders = favoriteFolders,
            onSelectFavorite = { mediaId -> favoriteFolders = null; viewModel.favorite(mediaId) },
            onDismissFavorite = { favoriteFolders = null },
            qualityDialogVisible = qualityDialogVisible,
            state = state,
            onSelectQuality = { qn -> qualityDialogVisible = false; viewModel.selectQuality(qn, player.currentPosition) },
            onDismissQuality = { qualityDialogVisible = false },
            danmakuSettingsDialogVisible = danmakuSettingsDialogVisible,
            danmakuSettings = danmakuSettings,
            settingsRepository = container.settings,
            onDismissDanmakuSettings = { danmakuSettingsDialogVisible = false },
            chapterDialogVisible = chapterDialogVisible,
            chapters = subtitleController.chapters,
            positionMs = positionMs,
            onSelectChapter = { chapter ->
                chapterDialogVisible = false
                player.seekTo(chapter.fromMs)
                if (!player.isPlaying) player.play()
            },
            onDismissChapter = { chapterDialogVisible = false },
            inlineInStage = false,
        )
    }

    PlayQueueSheet(
        visible = queueSheetVisible,
        queue = nav.playQueue,
        onDismiss = { queueSheetVisible = false },
        onPlay = { queued ->
            queueSheetVisible = false
            if (queued.video.bvid != nav.playQueue.playingBvid) {
                nav.playQueue.jumpTo(nav.playQueue.items.indexOf(queued))
                nav.replaceTop(Route.Video(queued.video.bvid, queued.startSec))
            }
        },
    )
}
