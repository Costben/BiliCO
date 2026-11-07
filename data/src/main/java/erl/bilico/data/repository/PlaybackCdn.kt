package erl.bilico.data.repository

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

/**
 * 播放线路。
 *
 * B 站 playurl 会把 PCDN（`os=mcdn`、`*.mcdn.bilivideo.*`、`*.edge.mountaintoys.cn`）排在
 * `baseUrl` 第一位。这类节点由用户设备互相供带宽，随时可能拒绝连接或几秒才响应，
 * 播放器拿不到数据就报 `Source error`。所以默认跳过它们，只挑常规 CDN。
 *
 * 各 mirror 之间签名只覆盖 query，不覆盖主机名，换 host 后地址依然有效。
 */
enum class CdnOption(
    val id: String,
    val label: String,
    val host: String?,
) {
    Auto("auto", "自动（跳过 PCDN）", null),
    Base("base", "基础地址（不推荐）", null),
    Ali("ali", "ali · 阿里云", "upos-sz-mirrorali.bilivideo.com"),
    Alib("alib", "alib · 阿里云", "upos-sz-mirroralib.bilivideo.com"),
    Alio1("alio1", "alio1 · 阿里云", "upos-sz-mirroralio1.bilivideo.com"),
    Cos("cos", "cos · 腾讯云", "upos-sz-mirrorcos.bilivideo.com"),
    Cosb("cosb", "cosb · 腾讯云 VOD", "upos-sz-mirrorcosb.bilivideo.com"),
    Coso1("coso1", "coso1 · 腾讯云", "upos-sz-mirrorcoso1.bilivideo.com"),
    Hw("hw", "hw · 华为云", "upos-sz-mirrorhw.bilivideo.com"),
    Hwb("hwb", "hwb · 华为云", "upos-sz-mirrorhwb.bilivideo.com"),
    Hwo1("hwo1", "hwo1 · 华为云", "upos-sz-mirrorhwo1.bilivideo.com"),
    Hw08c("08c", "08c · 华为云", "upos-sz-mirror08c.bilivideo.com"),
    Hw08h("08h", "08h · 华为云", "upos-sz-mirror08h.bilivideo.com"),
    Hw08ct("08ct", "08ct · 华为云", "upos-sz-mirror08ct.bilivideo.com"),
    TfHw("tf_hw", "tf_hw · 华为云", "upos-tf-all-hw.bilivideo.com"),
    TfTx("tf_tx", "tf_tx · 腾讯云", "upos-tf-all-tx.bilivideo.com"),
    Akamai("akamai", "akamai · 海外", "upos-hz-mirrorakam.akamaized.net"),
    AliOv("aliov", "aliov · 阿里云海外", "upos-sz-mirroraliov.bilivideo.com"),
    CosOv("cosov", "cosov · 腾讯云海外", "upos-sz-mirrorcosov.bilivideo.com"),
    HwOv("hwov", "hwov · 华为云海外", "upos-sz-mirrorhwov.bilivideo.com"),
    HkBcache("hk_bcache", "hk_bcache · B站海外", "cn-hk-eq-bcache-01.bilivideo.com"),
    ;

    companion object {
        fun fromId(id: String?): CdnOption = entries.firstOrNull { it.id == id } ?: Auto
    }
}

/** 候选地址排序与换线。 */
internal object PlaybackCdn {
    /** 所有候选都是 PCDN 时换到这个常规 CDN。 */
    private const val DEFAULT_MIRROR_HOST = "upos-sz-mirrorcoso1.bilivideo.com"

    /** PCDN 的 `/v1/resource/` 形态只能走这个反代，直接换 host 会 404。 */
    private const val PROXY_TF_HOST = "proxy-tf-all-ws.bilivideo.com"

    private val CDN_DOMAINS = listOf("bilivideo.com", "bilivideo.cn", "bilivideo.net", "akamaized.net")

    private val BARE_IP = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")

    private val MCDN_RESOURCE = Regex("""^https?://[^/]+/v\d+/resource""")

    /** 是否是 PCDN / 来路不明的地址。 */
    fun isPcdn(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return true
        val host = parsed.host.lowercase()
        if (parsed.queryParameter("os") == "mcdn") return true
        if (BARE_IP.matches(host)) return true
        if (host.endsWith(".mcdn.bilivideo.com") ||
            host.endsWith(".mcdn.bilivideo.cn") ||
            host.endsWith(".mcdn.bilivideo.net")
        ) {
            return true
        }
        if (host == "edge.mountaintoys.cn" || host.endsWith(".edge.mountaintoys.cn")) return true
        if (host.contains("pcdn") || host.contains("gotcha")) return true
        if (url.contains("szbdyd.com") ||
            url.contains("nexusedgeio.com") ||
            url.contains("ahdohpiechei.com") ||
            url.contains("upos-sz-302")
        ) {
            return true
        }
        return false
    }

    /**
     * 常规 CDN 的 `/upgcxcode/` 地址：换 host 后签名仍然有效。
     * 这类地址优先用，省掉一次换线。
     */
    private fun isPlainCdn(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (!parsed.encodedPath.startsWith("/upgcxcode/")) return false
        if (isPcdn(url)) return false
        val host = parsed.host.lowercase()
        return CDN_DOMAINS.any { host == it || host.endsWith(".$it") }
    }

    private fun rewriteHost(url: String, host: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        return parsed.newBuilder()
            .scheme("https")
            .host(host)
            .port(443)
            .build()
            .toString()
    }

    private fun proxyWrap(url: String): String =
        "https://$PROXY_TF_HOST/?url=${URLEncoder.encode(url, "UTF-8")}"

    /**
     * 按线路偏好挑一个能直接播的地址。
     * 顺序：常规 CDN → `/upgcxcode/` 地址换 host → PCDN 走反代 → 原样返回。
     */
    fun resolve(urls: List<String>, option: CdnOption): String {
        val candidates = urls.filter { it.isNotBlank() }
        if (candidates.isEmpty()) return ""
        if (option == CdnOption.Base) return candidates.first()

        var mcdnResource: String? = null
        var mcdnUpgcxcode: String? = null
        var last = ""

        for (url in candidates) {
            last = url
            if (isPlainCdn(url)) {
                val host = option.host ?: return url
                return rewriteHost(url, host) ?: url
            }
            if (MCDN_RESOURCE.containsMatchIn(url)) {
                mcdnResource = url
                continue
            }
            if (url.contains("/upgcxcode/")) {
                mcdnUpgcxcode = url
                continue
            }
            if (url.contains("szbdyd.com")) {
                val parsed = url.toHttpUrlOrNull() ?: continue
                val host = parsed.queryParameter("xy_usource")
                    ?: option.host
                    ?: DEFAULT_MIRROR_HOST
                return rewriteHost(url, host) ?: url
            }
        }

        mcdnUpgcxcode?.let { return rewriteHost(it, option.host ?: DEFAULT_MIRROR_HOST) ?: it }
        mcdnResource?.let { return proxyWrap(it) }
        return last
    }

    /**
     * 播放器按顺序重试的完整候选表：首选地址在前，其余常规 CDN 居中，PCDN 垫底。
     */
    fun candidates(urls: List<String>, option: CdnOption): List<String> {
        val raw = urls.filter { it.isNotBlank() }.distinct()
        if (raw.isEmpty()) return emptyList()
        val primary = resolve(raw, option)
        val rest = raw.filterNot { it == primary }
            .sortedBy { if (isPcdn(it)) 1 else 0 }
        return (listOf(primary) + rest).filter { it.isNotBlank() }.distinct()
    }
}
