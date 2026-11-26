package erl.bilico.data.session

import erl.bilico.data.biliDataStore
import erl.bilico.data.longPreferencesKey
import erl.bilico.data.model.Account
import erl.bilico.data.model.QrLogin
import erl.bilico.data.model.QrLoginState
import erl.bilico.data.model.QrPollData
import erl.bilico.data.network.AppSignUtils
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.PassportApi
import erl.bilico.data.stringPreferencesKey
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.SynchronizedMap
import erl.bilico.platform.clearWebCookies
import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.ioDispatcher
import erl.bilico.platform.logWarn
import erl.bilico.platform.parseUrlComponents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 登录态：Cookie 由 CookieStore 负责，这里只管账号画像与扫码流程。
 */
class SessionRepository(
    private val context: PlatformContext,
    private val cookies: CookieStore,
    private val api: BiliApi,
    private val passportApi: PassportApi,
    private val buvid: BuvidManager,
    private val scope: CoroutineScope,
) {
    private val accounts = AccountStore(context, scope)
    private val tokenMutex = Mutex()
    private val tvQrKeys = SynchronizedMap<String, Boolean>()

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _sessions = MutableStateFlow<List<StoredSession>>(emptyList())
    val sessions: StateFlow<List<StoredSession>> = _sessions.asStateFlow()

    init {
        scope.launch {
            _sessions.value = accounts.read()
            restore()
        }
    }

    fun isLoggedIn(): Boolean = !cookies.get("SESSDATA").isNullOrBlank()

    fun isVip(): Boolean = _account.value?.vip == true

    fun csrf(): String = cookies.get("bili_jct").orEmpty()

    fun mid(): Long = cookies.get("DedeUserID")?.toLongOrNull() ?: 0L

    fun webCookies(): Map<String, String> = cookies.snapshot()

    suspend fun generateQr(): Result<QrLogin> = runCatching {
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
                tvQrKeys[tvData.authCode] = true
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
        if (tvQrKeys.containsKey(key)) {
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

    suspend fun loginWithCookies(cookieMap: Map<String, String>): Result<Account> = runCatching {
        val sessData = cookieMap["SESSDATA"]?.trim().orEmpty()
        require(sessData.isNotEmpty()) { "未检测到登录凭证（SESSDATA）" }
        cookies.putAll(cookieMap)
        buvid.ensure()
        val account = refreshProfile() ?: error("读取账号信息失败，请重试")
        upsertCurrentSession(account)
        account
    }

    suspend fun refreshProfile(): Account? {
        return runCatching {
            buvid.ensure()
            val (response, statData) = coroutineScope {
                val statDeferred = async {
                    runCatching { api.navStat() }.getOrNull()?.takeIf { it.code == 0 }?.data
                }
                api.nav() to statDeferred.await()
            }
            val data = response.data
            if (response.code == 0 && data != null && data.isLogin && data.mid > 0L) {
                val prev = _account.value?.takeIf { it.mid == data.mid }
                val prevSign = prev?.sign.orEmpty()
                val levelInfo = data.levelInfo
                val account = Account(
                    mid = data.mid,
                    name = data.uname,
                    face = data.face,
                    vip = data.vipStatus == 1,
                    sign = prevSign,
                    level = levelInfo?.currentLevel ?: prev?.level ?: -1,
                    currentExp = levelInfo?.currentExp ?: prev?.currentExp ?: 0,
                    nextExp = if (levelInfo != null) levelInfo.nextExpOrNull() else prev?.nextExp,
                    coins = data.money ?: prev?.coins,
                    following = statData?.following ?: prev?.following,
                    follower = statData?.follower ?: prev?.follower,
                    dynamicCount = statData?.dynamicCount ?: prev?.dynamicCount,
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

    suspend fun logout() {
        val currentMid = _account.value?.mid ?: mid()
        upsertCurrentSession()
        clearSession()
        val next = _sessions.value.firstOrNull {
            it.mid != currentMid && it.cookies.isNotEmpty()
        } ?: return
        switchAccount(next.mid)
    }

    suspend fun switchAccount(mid: Long): Boolean {
        if (mid == _account.value?.mid && isLoggedIn()) return true
        val target = _sessions.value.firstOrNull { it.mid == mid } ?: return false
        if (target.cookies.isEmpty()) return false
        upsertCurrentSession()
        cookies.replaceAll(target.cookies)
        val account = refreshProfile()
        if (account == null || account.mid != mid) {
            clearSession()
            return false
        }
        upsertCurrentSession(account)
        return true
    }

    suspend fun removeAccount(mid: Long) {
        val activeMid = _account.value?.mid ?: this.mid()
        if (mid == activeMid) clearSession()
        _sessions.value = _sessions.value.filterNot { it.mid == mid }
        accounts.write(_sessions.value)
    }

    private suspend fun upsertCurrentSession(account: Account? = _account.value) {
        val current = account ?: return
        val snapshot = cookies.snapshot()
        if (snapshot["SESSDATA"].isNullOrBlank()) return
        val stored = StoredSession(
            mid = current.mid,
            name = current.name,
            face = current.face,
            cookies = snapshot,
            savedAt = currentTimeMillis(),
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

    suspend fun ensureAccessToken(): String? = withContext(ioDispatcher) {
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
                    logWarn("BiliSession", "ensureAccessToken authCode failed code=${codeResp.code}")
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
                    logWarn("BiliSession", "ensureAccessToken confirm failed code=${confirmResp.code}")
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
                    logWarn("BiliSession", "ensureAccessToken poll failed code=${pollResp.code}")
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
        val parsed = parseUrlComponents(rawUrl) ?: return
        val updates = buildMap {
            for (name in AUTH_COOKIE_NAMES) {
                if (cookies.get(name).isNullOrBlank()) {
                    parsed.queryParams[name]?.takeIf { it.isNotBlank() }?.let { put(name, it) }
                }
            }
        }
        if (updates.isNotEmpty()) {
            cookies.putAll(updates)
        }
    }

    private fun recoverMissingAuthCookies(preferredMid: Long) {
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
            cookies.putAll(missingFromRoster)
        }
    }

    private suspend fun persistAccount(account: Account) {
        val encoded = BiliClient.json.encodeToString(account)
        context.biliDataStore.edit { it[KEY_ACCOUNT] = encoded }
    }

    private suspend fun clearSession() {
        cookies.clear()
        clearWebCookies()
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

        const val MAX_STORED_ACCOUNTS = 8
        val AUTH_COOKIE_NAMES = setOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")
    }
}
