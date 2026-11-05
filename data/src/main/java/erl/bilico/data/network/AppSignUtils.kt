package erl.bilico.data.network

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** APP 端签名：参数按 key 排序后拼 query，末尾接 appsec 做 MD5。 */
object AppSignUtils {
    const val TV_APP_KEY = "4409e2ce8ffd12b8"
    private const val TV_APP_SEC = "59b43e04ad6965f34319062b478f83dd"

    const val ANDROID_APP_KEY = "1d8b6e7d45233436"
    private const val ANDROID_APP_SEC = "560c52ccd288fed045859ed18bffd973"

    const val ANDROID_HD_APP_KEY = "dfca71928277209b"
    private const val ANDROID_HD_APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"

    fun percentEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    fun sign(params: Map<String, String>, appSec: String): Map<String, String> {
        val sorted = params.toSortedMap()
        val query = sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        return sorted + ("sign" to md5(query + appSec))
    }

    fun signForTvApi(params: Map<String, String>): Map<String, String> =
        sign(params + ("appkey" to TV_APP_KEY), TV_APP_SEC)

    fun signForAndroidApi(params: Map<String, String>): Map<String, String> =
        sign(params, ANDROID_APP_SEC)

    fun signForAndroidHdLogin(params: Map<String, String>): Map<String, String> =
        signEncoded(params + ("appkey" to ANDROID_HD_APP_KEY), ANDROID_HD_APP_SEC)

    fun signForTvLogin(params: Map<String, String>): Map<String, String> =
        signEncoded(params + ("appkey" to TV_APP_KEY), TV_APP_SEC)

    private fun signEncoded(params: Map<String, String>, appSec: String): Map<String, String> {
        val sorted = params.toSortedMap()
        val query = sorted.entries.joinToString("&") { (key, value) ->
            "${percentEncode(key)}=${percentEncode(value)}"
        }
        return sorted + ("sign" to md5(query + appSec))
    }

    fun getTimestamp(): Long = System.currentTimeMillis() / 1000

    private fun md5(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
