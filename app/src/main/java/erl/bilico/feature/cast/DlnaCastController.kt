package erl.bilico.feature.cast

import android.content.Context
import erl.bilico.data.network.BiliClient
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.repository.PgcRepository
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URL

class DlnaCastController(
    context: Context,
    private val scope: CoroutineScope,
    private val client: BiliClient,
    private val videos: VideoRepository,
    private val pgc: PgcRepository,
    private val settings: SettingsRepository,
) {
    private val discovery = DlnaDiscovery(context, client.okHttp)
    private val soap = DlnaSoapClient(client.okHttp)
    private val proxy = CastProxyServer(client.mediaClient())

    private val _state = MutableStateFlow(CastUiState())
    val state: StateFlow<CastUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var pollJob: Job? = null

    fun scanDevices() {
        scanJob?.cancel()
        _state.update { it.copy(scanning = true, error = null) }
        scanJob = scope.launch {
            runCatching { discovery.discover() }
                .onSuccess { list ->
                    _state.update { it.copy(scanning = false, devices = list) }
                }
                .onFailure { err ->
                    _state.update { it.copy(scanning = false, error = err.message ?: "搜索设备失败") }
                }
        }
    }

    fun setUseProxy(enabled: Boolean) {
        _state.update { it.copy(useProxy = enabled) }
    }

    fun startCast(device: DlnaDevice, request: CastMediaRequest) {
        scope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    error = null,
                    activeDevice = device,
                    castingTitle = request.title,
                )
            }
            val resolvedStreamResult = resolveStreamUrl(request)
            resolvedStreamResult.fold(
                onSuccess = { upstream ->
                    val playUrl = if (_state.value.useProxy) {
                        val port = proxy.startIfNeeded()
                        val peerHost = runCatching { URL(device.location).host }.getOrNull()
                        val lanIp = DlnaDiscovery.findLanIpv4Address(peerHost)
                        if (lanIp.isNullOrBlank()) {
                            upstream
                        } else {
                            val bvid = request.bvid.takeIf { it.isNotBlank() }
                            val path = proxy.updateTarget(
                                url = upstream,
                                headers = client.mediaRequestHeaders(bvid),
                            )
                            "http://$lanIp:$port$path"
                        }
                    } else {
                        upstream
                    }

                    soap.setAvTransportUri(device, playUrl, request.title)
                        .onSuccess {
                            soap.play(device)
                            if (request.initialPositionMs > 3000L) {
                                delay(400)
                                soap.seek(device, request.initialPositionMs)
                            }
                            _state.update {
                                it.copy(
                                    isBusy = false,
                                    isPlaying = true,
                                    positionMs = request.initialPositionMs,
                                )
                            }
                            startPositionPolling(device)
                        }
                        .onFailure { err ->
                            _state.update {
                                it.copy(
                                    isBusy = false,
                                    isPlaying = false,
                                    error = err.message ?: "投屏启动失败",
                                )
                            }
                        }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            isPlaying = false,
                            error = err.message ?: "获取投屏流失败",
                        )
                    }
                },
            )
        }
    }

    fun togglePlayPause() {
        val device = _state.value.activeDevice ?: return
        val currentlyPlaying = _state.value.isPlaying
        scope.launch {
            val result = if (currentlyPlaying) soap.pause(device) else soap.play(device)
            result.onSuccess {
                _state.update { it.copy(isPlaying = !currentlyPlaying, error = null) }
            }.onFailure { err ->
                _state.update { it.copy(error = err.message ?: "控制失败") }
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        val device = _state.value.activeDevice ?: return
        val dur = _state.value.durationMs
        val target = (_state.value.positionMs + deltaMs)
            .let { if (dur > 0L) it.coerceIn(0L, dur) else it.coerceAtLeast(0L) }
        scope.launch {
            soap.seek(device, target).onSuccess {
                _state.update { it.copy(positionMs = target) }
            }.onFailure { err ->
                _state.update { it.copy(error = err.message ?: "快进失败") }
            }
        }
    }

    fun stopCast() {
        pollJob?.cancel()
        val device = _state.value.activeDevice
        proxy.stop()
        _state.update {
            it.copy(
                activeDevice = null,
                castingTitle = "",
                isPlaying = false,
                isBusy = false,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
        if (device != null) {
            scope.launch { soap.stop(device) }
        }
    }

    private suspend fun resolveStreamUrl(request: CastMediaRequest): Result<String> {
        if (request.liveStreamUrl.isNotBlank()) {
            return Result.success(request.liveStreamUrl)
        }
        val cdn = CdnOption.fromId(settings.cdnOption.value)
        val qn = request.qn.coerceAtMost(80)
        return if (request.epId > 0L) {
            pgc.castUrl(epId = request.epId, cid = request.cid, qn = qn, cdn = cdn)
        } else {
            videos.castUrl(bvid = request.bvid, cid = request.cid, qn = qn, cdn = cdn)
        }
    }

    private fun startPositionPolling(device: DlnaDevice) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && _state.value.activeDevice?.avTransportControlUrl == device.avTransportControlUrl) {
                soap.getPositionInfo(device).onSuccess { pos ->
                    _state.update {
                        it.copy(
                            positionMs = pos.relTimeMs,
                            durationMs = pos.durationMs,
                        )
                    }
                }
                delay(1500)
            }
        }
    }
}
