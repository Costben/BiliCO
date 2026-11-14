package erl.bilico.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** 评论正文内点击 B 站视频超链接时打开对应 BV 号视频。 */
val LocalOpenBvid = compositionLocalOf<((String) -> Unit)?> { null }

/** 评论正文内点击时间戳（如 `02: 24`）时跳转播放进度（毫秒）。 */
val LocalCommentSeek = compositionLocalOf<((Long) -> Unit)?> { null }

private val EmoteTokenRegex = Regex("\\[[^\\[\\]\\n]{1,64}\\]")
private val ReplyMentionPrefixRegex = Regex("^回复\\s+(@[^\\s:：]+)\\s*(?::|：)\\s*")
private val BiliVideoUrlRegex = Regex(
    "(?:https?://)?(?:www\\.|m\\.)?bilibili\\.com/video/(BV[0-9A-Za-z]+)(?:[/?#][^\\s\\u4e00-\\u9fa5\\]\\[]*)?",
    RegexOption.IGNORE_CASE,
)
private val GenericUrlRegex = Regex("https?://[^\\s\\u4e00-\\u9fa5\\]\\[]+", RegexOption.IGNORE_CASE)
private val TimestampRegex = Regex("(?<![0-9A-Za-z_\\:/.-])(?:(\\d{1,2})\\s*(?::|：)\\s*)?(\\d{1,3})\\s*(?::|：)\\s*([0-5]\\d)(?![0-9A-Za-z\\:])")
private val InlineHighlightRegex = Regex("(@[\\w\\u4e00-\\u9fa5\\-]+|#[^#\\n]{1,40}#)")
private val TrailingUrlPunctuation = setOf('.', ',', ';', ':', '!', '?', ')', '）', '"', '\'', '。', '，', '；', '：', '！', '？')

/**
 * 评论富文本：把 `[doge]` 等命中 [emotes] 的标记切为内联图片，其余保持原文本排版。
 * 小表情按 `1.3em` 内联；大表情包在主评论按 `2.4em`、在楼中楼预览（[compactEmotes]）按 `1.45em` 内联，均按控件像素尺寸请求缩略图。
 */
@Composable
fun RichCommentText(
    content: String,
    emotes: Map<String, EmoteImage>,
    modifier: Modifier = Modifier,
    prefixUserName: String? = null,
    badgeLabel: String? = null,
    compactEmotes: Boolean = false,
    selectable: Boolean = false,
    style: TextStyle = COUITheme.textStyles.body2,
    color: Color = COUITheme.colorScheme.onSurface,
    prefixColor: Color = COUITheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val smallPx = with(density) { 22.dp.roundToPx() }
    val largePx = with(density) { (if (compactEmotes) 26.dp else 42.dp).roundToPx() }
    val uriHandler = LocalUriHandler.current
    val openBvidCallback = LocalOpenBvid.current
    val commentSeekCallback = LocalCommentSeek.current
    val currentOpenBvid by rememberUpdatedState(openBvidCallback)
    val currentSeek by rememberUpdatedState(commentSeekCallback)
    val currentUriHandler by rememberUpdatedState(uriHandler)
    val enableTimestamps = commentSeekCallback != null

    val rawDisplay = content.ifBlank { if (emotes.isEmpty()) "[图片评论]" else "" }
    val (annotated, inlineMap) = remember(
        rawDisplay,
        emotes,
        prefixUserName,
        badgeLabel,
        compactEmotes,
        prefixColor,
        smallPx,
        largePx,
        enableTimestamps,
    ) {
        val openBvidAction: (String) -> Unit = { bvid ->
            val opener = currentOpenBvid
            if (opener != null) {
                opener(bvid)
            } else {
                runCatching { currentUriHandler.openUri("https://www.bilibili.com/video/$bvid") }
            }
        }
        val seekAction: ((Long) -> Unit)? = if (enableTimestamps) {
            { ms -> currentSeek?.invoke(ms) }
        } else {
            null
        }
        val inline = LinkedHashMap<String, InlineTextContent>()
        val builder = AnnotatedString.Builder()
        if (!badgeLabel.isNullOrBlank()) {
            val badgeId = "comment-badge"
            inline[badgeId] = InlineTextContent(
                Placeholder(
                    width = 1.9.em,
                    height = 1.1.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, prefixColor, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeLabel,
                        color = prefixColor,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            builder.appendInlineContent(badgeId, badgeLabel)
            builder.append(" ")
        }
        if (!prefixUserName.isNullOrBlank()) {
            builder.withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Medium)) {
                append(prefixUserName)
                append(": ")
            }
        }
        val mentionMatch = ReplyMentionPrefixRegex.find(rawDisplay)
        val display = if (mentionMatch != null) {
            builder.append("回复 ")
            builder.withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Medium)) {
                append(mentionMatch.groupValues[1])
            }
            builder.append(": ")
            rawDisplay.substring(mentionMatch.range.last + 1)
        } else {
            rawDisplay
        }
        if (emotes.isEmpty()) {
            builder.appendRichSegment(display, prefixColor, openBvidAction, seekAction)
        } else {
            var cursor = 0
            var index = 0
            EmoteTokenRegex.findAll(display).forEach { match ->
                if (match.range.first > cursor) {
                    builder.appendRichSegment(
                        segment = display.substring(cursor, match.range.first),
                        mentionColor = prefixColor,
                        onOpenBvid = openBvidAction,
                        onSeekTimeMs = seekAction,
                    )
                }
                val token = match.value
                val emote = emotes[token]
                if (emote != null && emote.url.isNotBlank()) {
                    val id = "emote_${index++}"
                    val widthEm = when {
                        emote.large && compactEmotes -> 1.56.em
                        emote.large -> 2.48.em
                        else -> 1.38.em
                    }
                    val heightEm = when {
                        emote.large && compactEmotes -> 1.44.em
                        emote.large -> 2.32.em
                        else -> 1.32.em
                    }
                    val px = if (emote.large) largePx else smallPx
                    val sizedUrl = Formatters.sizedImageUrl(emote.url, px, px)
                    builder.appendInlineContent(id, token)
                    inline[id] = InlineTextContent(
                        Placeholder(widthEm, heightEm, PlaceholderVerticalAlign.Center),
                    ) {
                        AsyncImage(
                            model = sizedUrl,
                            contentDescription = "表情$token",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 1.dp),
                        )
                    }
                } else {
                    builder.appendRichSegment(token, prefixColor, openBvidAction, seekAction)
                }
                cursor = match.range.last + 1
            }
            if (cursor < display.length) {
                builder.appendRichSegment(display.substring(cursor), prefixColor, openBvidAction, seekAction)
            }
        }
        builder.toAnnotatedString() to inline
    }

    if (selectable) {
        SelectionContainer(modifier = modifier) {
            Text(
                text = annotated,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                inlineContent = inlineMap,
                onTextLayout = { if (onTextLayout != null) onTextLayout(it) },
            )
        }
    } else {
        Text(
            text = annotated,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            inlineContent = inlineMap,
            onTextLayout = { if (onTextLayout != null) onTextLayout(it) },
        )
    }
}

private enum class RichTokenKind { BiliVideo, GenericUrl, Timestamp, Mention }

private data class MatchedToken(val kind: RichTokenKind, val result: MatchResult)

private fun AnnotatedString.Builder.appendRichSegment(
    segment: String,
    mentionColor: Color,
    onOpenBvid: (String) -> Unit,
    onSeekTimeMs: ((Long) -> Unit)?,
) {
    val urlStyles = TextLinkStyles(
        style = SpanStyle(color = mentionColor, textDecoration = TextDecoration.Underline),
    )
    val timestampStyles = TextLinkStyles(
        style = SpanStyle(color = mentionColor, fontWeight = FontWeight.Medium),
    )
    var cursor = 0
    while (cursor < segment.length) {
        val next = earliestToken(segment, cursor, includeTimestamp = onSeekTimeMs != null)
        if (next == null) {
            append(segment.substring(cursor))
            return
        }
        val match = next.result
        if (match.range.first > cursor) {
            append(segment.substring(cursor, match.range.first))
        }
        when (next.kind) {
            RichTokenKind.BiliVideo -> {
                val rawBv = match.groupValues[1]
                val bvid = if (rawBv.length > 2) "BV${rawBv.substring(2)}" else rawBv
                val consumedEnd = trimmedUrlEnd(segment, match.range)
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "bvid:$bvid",
                        styles = urlStyles,
                        linkInteractionListener = { onOpenBvid(bvid) },
                    ),
                ) {
                    append(bvid)
                }
                cursor = consumedEnd
            }

            RichTokenKind.GenericUrl -> {
                val consumedEnd = trimmedUrlEnd(segment, match.range)
                val url = segment.substring(match.range.first, consumedEnd)
                withLink(LinkAnnotation.Url(url = url, styles = urlStyles)) {
                    append(url)
                }
                cursor = consumedEnd
            }

            RichTokenKind.Timestamp -> {
                val ms = parseTimestampMs(match)
                if (ms != null && onSeekTimeMs != null) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "seek:$ms",
                            styles = timestampStyles,
                            linkInteractionListener = { onSeekTimeMs(ms) },
                        ),
                    ) {
                        append(match.value)
                    }
                } else {
                    append(match.value)
                }
                cursor = match.range.last + 1
            }

            RichTokenKind.Mention -> {
                withStyle(SpanStyle(color = mentionColor)) {
                    append(match.value)
                }
                cursor = match.range.last + 1
            }
        }
    }
}

private fun earliestToken(segment: String, startIndex: Int, includeTimestamp: Boolean): MatchedToken? {
    val candidates = buildList {
        BiliVideoUrlRegex.find(segment, startIndex)?.let { add(MatchedToken(RichTokenKind.BiliVideo, it)) }
        GenericUrlRegex.find(segment, startIndex)?.let { add(MatchedToken(RichTokenKind.GenericUrl, it)) }
        if (includeTimestamp) {
            TimestampRegex.find(segment, startIndex)?.let { add(MatchedToken(RichTokenKind.Timestamp, it)) }
        }
        InlineHighlightRegex.find(segment, startIndex)?.let { add(MatchedToken(RichTokenKind.Mention, it)) }
    }
    return candidates.minWithOrNull(compareBy<MatchedToken> { it.result.range.first }.thenBy { it.kind.ordinal })
}

private fun trimmedUrlEnd(segment: String, range: IntRange): Int {
    var end = range.last + 1
    while (end > range.first && segment[end - 1] in TrailingUrlPunctuation) {
        end--
    }
    return end
}

private fun parseTimestampMs(match: MatchResult): Long? {
    val hoursText = match.groupValues[1]
    val minutes = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    if (hoursText.isNotEmpty() && minutes >= 60L) return null
    val hours = if (hoursText.isEmpty()) 0L else (hoursText.toLongOrNull() ?: return null)
    return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
}
