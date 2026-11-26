package erl.bilico.feature.cast

import erl.bilico.data.network.BiliClient
import erl.bilico.data.repository.PgcRepository
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.platform.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * 局域网 DLNA 投屏控制器：Android 端提供完整 SSDP 发现、SOAP 控制与本地代理中转，iOS 端提供占位实现。
 */
expect class DlnaCastController(
    context: PlatformContext,
    scope: CoroutineScope,
    client: BiliClient,
    videos: VideoRepository,
    pgc: PgcRepository,
    settings: SettingsRepository,
) {
    val isSupported: Boolean
    val state: StateFlow<CastUiState>

    fun scanDevices()
    fun setUseProxy(enabled: Boolean)
    fun startCast(device: DlnaDevice, request: CastMediaRequest)
    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun stopCast()
}
