package erl.bilico.feature.cast

import erl.bilico.data.network.BiliClient
import erl.bilico.data.repository.CdnOption
import erl.bilico.data.repository.PgcRepository
import erl.bilico.data.repository.VideoRepository
import erl.bilico.data.settings.SettingsRepository
import erl.bilico.platform.PlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

actual class DlnaCastController actual constructor(
    context: PlatformContext,
    private val scope: CoroutineScope,
    private val client: BiliClient,
    private val videos: VideoRepository,
    private val pgc: PgcRepository,
    private val settings: SettingsRepository,
) {
    actual val isSupported: Boolean = true

    private val dlnaHttp = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val mediaHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val discovery = DlnaDiscovery(context.androidContext, dlnaHttp)
    private val soap = DlnaSoapClient(dlnaHttp)
    private val proxy = CastProxyServer(mediaHttp)

    private val _state = MutableStateFlow(CastUiState())
    actual val state: StateFlow<CastUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var castJob: Job? = null
    private var pollJob: Job? = null

    actual fun scanDevices() {
        scanJob?.cancel()
        _state.update { it.copy(scanning = true, error = null) }
        scanJob = scope.launch {
            try {
                val list = discovery.discover()
                _state.update { it.copy(scanning = false, devices = list) }
            } catch (e: CancellationException) {
                throw e
            } catch (err: Throwable) {
                _state.update { it.copy(scanning = false, error = err.message ?: "搜索设备失败") }
            }
        }
    }

    actual fun setUseProxy(enabled: Boolean) {
        _state.update { it.copy(useProxy = enabled) }
    }

    actual fun startCast(device: DlnaDevice, request: CastMediaRequest) {
        castJob?.cancel()
        pollJob?.cancel()
        castJob = scope.launch {
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
                        val peerHost = runCatching { URI(device.location).host }.getOrNull()
                        val lanIp = DlnaDiscovery.findLanIpv4Address(peerHost)
                        if (lanIp.isNullOrBlank()) {
                            upstream
                        } else {
                            val headers = if (request.liveStreamUrl.isNotBlank()) {
                                client.liveStreamHeaders()
                            } else {
                                val bvid = request.bvid.takeIf { it.isNotBlank() }
                                client.mediaRequestHeaders(bvid)
                            }
                            val path = proxy.updateTarget(
                                url = upstream,
                                headers = headers,
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
                            if (err is CancellationException) throw err
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
                    if (err is CancellationException) throw err
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

    actual fun togglePlayPause() {
        val device = _state.value.activeDevice ?: return
        val currentlyPlaying = _state.value.isPlaying
        scope.launch {
            val result = if (currentlyPlaying) soap.pause(device) else soap.play(device)
            result.onSuccess {
                _state.update { it.copy(isPlaying = !currentlyPlaying, error = null) }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _state.update { it.copy(error = err.message ?: "控制失败") }
            }
        }
    }

    actual fun seekBy(deltaMs: Long) {
        val device = _state.value.activeDevice ?: return
        val dur = _state.value.durationMs
        val target = (_state.value.positionMs + deltaMs)
            .let { if (dur > 0L) it.coerceIn(0L, dur) else it.coerceAtLeast(0L) }
        scope.launch {
            soap.seek(device, target).onSuccess {
                _state.update { it.copy(positionMs = target) }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _state.update { it.copy(error = err.message ?: "快进失败") }
            }
        }
    }

    actual fun stopCast() {
        castJob?.cancel()
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
