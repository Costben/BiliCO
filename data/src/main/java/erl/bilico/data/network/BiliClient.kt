package erl.bilico.data.network

import android.content.Context
import erl.bilico.data.session.CookieStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.GzipSource
import okio.buffer
import okio.source
import org.brotli.dec.BrotliInputStream
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

internal const val BILI_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

private val biliJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** 复用现有 brotli 解码器为 B 站 JSON 接口启用 br/gzip 压缩，减少响应体积与射频唤醒时长。 */
private class BrotliApiInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Accept-Encoding") != null || request.url.host !in BROTLI_API_HOSTS) {
            return chain.proceed(request)
        }
        val response = chain.proceed(
            request.newBuilder()
                .header("Accept-Encoding", "br, gzip")
                .build(),
        )
        val encoding = response.header("Content-Encoding")?.lowercase() ?: return response
        val body = response.body
        val decoded = when (encoding) {
            "br" -> BrotliInputStream(body.byteStream()).source().buffer()
            "gzip" -> GzipSource(body.source()).buffer()
            else -> return response
        }
        return response.newBuilder()
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .body(decoded.asResponseBody(body.contentType(), -1L))
            .build()
    }

    private companion object {
        val BROTLI_API_HOSTS = setOf(
            "api.bilibili.com",
            "api.live.bilibili.com",
            "app.bilibili.com",
        )
    }
}

private class BiliHeaderInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
        val request = chain.request()
        if (!usesDefaultBilibiliHeaders(request.url.host)) return chain.proceed(request)

        val builder = request.newBuilder().header("User-Agent", BILI_USER_AGENT)

        if (request.header("Referer") == null) {
            val bvid = request.url.queryParameter("bvid")
            val referer = if (request.url.encodedPath.contains("playurl") && !bvid.isNullOrBlank()) {
                "https://www.bilibili.com/video/$bvid"
            } else {
                "https://www.bilibili.com/"
            }
            builder.header("Referer", referer)
        }
        if (request.header("Origin") == null) {
            builder.header("Origin", "https://www.bilibili.com")
        }
        return chain.proceed(builder.build())
    }
}

/** 一个进程一套网络栈：Cookie 会话、WBI 密钥与所有仓库共享同一条 OkHttp。 */
class BiliClient(
    context: Context,
    private val scope: CoroutineScope,
) {
    val cookieStore = CookieStore(context, scope)

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .addInterceptor(BiliHeaderInterceptor())
        .addInterceptor(BrotliApiInterceptor())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mediaOkHttp: OkHttpClient = okHttp.newBuilder()
        .retryOnConnectionFailure(true)
        .build()

    /**
     * 图片加载专用客户端：提升单主机并发与 HTTP/2 连接池复用，避免信息流封面与头像排队空白。
     * 刻意不挂 [cookieStore]：图床不需要 Cookie，也不把登录态发给第三方图片域名；
     * 与主客户端分开连接池后，图片不会挤占接口请求的并发额度。
     */
    private val imageOkHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BiliHeaderInterceptor())
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            },
        )
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: BiliApi = retrofit("https://api.bilibili.com/", okHttp).create(BiliApi::class.java)

    val passportApi: PassportApi = retrofit(
        baseUrl = "https://passport.bilibili.com/",
        client = createQrAuthorizationClient(okHttp),
    ).create(PassportApi::class.java)

    val wbi = WbiKeyManager(context, api = { api })
    val buvid = BuvidManager(cookies = cookieStore, api = { api })

    /** 启动期异步预热图床 TCP+TLS 连接，避免首屏首批封面排队等握手。 */
    fun warmupConnections() {
        IMAGE_WARMUP_HOSTS.forEach { host ->
            scope.launch(Dispatchers.IO) {
                val req = Request.Builder()
                    .url("https://$host")
                    .head()
                    .build()
                runCatching { imageOkHttp.newCall(req).execute().close() }
            }
        }
    }

    /** 给 ExoPlayer 使用同一套请求头，避免 CDN 403。 */
    fun mediaClient(): OkHttpClient = mediaOkHttp

    /** 给 Coil 全局 ImageLoader 使用的高并发图片客户端。 */
    fun imageClient(): OkHttpClient = imageOkHttp

    /** 封面走同一条网络栈。系统自己去拉图床会被拒。 */
    fun fetchBytes(url: String): ByteArray {
        okHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body.bytes()
        }
    }

    /** 播放器取流必须自带 UA 与 Referer，否则 CDN 直接拒绝。 */
    fun mediaRequestHeaders(bvid: String? = null): Map<String, String> = buildMap {
        put("User-Agent", BILI_USER_AGENT)
        put("Referer", if (bvid.isNullOrBlank()) "https://www.bilibili.com/" else "https://www.bilibili.com/video/$bvid")
        put("Origin", "https://www.bilibili.com")
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(biliJson.asConverterFactory("application/json".toMediaType()))
            .build()

    companion object {
        val json: Json = biliJson
        private val IMAGE_WARMUP_HOSTS = listOf("i0.hdslb.com", "i1.hdslb.com", "i2.hdslb.com")
    }
}
