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
 * iOS 投屏占位控制器：保持与 Android 一致的状态流接口。
 */
actual class DlnaCastController actual constructor(
    context: PlatformContext,
    scope: CoroutineScope,
    client: BiliClient,
    videos: VideoRepository,
    pgc: PgcRepository,
    settings: SettingsRepository,
) {
    actual val isSupported: Boolean = false

    private val _state = MutableStateFlow(
        CastUiState(error = "iOS 暂不支持 DLNA 投屏，请使用系统屏幕镜像或 AirPlay"),
    )
    actual val state: StateFlow<CastUiState> = _state.asStateFlow()

    actual fun scanDevices() = Unit
    actual fun setUseProxy(enabled: Boolean) = Unit
    actual fun startCast(device: DlnaDevice, request: CastMediaRequest) = Unit
    actual fun togglePlayPause() = Unit
    actual fun seekBy(deltaMs: Long) = Unit
    actual fun stopCast() = Unit
}
