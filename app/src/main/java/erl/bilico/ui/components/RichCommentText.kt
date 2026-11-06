package erl.bilico.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.util.Formatters
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

private val EmoteTokenRegex = Regex("\\[[^\\[\\]\\n]{1,32}\\]")

/**
 * 评论富文本：把 `[doge]` 等命中 [emotes] 的标记切为内联图片，其余保持原文本排版。
 * 小表情按 `1.3em` 内联；大表情包按 `2.6em` 内联，均按控件像素尺寸请求缩略图。
 */
@Composable
fun RichCommentText(
    content: String,
    emotes: Map<String, EmoteImage>,
    modifier: Modifier = Modifier,
    prefixUserName: String? = null,
    style: TextStyle = COUITheme.textStyles.body2,
    color: Color = COUITheme.colorScheme.onSurface,
    prefixColor: Color = COUITheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
) {
    val density = LocalDensity.current
    val smallPx = with(density) { 22.dp.roundToPx() }
    val largePx = with(density) { 44.dp.roundToPx() }

    val display = content.ifBlank { if (emotes.isEmpty()) "[图片评论]" else "" }
    val (annotated, inlineMap) = remember(display, emotes, prefixUserName, prefixColor, smallPx, largePx) {
        val inline = LinkedHashMap<String, InlineTextContent>()
        val builder = AnnotatedString.Builder()
        if (!prefixUserName.isNullOrBlank()) {
            builder.withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Medium)) {
                append(prefixUserName)
                append("：")
            }
        }
        if (emotes.isEmpty()) {
            builder.append(display)
        } else {
            var cursor = 0
            var index = 0
            EmoteTokenRegex.findAll(display).forEach { match ->
                if (match.range.first > cursor) {
                    builder.append(display.substring(cursor, match.range.first))
                }
                val token = match.value
                val emote = emotes[token]
                if (emote != null && emote.url.isNotBlank()) {
                    val id = "emote_${index++}"
                    val sizeEm = if (emote.large) 2.6.em else 1.32.em
                    val px = if (emote.large) largePx else smallPx
                    val sizedUrl = Formatters.sizedImageUrl(emote.url, px, px)
                    builder.appendInlineContent(id, token)
                    inline[id] = InlineTextContent(
                        Placeholder(sizeEm, sizeEm, PlaceholderVerticalAlign.Center),
                    ) {
                        AsyncImage(
                            model = sizedUrl,
                            contentDescription = "表情$token",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    builder.append(token)
                }
                cursor = match.range.last + 1
            }
            if (cursor < display.length) {
                builder.append(display.substring(cursor))
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
    )
}
