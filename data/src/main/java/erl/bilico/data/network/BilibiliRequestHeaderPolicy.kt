package erl.bilico.data.network

/**
 * 只有 B 站自己的域名才附加默认 Referer/Origin。
 * 共享客户端也会加载第三方图片与 RSS，对它们附加 B 站头会污染请求来源。
 */
internal fun usesDefaultBilibiliHeaders(host: String): Boolean {
    val normalized = host.lowercase()
    return listOf(
        "bilibili.com",
        "bilibili.tv",
        "biliapi.net",
        "biliapi.com",
        "hdslb.com",
        "bilivideo.com",
        "bilivideo.cn",
    ).any { normalized == it || normalized.endsWith(".$it") }
}
