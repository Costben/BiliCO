package erl.bilico.data.network

import java.net.URLEncoder
import java.security.MessageDigest

/** WBI 签名：imgKey/subKey 经 mixinKey 重排后与排序参数一起做 MD5。 */
object WbiUtils {
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52,
    )

    private fun getMixinKey(orig: String): String {
        val sb = StringBuilder()
        for (index in mixinKeyEncTab) {
            if (index < orig.length) sb.append(orig[index])
        }
        return sb.toString().substring(0, 32)
    }

    private fun filterIllegalChars(value: String): String =
        value.replace(Regex("[!'()*]"), "")

    private fun encodeURIComponent(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    private fun md5(str: String): String =
        MessageDigest.getInstance("MD5")
            .digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * 返回的 Map 中 Value 保持原始状态，由 Retrofit 编码；签名使用编码后的值。
     *
     * 空间类接口还要带一组播放器指纹参数，缺了会被判 风控校验失败(-352)；
     * 别的接口对多余参数敏感，所以默认不加。
     */
    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
        includeRiskFingerprint: Boolean = false,
    ): Map<String, String> {
        val mixinKey = getMixinKey(imgKey + subKey)
        val currTime = System.currentTimeMillis() / 1000

        val rawParams = mutableMapOf<String, String>()
        for ((key, value) in params) {
            rawParams[key] = filterIllegalChars(value)
        }
        rawParams["wts"] = currTime.toString()
        if (includeRiskFingerprint) {
            rawParams.putAll(RISK_FINGERPRINT)
        }

        val queryBuilder = StringBuilder()
        for (key in rawParams.keys.sorted()) {
            val value = rawParams[key] ?: continue
            if (queryBuilder.isNotEmpty()) queryBuilder.append("&")
            queryBuilder.append(key).append("=").append(encodeURIComponent(value))
        }

        rawParams["w_rid"] = md5(queryBuilder.toString() + mixinKey)
        return rawParams
    }

    /** 网页端播放器上报的渲染指纹，空间接口按它判断请求是不是真浏览器。 */
    private val RISK_FINGERPRINT = mapOf(
        "dm_img_list" to "[]",
        "dm_img_str" to "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ",
        "dm_cover_img_str" to
            "QU5HTEUgKE5WSURJQSwgTlZJRElBIEdlRm9yY2UgR1RYIDEwNjAgNkdCIERpcmVjdDNEMTEg" +
            "dnNfNV8wIHBzXzVfMCwgRDNEMTEp",
        "dm_img_inter" to """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}""",
    )
}
