package erl.bilico.feature.article

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.ArticleBlock
import erl.bilico.data.model.ArticleDetail
import erl.bilico.data.model.ArticleSpan
import erl.bilico.data.util.Formatters

/** 一段正文渲染所需的全部产物：带样式的文本 + 行内表情占位。 */
internal class ArticleRichText(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
)

/**
 * 正文行内富文本：字号、颜色、加粗、斜体、删除线、外链、表情图。
 * 没有任何样式时退化为纯文本，避免每段都建 AnnotatedString 的额外开销。
 */
@Composable
internal fun rememberArticleRichText(
    spans: List<ArticleSpan>,
    baseFontSize: Int,
    defaultColor: Color,
    linkColor: Color,
    onOpenLink: (String) -> Unit,
): ArticleRichText {
    val listener = remember(onOpenLink) {
        object : LinkInteractionListener {
            override fun onClick(link: LinkAnnotation) {
                (link as? LinkAnnotation.Url)?.url?.let(onOpenLink)
            }
        }
    }
    val emoteSize = (baseFontSize + 5).sp
    return remember(spans, baseFontSize, defaultColor, linkColor, listener, emoteSize) {
        val inline = mutableMapOf<String, InlineTextContent>()
        var emoteIndex = 0
        val annotated = buildAnnotatedString {
            spans.forEach { span ->
                val emote = span.emote
                if (emote != null) {
                    val id = "emote-${emoteIndex++}"
                    val token = span.text.ifBlank { "[表情]" }
                    val itemEmoteSize = if (emote.large) (baseFontSize * 2.3f).sp else emoteSize
                    inline[id] = InlineTextContent(
                        Placeholder(
                            width = itemEmoteSize,
                            height = itemEmoteSize,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                        {
                            AsyncImage(
                                model = Formatters.sizedImageUrl(
                                    emote.url,
                                    (itemEmoteSize.value * 2.5f).toInt(),
                                    (itemEmoteSize.value * 2.5f).toInt(),
                                ),
                                contentDescription = token,
                                modifier = Modifier.size(itemEmoteSize.value.dp),
                            )
                        },
                    )
                    appendInlineContent(id, token)
                    return@forEach
                }
                if (span.text.isEmpty()) return@forEach
                val style = SpanStyle(
                    color = span.colorArgb?.let { Color(it) } ?: defaultColor,
                    fontSize = (span.fontSize?.coerceIn(11, 40) ?: baseFontSize).sp,
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = if (span.strikethrough) TextDecoration.LineThrough else null,
                )
                val link = span.link
                if (link != null) {
                    withLink(
                        LinkAnnotation.Url(
                            url = link,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            ),
                            linkInteractionListener = listener,
                        ),
                    ) { withStyle(style) { append(span.text) } }
                } else {
                    withStyle(style) { append(span.text) }
                }
            }
        }
        ArticleRichText(annotated, inline)
    }
}

/** 图片按原比例展示，但超长图会被压到 3:1，避免一张 7648px 的长图占满几十屏。 */
internal fun articleImageAspectRatio(width: Int, height: Int): Float? {
    if (width <= 0 || height <= 0) return null
    return (width.toFloat() / height.toFloat()).coerceAtLeast(1f / 3f)
}

internal fun spansPlainText(spans: List<ArticleSpan>): String = spans.joinToString("") { it.text }

internal fun ArticleDetail.summaryRepeatsBody(): Boolean {
    val head = blocks.firstOrNull { it.plainText().isNotBlank() }?.plainText() ?: return false
    val probe = summary.trim().take(24)
    return probe.length >= 8 && head.contains(probe)
}

internal fun ArticleBlock.plainText(): String = when (this) {
    is ArticleBlock.Heading -> spans.joinToString("") { it.text }
    is ArticleBlock.Paragraph -> spans.joinToString("") { it.text }
    is ArticleBlock.Quote -> spans.joinToString("") { it.text }
    is ArticleBlock.ListBlock -> items.flatten().joinToString("") { it.text }
    is ArticleBlock.Code -> content
    is ArticleBlock.LinkCard -> card.title
    is ArticleBlock.Image, ArticleBlock.Divider -> ""
}
