package erl.bilico.feature.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import erl.bilico.data.model.DanmakuItem
import erl.bilico.data.settings.DanmakuSettings
import erl.bilico.player.BiliPlayer

/**
 * 弹幕渲染层：
 * - Android 使用原生 120Hz [DanmakuSurfaceView] + 离线精灵图缓存，避免 Compose 重组与描边透底；
 * - iOS 使用 Skia/Compose 渲染层并在半透明绘制时统一合成，保持双端视觉一致。
 * 点播传 null 由播放器位置驱动；直播传 [DanmakuTimeline] 由外部时间轴驱动。
 */
@Composable
internal expect fun DanmakuOverlay(
    player: BiliPlayer?,
    items: List<DanmakuItem>,
    isPlaying: Boolean,
    settings: DanmakuSettings,
    modifier: Modifier = Modifier.fillMaxSize(),
    timeline: DanmakuTimeline? = null,
)
