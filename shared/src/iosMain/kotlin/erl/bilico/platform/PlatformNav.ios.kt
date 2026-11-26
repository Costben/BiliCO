package erl.bilico.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import erl.bilico.player.IosMediaSession
import kotlinx.coroutines.flow.Flow

actual val useEdgeSwipeBackOverlay: Boolean = true

@Composable
actual fun PlatformPredictiveBackHandler(
    enabled: Boolean,
    onBack: suspend (progress: Flow<PlatformBackEvent>) -> Unit,
) = Unit

@Composable
actual fun PlatformBackActionHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit

actual fun canEnterPip(context: PlatformContext): Boolean = IosMediaSession.isPipSupported()

actual fun enterPipNow(context: PlatformContext): Boolean = IosMediaSession.startPipNow()

@Composable
actual fun SyncPictureInPictureParams(
    activePlayback: Boolean,
    isPlaying: Boolean,
    autoPip: Boolean,
) {
    LaunchedEffect(activePlayback, isPlaying, autoPip) {
        IosMediaSession.updatePipParams(
            activePlayback = activePlayback,
            isPlaying = isPlaying,
            autoPip = autoPip,
        )
    }
}
