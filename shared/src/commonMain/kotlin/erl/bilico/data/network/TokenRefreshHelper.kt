package erl.bilico.data.network

import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TV 端 access_token 刷新。
 *
 * 当前接入的是 Web 扫码登录，会话完全由 SESSDATA Cookie 表达，不会产生 access_token；
 * 这里保留 TV 登录所需的刷新链路，供后续切换到 access_token 取流时直接复用。
 */
class TokenRefreshHelper(
    private val passportApi: PassportApi,
    private val session: SessionRepository,
) {
    private val mutex = Mutex()

    suspend fun refreshIfPossible(): Boolean = mutex.withLock {
        val (accessToken, refreshToken) = session.readTokens()
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) return@withLock false

        val params = AppSignUtils.signForTvLogin(
            mapOf(
                "access_key" to accessToken,
                "refresh_token" to refreshToken,
                "ts" to AppSignUtils.getTimestamp().toString(),
            ),
        )
        val response = runCatching { passportApi.refreshToken(params) }.getOrNull()
            ?: return@withLock false
        val data = response.data
        if (response.code != 0 || data == null || data.accessToken.isBlank()) {
            return@withLock false
        }
        session.saveTokens(data.accessToken, data.refreshToken)
        true
    }
}
