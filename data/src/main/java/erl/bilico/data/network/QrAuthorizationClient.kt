package erl.bilico.data.network

import java.io.IOException
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * 扫码授权链路不复用通用客户端的调试配置，只放行护照域名下的二维码端点。
 * 这样即使将来给主客户端加了抓包信任配置，登录仍在系统信任链上完成。
 */
fun createQrAuthorizationClient(base: OkHttpClient): OkHttpClient {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    val trustManager = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
    return base.newBuilder()
        .sslSocketFactory(context.socketFactory, trustManager)
        .hostnameVerifier(OkHttpClient().hostnameVerifier)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val url = chain.request().url
            val allowed = url.isHttps && url.port == 443 && when (url.host) {
                "api.bilibili.com" -> url.encodedPath == "/x/web-interface/nav"
                "passport.bilibili.com" -> url.encodedPath in setOf(
                    "/x/passport-login/web/qrcode/generate",
                    "/x/passport-login/web/qrcode/poll",
                    "/x/passport-login/web/qrcode/check",
                    "/x/passport-login/web/qrcode/scene",
                    "/x/passport-login/web/qrcode/confirm",
                    "/x/passport-tv-login/h5/refresh",
                )
                else -> false
            }
            if (!allowed) throw IOException("扫码授权请求地址不受支持")
            chain.proceed(chain.request())
        }
        .build()
}
