package erl.bilico.data.util

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object Formatters {

    fun formatStat(count: Long): String = when {
        count >= 100_000_000 -> oneDecimal(count, 100_000_000L, "亿")
        count >= 10_000 -> oneDecimal(count, 10_000L, "万")
        else -> count.toString()
    }

    private fun oneDecimal(count: Long, divisor: Long, unit: String): String {
        val tenths = (count + divisor / 20) / (divisor / 10)
        return "${tenths / 10}.${tenths % 10}$unit"
    }

    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d".format(minutes, secs)
        }
    }

    /** 接口里的时长是 "12:34" / "1:02:03" 文本，统一换成秒。 */
    fun parseDurationText(text: String): Int {
        if (text.isBlank()) return 0
        val parts = text.trim().split(':')
        if (parts.any { it.toIntOrNull() == null }) return 0
        return when (parts.size) {
            3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
            2 -> parts[0].toInt() * 60 + parts[1].toInt()
            1 -> parts[0].toInt()
            else -> 0
        }
    }

    fun formatTimeAgo(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return ""
        val zone = ZoneId.systemDefault()
        val published = Instant.ofEpochSecond(timestampSeconds).atZone(zone)
        val now = Instant.now().atZone(zone)
        val minutes = ChronoUnit.MINUTES.between(published, now).coerceAtLeast(0L)
        if (minutes < 1) return "刚刚"
        if (minutes < 60) return "${minutes}分钟前"
        val hours = ChronoUnit.HOURS.between(published, now).coerceAtLeast(0L)
        if (hours < 24) return "${hours}小时前"
        val days = ChronoUnit.DAYS.between(published.toLocalDate(), now.toLocalDate()).coerceAtLeast(0L)
        if (days == 1L) return "昨天"
        if (days < 30) return "${days}天前"
        val pattern = if (published.year == now.year) "MM-dd" else "yyyy-MM-dd"
        return java.time.format.DateTimeFormatter.ofPattern(pattern).format(published)
    }

    /** 评论区时间：24 小时内显示相对时间，超过 24 小时按截图规范显示 MM-dd / yyyy-MM-dd。 */
    fun formatCommentDate(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return ""
        val zone = ZoneId.systemDefault()
        val published = Instant.ofEpochSecond(timestampSeconds).atZone(zone)
        val now = Instant.now().atZone(zone)
        val minutes = ChronoUnit.MINUTES.between(published, now).coerceAtLeast(0L)
        if (minutes < 1) return "刚刚"
        if (minutes < 60) return "${minutes}分钟前"
        val hours = ChronoUnit.HOURS.between(published, now).coerceAtLeast(0L)
        if (hours < 24) return "${hours}小时前"
        val pattern = if (published.year == now.year) "MM-dd" else "yyyy-MM-dd"
        return java.time.format.DateTimeFormatter.ofPattern(pattern).format(published)
    }

    /** 投稿时间：精确到分钟，视频简介里跟播放、弹幕数排在同一行。 */
    fun formatDateTime(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return ""
        val published = Instant.ofEpochSecond(timestampSeconds).atZone(ZoneId.systemDefault())
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(published)
    }

    /** 补全协议并去掉旧缩放后缀，显示尺寸由调用方按控件尺寸重新拼。 */
    fun normalizeImageUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val withProtocol = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
            else -> url
        }
        return withProtocol.substringBefore("@")
    }

    /** B 站图片支持 `@宽w_高h_1c.webp` 后缀，按控件尺寸请求可显著省流量。 */
    fun sizedImageUrl(url: String?, widthPx: Int, heightPx: Int): String {
        val normalized = normalizeImageUrl(url)
        if (normalized.isEmpty() || widthPx <= 0 || heightPx <= 0) return normalized
        if (normalized.contains('?') || normalized.endsWith(".gif", ignoreCase = true)) return normalized
        return "$normalized@${widthPx}w_${heightPx}h_1c.webp"
    }

    /** 只限宽度不裁切，用于全屏看图与单图自适应。 */
    fun widthImageUrl(url: String?, widthPx: Int): String {
        val normalized = normalizeImageUrl(url)
        if (normalized.isEmpty() || widthPx <= 0) return normalized
        if (normalized.contains('?') || normalized.endsWith(".gif", ignoreCase = true)) return normalized
        return "$normalized@${widthPx}w.webp"
    }

    private val ENTITY = Regex("&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);")

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ensp" to " ", "emsp" to " ", "hellip" to "…",
        "mdash" to "—", "ndash" to "–", "ldquo" to "“", "rdquo" to "”",
        "lsquo" to "‘", "rsquo" to "’", "middot" to "·", "times" to "×",
    )

    /** 搜索与空间接口的文本是 HTML 转义过的，个别字段还转义了两层。 */
    fun decodeHtmlEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        var text = raw
        repeat(3) {
            val decoded = ENTITY.replace(text) { match ->
                val body = match.groupValues[1]
                when {
                    body.startsWith("#x") || body.startsWith("#X") ->
                        codePoint(body.drop(2).toIntOrNull(16)) ?: match.value

                    body.startsWith("#") ->
                        codePoint(body.drop(1).toIntOrNull()) ?: match.value

                    else -> NAMED_ENTITIES[body] ?: match.value
                }
            }
            if (decoded == text) return text
            text = decoded
        }
        return text
    }

    private fun codePoint(value: Int?): String? {
        if (value == null || value <= 0 || value > 0x10FFFF) return null
        return runCatching { String(Character.toChars(value)) }.getOrNull()
    }

    /** 观看时间：今天 / 昨天 / 月-日（跨年补年份）。 */    fun formatViewDate(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return ""
        val zone = ZoneId.systemDefault()
        val viewed = Instant.ofEpochSecond(timestampSeconds).atZone(zone)
        val now = Instant.now().atZone(zone)
        val days = ChronoUnit.DAYS.between(viewed.toLocalDate(), now.toLocalDate())
        return when {
            days <= 0L -> "今天"
            days == 1L -> "昨天"
            viewed.year == now.year ->
                java.time.format.DateTimeFormatter.ofPattern("MM-dd").format(viewed)

            else -> java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").format(viewed)
        }
    }
}
