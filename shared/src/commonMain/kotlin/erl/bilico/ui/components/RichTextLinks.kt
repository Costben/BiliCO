package erl.bilico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.model.DynamicTextNode
import erl.bilico.data.model.DynamicTextNodeKind
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.util.Formatters
import erl.bilico.ui.hyperlinkColor
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

internal val InlineGiftIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "InlineGift",
        paths = listOf(
            "M19.2,12V19.2A1,1 0 0,1 18.2,20.2H5.8A1,1 0 0,1 4.8,19.2V12",
            "M4,8.6H20V12H4Z",
            "M12,8.6V20.2",
            "M12,8.6H9A2.2,2.2 0 1,1 9,4.2C11,4.2 12,8.6 12,8.6Z",
            "M12,8.6H15A2.2,2.2 0 1,0 15,4.2C13,4.2 12,8.6 12,8.6Z",
        ),
    )
}

internal val InlineLinkIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "InlineLink",
        paths = listOf(
            "M9.8,13.2A5.2,5.2 0 0,0 17.64,13.74L20.64,10.74A5.2,5.2 0 0,0 13.26,3.36L11.55,5.08",
            "M14.2,10.8A5.2,5.2 0 0,0 6.36,10.26L3.36,13.26A5.2,5.2 0 0,0 10.74,20.64L12.45,18.92",
        ),
    )
}

internal val InlinePlayCircleIcon: ImageVector by lazy {
    unifiedStrokeIcon(
        name = "InlinePlayCircle",
        paths = listOf(
            "M12,21A9,9 0 1,1 21,12A9,9 0 0,1 12,21Z",
            "M10.2,8.7L15.8,12L10.2,15.3Z",
        ),
    )
}

internal fun AnnotatedString.Builder.appendIconLink(
    inline: MutableMap<String, InlineTextContent>,
    inlineKey: String,
    icon: ImageVector,
    label: String,
    tag: String,
    color: Color,
    fontWeight: FontWeight? = null,
    onClick: (() -> Unit)? = null,
) {
    inline[inlineKey] = InlineTextContent(
        Placeholder(1.10.em, 0.96.em, PlaceholderVerticalAlign.TextCenter),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
                tint = color,
            )
        }
    }
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = color, fontWeight = fontWeight),
    )
    if (onClick != null) {
        withLink(
            LinkAnnotation.Clickable(
                tag = tag,
                styles = linkStyle,
                linkInteractionListener = { onClick() },
            ),
        ) {
            appendInlineContent(inlineKey, "\uFFFD")
            append(label)
        }
    } else {
        appendInlineContent(inlineKey, "\uFFFD")
        append(label)
    }
}

/** 评论正文内联描边徽章（如置顶 `TOP`）。 */
internal fun AnnotatedString.Builder.appendInlineOutlineBadge(
    inline: MutableMap<String, InlineTextContent>,
    badgeLabel: String,
    color: Color,
) {
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
                .border(1.dp, color, SquircleShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badgeLabel,
                color = color,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
    appendInlineContent(badgeId, badgeLabel)
    append(" ")
}

/** 楼中楼预览里跟在 UP 主用户名后面的实心粉色「UP主」内联徽章。 */
internal fun AnnotatedString.Builder.appendInlineUpBadge(
    inline: MutableMap<String, InlineTextContent>,
) {
    val badgeId = "comment-up-badge"
    inline[badgeId] = InlineTextContent(
        Placeholder(
            width = 2.55.em,
            height = 1.05.em,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp)
                .clip(SquircleShape(3.dp))
                .background(Color(0xFFFB7299)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "UP主",
                color = Color.White,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
    appendInlineContent(badgeId, "UP主")
}

/**
 * 动态正文富文本：优先渲染结构化的 `rich_text_nodes`（互动抽奖 H5、网页链接、视频、@、#话题#、表情），
 * 普通文本片段复用 [appendRichSegment] 保持去下划线超链接与原网址转「网页链接」。
 */
@Composable
fun RichDynamicText(
    richNodes: List<DynamicTextNode>,
    fallbackContent: String,
    emotes: Map<String, EmoteImage>,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    style: TextStyle = COUITheme.textStyles.body1,
    color: Color = COUITheme.colorScheme.onSurface,
    linkColor: Color = hyperlinkColor(),
    maxLines: Int = Int.MAX_VALUE,
    onOpenUser: ((Long) -> Unit)? = null,
    stripImagePlaceholders: Boolean = false,
) {
    if (richNodes.isEmpty()) {
        RichCommentText(
            content = fallbackContent,
            emotes = emotes,
            modifier = modifier,
            selectable = selectable,
            style = style,
            color = color,
            prefixColor = linkColor,
            maxLines = maxLines,
            onOpenUser = onOpenUser,
        )
        return
    }

    val density = LocalDensity.current
    val smallPx = with(density) { 22.dp.roundToPx() }
    val largePx = with(density) { 42.dp.roundToPx() }
    val uriHandler = LocalUriHandler.current
    val openBvidCallback = LocalOpenBvid.current
    val openUrlCallback = LocalOpenUrl.current
    val openTopicCallback = LocalOpenTopic.current
    val currentOpenBvid by rememberUpdatedState(openBvidCallback)
    val currentOpenUrl by rememberUpdatedState(openUrlCallback)
    val currentOpenTopic by rememberUpdatedState(openTopicCallback)
    val currentUriHandler by rememberUpdatedState(uriHandler)
    val currentOpenUser by rememberUpdatedState(onOpenUser)

    val (annotated, inlineMap) = remember(
        richNodes,
        emotes,
        linkColor,
        smallPx,
        largePx,
        stripImagePlaceholders,
    ) {
        val openUrlAction: (String, String?) -> Unit = { url, title ->
            val opener = currentOpenUrl
            if (opener != null) {
                opener(url, title)
            } else {
                runCatching { currentUriHandler.openUri(url) }
            }
        }
        val openBvidAction: (String) -> Unit = { bvid ->
            val opener = currentOpenBvid
            if (opener != null) {
                opener(bvid)
            } else {
                openUrlAction("https://www.bilibili.com/video/$bvid", null)
            }
        }
        val openTopicAction: (String) -> Unit = { topic ->
            val opener = currentOpenTopic
            if (opener != null) {
                opener(topic)
            } else if (topic.startsWith("http", ignoreCase = true)) {
                openUrlAction(topic, null)
            }
        }
        val inline = LinkedHashMap<String, InlineTextContent>()
        val builder = AnnotatedString.Builder()
        var iconCounter = 0
        var emoteCounter = 0

        richNodes.forEach { node ->
            when (node.kind) {
                DynamicTextNodeKind.LOTTERY -> {
                    val target = node.target
                    builder.appendIconLink(
                        inline = inline,
                        inlineKey = "dyn_icon_${iconCounter++}",
                        icon = InlineGiftIcon,
                        label = node.text.ifBlank { "互动抽奖" },
                        tag = "lottery:$target",
                        color = linkColor,
                        fontWeight = FontWeight.Medium,
                        onClick = target.takeIf { it.isNotBlank() }?.let { url ->
                            { openUrlAction(url, "互动抽奖") }
                        },
                    )
                }

                DynamicTextNodeKind.WEB -> {
                    val target = node.target
                    builder.appendIconLink(
                        inline = inline,
                        inlineKey = "dyn_icon_${iconCounter++}",
                        icon = InlineLinkIcon,
                        label = "网页链接",
                        tag = "web:$target",
                        color = linkColor,
                        onClick = target.takeIf { it.isNotBlank() }?.let { url ->
                            { openUrlAction(url, "网页") }
                        },
                    )
                }

                DynamicTextNodeKind.VIDEO -> {
                    val bvid = node.target
                    builder.appendIconLink(
                        inline = inline,
                        inlineKey = "dyn_icon_${iconCounter++}",
                        icon = InlinePlayCircleIcon,
                        label = node.text.ifBlank { bvid },
                        tag = "bvid:$bvid",
                        color = linkColor,
                        onClick = bvid.takeIf { it.isNotBlank() }?.let { id ->
                            { openBvidAction(id) }
                        },
                    )
                }

                DynamicTextNodeKind.AT -> {
                    val mid = node.target.toLongOrNull()
                    val linkStyle = TextLinkStyles(
                        style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                    )
                    if (mid != null && mid > 0L) {
                        builder.withLink(
                            LinkAnnotation.Clickable(
                                tag = "user:$mid",
                                styles = linkStyle,
                                linkInteractionListener = { currentOpenUser?.invoke(mid) },
                            ),
                        ) {
                            append(node.text)
                        }
                    } else {
                        builder.appendRichSegment(
                            segment = node.text,
                            mentionColor = linkColor,
                            onOpenBvid = openBvidAction,
                            onOpenUrl = openUrlAction,
                            onOpenTopic = openTopicAction,
                            onSeekTimeMs = null,
                            mentions = emptyMap(),
                            mentionNames = emptyList(),
                            onOpenUser = currentOpenUser,
                            inline = inline,
                        )
                    }
                }

                DynamicTextNodeKind.TOPIC -> {
                    val topicTarget = node.target
                    val linkStyle = TextLinkStyles(style = SpanStyle(color = linkColor))
                    if (topicTarget.isNotBlank()) {
                        builder.withLink(
                            LinkAnnotation.Clickable(
                                tag = "topic:$topicTarget",
                                styles = linkStyle,
                                linkInteractionListener = { openTopicAction(topicTarget) },
                            ),
                        ) {
                            append(node.text)
                        }
                    } else {
                        builder.append(node.text)
                    }
                }

                DynamicTextNodeKind.LINK -> {
                    val target = node.target
                    val linkStyle = TextLinkStyles(style = SpanStyle(color = linkColor))
                    if (target.isNotBlank()) {
                        builder.withLink(
                            LinkAnnotation.Clickable(
                                tag = "link:$target",
                                styles = linkStyle,
                                linkInteractionListener = { openUrlAction(target, node.text) },
                            ),
                        ) {
                            append(node.text)
                        }
                    } else {
                        builder.append(node.text)
                    }
                }

                DynamicTextNodeKind.EMOJI -> {
                    val emote = emotes[node.text]
                    if (emote != null && emote.url.isNotBlank()) {
                        val id = "dyn_emote_${emoteCounter++}"
                        val widthEm = if (emote.large) 2.48.em else 1.38.em
                        val heightEm = if (emote.large) 2.32.em else 1.32.em
                        val px = if (emote.large) largePx else smallPx
                        val sizedUrl = Formatters.sizedImageUrl(emote.url, px, px)
                        builder.appendInlineContent(id, node.text.ifEmpty { "\uFFFD" })
                        inline[id] = InlineTextContent(
                            Placeholder(widthEm, heightEm, PlaceholderVerticalAlign.Center),
                        ) {
                            AsyncImage(
                                model = sizedUrl,
                                contentDescription = "表情${node.text}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 1.dp),
                            )
                        }
                    } else {
                        builder.append(node.text)
                    }
                }

                DynamicTextNodeKind.TEXT -> {
                    val raw = if (stripImagePlaceholders) {
                        node.text.replace("[图片]", "")
                    } else {
                        node.text
                    }
                    if (raw.isNotEmpty()) {
                        builder.appendRichSegment(
                            segment = raw,
                            mentionColor = linkColor,
                            onOpenBvid = openBvidAction,
                            onOpenUrl = openUrlAction,
                            onOpenTopic = openTopicAction,
                            onSeekTimeMs = null,
                            mentions = emptyMap(),
                            mentionNames = emptyList(),
                            onOpenUser = currentOpenUser,
                            inline = inline,
                        )
                    }
                }
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
        )
    }
}
