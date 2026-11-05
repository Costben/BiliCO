package erl.bilico.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import erl.bilico.data.model.Account
import erl.bilico.data.model.QrLogin
import erl.bilico.data.model.QrLoginState
import erl.bilico.data.model.QrPollData
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.PassportApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 登录态：Cookie 由 CookieStore 负责，这里只管账号画像与扫码流程。
 * 启动时先用本地缓存的画像点亮界面，再用 nav 接口校正，避免首屏空白。
 */
class SessionRepository(
    private val context: Context,
    private val cookies: CookieStore,
    private val api: BiliApi,
    private val passportApi: PassportApi,
    private val buvid: BuvidManager,
    private val scope: CoroutineScope,
) {
    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    init {
        scope.launch { restore() }
    }

    fun isLoggedIn(): Boolean = !cookies.get("SESSDATA").isNullOrBlank()

    fun csrf(): String = cookies.get("bili_jct").orEmpty()

    fun mid(): Long = cookies.get("DedeUserID")?.toLongOrNull() ?: 0L

    fun hasAccessToken(): Boolean = !cookies.get("access_token").isNullOrBlank()

    suspend fun generateQr(): Result<QrLogin> = runCatching {
        val response = passportApi.generateQrCode()
        if (response.code != 0) error(response.message.ifBlank { "二维码获取失败(${response.code})" })
        val data = response.data ?: error("二维码数据为空")
        val key = data.qrcodeKey?.takeIf { it.isNotBlank() } ?: error("二维码 key 为空")
        val url = data.url?.takeIf { it.isNotBlank() } ?: error("二维码内容为空")
        QrLogin(key = key, url = url)
    }

    suspend fun pollQr(key: String): Result<Pair<QrLoginState, QrPollData?>> = runCatching {
        val response = passportApi.pollQrCode(key)
        val data = response.data
        val state = when (data?.code) {
            0 -> QrLoginState.SUCCESS
            86090 -> QrLoginState.SCANNED
            86038 -> QrLoginState.EXPIRED
            else -> QrLoginState.WAITING
        }
        if (state == QrLoginState.SUCCESS) {
            buvid.ensure()
            refreshProfile()
        }
        state to data
    }

    /** nav 是唯一可信的登录态来源；-101 时清掉失效 Cookie。 */
    suspend fun refreshProfile(): Account? {
        return runCatching {
            buvid.ensure()
            val response = api.nav()
            val data = response.data
            if (response.code == 0 && data != null && data.isLogin && data.mid > 0L) {
                val account = Account(mid = data.mid, name = data.uname, face = data.face)
                persistAccount(account)
                _account.value = account
                account
            } else {
                if (response.code == -101) clearSession()
                _account.value
            }
        }.getOrNull()
    }

    suspend fun logout() {
        cookies.clear()
        context.biliDataStore.edit {
            it.remove(KEY_ACCOUNT)
            it.remove(KEY_ACCESS_TOKEN)
            it.remove(KEY_REFRESH_TOKEN)
        }
        _account.value = null
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.biliDataStore.edit {
            it[KEY_ACCESS_TOKEN] = accessToken
            it[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun readTokens(): Pair<String?, String?> {
        val prefs = context.biliDataStore.data.first()
        return prefs[KEY_ACCESS_TOKEN] to prefs[KEY_REFRESH_TOKEN]
    }

    private suspend fun restore() {
        val prefs = context.biliDataStore.data.first()
        prefs[KEY_ACCOUNT]?.let { raw ->
            runCatching {
                BiliClient.json.decodeFromString<Account>(raw)
            }.getOrNull()?.let { _account.value = it }
        }
        if (isLoggedIn()) refreshProfile()
    }

    private suspend fun persistAccount(account: Account) {
        val encoded = BiliClient.json.encodeToString(account)
        context.biliDataStore.edit { it[KEY_ACCOUNT] = encoded }
    }

    private suspend fun clearSession() {
        cookies.clear()
        context.biliDataStore.edit { it.remove(KEY_ACCOUNT) }
        _account.value = null
    }

    private companion object {
        val KEY_ACCOUNT = stringPreferencesKey("account")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("tv_access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("tv_refresh_token")
    }
}
