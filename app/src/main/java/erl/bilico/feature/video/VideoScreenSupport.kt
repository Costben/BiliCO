package erl.bilico.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.OrientationEventListener
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import erl.bilico.data.model.FavoriteFolder
import erl.bilico.di.AppContainer
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * ColorOS 17 专属视频转屏动画参数 (逆向自 com.oplus.animation.OplusVideoRotateAnimation)
 * 时长：450ms
 * 曲线：COUIMoveEaseInterpolator (0.3f, 0.0f, 0.1f, 1.0f)
 * 辅助淡出时长：50ms (0.33f, 0.0f, 0.67f, 1.0f)
 */
internal const val COLOROS_VIDEO_ROTATE_DURATION_MS = 450
internal const val COLOROS_VIDEO_ALPHA_DURATION_MS = 50

internal val ColorOSMoveEase = CubicBezierEasing(0.3f, 0.0f, 0.1f, 1.0f)
internal val ColorOSEase = CubicBezierEasing(0.33f, 0.0f, 0.67f, 1.0f)

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
 * 手机是不是横着拿的，带 500ms 持续确认防抖机制。
 *
 * 手机倾斜到目标角度区间后，必须持续稳定 500ms（0.5秒）才真正改变状态，
 * 中途如果晃回原姿态立即取消计时，彻底消除晃动误判。
 */
@Composable
internal fun rememberDeviceLandscape(mode: Int): Boolean {
    if (mode == 0) return false
    val context = LocalContext.current
    var landscape by remember { mutableStateOf(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var pendingTarget by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(context, mode) {
        val runnable = Runnable {
            pendingTarget?.let { target ->
                landscape = target
                pendingTarget = null
            }
        }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val delta = when (mode) {
                    1 -> 15 // 迟钝防误触：75..105 / 255..285
                    2 -> 25 // 标准：65..115 / 245..295
                    else -> 35 // 灵敏：55..125 / 235..305
                }
                val raw = orientation in (90 - delta)..(90 + delta) ||
                    orientation in (270 - delta)..(270 + delta)
                if (raw == landscape) {
                    if (pendingTarget != null) {
                        handler.removeCallbacks(runnable)
                        pendingTarget = null
                    }
                } else {
                    if (pendingTarget != raw) {
                        handler.removeCallbacks(runnable)
                        pendingTarget = raw
                        handler.postDelayed(runnable, 500L)
                    }
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose {
            handler.removeCallbacks(runnable)
            listener.disable()
        }
    }
    return landscape
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
) {
    if (speedDialogVisible) {
        SpeedDialog(
            active = speed,
            onSelect = onSpeedChange,
            onDismiss = onDismissSpeed,
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
        )
    }

    if (coinDialogVisible) {
        CoinDialog(
            remaining = (2 - coined).coerceIn(1, 2),
            onSelect = onSelectCoin,
            onDismiss = onDismissCoin,
        )
    }

    favoriteFolders?.let { folders ->
        FavoriteFolderDialog(
            folders = folders,
            onSelect = onSelectFavorite,
            onDismiss = onDismissFavorite,
        )
    }

    val source = state.source
    if (qualityDialogVisible && source != null) {
        QualityDialog(
            qualities = source.qualities,
            activeQuality = state.activeQuality,
            onSelect = onSelectQuality,
            onDismiss = onDismissQuality,
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
) {
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlayingChanged(playing)
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
internal fun BoxScope.VideoMessageToast(message: String?, inPipMode: Boolean) {
    if (message != null && !inPipMode) {
        Text(
            text = message,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = COUITheme.textStyles.footnote1,
            color = Color.White,
        )
    }
}
