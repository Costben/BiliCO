package erl.bilico.platform

import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import erl.bilico.data.network.BILI_USER_AGENT
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.usesDefaultBilibiliHeaders
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val imageHeaderInterceptor = Interceptor { chain ->
    val request = chain.request()
    if (!usesDefaultBilibiliHeaders(request.url.host)) return@Interceptor chain.proceed(request)

    val builder = request.newBuilder()
    if (request.header("User-Agent") == null) {
        builder.header("User-Agent", BILI_USER_AGENT)
    }
    if (request.header("Referer") == null) {
        builder.header("Referer", "https://www.bilibili.com/")
    }
    if (request.header("Origin") == null) {
        builder.header("Origin", "https://www.bilibili.com")
    }
    chain.proceed(builder.build())
}

/**
 * Android 图片专用 OkHttp 客户端：提高单主机并发与 HTTP/2 连接池复用，
 * 刻意不附带 Cookie，避免把登录态发往图床或第三方域名。
 */
private val imageOkHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor(imageHeaderInterceptor)
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
}

actual fun ImageLoader.Builder.configurePlatformImageLoader(client: BiliClient): ImageLoader.Builder =
    components {
        add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(AnimatedImageDecoder.Factory())
        } else {
            add(GifDecoder.Factory())
        }
    }
