package erl.bilico.feature.login

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import erl.bilico.data.model.QrLoginState
import erl.bilico.data.session.SessionRepository
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
                    val bitmap = withContext(Dispatchers.Default) { encodeQr(login.url) }
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
                val state = result.getOrNull()?.first
                if (state == null) {
                    // 单次网络抖动不终止轮询，等下一轮继续。
                    continue
                }
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

    private fun encodeQr(content: String): ImageBitmap {
        val size = 512
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val offset = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[offset + x] = if (matrix[x, y]) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2000L
    }
}
