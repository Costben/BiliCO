package erl.bilico.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

@Stable
internal class StageFrameSource {
    var capture: ((callback: (fullFrame: Boolean) -> Unit) -> Unit)? = null
}

internal val LocalStageFrameSource = staticCompositionLocalOf { StageFrameSource() }

internal val LocalStageFrozenVideo = compositionLocalOf { false }

internal const val STAGE_FRAME_CAPTURE_TIMEOUT_MS = 250L
internal const val REFINE_TIMEOUT_MS = 280L

@Stable
internal class StageFrameHolder {
    val source = StageFrameSource()

    var frozen by mutableStateOf(false)
        private set

    suspend fun freeze(): Boolean = false

    suspend fun refine(timeoutMs: Long = REFINE_TIMEOUT_MS): Boolean = false

    fun unfreeze() {
        frozen = false
    }

    @Composable
    fun SurfacePlaceholder(modifier: Modifier = Modifier) {
        Box(modifier)
    }
}

@Composable
internal fun rememberStageFrameHolder(): StageFrameHolder = remember { StageFrameHolder() }

@Composable
internal fun StageFrozenFrame(holder: StageFrameHolder) {
}
