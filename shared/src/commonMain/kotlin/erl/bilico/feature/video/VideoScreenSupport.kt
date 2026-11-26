package erl.bilico.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.model.FeedVideo
import erl.bilico.data.model.PlaybackEndMode
import erl.bilico.data.model.VideoChapter
import erl.bilico.data.model.VideoDetail
import erl.bilico.data.model.VideoEpisode
import erl.bilico.data.repository.BlockSnapshot
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.di.AppContainer
import erl.bilico.platform.LocalScreenConfig
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.rememberDeviceOrientationSide
import erl.bilico.platform.shareText
import erl.bilico.platform.uptimeMillis
import erl.bilico.player.BiliPlayer
import erl.bilico.player.BiliPlayerListener
import erl.bilico.player.PlaybackBridge
import erl.bilico.player.PlayerStates
import erl.bilico.player.hasPlayIntent
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

internal const val MANUAL_FULLSCREEN_COOLDOWN_MS = 3000L

internal fun VideoUiState.qualityLabel(): String? {
    val source = source ?: return null
    val active = source.qualities.firstOrNull { it.quality == activeQuality }
    return active?.label?.takeIf { it.isNotBlank() } ?: "${activeQuality}P"
}

internal val DETAIL_PAGES = listOf("info", "comment")

@Stable
internal class PlayerFullscreenController(
    val fullscreen: Boolean,
    val portraitStage: Boolean,
    val deviceSide: Int,
    val applyFullscreen: (Boolean) -> Unit,
)

@Composable
internal fun rememberPlayerFullscreenController(
    videoAspect: Float,
    autoRotateMode: Int,
    inPipMode: Boolean,
    orientationEnabled: Boolean = true,
): PlayerFullscreenController {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val windowLandscape = LocalScreenConfig.current.isLandscape
    val portraitStage = isPortraitVideoStage(videoAspect, fullscreen && windowLandscape)
    val allowAutoLandscape = videoAspect >= 1f
    val deviceSide = rememberDeviceOrientationSide(if (orientationEnabled) autoRotateMode else 0)
    var manualFullscreenExit by rememberSaveable { mutableStateOf(false) }
    var lastManualActionTime by remember { mutableLongStateOf(0L) }
    var manualEnterAwaitingLandscape by remember { mutableStateOf(false) }
    val applyFullscreen: (Boolean) -> Unit = { value ->
        lastManualActionTime = uptimeMillis()
        manualFullscreenExit = !value
        manualEnterAwaitingLandscape = value && allowAutoLandscape && deviceSide == 0
        fullscreen = value
    }

    var wasLandscape by remember { mutableStateOf(deviceSide != 0) }
    LaunchedEffect(
        deviceSide,
        manualFullscreenExit,
        manualEnterAwaitingLandscape,
        allowAutoLandscape,
        inPipMode,
    ) {
        if (inPipMode) return@LaunchedEffect
        if (!allowAutoLandscape) {
            manualEnterAwaitingLandscape = false
            wasLandscape = deviceSide != 0
            return@LaunchedEffect
        }
        val now = uptimeMillis()
        val inCooldown = lastManualActionTime > 0L &&
            now - lastManualActionTime < MANUAL_FULLSCREEN_COOLDOWN_MS
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

    return PlayerFullscreenController(
        fullscreen = fullscreen,
        portraitStage = portraitStage,
        deviceSide = deviceSide,
        applyFullscreen = applyFullscreen,
    )
}

internal fun shareVideo(context: PlatformContext, title: String, bvid: String) {
    shareText(context, "$title\nhttps://www.bilibili.com/video/$bvid", "分享视频")
}

@Composable
internal fun VideoScreenDialogs(
    coinDialogVisible: Boolean,
    coined: Int,
    onSelectCoin: (Int) -> Unit,
    onDismissCoin: () -> Unit,
    favoriteFolders: List<FavoriteFolder>?,
    onConfirmFavorite: (addMediaIds: List<Long>, delMediaIds: List<Long>, nowFavorited: Boolean) -> Unit,
    onDismissFavorite: () -> Unit,
    onCreateFavoriteFolder: (suspend (title: String, intro: String, isPrivate: Boolean) -> Result<FavoriteFolder>)? = null,
    danmakuSettingsDialogVisible: Boolean = false,
    danmakuSettings: DanmakuSettings? = null,
    settingsRepository: SettingsRepository? = null,
    onDismissDanmakuSettings: () -> Unit = {},
    chapterDialogVisible: Boolean = false,
    chapters: List<VideoChapter> = emptyList(),
    positionMs: Long = 0L,
    onSelectChapter: (VideoChapter) -> Unit = {},
    onDismissChapter: () -> Unit = {},
    sponsorSubmitDialog: (@Composable (inlineInStage: Boolean) -> Unit)? = null,
    castDialog: (@Composable (inlineInStage: Boolean) -> Unit)? = null,
    downloadDialog: (@Composable (inlineInStage: Boolean) -> Unit)? = null,
    reportVideoDialog: (@Composable (inlineInStage: Boolean) -> Unit)? = null,
    inlineInStage: Boolean = false,
) {
    if (danmakuSettingsDialogVisible && danmakuSettings != null && settingsRepository != null) {
        DanmakuSettingsDialog(
            settings = danmakuSettings,
            repository = settingsRepository,
            onDismiss = onDismissDanmakuSettings,
            inlineInStage = inlineInStage,
        )
    }

    if (!inlineInStage && coinDialogVisible) {
        CoinDialog(
            remaining = (2 - coined).coerceIn(1, 2),
            onSelect = onSelectCoin,
            onDismiss = onDismissCoin,
        )
    }

    if (!inlineInStage) {
        favoriteFolders?.let { folders ->
            FavoriteFolderDialog(
                folders = folders,
                onConfirm = onConfirmFavorite,
                onDismiss = onDismissFavorite,
                onCreateFolder = onCreateFavoriteFolder,
            )
        }
    }

    if (chapterDialogVisible && chapters.isNotEmpty()) {
        ChapterDialog(
            chapters = chapters,
            positionMs = positionMs,
            onSelectChapter = onSelectChapter,
            onDismiss = onDismissChapter,
            inlineInStage = inlineInStage,
        )
    }

    sponsorSubmitDialog?.invoke(inlineInStage)
    castDialog?.invoke(inlineInStage)
    downloadDialog?.invoke(inlineInStage)
    reportVideoDialog?.invoke(inlineInStage)
}

@Composable
internal fun rememberSponsorSubmitDialog(
    visible: Boolean,
    bvid: String,
    cid: Long,
    durationMs: Long,
    currentPositionMs: () -> Long,
    controller: SponsorBlockController,
    sponsorUserIdProvider: () -> String,
    onSuccess: (String) -> Unit,
    onDismiss: () -> Unit,
): (@Composable (Boolean) -> Unit)? = if (visible && cid > 0L) {
    { inlineInStage ->
        SponsorSubmitDialog(
            bvid = bvid,
            cid = cid,
            durationMs = durationMs,
            currentPositionMs = currentPositionMs,
            controller = controller,
            sponsorUserIdProvider = sponsorUserIdProvider,
            inlineInStage = inlineInStage,
            onSuccess = onSuccess,
            onDismiss = onDismiss,
        )
    }
} else null

@Composable
internal fun rememberCastDialog(
    visible: Boolean,
    bvid: String,
    cid: Long,
    qn: Int,
    title: String,
    positionProvider: () -> Long,
    onDismiss: () -> Unit,
): (@Composable (Boolean) -> Unit)? = if (visible && cid > 0L) {
    { inlineInStage ->
        erl.bilico.feature.cast.CastSheet(
            request = erl.bilico.feature.cast.CastMediaRequest(
                title = title,
                bvid = bvid,
                cid = cid,
                qn = qn,
                initialPositionMs = positionProvider(),
            ),
            inlineInStage = inlineInStage,
            onDismiss = onDismiss,
        )
    }
} else null

@Composable
internal fun rememberReportVideoDialog(
    visible: Boolean,
    aid: Long,
    bvid: String,
    title: String,
    onDismiss: () -> Unit,
): (@Composable (Boolean) -> Unit)? = if (visible && aid > 0L) {
    { inlineInStage ->
        erl.bilico.ui.components.ReportDialog(
            target = erl.bilico.ui.components.ReportTarget.Video(
                aid = aid,
                bvid = bvid,
                title = title,
            ),
            inlineInStage = inlineInStage,
            onDismiss = onDismiss,
        )
    }
} else null

@Composable
internal fun BindPlayerEvents(
    player: BiliPlayer,
    viewModel: VideoViewModel,
    container: AppContainer,
    onPlayingChanged: (Boolean) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onPlayIntentChanged: (Boolean) -> Unit = {},
    onError: (String) -> Unit,
    onVideoAspectChanged: (Float) -> Unit = {},
    onPlaybackEnded: (() -> Unit)? = null,
    onPlaybackLeaveEnded: (() -> Unit)? = null,
) {
    val currentOnPlaying by rememberUpdatedState(onPlayingChanged)
    val currentOnBuffering by rememberUpdatedState(onBufferingChanged)
    val currentOnPlayIntent by rememberUpdatedState(onPlayIntentChanged)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnAspect by rememberUpdatedState(onVideoAspectChanged)
    val currentOnEnded by rememberUpdatedState(onPlaybackEnded)
    val currentOnLeaveEnded by rememberUpdatedState(onPlaybackLeaveEnded)
    val currentViewModel by rememberUpdatedState(viewModel)

    DisposableEffect(player) {
        currentOnPlaying(player.isPlaying)
        currentOnBuffering(player.playbackState == PlayerStates.STATE_BUFFERING)
        currentOnPlayIntent(player.hasPlayIntent)
        val initialSize = player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            currentOnAspect(initialSize.width.toFloat() / initialSize.height.toFloat())
        }
        val listener = object : BiliPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                currentOnPlaying(isPlaying)
                if (isPlaying) currentOnLeaveEnded?.invoke()
            }

            override fun onPlayIntentChanged(hasPlayIntent: Boolean) {
                currentOnPlayIntent(hasPlayIntent)
            }

            override fun onVideoSizeChanged(width: Int, height: Int) {
                if (width > 0 && height > 0) {
                    currentOnAspect(width.toFloat() / height.toFloat())
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                currentOnBuffering(state == PlayerStates.STATE_BUFFERING)
                currentOnPlayIntent(player.hasPlayIntent)
                if (state == PlayerStates.STATE_READY) {
                    currentViewModel.markPlaybackStarted()
                } else if (state == PlayerStates.STATE_ENDED) {
                    currentViewModel.flushHeartbeat(player.duration.coerceAtLeast(0L) / 1000, container.scope)
                    val ended = currentOnEnded
                    if (ended != null) ended() else currentViewModel.playNextPart()
                }
                if (state != PlayerStates.STATE_ENDED) currentOnLeaveEnded?.invoke()
            }

            override fun onPlayerError(message: String) {
                currentOnError(message)
            }
        }
        player.addListener(listener)
        onDispose {
            currentViewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
            player.removeListener(listener)
        }
    }
}

@Composable
internal fun BoxScope.VideoMessageToast(
    message: String?,
    inPipMode: Boolean,
    inFullscreen: Boolean = false,
) {
    if (message != null && !inPipMode) {
        Text(
            text = message,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (inFullscreen) Modifier else Modifier.navigationBarsPadding())
                .padding(bottom = if (inFullscreen) 64.dp else 96.dp)
                .clip(SquircleShape(8.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = COUITheme.textStyles.footnote1,
            color = Color.White,
        )
    }
}

/** 在合集剧集列表里定位当前正在播放的条目索引。 */
internal fun findCurrentCollectionEpisodeIndex(
    episodes: List<VideoEpisode>,
    currentBvid: String,
    currentCid: Long,
): Int {
    if (episodes.isEmpty()) return -1
    if (currentCid > 0L) {
        val exact = episodes.indexOfFirst { it.bvid == currentBvid && it.cid == currentCid }
        if (exact >= 0) return exact
    }
    val byBvidNoCid = episodes.indexOfFirst { it.bvid == currentBvid && it.cid <= 0L }
    if (byBvidNoCid >= 0) return byBvidNoCid
    // 同一视频有多个分 P，合集只登记了首 P 的 cid：播到末 P 时按 bvid 回退定位当前集。
    val byBvid = episodes.indexOfLast { it.bvid == currentBvid }
    if (byBvid >= 0) return byBvid
    return if (currentCid > 0L) episodes.indexOfFirst { it.cid == currentCid } else -1
}

/** 按自动连播或列表循环规则解析合集里的下一集；不在合集内或已是末集且不循环时返回 null。 */
internal fun resolveNextCollectionEpisode(
    detail: VideoDetail?,
    currentBvid: String,
    currentCid: Long,
    loop: Boolean,
): VideoEpisode? {
    val episodes = detail?.collection?.episodes ?: return null
    if (episodes.size <= 1) return null
    val resolvedBvid = detail.bvid.ifBlank { currentBvid }
    val currentIndex = findCurrentCollectionEpisodeIndex(episodes, resolvedBvid, currentCid)
    if (currentIndex < 0) return null
    val nextIndex = currentIndex + 1
    return when {
        nextIndex in episodes.indices -> episodes[nextIndex]
        loop -> episodes.first()
        else -> null
    }
}

private fun playNextCollectionEpisode(
    state: VideoUiState,
    bvid: String,
    loop: Boolean,
    player: BiliPlayer,
    viewModel: VideoViewModel,
    nav: NavState,
    onAutoNavigateVideo: ((String) -> Unit)?,
): Boolean {
    val detail = state.detail ?: return false
    val resolvedBvid = detail.bvid.ifBlank { bvid }
    val currentCid = state.selectedCid.takeIf { it > 0L } ?: detail.cid
    val nextEpisode = resolveNextCollectionEpisode(detail, resolvedBvid, currentCid, loop) ?: return false
    if (nextEpisode.bvid == resolvedBvid) {
        val targetCid = nextEpisode.cid.takeIf { it > 0L } ?: detail.pages.firstOrNull()?.cid ?: 0L
        if (targetCid > 0L && targetCid != currentCid && viewModel.playPartByCid(targetCid)) {
            return true
        }
        if (loop) {
            player.seekTo(0L)
            player.play()
            return true
        }
        return false
    }
    onAutoNavigateVideo?.invoke(nextEpisode.bvid)
    nav.replaceTop(Route.Video(nextEpisode.bvid, 0L, nextEpisode.cid))
    return true
}

internal fun handleVideoPlaybackEnded(
    mode: PlaybackEndMode,
    player: BiliPlayer,
    viewModel: VideoViewModel,
    nav: NavState,
    state: VideoUiState,
    bvid: String = viewModel.currentBvid,
    blocks: BlockSnapshot = BlockSnapshot(),
    onPromptAutoNextRecommend: ((FeedVideo) -> Unit)? = null,
    onAutoNavigateVideo: ((String) -> Unit)? = null,
) {
    PlaybackBridge.activeMediaId.takeIf { it.isNotEmpty() }?.let(PlaybackPositions::forget)
    val liveState = if (viewModel.currentBvid == bvid) viewModel.state.value else state
    when (mode) {
        PlaybackEndMode.OFF -> Unit
        PlaybackEndMode.SINGLE_LOOP -> {
            player.seekTo(0L)
            player.play()
        }
        PlaybackEndMode.LIST_LOOP -> {
            if (viewModel.playNextPart(loop = false)) return
            val queued = nav.playQueue.next(loop = true)
            if (queued != null) {
                onAutoNavigateVideo?.invoke(queued.video.bvid)
                nav.replaceTop(Route.Video(queued.video.bvid, queued.startSec))
                return
            }
            if (playNextCollectionEpisode(liveState, bvid, loop = true, player, viewModel, nav, onAutoNavigateVideo)) return
            if (viewModel.playNextPart(loop = true)) return
            player.seekTo(0L)
            player.play()
        }
        PlaybackEndMode.AUTO_NEXT -> {
            if (viewModel.playNextPart(loop = false)) return
            val queued = nav.playQueue.next()
            if (queued != null) {
                onAutoNavigateVideo?.invoke(queued.video.bvid)
                nav.replaceTop(Route.Video(queued.video.bvid, queued.startSec))
                return
            }
            if (playNextCollectionEpisode(liveState, bvid, loop = false, player, viewModel, nav, onAutoNavigateVideo)) return
            val next = liveState.related.firstOrNull { candidate ->
                candidate.bvid != bvid && blocks.shouldShowFeedVideo(candidate, isRelated = true)
            } ?: return
            if (onPromptAutoNextRecommend != null) {
                onPromptAutoNextRecommend(next)
            } else {
                onAutoNavigateVideo?.invoke(next.bvid)
                nav.replaceTop(Route.Video(next.bvid, 0L))
            }
        }
    }
}
