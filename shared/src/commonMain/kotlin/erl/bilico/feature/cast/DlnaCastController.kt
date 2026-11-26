package erl.bilico.feature.cast

import erl.bilico.data.network.BiliClient
import erl.bilico.data.repository.PgcRepository
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.platform.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS 投屏占位控制器：保持与 Android 0.3.14-pre 一致的状态流接口。
 */
class DlnaCastController(
    context: PlatformContext,
    private val scope: CoroutineScope,
    private val client: BiliClient,
    private val videos: VideoRepository,
    private val pgc: PgcRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow(CastUiState(error = "iOS 暂不支持 DLNA 投屏，请使用系统屏幕镜像或 AirPlay"))
    val state: StateFlow<CastUiState> = _state.asStateFlow()

    fun scanDevices() = Unit
    fun setUseProxy(enabled: Boolean) = Unit
    fun startCast(device: DlnaDevice, request: CastMediaRequest) = Unit
    fun togglePlayPause() = Unit
    fun seekBy(deltaMs: Long) = Unit
    fun stopCast() = Unit
}
