package erl.bilico.feature.login

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.QrLoginState
import erl.bilico.data.session.SessionRepository
import erl.bilico.platform.generateQrCodeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginUiState(
    val qrCode: ImageBitmap? = null,
    val statusText: String = "正在获取二维码…",
    val isExpired: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refreshQr()
    }

    fun refreshQr() {
        pollJob?.cancel()
        viewModelScope.launch {
            _state.value = LoginUiState(isLoading = true, statusText = "正在获取二维码…")
            session.generateQr()
                .onSuccess { login ->
                    val bitmap = withContext(Dispatchers.Default) { generateQrCodeBitmap(login.url, 512) }
                    _state.value = LoginUiState(
                        qrCode = bitmap,
                        statusText = "请使用哔哩哔哩 App 扫码登录",
                    )
                    poll(login.key)
                }
                .onFailure { error ->
                    _state.value = LoginUiState(
                        statusText = "二维码获取失败",
                        error = error.message ?: "网络错误",
                    )
                }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun poll(key: String) {
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                val result = session.pollQr(key)
                val state = result.getOrNull()?.first ?: continue
                when (state) {
                    QrLoginState.WAITING -> Unit
                    QrLoginState.SCANNED -> _state.update {
                        it.copy(statusText = "已扫码，请在手机上确认")
                    }
                    QrLoginState.SUCCESS -> {
                        _state.update { it.copy(statusText = "登录成功") }
                        return@launch
                    }
                    QrLoginState.EXPIRED -> {
                        _state.update {
                            it.copy(
                                qrCode = null,
                                statusText = "二维码已失效",
                                isExpired = true,
                            )
                        }
                        return@launch
                    }
                }
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2000L
    }
}
