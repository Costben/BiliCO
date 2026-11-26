package erl.bilico.platform

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import erl.bilico.player.PlaybackBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

actual val useEdgeSwipeBackOverlay: Boolean = false

@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onBack: suspend (progress: Flow<PlatformBackEvent>) -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { events ->
        onBack(
            events.map { event ->
                PlatformBackEvent(
                    touchX = event.touchX,
                    touchY = event.touchY,
                    progress = event.progress,
                    frameTimeMillis = event.frameTimeMillis,
                )
            },
        )
    }
}

@Composable
actual fun PlatformBackActionHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}

private fun Context.findPipActivity(): Activity? {
    var cur: Context? = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

private fun resolvePipActivity(context: PlatformContext): Activity? =
    AndroidPlatformBridge.currentActivity() ?: context.androidContext.findPipActivity()

private fun canEnterAndroidPip(activity: Activity?): Boolean {
    if (activity == null) return false
    return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}

private fun buildAndroidPipParams(
    context: Context,
    isPlaying: Boolean,
    autoEnter: Boolean,
): PictureInPictureParams {
    val actionIntent = Intent(PlaybackBridge.ACTION_TOGGLE_PLAY).setPackage(context.packageName)
    val pending = PendingIntent.getBroadcast(
        context,
        101,
        actionIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val iconRes = if (isPlaying) {
        android.R.drawable.ic_media_pause
    } else {
        android.R.drawable.ic_media_play
    }
    val label = if (isPlaying) "暂停" else "播放"
    val action = RemoteAction(Icon.createWithResource(context, iconRes), label, label, pending)
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(listOf(action))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter && isPlaying)
    }
    return builder.build()
}

private fun enterAndroidPipNow(activity: Activity?): Boolean {
    if (!canEnterAndroidPip(activity) || activity == null) return false
    val playing = PlaybackBridge.activePlayer?.isPlaying == true
    val params = buildAndroidPipParams(activity, playing, PlaybackBridge.autoPipEnabled)
    return runCatching { activity.enterPictureInPictureMode(params) }.getOrDefault(false)
}

actual fun canEnterPip(context: PlatformContext): Boolean =
    canEnterAndroidPip(resolvePipActivity(context))

actual fun enterPipNow(context: PlatformContext): Boolean =
    enterAndroidPipNow(resolvePipActivity(context))

/**
 * Android 12 以下没有 `setAutoEnterEnabled`，在 `MainActivity.onUserLeaveHint()` 触发自动画中画。
 */
fun handleAndroidPipUserLeaveHint(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        if (PlaybackBridge.autoPipEnabled &&
            PlaybackBridge.activePlayer?.isPlaying == true &&
            canEnterAndroidPip(activity)
        ) {
            enterAndroidPipNow(activity)
        }
    }
}

@Composable
actual fun SyncPictureInPictureParams(
    activePlayback: Boolean,
    isPlaying: Boolean,
    autoPip: Boolean,
) {
    val context = LocalPlatformContext.current
    var configured by remember { mutableStateOf(false) }

    LaunchedEffect(activePlayback, isPlaying, autoPip, context) {
        val activity = resolvePipActivity(context) ?: return@LaunchedEffect
        if (!canEnterAndroidPip(activity)) return@LaunchedEffect
        if (activePlayback) {
            configured = true
            runCatching {
                activity.setPictureInPictureParams(
                    buildAndroidPipParams(activity, isPlaying = isPlaying, autoEnter = autoPip),
                )
            }
        } else if (configured) {
            configured = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.setPictureInPictureParams(
                        buildAndroidPipParams(activity, isPlaying = false, autoEnter = false),
                    )
                }
            }
        }
    }
}
