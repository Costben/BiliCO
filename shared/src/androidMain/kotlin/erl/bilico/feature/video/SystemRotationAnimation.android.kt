package erl.bilico.feature.video

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import erl.bilico.platform.AndroidPlatformBridge
import erl.bilico.platform.isColorOsRotationVendor

internal actual object SystemRotationAnimation {
    actual val appDriven: Boolean = isColorOsRotationVendor
}

@Composable
internal actual fun StagePlatformWindowAttributesEffect() {
    val activity = AndroidPlatformBridge.currentActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val oldRotation = window?.attributes?.rotationAnimation
        val oldCutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes?.layoutInDisplayCutoutMode
        } else {
            null
        }
        val targetRotation = if (SystemRotationAnimation.appDriven) {
            WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
        } else {
            oldRotation
        }
        val targetCutout = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            else -> oldCutout
        }
        var changed = false
        if (window != null && (oldRotation != targetRotation || oldCutout != targetCutout)) {
            window.attributes = window.attributes.apply {
                if (targetRotation != null) rotationAnimation = targetRotation
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && targetCutout != null) {
                    layoutInDisplayCutoutMode = targetCutout
                }
            }
            changed = true
        }
        onDispose {
            if (changed && window != null) {
                window.attributes = window.attributes.apply {
                    if (oldRotation != null) rotationAnimation = oldRotation
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && oldCutout != null) {
                        layoutInDisplayCutoutMode = oldCutout
                    }
                }
            }
        }
    }
}
