package erl.bilico

import android.content.Intent

private val BvidPattern = Regex("BV[0-9A-Za-z]{10}")

/** 从外部 VIEW Intent 解析出的 B 站视频目标（支持网页链接与 `bilibili://video/...`）。 */
internal class ExternalVideoLink private constructor(
    val bvid: String?,
    val aid: Long?,
    val startSec: Long,
) {
    companion object {
        fun fromIntent(intent: Intent?): ExternalVideoLink? {
            if (intent?.action != Intent.ACTION_VIEW) return null
            // 从系统最近任务拉起被杀掉的进程时，系统会重放冷启动时的外链 Intent，不能再跳进去。
            if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return null
            val uri = intent.data ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            val host = uri.host?.lowercase()
            val path = uri.pathSegments
            val id = when (scheme) {
                "https", "http" -> {
                    if (host !in setOf("m.bilibili.com", "www.bilibili.com")) return null
                    if (path.size != 2 || path[0] != "video") return null
                    path[1]
                }
                "bilibili" -> {
                    if (host != "video" || path.size > 1) return null
                    path.singleOrNull() ?: uri.getQueryParameter("bvid") ?: return null
                }
                else -> return null
            }
            val bvid = id.takeIf(BvidPattern::matches)
            val aid = when {
                id.startsWith("av", ignoreCase = true) -> id.drop(2)
                scheme == "bilibili" -> id
                else -> ""
            }.takeIf { it.isNotEmpty() && it.all { digit -> digit in '0'..'9' } }
                ?.toLongOrNull()?.takeIf { it > 0 }
            if (bvid == null && aid == null) return null
            val startSec = uri.getQueryParameter("t")?.toLongOrNull()
                ?.takeIf { it in 1..Long.MAX_VALUE / 1000L } ?: 0L
            return ExternalVideoLink(bvid, aid, startSec)
        }
    }
}
