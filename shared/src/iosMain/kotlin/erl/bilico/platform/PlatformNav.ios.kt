package erl.bilico.platform

import androidx.compose.runtime.Composable
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

actual fun canEnterPip(context: PlatformContext): Boolean = false

actual fun enterPipNow(context: PlatformContext): Boolean = false

@Composable
actual fun SyncPictureInPictureParams(
    activePlayback: Boolean,
    isPlaying: Boolean,
    autoPip: Boolean,
) = Unit
