package erl.bilico.data.repository

import erl.bilico.platform.parseUrlComponents
import erl.bilico.platform.rewriteHttpsHost
import erl.bilico.platform.urlEncode

/**
 * 播放线路。
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
    private const val DEFAULT_MIRROR_HOST = "upos-sz-mirrorcoso1.bilivideo.com"
    private const val PROXY_TF_HOST = "proxy-tf-all-ws.bilivideo.com"
    private val CDN_DOMAINS = listOf("bilivideo.com", "bilivideo.cn", "bilivideo.net", "akamaized.net")
    private val BARE_IP = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")
    private val MCDN_RESOURCE = Regex("""^https?://[^/]+/v\d+/resource""")

    fun isPcdn(url: String): Boolean {
        val parsed = parseUrlComponents(url) ?: return true
        val host = parsed.host.lowercase()
        if (parsed.queryParams["os"] == "mcdn") return true
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

    private fun isPlainCdn(url: String): Boolean {
        val parsed = parseUrlComponents(url) ?: return false
        if (!parsed.encodedPath.startsWith("/upgcxcode/")) return false
        if (isPcdn(url)) return false
        val host = parsed.host.lowercase()
        return CDN_DOMAINS.any { host == it || host.endsWith(".$it") }
    }

    private fun rewriteHost(url: String, host: String): String? = rewriteHttpsHost(url, host)

    private fun proxyWrap(url: String): String = "https://$PROXY_TF_HOST/?url=${urlEncode(url)}"

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
                val parsed = parseUrlComponents(url) ?: continue
                val host = parsed.queryParams["xy_usource"]
                    ?: option.host
                    ?: DEFAULT_MIRROR_HOST
                return rewriteHost(url, host) ?: url
            }
        }

        mcdnUpgcxcode?.let { return rewriteHost(it, option.host ?: DEFAULT_MIRROR_HOST) ?: it }
        mcdnResource?.let { return proxyWrap(it) }
        return last
    }

    fun candidates(urls: List<String>, option: CdnOption): List<String> {
        val raw = urls.filter { it.isNotBlank() }.distinct()
        if (raw.isEmpty()) return emptyList()
        val primary = resolve(raw, option)
        val rest = raw.filterNot { it == primary }
            .sortedBy { if (isPcdn(it)) 1 else 0 }
        return (listOf(primary) + rest).filter { it.isNotBlank() }.distinct()
    }
}
