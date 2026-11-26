package erl.bilico.data.network

import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.md5Hex
import erl.bilico.platform.urlEncode

/** APP 端签名：参数按 key 排序后拼 query，末尾接 appsec 做 MD5。 */
object AppSignUtils {
    const val TV_APP_KEY = "4409e2ce8ffd12b8"
    private const val TV_APP_SEC = "59b43e04ad6965f34319062b478f83dd"

    const val ANDROID_LOGIN_APP_KEY = "783bbb7264451d82"
    private const val ANDROID_LOGIN_APP_SEC = "2653583c8873dea268ab9386918b1d65"

    const val ANDROID_APP_KEY = "1d8b6e7d45233436"
    private const val ANDROID_APP_SEC = "560c52ccd288fed045859ed18bffd973"

    const val ANDROID_HD_APP_KEY = "dfca71928277209b"
    private const val ANDROID_HD_APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"

    fun percentEncode(value: String): String = urlEncode(value)

    fun sign(params: Map<String, String>, appSec: String): Map<String, String> {
        val sorted = LinkedHashMap<String, String>(params.size + 1)
        params.keys.sorted().forEach { k -> sorted[k] = params[k].orEmpty() }
        val query = sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        sorted["sign"] = md5Hex(query + appSec)
        return sorted
    }

    fun signForTvApi(params: Map<String, String>): Map<String, String> =
        sign(params + ("appkey" to TV_APP_KEY), TV_APP_SEC)

    fun signForAndroidApi(params: Map<String, String>): Map<String, String> =
        sign(params, ANDROID_APP_SEC)

    fun signForAndroidHdLogin(params: Map<String, String>): Map<String, String> =
        signEncoded(params + ("appkey" to ANDROID_HD_APP_KEY), ANDROID_HD_APP_SEC)

    fun signForTvLogin(params: Map<String, String>): Map<String, String> =
        signEncoded(params + ("appkey" to TV_APP_KEY), TV_APP_SEC)

    fun signForAndroidQrLogin(params: Map<String, String>): Map<String, String> =
        signEncoded(params + ("appkey" to ANDROID_LOGIN_APP_KEY), ANDROID_LOGIN_APP_SEC)

    private fun signEncoded(params: Map<String, String>, appSec: String): Map<String, String> {
        val sorted = LinkedHashMap<String, String>(params.size + 1)
        params.keys.sorted().forEach { k -> sorted[k] = params[k].orEmpty() }
        val query = sorted.entries.joinToString("&") { (key, value) ->
            "${percentEncode(key)}=${percentEncode(value)}"
        }
        sorted["sign"] = md5Hex(query + appSec)
        return sorted
    }

    fun getTimestamp(): Long = currentTimeMillis() / 1000L
}
