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

    /** 仅日期（yyyy-MM-dd），用于番剧/影视开播日期等。 */
    fun formatDate(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return ""
        val date = Instant.ofEpochSecond(timestampSeconds).atZone(ZoneId.systemDefault())
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date)
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

    private const val BV_XOR_CODE = 23442827791579L
    private const val BV_MAX_AID = 1L shl 51
    private const val BV_BASE = 58L
    private const val BV_TABLE = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"

    /** 将数字 aid 转为 BV 号（对齐官方 / PiliPlus Base58 算法）。 */
    fun av2bv(aid: Long): String {
        if (aid <= 0L) return ""
        val bytes = charArrayOf('B', 'V', '1', '0', '0', '0', '0', '0', '0', '0', '0', '0')
        var bvIndex = bytes.size - 1
        var tmp = (BV_MAX_AID or aid) xor BV_XOR_CODE
        while (tmp > 0L && bvIndex >= 3) {
            bytes[bvIndex--] = BV_TABLE[(tmp % BV_BASE).toInt()]
            tmp /= BV_BASE
        }
        val t1 = bytes[3]; bytes[3] = bytes[9]; bytes[9] = t1
        val t2 = bytes[4]; bytes[4] = bytes[7]; bytes[7] = t2
        return String(bytes)
    }

    /** 接口会用 `-` 这类占位表示数量未知，这种文本不该出现在界面上，返回空串让调用方跳过。 */
    fun statLabelOrBlank(raw: String): String =
        raw.trim().takeIf { label -> label.any { it.isDigit() } }.orEmpty()

    /** 将 `96.6万` / `1.2亿` / `2011` 等简写数字还原为近似数值，供过滤规则使用。 */
    fun parseStatCount(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty()) return 0L
        val clean = Regex("([0-9]+(?:\\.[0-9]+)?)([万亿]?)").find(text) ?: return 0L
        val num = clean.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (clean.groupValues[2]) {
            "万" -> (num * 10_000.0).toLong()
            "亿" -> (num * 100_000_000.0).toLong()
            else -> num.toLong()
        }
    }
}
