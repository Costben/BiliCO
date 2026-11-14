package erl.bilico.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

private val EmoteTokenRegex = Regex("\\[[^\\[\\]\\n]{1,64}\\]")
private val ReplyMentionPrefixRegex = Regex("^回复\\s+(@[^\\s:：]+)\\s*[:：]\\s*")
private val InlineHighlightRegex = Regex("(@[\\w\\u4e00-\\u9fa5\\-]+|#[^#\\n]{1,40}#)")

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
    style: TextStyle = COUITheme.textStyles.body2,
    color: Color = COUITheme.colorScheme.onSurface,
    prefixColor: Color = COUITheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val smallPx = with(density) { 22.dp.roundToPx() }
    val largePx = with(density) { (if (compactEmotes) 26.dp else 42.dp).roundToPx() }

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
    ) {
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
            builder.appendWithMentions(display, prefixColor)
        } else {
            var cursor = 0
            var index = 0
            EmoteTokenRegex.findAll(display).forEach { match ->
                if (match.range.first > cursor) {
                    builder.appendWithMentions(display.substring(cursor, match.range.first), prefixColor)
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
                    builder.appendWithMentions(token, prefixColor)
                }
                cursor = match.range.last + 1
            }
            if (cursor < display.length) {
                builder.appendWithMentions(display.substring(cursor), prefixColor)
            }
        }
        builder.toAnnotatedString() to inline
    }

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

private fun AnnotatedString.Builder.appendWithMentions(
    segment: String,
    mentionColor: Color,
) {
    var cursor = 0
    InlineHighlightRegex.findAll(segment).forEach { match ->
        if (match.range.first > cursor) {
            append(segment.substring(cursor, match.range.first))
        }
        withStyle(SpanStyle(color = mentionColor)) {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < segment.length) {
        append(segment.substring(cursor))
    }
}
