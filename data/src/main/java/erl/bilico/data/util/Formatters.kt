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
        return "$normalized@${widthPx}w_${heightPx}h_1c.webp"
    }
}
