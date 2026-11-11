package erl.bilico.feature.video

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.CommentTab
import erl.bilico.player.BindPlaybackSession
import erl.bilico.player.LocalInPipMode
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.buildConfiguredExoPlayer
import erl.bilico.ui.BlurEffectsEnabled
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.layerBackdropIf
import io.github.suqi8.coui.kmp.basic.FloatingActionButton
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Playlist
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
    val longPressSpeedPercent by container.settings.longPressSpeed.collectAsStateWithLifecycle()
    val autoRotateMode by container.settings.autoRotateMode.collectAsStateWithLifecycle()
    val gestureVolBright by container.settings.gestureVolumeBrightness.collectAsStateWithLifecycle()
    val fullscreenGestureHint by container.settings.fullscreenGestureHint.collectAsStateWithLifecycle()
    val inPipMode = LocalInPipMode.current

    val scope = rememberCoroutineScope()
    val danmakuController = remember { DanmakuController(container.danmaku, scope) }
    val subtitleController = remember { SubtitleController(container.subtitles, scope) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val player = remember { buildConfiguredExoPlayer(context) }

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    // 视频本身的宽高比（宽 / 高），来自解码器上报的首帧尺寸；0 表示还没拿到。
    var videoAspect by remember { mutableFloatStateOf(0f) }
    // 竖屏视频的全屏方向跟着视频走：窗口不转，画面在竖屏里放到最大。
    val portraitVideo = videoAspect > 0f && videoAspect < 1f
    val deviceSide = rememberDeviceOrientationSide(autoRotateMode)
    // 用户主动退出全屏、而手机还横着时，先锁竖屏，等手机真的竖过来再交还给传感器。
    var manualFullscreenExit by rememberSaveable { mutableStateOf(false) }
    var lastManualActionTime by remember { mutableLongStateOf(0L) }
    var manualEnterAwaitingLandscape by remember { mutableStateOf(false) }
    val applyFullscreen: (Boolean) -> Unit = { value ->
        lastManualActionTime = SystemClock.uptimeMillis()
        manualFullscreenExit = !value
        manualEnterAwaitingLandscape = value && !portraitVideo && deviceSide == 0
        fullscreen = value
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 简介 / 评论两页横向翻。页签跟着 currentPage 走：手指把页面拖过半就切，
    // 和私信页一致；点标签时直接开翻页动画，不再绕一圈回写状态。
    val detailPager = rememberPagerState(initialPage = tab, pageCount = { DETAIL_PAGES.size })
    LaunchedEffect(detailPager) {
        snapshotFlow { detailPager.currentPage }
            .distinctUntilChanged()
            .collect { tab = it }
    }
    val infoListState = rememberLazyListState()
    val commentListState = rememberLazyListState()
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var queueSheetVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }
    var subtitleDialogVisible by remember { mutableStateOf(false) }
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
        onVideoAspectChanged = { videoAspect = it },
        onPlaybackEnded = {
            // 先看当前视频还有没有下一 P；没有就顺着「播放全部」的队列往下走。
            if (!viewModel.playNextPart()) {
                nav.playQueue.next()?.let { next ->
                    nav.replaceTop(Route.Video(next.video.bvid, next.startSec))
                }
            }
        },
    )

    LaunchedEffect(bvid) { nav.playQueue.sync(bvid) }

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
            delay(500)
        }
    }

    // 手机横过来就进全屏；只有「横屏转回竖屏」才退出。
    // 手势或按钮手动触发全屏后，享受冷却与物理朝向豁免保护，防止手机还没转过来就被强行切回。
    // 竖屏视频不受手机朝向影响：全屏方向由视频决定，转手机不进也不退。
    var wasLandscape by remember { mutableStateOf(deviceSide != 0) }
    LaunchedEffect(deviceSide, manualFullscreenExit, manualEnterAwaitingLandscape, portraitVideo) {
        if (inPipMode) return@LaunchedEffect
        if (portraitVideo) {
            manualEnterAwaitingLandscape = false
            wasLandscape = deviceSide != 0
            return@LaunchedEffect
        }
        val now = SystemClock.uptimeMillis()
        val inCooldown = now - lastManualActionTime < MANUAL_FULLSCREEN_COOLDOWN_MS

        if (deviceSide != 0) {
            if (manualEnterAwaitingLandscape) {
                manualEnterAwaitingLandscape = false
            }
            if (manualFullscreenExit && !inCooldown) {
                manualFullscreenExit = false
            }
            if (!manualFullscreenExit && !inCooldown) {
                if (!fullscreen) fullscreen = true
            }
        } else {
            if (manualFullscreenExit) {
                manualFullscreenExit = false
            }
            if (wasLandscape && !inCooldown && !manualEnterAwaitingLandscape) {
                if (fullscreen) fullscreen = false
            }
        }
        wasLandscape = deviceSide != 0
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
        videoAspect = videoAspect,
        portraitResizable = true,
        playing = isPlaying,
        canExpand = {
            val list = if (tab == 0) infoListState else commentListState
            list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
        },
        playerContent = {
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
                portraitFullscreen = fullscreen && portraitVideo,
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
                modifier = Modifier.fillMaxSize(),
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
                VideoScreenDialogs(
                    speedDialogVisible = speedDialogVisible,
                    speed = speed,
                    onSpeedChange = { value ->
                        speed = value
                        speedDialogVisible = false
                    },
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
                    onSelectQuality = { qn ->
                        qualityDialogVisible = false
                        viewModel.selectQuality(qn, player.currentPosition)
                    },
                    onDismissQuality = { qualityDialogVisible = false },
                    danmakuSettingsDialogVisible = danmakuSettingsDialogVisible,
                    danmakuSettings = danmakuSettings,
                    settingsRepository = container.settings,
                    onDismissDanmakuSettings = { danmakuSettingsDialogVisible = false },
                    inlineInStage = true,
                )
            }
        },
        detailContent = {
            val navBarBottom = rememberStableNavBarBottomDp()
            val detailBackdrop = rememberCouiBackdrop()
            val detailLayerBackdrop = rememberLayerBackdrop()
            val pageBg = pageContainerColor()
            // 列表整块铺到最上面，滚动时正文从标签栏底下穿过去，由栏的毛玻璃盖住；
            // 静止时靠列表自己的顶部内边距让开这一条，第一行不会被栏压住。
            val contentTop = DETAIL_BAR_HEIGHT + 12.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageBg),
            ) {
                HorizontalPager(
                    state = detailPager,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = navBarBottom)
                        .layerBackdropIf(BlurEffectsEnabled, detailLayerBackdrop)
                        .couiBackdrop(detailBackdrop)
                        .background(pageBg),
                    beyondViewportPageCount = 0,
                    key = { DETAIL_PAGES[it] },
                ) { page ->
                    when (page) {
                        0 -> Box(Modifier.fillMaxSize()) {
                            VideoInfoTab(
                                detail = state.detail,
                                isLoading = state.isLoading,
                                error = state.error,
                                selectedCid = state.selectedCid,
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
                                onOpenUser = { mid -> if (mid > 0L) nav.push(Route.Space(mid)) },
                                onOpenVideo = { next -> nav.push(Route.Video(next)) },
                                related = state.related,
                                container = container,
                                liked = state.liked,
                                disliked = state.disliked,
                                coined = state.coined,
                                favorited = state.favorited,
                                following = state.following,
                                upFollowers = state.upFollowers,
                                onLike = viewModel::toggleLike,
                                onDislike = viewModel::toggleDislike,
                                onCoin = { coinDialogVisible = true },
                                onFavorite = {
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
                                onWatchLater = viewModel::addToWatchLater,
                                onFollow = viewModel::toggleFollow,
                                onShare = { shareVideo(context, state.detail?.title.orEmpty(), bvid) },
                                listState = infoListState,
                                topInset = contentTop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (nav.playQueue.items.isNotEmpty()) {
                                FloatingActionButton(
                                    onClick = { queueSheetVisible = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .semantics { contentDescription = "播放队列" },
                                ) {
                                    Icon(
                                        imageVector = COUIIcons.Playlist,
                                        contentDescription = "播放队列",
                                        modifier = Modifier.size(24.dp),
                                        tint = COUITheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }

                        else -> {
                            val aid = state.detail?.aid ?: 0L
                            if (aid > 0L) {
                                CommentTab(
                                    container = container,
                                    oid = aid,
                                    onOpenUser = { mid ->
                                        if (mid > 0L) nav.push(Route.Space(mid))
                                    },
                                    listState = commentListState,
                                    topInset = contentTop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                DetailBarGlassSurface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    containerColor = pageBg,
                    backdrop = if (BlurEffectsEnabled) detailLayerBackdrop else null,
                ) {
                    VideoDetailBar(
                        selected = tab,
                        onSelect = { page -> scope.launch { detailPager.animateScrollToPage(page) } },
                        commentCount = state.detail?.reply ?: 0L,
                        danmakuEnabled = danmakuEnabled,
                        onToggleDanmaku = { container.settings.setDanmakuEnabled(!danmakuEnabled) },
                        composing = danmakuComposing,
                        draft = danmakuDraft,
                        onDraftChange = { danmakuDraft = it },
                        onComposingChange = {
                            danmakuComposing = it
                            if (!it) danmakuDraft = ""
                        },
                        onSend = sendDanmaku,
                        backdrop = detailBackdrop,
                    )
                }
            }
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
