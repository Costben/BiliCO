package erl.bilico.feature.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import coil3.compose.AsyncImage
import erl.bilico.data.model.LiveRoomDetail
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.feature.video.QualityDialog
import erl.bilico.ui.components.FullscreenEffect
import erl.bilico.ui.components.LoadingBox
import erl.bilico.ui.components.MessageState
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun LiveRoomScreen(
    container: AppContainer,
    roomId: Long,
    onBack: () -> Unit,
) {
    val viewModel: LiveRoomViewModel = viewModel(key = "live-$roomId") {
        LiveRoomViewModel(container.live, roomId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.message ?: "播放失败"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(state.playback) {
        val playback = state.playback ?: return@LaunchedEffect
        playerError = null
        val headers = container.client.mediaRequestHeaders().toMutableMap().apply {
            put("Referer", "https://live.bilibili.com/")
            put("Origin", "https://live.bilibili.com")
        }
        val dataSourceFactory = OkHttpDataSource.Factory(container.client.mediaClient())
            .setDefaultRequestProperties(headers)
        val mediaSource = if (playback.isHls) {
            HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(playback.url))
        } else {
            ProgressiveMediaSource.Factory(
                dataSourceFactory,
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
            ).createMediaSource(MediaItem.fromUri(playback.url))
        }
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    FullscreenEffect(fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val configuration = LocalConfiguration.current
    val playerHeight = if (fullscreen) {
        configuration.screenHeightDp.dp
    } else {
        minOf(
            configuration.screenWidthDp.dp * 9f / 16f,
            configuration.screenHeightDp.dp * 0.7f,
        )
    }

    Box(Modifier.fillMaxSize().background(COUITheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fullscreen) Modifier else Modifier.statusBarsPadding()),
        ) {
            Box(Modifier.fillMaxWidth().height(playerHeight)) {
                LivePlayer(
                    player = player,
                    title = state.detail?.title.orEmpty(),
                    isPlaying = isPlaying,
                    isBuffering = state.isPlaybackLoading || isBuffering,
                    errorMessage = state.playbackError ?: playerError,
                    statusText = playerStatus(state),
                    fullscreen = fullscreen,
                    onPlayPause = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onQualityClick = { qualityDialogVisible = true },
                    onBack = {
                        if (fullscreen) fullscreen = false else onBack()
                    },
                    onRetry = viewModel::retryPlayback,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (!fullscreen) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    when {
                        state.isLoading -> LoadingBox(Modifier.height(160.dp))
                        state.error != null -> MessageState(
                            message = state.error.orEmpty(),
                            action = {
                                Button(onClick = viewModel::load) { Text("重试") }
                            },
                        )
                        else -> state.detail?.let { LiveRoomInfo(it) }
                    }
                }
            }
        }
    }

    val playback = state.playback
    if (qualityDialogVisible && playback != null && playback.qualities.isNotEmpty()) {
        QualityDialog(
            qualities = playback.qualities,
            activeQuality = state.activeQuality,
            onSelect = { qn ->
                qualityDialogVisible = false
                viewModel.selectQuality(qn)
            },
            onDismiss = { qualityDialogVisible = false },
        )
    }
}

@Composable
private fun LiveRoomInfo(detail: LiveRoomDetail) {
    val density = LocalDensity.current
    val avatarPx = with(density) { 40.dp.roundToPx() }
    val avatarUrl = remember(detail.anchorFace) {
        Formatters.sizedImageUrl(detail.anchorFace, avatarPx, avatarPx)
    }

    Text(
        text = detail.title,
        style = COUITheme.textStyles.title3,
    )

    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (detail.anchorFace.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
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
        Spacer(Modifier.height(14.dp))
        Text(
            text = detail.description,
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceSecondary,
        )
    }
}

private fun playerStatus(state: LiveRoomUiState): String {
    val detail = state.detail ?: return ""
    if (!detail.isLive) return "未开播"
    val playback = state.playback ?: return "加载中"
    val label = playback.qualities.firstOrNull { it.quality == state.activeQuality }?.label
    return label?.takeIf { it.isNotBlank() } ?: "直播中"
}
