package erl.bilico

import android.content.Intent

private val BvidPattern = Regex("BV[0-9A-Za-z]{10}")

internal class ExternalVideoLink private constructor(
    val bvid: String?,
    val aid: Long?,
    val startSec: Long,
) {
    companion object {
        fun fromIntent(intent: Intent?): ExternalVideoLink? {
            if (intent?.action != Intent.ACTION_VIEW) return null
            val uri = intent.data ?: return null
            val path = uri.pathSegments
            val id = when (uri.scheme) {
                "https", "http" -> {
                    if (uri.host !in setOf("m.bilibili.com", "www.bilibili.com")) return null
                    if (path.size != 2 || path[0] != "video") return null
                    path[1]
                }
                "bilibili" -> {
                    if (uri.host != "video" || path.size > 1) return null
                    path.singleOrNull() ?: uri.getQueryParameter("bvid") ?: return null
                }
                else -> return null
            }
            val bvid = id.takeIf(BvidPattern::matches)
            val aid = when {
                id.startsWith("av", ignoreCase = true) -> id.drop(2)
                uri.scheme == "bilibili" -> id
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
