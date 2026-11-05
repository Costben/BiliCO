package erl.bilico.data.network

import android.content.Context
import erl.bilico.data.session.CookieStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
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
    scope: CoroutineScope,
) {
    val cookieStore = CookieStore(context, scope)

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .addInterceptor(BiliHeaderInterceptor())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mediaOkHttp: OkHttpClient = okHttp.newBuilder()
        .retryOnConnectionFailure(true)
        .build()

    val api: BiliApi = retrofit("https://api.bilibili.com/", okHttp).create(BiliApi::class.java)

    val passportApi: PassportApi = retrofit(
        baseUrl = "https://passport.bilibili.com/",
        client = createQrAuthorizationClient(okHttp),
    ).create(PassportApi::class.java)

    val wbi = WbiKeyManager(context, api = { api })
    val buvid = BuvidManager(cookies = cookieStore, api = { api })

    /** 给 ExoPlayer 使用同一套请求头，避免 CDN 403。 */
    fun mediaClient(): OkHttpClient = mediaOkHttp

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
    }
}
