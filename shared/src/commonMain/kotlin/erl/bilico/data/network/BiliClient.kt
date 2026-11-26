package erl.bilico.data.network

import erl.bilico.data.session.CookieStore
import erl.bilico.platform.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

internal const val BILI_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

private val biliJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** 一个进程一套网络栈：Cookie 会话、WBI 密钥与所有仓库共享同一套 Ktor 客户端。 */
class BiliClient(
    context: PlatformContext,
    scope: CoroutineScope,
) {
    val cookieStore = CookieStore(context, scope)

    private val biliCookieAndHeaderPlugin = createClientPlugin("BiliCookieAndHeaders") {
        onRequest { request, _ ->
            val host = request.url.host
            if (usesDefaultBilibiliHeaders(host)) {
                if (!request.headers.contains(HttpHeaders.UserAgent)) {
                    request.header(HttpHeaders.UserAgent, BILI_USER_AGENT)
                }
                if (!request.headers.contains(HttpHeaders.Referrer)) {
                    val bvid = request.url.parameters["bvid"]
                    val referer = if (request.url.encodedPath.contains("playurl") && !bvid.isNullOrBlank()) {
                        "https://www.bilibili.com/video/$bvid"
                    } else {
                        "https://www.bilibili.com/"
                    }
                    request.header(HttpHeaders.Referrer, referer)
                }
                if (!request.headers.contains(HttpHeaders.Origin)) {
                    request.header(HttpHeaders.Origin, "https://www.bilibili.com")
                }
            }
            val cookieHeader = cookieStore.cookieHeaderForHost(host)
            if (cookieHeader.isNotEmpty() && !request.headers.contains(HttpHeaders.Cookie)) {
                request.header(HttpHeaders.Cookie, cookieHeader)
            }
        }
        onResponse { response ->
            val reqUrl = response.request.url
            val host = reqUrl.host.lowercase()
            if (host != "bilibili.com" && !host.endsWith(".bilibili.com")) return@onResponse
            val setCookies = response.headers.getAll(HttpHeaders.SetCookie)
            if (!setCookies.isNullOrEmpty()) {
                cookieStore.saveSetCookieHeaders(setCookies, sourceUrl = "$host${reqUrl.encodedPath}")
            }
        }
    }

    private val passportAllowlistPlugin = createClientPlugin("QrAuthAllowlist") {
        onRequest { request, _ ->
            val url = request.url
            val allowed = url.protocol == URLProtocol.HTTPS &&
                (url.port == 443 || url.port == URLProtocol.HTTPS.defaultPort) &&
                when (url.host) {
                    "api.bilibili.com" -> url.encodedPath == "/x/web-interface/nav"
                    "passport.bilibili.com" -> url.encodedPath in setOf(
                        "/x/passport-login/web/qrcode/generate",
                        "/x/passport-login/web/qrcode/poll",
                        "/x/passport-login/web/qrcode/check",
                        "/x/passport-login/web/qrcode/scene",
                        "/x/passport-login/web/qrcode/confirm",
                        "/x/passport-tv-login/h5/refresh",
                        "/x/passport-tv-login/qrcode/auth_code",
                        "/x/passport-tv-login/h5/qrcode/confirm",
                        "/x/passport-tv-login/qrcode/poll",
                    )
                    else -> false
                }
            if (!allowed) error("扫码授权请求地址不受支持: ${url.host}${url.encodedPath}")
        }
    }

    val http: HttpClient = HttpClient {
        install(ContentNegotiation) { json(biliJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        install(WebSockets)
        install(biliCookieAndHeaderPlugin)
    }

    val redirectHttp: HttpClient = HttpClient {
        followRedirects = true
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
        }
        install(biliCookieAndHeaderPlugin)
    }

    private val passportHttp: HttpClient = HttpClient {
        followRedirects = false
        install(ContentNegotiation) { json(biliJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
        install(biliCookieAndHeaderPlugin)
        install(passportAllowlistPlugin)
    }

    val api: BiliApi = BiliApiImpl(http)

    val passportApi: PassportApi = PassportApiImpl(passportHttp)

    val wbi = WbiKeyManager(context, api = { api })
    val buvid = BuvidManager(cookies = cookieStore, api = { api })

    /** 给播放器或图片加载使用的请求头。 */
    fun liveStreamHeaders(): Map<String, String> = buildMap {
        putAll(mediaRequestHeaders())
        put("Referer", "https://live.bilibili.com/")
        put("Origin", "https://live.bilibili.com")
    }

    fun mediaRequestHeaders(bvid: String? = null): Map<String, String> = buildMap {
        put("User-Agent", BILI_USER_AGENT)
        put("Referer", if (bvid.isNullOrBlank()) "https://www.bilibili.com/" else "https://www.bilibili.com/video/$bvid")
        put("Origin", "https://www.bilibili.com")
    }

    suspend fun fetchBytes(url: String): ByteArray {
        val response = http.get(url)
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        return response.bodyAsBytes()
    }

    companion object {
        val json: Json = biliJson
    }
}
