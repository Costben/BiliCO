package erl.bilico.feature.video

import erl.bilico.data.model.EmoteImage
import kotlin.math.max

internal sealed interface DanmakuSegment {
    data class TextSeg(val text: String) : DanmakuSegment
    data class EmoteSeg(val token: String, val url: String, val large: Boolean) : DanmakuSegment
}

private val EmoteTokenRegex = Regex("\\[[^\\]\\n\\r]{1,24}\\]")

internal fun parseDanmakuSegments(
    text: String,
    emotes: Map<String, EmoteImage>,
): List<DanmakuSegment> {
    if (emotes.isEmpty() || text.isEmpty()) return listOf(DanmakuSegment.TextSeg(text))
    val trimmed = text.trim()
    val standalone = emotes[trimmed]
    if (standalone != null && emotes.size == 1 && standalone.large) {
        return listOf(DanmakuSegment.EmoteSeg(trimmed, standalone.url, large = true))
    }
    val out = ArrayList<DanmakuSegment>(4)
    var cursor = 0
    for (match in EmoteTokenRegex.findAll(text)) {
        val emote = emotes[match.value] ?: continue
        if (match.range.first > cursor) {
            out += DanmakuSegment.TextSeg(text.substring(cursor, match.range.first))
        }
        out += DanmakuSegment.EmoteSeg(match.value, emote.url, emote.large)
        cursor = match.range.last + 1
    }
    if (out.isEmpty()) return listOf(DanmakuSegment.TextSeg(text))
    if (cursor < text.length) {
        out += DanmakuSegment.TextSeg(text.substring(cursor))
    }
    return out
}

internal fun emoteIconSizePx(fontPx: Float, large: Boolean): Float =
    if (large) fontPx * 1.55f else fontPx * 1.25f

internal fun emoteAdvancePx(fontPx: Float, large: Boolean): Float =
    emoteIconSizePx(fontPx, large) + fontPx * 0.14f

internal fun maxEmoteHeightPx(segments: List<DanmakuSegment>, fontPx: Float): Float {
    var best = 0f
    for (seg in segments) {
        if (seg is DanmakuSegment.EmoteSeg) {
            best = max(best, emoteIconSizePx(fontPx, seg.large))
        }
    }
    return best
}
