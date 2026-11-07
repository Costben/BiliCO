package erl.bilico.feature.pgc

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import erl.bilico.di.AppContainer
import erl.bilico.feature.comment.CommentTab
import erl.bilico.feature.video.DanmakuController
import erl.bilico.feature.video.DanmakuOverlay
import erl.bilico.feature.video.DanmakuSettingsDialog
import erl.bilico.feature.video.MANUAL_FULLSCREEN_COOLDOWN_MS
import erl.bilico.feature.video.PlaybackAttempts
import erl.bilico.feature.video.QualityDialog
import erl.bilico.feature.video.SpeedDialog
import erl.bilico.feature.video.SubtitleController
import erl.bilico.feature.video.SubtitleDialog
import erl.bilico.feature.video.SubtitleOverlay
import erl.bilico.feature.video.VideoFullscreenStage
import erl.bilico.feature.video.VideoMessageToast
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
import erl.bilico.ui.Route
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

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
    val darkMode by container.settings.darkMode.collectAsStateWithLifecycle()
    val gestureHaptic by container.settings.gestureHaptic.collectAsStateWithLifecycle()
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }
    var subtitleDialogVisible by remember { mutableStateOf(false) }
    var danmakuSettingsDialogVisible by remember { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var longPressSpeed by remember { mutableStateOf(false) }

    val boostSpeed = if (speed >= 2f) 3f else 2f
    val effectiveSpeed = if (longPressSpeed) boostSpeed else speed

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    val playbackAttempts = remember(player) { PlaybackAttempts(player) { effectiveSpeed } }

    val episode = viewModel.currentEpisode
    BindPlaybackSession(
        player = player,
        title = state.detail?.title.orEmpty(),
        subtitle = episode?.title.orEmpty(),
        isPlaying = isPlaying,
        backgroundPlay = backgroundPlay,
        autoPip = autoPip,
    )

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    viewModel.markPlaybackStarted()
                } else if (playbackState == Player.STATE_ENDED) {
                    viewModel.flushHeartbeat(player.duration.coerceAtLeast(0L) / 1000, container.scope)
                    viewModel.playNextEpisode()
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
            player.release()
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
        playerError = null
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

    val message = state.message
    if (message != null) {
        LaunchedEffect(message) {
            delay(2600)
            viewModel.consumeMessage()
        }
    }

    @Composable
    fun RenderPgcDialogs(inlineInStage: Boolean) {
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
        if (subtitleDialogVisible) {
            SubtitleDialog(
                tracks = subtitleController.tracks,
                selectedKey = subtitleController.selectedTrackKey,
                onSelect = { key ->
                    subtitleController.selectTrack(key)
                    subtitleDialogVisible = false
                },
                onDismiss = { subtitleDialogVisible = false },
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
        val source = state.source
        if (qualityDialogVisible && source != null) {
            QualityDialog(
                qualities = source.qualities,
                activeQuality = state.activeQuality,
                onSelect = { qn ->
                    qualityDialogVisible = false
                    viewModel.selectQuality(qn, player.currentPosition)
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
                positionMs = positionMs,
                durationMs = durationMs.takeIf { it > 0L } ?: state.source?.durationMs ?: 0L,
                isBuffering = state.isPlaybackLoading || isBuffering,
                errorMessage = state.playbackError ?: playerError,
                qualityLabel = state.qualityLabel(),
                speed = speed,
                fullscreen = fullscreen,
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
                onRetry = viewModel::retryPlayback,
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
                RenderPgcDialogs(inlineInStage = true)
            }
        },
        detailContent = {
            val navBarBottom = rememberStableNavBarBottomDp()
            Column(modifier = Modifier.fillMaxSize()) {
                PgcTabRow(selected = tab, onSelect = { tab = it })

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = navBarBottom),
                ) {
                    when (tab) {
                        0 -> PgcInfoTab(
                            detail = state.detail,
                            isLoading = state.isLoading,
                            error = state.error,
                            selectedEpIndex = state.selectedEpIndex,
                            followed = state.followed,
                            onToggleFollow = viewModel::toggleFollow,
                            onRetry = viewModel::load,
                            onSelectEpisode = { index ->
                                viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
                                viewModel.selectEpisode(index)
                            },
                            onSelectSeason = { id -> nav.push(Route.Pgc(id, 0L)) },
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> {
                            val aid = episode?.aid ?: 0L
                            if (aid > 0L) {
                                CommentTab(
                                    container = container,
                                    oid = aid,
                                    onOpenUser = { mid ->
                                        if (mid > 0L) nav.push(Route.Space(mid))
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        },
        portraitOverlay = {
            VideoMessageToast(message = message, inPipMode = inPipMode, inFullscreen = false)
        },
    )

    if (!fullscreen) {
        RenderPgcDialogs(inlineInStage = false)
    }
}

@Composable
private fun PgcTabRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TABS.forEachIndexed { index, label ->
            val active = selected == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = COUITheme.textStyles.body1,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) {
                        COUITheme.colorScheme.onSurface
                    } else {
                        COUITheme.colorScheme.onSurfaceSecondary
                    },
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) COUITheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

private fun PgcUiState.qualityLabel(): String? {
    val source = source ?: return null
    val active = source.qualities.firstOrNull { it.quality == activeQuality }
    return active?.label?.takeIf { it.isNotBlank() } ?: "${activeQuality}P"
}

private val TABS = listOf("简介", "评论")
