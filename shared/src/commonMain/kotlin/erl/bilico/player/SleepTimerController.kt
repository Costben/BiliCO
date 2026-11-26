package erl.bilico.player

import erl.bilico.platform.PlatformContext
import erl.bilico.platform.padTwoDigits
import erl.bilico.platform.showToast
import erl.bilico.platform.uptimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 定时关闭模式。 */
sealed interface SleepTimerMode {
    data object Off : SleepTimerMode
    data object AfterCurrentVideo : SleepTimerMode
    data class Timed(
        val totalMinutes: Int,
        val remainingSeconds: Int,
    ) : SleepTimerMode
}

/** 定时关闭对外暴露的不可变状态快照。 */
data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.Off,
) {
    val isActive: Boolean
        get() = mode !is SleepTimerMode.Off

    val remainingSeconds: Int
        get() = (mode as? SleepTimerMode.Timed)?.remainingSeconds ?: 0

    /** 顶栏显示的简洁倒计时或模式文案；未开启时为 null。 */
    val label: String?
        get() = when (val current = mode) {
            SleepTimerMode.Off -> null
            SleepTimerMode.AfterCurrentVideo -> "播完本集"
            is SleepTimerMode.Timed -> formatRemaining(current.remainingSeconds)
        }

    /** 菜单或弹窗摘要文案。 */
    val summary: String
        get() = when (val current = mode) {
            SleepTimerMode.Off -> "未开启"
            SleepTimerMode.AfterCurrentVideo -> "播完当前视频后停止"
            is SleepTimerMode.Timed -> "剩余 ${formatRemaining(current.remainingSeconds)}"
        }

    companion object {
        fun formatRemaining(totalSeconds: Int): String {
            val safe = totalSeconds.coerceAtLeast(0)
            val hours = safe / 3600
            val minutes = (safe % 3600) / 60
            val seconds = safe % 60
            return if (hours > 0) {
                "$hours:${padTwoDigits(minutes.toLong())}:${padTwoDigits(seconds.toLong())}"
            } else {
                "${padTwoDigits(minutes.toLong())}:${padTwoDigits(seconds.toLong())}"
            }
        }
    }
}

/**
 * 全局定时关闭控制器，挂在 [erl.bilico.di.AppContainer] 下，
 * 跨全屏旋转、融合球最小化与切视频保持同一份倒计时状态。
 */
class SleepTimerController(
    private val context: PlatformContext,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    /** 定时倒计时自然到期触发时自增，供播放页同步取消连播弹窗。 */
    private val _triggerVersion = MutableStateFlow(0)
    val triggerVersion: StateFlow<Int> = _triggerVersion.asStateFlow()

    private var countdownJob: Job? = null

    fun startMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(MIN_CUSTOM_MINUTES, MAX_CUSTOM_MINUTES)
        countdownJob?.cancel()
        val totalSeconds = clamped * 60
        val deadlineMs = uptimeMillis() + totalSeconds * 1000L
        _state.value = SleepTimerState(SleepTimerMode.Timed(clamped, totalSeconds))
        countdownJob = scope.launch {
            while (true) {
                val remainingMs = deadlineMs - uptimeMillis()
                if (remainingMs <= 0L) break
                val remainingSec = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                val current = _state.value.mode
                if (current is SleepTimerMode.Timed && current.remainingSeconds != remainingSec) {
                    _state.value = SleepTimerState(SleepTimerMode.Timed(clamped, remainingSec))
                }
                delay(minOf(1_000L, remainingMs).coerceAtLeast(50L))
            }
            withContext(Dispatchers.Main) {
                countdownJob = null
                _state.value = SleepTimerState(SleepTimerMode.Off)
                _triggerVersion.value += 1
                PlaybackBridge.pauseAndStopSession(context)
                showToast(context, "定时关闭已生效，已暂停播放")
            }
        }
    }

    fun setAfterCurrentVideo() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SleepTimerState(SleepTimerMode.AfterCurrentVideo)
    }

    fun cancel() {
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SleepTimerState(SleepTimerMode.Off)
    }

    /**
     * 视频自然播完时由播放页调用：若处于「播完当前视频后停止」模式，
     * 立即暂停并清除定时，返回 true 以阻止自动连播或循环。
     */
    fun consumeAfterCurrentVideo(player: BiliPlayer? = null): Boolean {
        if (_state.value.mode != SleepTimerMode.AfterCurrentVideo) return false
        countdownJob?.cancel()
        countdownJob = null
        _state.value = SleepTimerState(SleepTimerMode.Off)
        player?.pause()
        PlaybackBridge.pauseAndStopSession(context)
        showToast(context, "定时关闭已生效，播完当前视频已暂停")
        return true
    }

    companion object {
        val PRESET_MINUTES: List<Int> = listOf(15, 30, 60, 90)
        const val MIN_CUSTOM_MINUTES: Int = 1
        const val MAX_CUSTOM_MINUTES: Int = 480
    }
}
