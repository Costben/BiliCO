package erl.bilico.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.OrientationEventListener
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.di.AppContainer
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * ColorOS 17 专属视频转屏动画参数 (逆向自 com.oplus.animation.OplusVideoRotateAnimation)
 * 时长：450ms
 * 曲线：COUIMoveEaseInterpolator (0.3f, 0.0f, 0.1f, 1.0f)
 */
internal const val COLOROS_VIDEO_ROTATE_DURATION_MS = 450

internal val ColorOSMoveEase = CubicBezierEasing(0.3f, 0.0f, 0.1f, 1.0f)

/** 手动/手势切换全屏后的重力感应冷却保护时长 (毫秒) */
internal const val MANUAL_FULLSCREEN_COOLDOWN_MS = 3000L

internal fun VideoUiState.qualityLabel(): String? {
    val source = source ?: return null
    val active = source.qualities.firstOrNull { it.quality == activeQuality }
    return active?.label?.takeIf { it.isNotBlank() } ?: "${activeQuality}P"
}

internal val DETAIL_PAGES = listOf("info", "comment")

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 物理横竖屏方向，带 500ms 持续确认防抖、双门限迟滞与前后台生命周期启停：
 * - `0`：竖屏
 * - `+1`：逆时针横屏（听筒在左，约 270°，对应自绘顺时针旋转 `+90°`）
 * - `-1`：顺时针反向横屏（听筒在右，约 90°，对应自绘逆时针旋转 `-90°`）
 */
@Composable
internal fun rememberDeviceOrientationSide(mode: Int): Int {
    if (mode == 0) return 0
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var side by remember { mutableIntStateOf(0) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var pendingRunnable by remember { mutableStateOf<Runnable?>(null) }
    var pendingTarget by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(context, lifecycleOwner, mode) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val delta = when (mode) {
                    1 -> 30 // 迟钝防误触：60..120 / 240..300
                    2 -> 38 // 标准：52..128 / 232..308
                    else -> 45 // 灵敏：45..135 / 225..315
                }
                val inStandardLandscape = orientation in (270 - delta)..(270 + delta)
                val inReverseLandscape = orientation in (90 - delta)..(90 + delta)
                val inPortrait = orientation in 0..(90 - delta - 10) ||
                    orientation in (360 - (90 - delta - 10))..359 ||
                    orientation in (90 + delta + 10)..(270 - delta - 10)

                val target: Int? = when {
                    inStandardLandscape -> 1
                    inReverseLandscape -> -1
                    inPortrait -> 0
                    else -> null
                }

                if (target == null || target == side) {
                    pendingRunnable?.let { handler.removeCallbacks(it) }
                    pendingRunnable = null
                    pendingTarget = null
                } else if (pendingTarget != target) {
                    pendingRunnable?.let { handler.removeCallbacks(it) }
                    pendingTarget = target
                    val run = Runnable {
                        side = target
                        pendingRunnable = null
                        pendingTarget = null
                    }
                    pendingRunnable = run
                    handler.postDelayed(run, 500L)
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (listener.canDetectOrientation()) listener.enable()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    pendingRunnable?.let { handler.removeCallbacks(it) }
                    pendingRunnable = null
                    pendingTarget = null
                    listener.disable()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            listener.canDetectOrientation()
        ) {
            listener.enable()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pendingRunnable?.let { handler.removeCallbacks(it) }
            listener.disable()
        }
    }
    return side
}

internal fun shareVideo(context: Context, title: String, bvid: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$title\nhttps://www.bilibili.com/video/$bvid")
    }
    context.startActivity(Intent.createChooser(intent, "分享视频"))
}

@Composable
internal fun VideoScreenDialogs(
    speedDialogVisible: Boolean,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismissSpeed: () -> Unit,
    subtitleDialogVisible: Boolean,
    subtitleController: SubtitleController,
    onDismissSubtitle: () -> Unit,
    coinDialogVisible: Boolean,
    coined: Int,
    onSelectCoin: (Int) -> Unit,
    onDismissCoin: () -> Unit,
    favoriteFolders: List<FavoriteFolder>?,
    onSelectFavorite: (Long) -> Unit,
    onDismissFavorite: () -> Unit,
    qualityDialogVisible: Boolean,
    state: VideoUiState,
    onSelectQuality: (Int) -> Unit,
    onDismissQuality: () -> Unit,
    danmakuSettingsDialogVisible: Boolean = false,
    danmakuSettings: DanmakuSettings? = null,
    settingsRepository: SettingsRepository? = null,
    onDismissDanmakuSettings: () -> Unit = {},
    inlineInStage: Boolean = false,
) {
    if (speedDialogVisible) {
        SpeedDialog(
            active = speed,
            onSelect = onSpeedChange,
            onDismiss = onDismissSpeed,
            inlineInStage = inlineInStage,
        )
    }

    if (subtitleDialogVisible) {
        SubtitleDialog(
            tracks = subtitleController.tracks,
            selectedKey = subtitleController.selectedTrackKey,
            onSelect = { key ->
                subtitleController.selectTrack(key)
                onDismissSubtitle()
            },
            onDismiss = onDismissSubtitle,
            inlineInStage = inlineInStage,
        )
    }

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
                onSelect = onSelectFavorite,
                onDismiss = onDismissFavorite,
            )
        }
    }

    val source = state.source
    if (qualityDialogVisible && source != null) {
        QualityDialog(
            qualities = source.qualities,
            activeQuality = state.activeQuality,
            onSelect = onSelectQuality,
            onDismiss = onDismissQuality,
            inlineInStage = inlineInStage,
        )
    }
}

@Composable
internal fun BindPlayerEvents(
    player: Player,
    viewModel: VideoViewModel,
    container: AppContainer,
    onPlayingChanged: (Boolean) -> Unit,
    onBufferingChanged: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onVideoAspectChanged: (Float) -> Unit = {},
) {
    DisposableEffect(player) {
        val initialSize = player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            onVideoAspectChanged(
                (initialSize.width * initialSize.pixelWidthHeightRatio) / initialSize.height,
            )
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlayingChanged(playing)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onVideoAspectChanged(
                        (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height,
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                onBufferingChanged(playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    viewModel.markPlaybackStarted()
                } else if (playbackState == Player.STATE_ENDED) {
                    viewModel.flushHeartbeat(player.duration.coerceAtLeast(0L) / 1000, container.scope)
                    viewModel.playNextPart()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(error.message ?: "播放失败")
            }
        }
        player.addListener(listener)
        onDispose {
            viewModel.flushHeartbeat(player.currentPosition.coerceAtLeast(0L) / 1000, container.scope)
            player.removeListener(listener)
            player.release()
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
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = COUITheme.textStyles.footnote1,
            color = Color.White,
        )
    }
}
