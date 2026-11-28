package erl.bilico.platform

import kotlin.math.abs
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSTimer
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIScreen
import platform.UIKit.UISlider
import platform.UIKit.UIView
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

actual fun keepScreenOn(context: PlatformContext, enabled: Boolean) {
    dispatch_async(dispatch_get_main_queue()) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
    }
}

private var savedIosScreenBrightness: Double? = null
private var pendingIosBrightness: Float? = null
private var brightnessFlushPending = false

actual fun readScreenBrightness(): Float = UIScreen.mainScreen.brightness.toFloat().coerceIn(0.02f, 1f)

actual fun writeScreenBrightness(value: Float) {
    pendingIosBrightness = value
    if (brightnessFlushPending) return
    brightnessFlushPending = true
    dispatch_async(dispatch_get_main_queue()) {
        brightnessFlushPending = false
        val target = pendingIosBrightness ?: return@dispatch_async
        if (target < 0f) {
            savedIosScreenBrightness?.let { UIScreen.mainScreen.brightness = it }
            savedIosScreenBrightness = null
        } else {
            if (savedIosScreenBrightness == null) savedIosScreenBrightness = UIScreen.mainScreen.brightness
            UIScreen.mainScreen.brightness = target.coerceIn(0.02f, 1f).toDouble()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private object IosSystemVolumeBridge {
    private const val FLUSH_INTERVAL_MS = 65L
    private const val MIN_STEP_DELTA = 0.025f
    private var volumeView: MPVolumeView? = null
    private var cachedSlider: UISlider? = null
    private var detachTimer: NSTimer? = null
    private var latestTargetVolume: Float? = null
    private var lastAppliedVolume = Float.NaN
    private var lastFlushAtMs = 0L
    private var flushScheduled = false

    private fun ensureAttached(): Pair<UISlider?, Boolean> {
        val view = volumeView ?: MPVolumeView(
            frame = CGRectMake(-2000.0, -2000.0, 1.0, 1.0),
        ).apply {
            alpha = 0.01
            clipsToBounds = true
            userInteractionEnabled = false
            volumeView = this
        }
        val window = findKeyWindow()
        var newlyAttached = false
        if (window != null && view.superview !== window) {
            view.removeFromSuperview()
            window.addSubview(view)
            newlyAttached = true
        }
        var slider = cachedSlider
        if (slider == null) {
            slider = findSlider(view) ?: run {
                view.layoutIfNeeded()
                findSlider(view)
            }
            cachedSlider = slider
        }
        return slider to newlyAttached
    }

    private fun findSlider(view: UIView): UISlider? {
        view.subviews.forEach { raw ->
            if (raw is UISlider) return raw
            if (raw is UIView) findSlider(raw)?.let { return it }
        }
        return null
    }

    private fun scheduleDetach() {
        detachTimer?.invalidate()
        detachTimer = NSTimer.scheduledTimerWithTimeInterval(interval = 2.5, repeats = false) { _ ->
            detachTimer = null
            latestTargetVolume = null
            lastAppliedVolume = Float.NaN
            volumeView?.removeFromSuperview()
        }
    }

    private fun applyToSlider(slider: UISlider, target: Float) {
        slider.setValue(target, animated = false)
        slider.sendActionsForControlEvents(UIControlEventValueChanged)
        lastAppliedVolume = target
        lastFlushAtMs = uptimeMillis()
    }

    private fun flushOnMain() {
        flushScheduled = false
        val target = latestTargetVolume ?: return
        val isEdge = target <= 0.005f || target >= 0.995f
        if (lastAppliedVolume.isFinite() && !isEdge && abs(target - lastAppliedVolume) < MIN_STEP_DELTA) {
            scheduleDetach()
            return
        }
        val (slider, newlyAttached) = ensureAttached()
        if (slider != null) {
            applyToSlider(slider, target)
            if (newlyAttached) {
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 80_000_000L), dispatch_get_main_queue()) {
                    val pending = latestTargetVolume ?: return@dispatch_after
                    val s = cachedSlider ?: return@dispatch_after
                    if (abs(s.value - pending) > 0.01f) applyToSlider(s, pending)
                }
            }
        }
        scheduleDetach()
    }

    fun read(): Float {
        if (volumeView?.superview == null) {
            dispatch_async(dispatch_get_main_queue()) {
                ensureAttached()
                scheduleDetach()
            }
        }
        val cur = AVAudioSession.sharedInstance().outputVolume.coerceIn(0f, 1f)
        lastAppliedVolume = cur
        return cur
    }

    fun write(value: Float) {
        latestTargetVolume = value.coerceIn(0f, 1f)
        if (flushScheduled) return
        flushScheduled = true
        val elapsed = uptimeMillis() - lastFlushAtMs
        if (elapsed >= FLUSH_INTERVAL_MS) {
            dispatch_async(dispatch_get_main_queue()) { flushOnMain() }
        } else {
            val delayNs = (FLUSH_INTERVAL_MS - elapsed).coerceAtLeast(1L) * 1_000_000L
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayNs), dispatch_get_main_queue()) { flushOnMain() }
        }
    }
}

actual fun readSystemVolume(): Float = IosSystemVolumeBridge.read()

actual fun writeSystemVolume(value: Float) {
    IosSystemVolumeBridge.write(value)
}
