package erl.bilico.feature.video

import androidx.compose.runtime.Composable

/** iOS 使用 AVPlayerLayer 合成，无 Android SurfaceView 挖洞限制，这里仅保留签名兼容。 */
@Composable
internal fun RegisterSurfaceViewEntry(active: Boolean) = Unit
