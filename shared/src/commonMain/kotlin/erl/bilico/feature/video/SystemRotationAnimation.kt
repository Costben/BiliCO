package erl.bilico.feature.video

import androidx.compose.runtime.Composable

/**
 * 转屏动画由谁来演：ColorOS 系（OPPO / OnePlus / realme）走应用自绘 + `ROTATION_ANIMATION_SEAMLESS`，
 * 其余 ROM 与 iOS 回落系统原生转屏动画，避免双动画叠播。
 */
internal expect object SystemRotationAnimation {
    val appDriven: Boolean
}

/** 播放舞台生命周期内的平台窗口属性（Android 设 `SEAMLESS` 转屏与刘海区铺满，离开时还原）。 */
@Composable
internal expect fun StagePlatformWindowAttributesEffect()
