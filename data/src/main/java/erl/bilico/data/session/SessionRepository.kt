package erl.bilico.data.session

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import erl.bilico.data.model.Account
import erl.bilico.data.model.QrLogin
import erl.bilico.data.model.QrLoginState
import erl.bilico.data.model.QrPollData
import erl.bilico.data.network.AppSignUtils
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.PassportApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val accounts = AccountStore(context, scope)
    private val tokenMutex = Mutex()
    private val tvQrKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    /** 已登录过的账号名册，最近的在前；切换账号的面板直接读它。 */
    private val _sessions = MutableStateFlow<List<StoredSession>>(emptyList())
    val sessions: StateFlow<List<StoredSession>> = _sessions.asStateFlow()

    init {
        scope.launch {
            _sessions.value = accounts.read()
            restore()
        }
    }

    fun isLoggedIn(): Boolean = !cookies.get("SESSDATA").isNullOrBlank()

    /** 当前账号是不是大会员。清晰度菜单只对大会员展开会员档位。 */
    fun isVip(): Boolean = _account.value?.vip == true

    fun csrf(): String = cookies.get("bili_jct").orEmpty()

    fun mid(): Long = cookies.get("DedeUserID")?.toLongOrNull() ?: 0L

    fun hasAccessToken(): Boolean = !cookies.get("access_token").isNullOrBlank()

    fun webCookies(): Map<String, String> = cookies.snapshot()

    suspend fun generateQr(): Result<QrLogin> = runCatching {
        // 优先走 TV/App 端二维码授权：手机扫码确认后可同时获得 SESSDATA/bili_jct 与 App access_token，
        // 让「我不想看」等需要 access_key 的推荐偏好也能直接同步到 B 站服务器。
        val tvAttempt = runCatching {
            val params = AppSignUtils.signForAndroidQrLogin(
                mapOf(
                    "local_id" to "0",
                    "mobi_app" to "android",
                    "platform" to "android",
                    "ts" to AppSignUtils.getTimestamp().toString(),
                ),
            )
            val tvResp = passportApi.generateTvAuthCode(params)
            val tvData = tvResp.data
            if (tvResp.code == 0 && tvData != null && tvData.authCode.isNotBlank() && tvData.url.isNotBlank()) {
                tvQrKeys.add(tvData.authCode)
                QrLogin(key = tvData.authCode, url = tvData.url)
            } else {
                null
            }
        }.getOrNull()
        if (tvAttempt != null) return@runCatching tvAttempt

        val response = passportApi.generateQrCode()
        if (response.code != 0) error(response.message.ifBlank { "二维码获取失败(${response.code})" })
        val data = response.data ?: error("二维码数据为空")
        val key = data.qrcodeKey?.takeIf { it.isNotBlank() } ?: error("二维码 key 为空")
        val url = data.url?.takeIf { it.isNotBlank() } ?: error("二维码内容为空")
        QrLogin(key = key, url = url)
    }

    suspend fun pollQr(key: String): Result<Pair<QrLoginState, QrPollData?>> = runCatching {
        if (key in tvQrKeys) {
            val pollParams = AppSignUtils.signForAndroidQrLogin(
                mapOf(
                    "auth_code" to key,
                    "local_id" to "0",
                    "ts" to AppSignUtils.getTimestamp().toString(),
                ),
            )
            val pollResp = passportApi.pollTvQrCode(pollParams)
            val data = pollResp.data
            val state = when (pollResp.code) {
                0 -> QrLoginState.SUCCESS
                86090 -> QrLoginState.SCANNED
                86038 -> QrLoginState.EXPIRED
                else -> QrLoginState.WAITING
            }
            if (state == QrLoginState.SUCCESS && data != null) {
                tvQrKeys.remove(key)
                val cookieMap = data.cookieInfo?.cookies.orEmpty()
                    .filter { it.name.isNotBlank() && it.value.isNotBlank() }
                    .associate { it.name to it.value }
                if (cookieMap.isNotEmpty()) cookies.putAll(cookieMap)
                buvid.ensure()
                val ownerMid = data.mid.takeIf { it > 0L } ?: mid()
                if (data.accessToken.isNotBlank()) {
                    saveTokens(data.accessToken, data.refreshToken, ownerMid)
                }
                refreshProfile()?.let { upsertCurrentSession(it) }
            } else if (state == QrLoginState.EXPIRED) {
                tvQrKeys.remove(key)
            }
            return@runCatching state to QrPollData(
                refreshToken = data?.refreshToken,
                code = pollResp.code,
                message = pollResp.message,
            )
        }

        val response = passportApi.pollQrCode(key)
        val data = response.data
        val state = when (data?.code) {
            0 -> QrLoginState.SUCCESS
            86090 -> QrLoginState.SCANNED
            86038 -> QrLoginState.EXPIRED
            else -> QrLoginState.WAITING
        }
        if (state == QrLoginState.SUCCESS) {
            seedMissingAuthCookiesFromUrl(data?.url)
            buvid.ensure()
            refreshProfile()?.let { upsertCurrentSession(it) }
        }
        state to data
    }

    /** 网页登录完成后把 WebView 的 Cookie 写入 CookieStore 并拉取账号信息。 */
    suspend fun loginWithCookies(cookieMap: Map<String, String>): Result<Account> = runCatching {
        val sessData = cookieMap["SESSDATA"]?.trim().orEmpty()
        require(sessData.isNotEmpty()) { "未检测到登录凭证（SESSDATA）" }
        cookies.putAll(cookieMap)
        buvid.ensure()
        val account = refreshProfile() ?: error("读取账号信息失败，请重试")
        upsertCurrentSession(account)
        account
    }

    /** nav 是唯一可信的登录态来源；-101 时清掉失效 Cookie。 */
    suspend fun refreshProfile(): Account? {
        return runCatching {
            buvid.ensure()
            val response = api.nav()
            val data = response.data
            if (response.code == 0 && data != null && data.isLogin && data.mid > 0L) {
                val prevSign = _account.value?.takeIf { it.mid == data.mid }?.sign.orEmpty()
                val account = Account(
                    mid = data.mid,
                    name = data.uname,
                    face = data.face,
                    vip = data.vipStatus == 1,
                    sign = prevSign,
                )
                _account.value = account
                runCatching { persistAccount(account) }
                account
            } else {
                if (response.code == -101) clearSession()
                _account.value
            }
        }.getOrNull()
    }

    /** 资料修改成功后立即同步内存与本地名册缓存，不等下一次 nav 轮询。 */
    suspend fun applyUpdatedProfile(
        name: String? = null,
        face: String? = null,
        sign: String? = null,
    ) {
        val current = _account.value ?: return
        val updated = current.copy(
            name = name?.takeIf { it.isNotBlank() } ?: current.name,
            face = face?.takeIf { it.isNotBlank() } ?: current.face,
            sign = sign ?: current.sign,
        )
        _account.value = updated
        runCatching { persistAccount(updated) }
        upsertCurrentSession(updated)
    }

    /**
     * 退出当前账号。
     *
     * 先给当前账号留档（下次还能切回来），再清掉当前 Cookie。
     * 名册里还有别的号就直接换过去：只退出一个号不该把用户丢回扫码页，剩下的号还要能继续用；
     * 一个别的号都没有才算真的退出。
     */
    suspend fun logout() {
        val currentMid = _account.value?.mid ?: mid()
        upsertCurrentSession()
        clearSession()
        val next = _sessions.value.firstOrNull {
            it.mid != currentMid && it.cookies.isNotEmpty()
        } ?: return
        switchAccount(next.mid)
    }

    /**
     * 切到名册里的另一个账号：把它的 Cookie 整份换回来，再校正画像。
     * 换不回来就保持原账号不动。
     */
    suspend fun switchAccount(mid: Long): Boolean {
        if (mid == _account.value?.mid && isLoggedIn()) return true
        val target = _sessions.value.firstOrNull { it.mid == mid } ?: return false
        if (target.cookies.isEmpty()) return false
        upsertCurrentSession()
        cookies.replaceAll(target.cookies)
        val account = refreshProfile()
        if (account == null || account.mid != mid) {
            // 目标会话已失效：Cookie 已经换掉了，只能按退出处理，避免停在半登录态。
            clearSession()
            return false
        }
        upsertCurrentSession(account)
        return true
    }

    /** 从名册里删掉一个账号；删的正好是当前账号就一并退出。 */
    suspend fun removeAccount(mid: Long) {
        val activeMid = _account.value?.mid ?: this.mid()
        if (mid == activeMid) clearSession()
        _sessions.value = _sessions.value.filterNot { it.mid == mid }
        accounts.write(_sessions.value)
    }

    /** 把当前 Cookie 连同画像存进名册；同一个 mid 覆盖旧的一份并排到最前。 */
    private suspend fun upsertCurrentSession(account: Account? = _account.value) {
        val current = account ?: return
        val snapshot = cookies.snapshot()
        if (snapshot["SESSDATA"].isNullOrBlank()) return
        val stored = StoredSession(
            mid = current.mid,
            name = current.name,
            face = current.face,
            cookies = snapshot,
            savedAt = System.currentTimeMillis(),
        )
        _sessions.value = (listOf(stored) + _sessions.value.filterNot { it.mid == current.mid })
            .take(MAX_STORED_ACCOUNTS)
        accounts.write(_sessions.value)
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        ownerMid: Long = mid(),
    ) {
        context.biliDataStore.edit {
            it[KEY_ACCESS_TOKEN] = accessToken
            it[KEY_REFRESH_TOKEN] = refreshToken
            it[KEY_ACCESS_TOKEN_MID] = ownerMid
        }
    }

    suspend fun clearTokens() {
        context.biliDataStore.edit {
            it.remove(KEY_ACCESS_TOKEN)
            it.remove(KEY_REFRESH_TOKEN)
            it.remove(KEY_ACCESS_TOKEN_MID)
        }
    }

    suspend fun readTokens(): Pair<String?, String?> {
        val prefs = context.biliDataStore.data.first()
        val storedMid = prefs[KEY_ACCESS_TOKEN_MID] ?: 0L
        val currentMid = mid()
        if (storedMid > 0L && currentMid > 0L && storedMid != currentMid) return null to null
        return prefs[KEY_ACCESS_TOKEN] to prefs[KEY_REFRESH_TOKEN]
    }

    /**
     * 使用当前 Web 会话（SESSDATA + bili_jct）自动授权换取 TV/App 端 access_key，
     * 让「我不想看」及 App 推荐流可以直接向服务器同步偏好，无需重复扫码。
     */
    suspend fun ensureAccessToken(): String? = withContext(Dispatchers.IO) {
        tokenMutex.withLock {
            val activeMid = mid().takeIf { it > 0L } ?: (_account.value?.mid ?: 0L)
            val jct = csrf()
            if (activeMid <= 0L || !isLoggedIn() || jct.isBlank()) return@withLock null
            val prefs = context.biliDataStore.data.first()
            val stored = prefs[KEY_ACCESS_TOKEN]
            val storedMid = prefs[KEY_ACCESS_TOKEN_MID] ?: 0L
            if (!stored.isNullOrBlank() && storedMid == activeMid) return@withLock stored

            runCatching {
                buvid.ensure()
                val authParams = AppSignUtils.signForAndroidQrLogin(
                    mapOf(
                        "local_id" to "0",
                        "mobi_app" to "android",
                        "platform" to "android",
                        "ts" to AppSignUtils.getTimestamp().toString(),
                    ),
                )
                val codeResp = passportApi.generateTvAuthCode(authParams)
                val authCode = codeResp.data?.authCode?.takeIf { codeResp.code == 0 && it.isNotBlank() }
                if (authCode == null) {
                    Log.w("BiliSession", "ensureAccessToken authCode failed code=${codeResp.code}")
                    return@runCatching null
                }
                val confirmResp = passportApi.confirmTvQrCode(
                    mapOf(
                        "auth_code" to authCode,
                        "build" to "7082000",
                        "csrf" to jct,
                    ),
                )
                if (confirmResp.code != 0) {
                    Log.w("BiliSession", "ensureAccessToken confirm failed code=${confirmResp.code}")
                    return@runCatching null
                }
                val pollParams = AppSignUtils.signForAndroidQrLogin(
                    mapOf(
                        "auth_code" to authCode,
                        "local_id" to "0",
                        "ts" to AppSignUtils.getTimestamp().toString(),
                    ),
                )
                val pollResp = passportApi.pollTvQrCode(pollParams)
                val data = pollResp.data?.takeIf { pollResp.code == 0 && it.accessToken.isNotBlank() }
                if (data == null) {
                    Log.w("BiliSession", "ensureAccessToken poll failed code=${pollResp.code}")
                    return@runCatching null
                }
                saveTokens(data.accessToken, data.refreshToken, activeMid)
                data.accessToken
            }.getOrNull()
        }
    }

    private suspend fun restore() {
        val prefs = context.biliDataStore.data.first()
        val cachedAccount = prefs[KEY_ACCOUNT]?.let { raw ->
            runCatching { BiliClient.json.decodeFromString<Account>(raw) }.getOrNull()
        }
        recoverMissingAuthCookies(cachedAccount?.mid ?: 0L)
        if (!isLoggedIn()) {
            // Cookie 已不存在且无法从名册恢复时，清理残留画像，避免界面误判「已登录」而操作提示「登录后可用」。
            _account.value = null
            if (cachedAccount != null) {
                context.biliDataStore.edit { it.remove(KEY_ACCOUNT) }
            }
            return
        }
        if (cachedAccount != null) {
            _account.value = cachedAccount
        }
        refreshProfile()?.let { upsertCurrentSession(it) }
    }

    private fun seedMissingAuthCookiesFromUrl(url: String?) {
        val rawUrl = url?.trim().orEmpty()
        if (rawUrl.isEmpty()) return
        val parsed = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return
        val updates = buildMap {
            for (name in AUTH_COOKIE_NAMES) {
                if (cookies.get(name).isNullOrBlank()) {
                    parsed.getQueryParameter(name)?.takeIf { it.isNotBlank() }?.let { put(name, it) }
                }
            }
        }
        if (updates.isNotEmpty()) {
            cookies.putAll(updates)
        }
    }

    private fun recoverMissingAuthCookies(preferredMid: Long) {
        if (!cookies.get("SESSDATA").isNullOrBlank() && !cookies.get("bili_jct").isNullOrBlank()) return
        val webCookies = readWebViewAuthCookies()
        if (webCookies.isNotEmpty()) {
            val missingFromWeb = webCookies.filterKeys { cookies.get(it).isNullOrBlank() }
            if (missingFromWeb.isNotEmpty()) {
                cookies.putAll(missingFromWeb)
            }
        }
        if (!cookies.get("SESSDATA").isNullOrBlank() && !cookies.get("bili_jct").isNullOrBlank()) return
        val targetMid = preferredMid.takeIf { it > 0L } ?: mid()
        val stored = _sessions.value.firstOrNull { session ->
            (targetMid <= 0L || session.mid == targetMid) &&
                !session.cookies["SESSDATA"].isNullOrBlank()
        } ?: return
        val missingFromRoster = buildMap {
            AUTH_COOKIE_NAMES.forEach { name ->
                if (cookies.get(name).isNullOrBlank()) {
                    stored.cookies[name]?.takeIf { it.isNotBlank() }?.let { put(name, it) }
                }
            }
        }
        if (missingFromRoster.isNotEmpty()) {
            Log.i("BiliSession", "Recovered missing auth cookies from roster for mid=${stored.mid}")
            cookies.putAll(missingFromRoster)
        }
    }

    private fun readWebViewAuthCookies(): Map<String, String> {
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        listOf(
            "https://www.bilibili.com",
            "https://passport.bilibili.com",
            "https://api.bilibili.com",
            "https://m.bilibili.com",
        ).forEach { domainUrl ->
            val raw = runCatching { manager.getCookie(domainUrl) }.getOrNull().orEmpty()
            raw.split(';').forEach { segment ->
                val trimmed = segment.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val key = trimmed.substring(0, eq).trim()
                    val value = trimmed.substring(eq + 1).trim()
                    if (key in AUTH_COOKIE_NAMES && value.isNotEmpty()) {
                        result[key] = value
                    }
                }
            }
        }
        return result
    }

    private suspend fun persistAccount(account: Account) {
        val encoded = BiliClient.json.encodeToString(account)
        context.biliDataStore.edit { it[KEY_ACCOUNT] = encoded }
    }

    private suspend fun clearSession() {
        cookies.clear()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        context.biliDataStore.edit {
            it.remove(KEY_ACCOUNT)
            it.remove(KEY_ACCESS_TOKEN)
            it.remove(KEY_REFRESH_TOKEN)
            it.remove(KEY_ACCESS_TOKEN_MID)
        }
        _account.value = null
    }

    private companion object {
        val KEY_ACCOUNT = stringPreferencesKey("account")
        val KEY_ACCESS_TOKEN = stringPreferencesKey("tv_access_token")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("tv_refresh_token")
        val KEY_ACCESS_TOKEN_MID = longPreferencesKey("tv_access_token_mid")

        /** 名册上限：够来回切几个号就行，不做无限堆积。 */
        const val MAX_STORED_ACCOUNTS = 8
        val AUTH_COOKIE_NAMES = setOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")
    }
}
