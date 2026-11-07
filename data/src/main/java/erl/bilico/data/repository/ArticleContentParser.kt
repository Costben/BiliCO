package erl.bilico.data.repository

import erl.bilico.data.model.ArticleBlock
import erl.bilico.data.model.ArticleLinkCard
import erl.bilico.data.model.ArticleSpan
import erl.bilico.data.model.EmoteImage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 专栏 / 图文正文解析。专栏、opus/detail、旧版 Ops(Quill) JSON、HTML 四种来源
 * 都归一到 [ArticleBlock]，取评分最高的那一份，避免正文被截断。
 */
internal object ArticleContentParser {

    /** 结构化段落里字号 ≥ 这个值的文字当作小标题。 */
    private const val HEADING_FONT_SIZE = 20

    fun parse(
        paragraphs: List<JsonObject>,
        ops: List<JsonObject>,
        htmlOrJson: String,
    ): List<ArticleBlock> {
        val fromParagraphs = paragraphs.flatMap(::parseParagraph).mergeLists()
        val contentOps = ops.ifEmpty { parseOpsFromContent(htmlOrJson) }
        val fromOps = parseOps(contentOps).mergeLists()
        val fromHtml = if (contentOps.isEmpty()) parseHtml(htmlOrJson).mergeLists() else emptyList()
        return listOf(fromParagraphs, fromOps, fromHtml).maxByOrNull(::score).orEmpty()
    }

    /** 给 opus 图文（`contentBlocks` 那种随接口给的结构化数组）复用同一套解析。 */
    fun parseOpusParagraphs(paragraphs: List<JsonObject>): List<ArticleBlock> =
        paragraphs.flatMap(::parseParagraph).mergeLists()

    fun score(blocks: List<ArticleBlock>): Int {
        if (blocks.isEmpty()) return 0
        val text = blocks.sumOf { block ->
            when (block) {
                is ArticleBlock.Heading -> block.spans.plainLength()
                is ArticleBlock.Paragraph -> block.spans.plainLength()
                is ArticleBlock.Quote -> block.spans.plainLength()
                is ArticleBlock.ListBlock -> block.items.sumOf { it.plainLength() }
                is ArticleBlock.Code -> block.content.length
                is ArticleBlock.LinkCard -> block.card.title.length + block.card.description.length
                is ArticleBlock.Image, ArticleBlock.Divider -> 0
            }
        }
        val images = blocks.count { it is ArticleBlock.Image }
        val semantic = blocks.sumOf { block ->
            when (block) {
                is ArticleBlock.Heading -> 60
                is ArticleBlock.Quote -> 40
                is ArticleBlock.ListBlock -> 60 + block.items.size * 10
                is ArticleBlock.Code -> 80
                is ArticleBlock.LinkCard -> 200
                else -> 0
            }
        }
        return text + images * 200 + blocks.size * 8 + semantic
    }

    private fun List<ArticleSpan>.plainLength(): Int = sumOf { it.text.length }

    private fun List<ArticleBlock>.mergeLists(): List<ArticleBlock> {
        if (size < 2) return this
        return buildList {
            this@mergeLists.forEach { block ->
                val previous = lastOrNull()
                if (block is ArticleBlock.ListBlock &&
                    previous is ArticleBlock.ListBlock &&
                    previous.ordered == block.ordered
                ) {
                    removeAt(lastIndex)
                    add(previous.copy(items = previous.items + block.items))
                } else if (block is ArticleBlock.Quote && previous is ArticleBlock.Quote) {
                    removeAt(lastIndex)
                    add(ArticleBlock.Quote(previous.spans + ArticleSpan("\n") + block.spans))
                } else {
                    add(block)
                }
            }
        }
    }

    // ---------- 结构化段落（article/view 的 opus.content / opus/detail 的 module_content）----------

    private fun parseParagraph(paragraph: JsonObject): List<ArticleBlock> {
        val type = paragraph["para_type"]?.jsonPrimitive?.intOrNull ?: 0
        val blocks = mutableListOf<ArticleBlock>()

        parseInline(paragraph["heading"])?.takeIf { it.plainText().isNotBlank() }?.let {
            blocks += ArticleBlock.Heading(it)
        }
        paragraph["list"]?.asObject()?.let(::parseListBlock)?.let(blocks::add)
        paragraph["code"]?.asObject()?.let(::parseCodeBlock)?.let(blocks::add)

        val images = parseImages(paragraph)
        val lineImage = if (type != 3) null else parseLineImage(paragraph)
        val isMediaParagraph = type == 2 || type == 3

        if (!isMediaParagraph || images.isEmpty()) {
            val textSpans = parseInline(paragraph["text"])
            if (textSpans != null && textSpans.plainText().isNotBlank()) {
                val maxFont = textSpans.maxOfOrNull { it.fontSize ?: 0 } ?: 0
                blocks += when {
                    type == 4 -> ArticleBlock.Quote(textSpans)
                    maxFont >= HEADING_FONT_SIZE -> ArticleBlock.Heading(textSpans)
                    else -> ArticleBlock.Paragraph(textSpans)
                }
            }
        }

        images.forEach { blocks += it }
        lineImage?.let { blocks += it }
        paragraph["link_card"]?.asObject()?.let(::parseLinkCard)?.let(blocks::add)
        return blocks
    }

    private fun parseListBlock(list: JsonObject): ArticleBlock.ListBlock? {
        val ordered = list["style"]?.jsonPrimitive?.intOrNull == 1
        val items = list["items"]?.asArray().orEmpty().mapNotNull { element ->
            val item = element.asObject() ?: return@mapNotNull null
            parseInline(item["nodes"]?.let(::nodesHolder))?.takeIf { it.plainText().isNotBlank() }
        }
        if (items.isEmpty()) return null
        return ArticleBlock.ListBlock(ordered = ordered, items = items)
    }

    private fun parseCodeBlock(code: JsonObject): ArticleBlock.Code? {
        val content = decodeEntities(code["content"]?.jsonPrimitive?.contentOrNull.orEmpty()).trim()
        if (content.isEmpty()) return null
        val language = code["lang"]?.jsonPrimitive?.contentOrNull
            .orEmpty()
            .removePrefix("language-")
            .trim()
        return ArticleBlock.Code(language = language, content = content)
    }

    private fun parseImages(paragraph: JsonObject): List<ArticleBlock.Image> {
        val result = mutableListOf<ArticleBlock.Image>()
        collectPics(paragraph["pic"]).mapNotNullTo(result, ::parseImage)
        if (result.isEmpty()) collectPics(paragraph["pics"]).mapNotNullTo(result, ::parseImage)
        return result
    }

    private fun parseLineImage(paragraph: JsonObject): ArticleBlock.Image? {
        val pic = paragraph["line"]?.asObject()?.get("pic")?.asObject() ?: return null
        return parseImage(pic)
    }

    private fun collectPics(element: JsonElement?): List<JsonObject> {
        if (element == null) return emptyList()
        element.asArray()?.let { array ->
            return array.mapNotNull { it.asObject() }
        }
        val obj = element.asObject() ?: return emptyList()
        obj["pics"]?.asArray()?.let { nested ->
            return nested.mapNotNull { it.asObject() }
        }
        return listOf(obj)
    }

    private fun parseImage(image: JsonObject): ArticleBlock.Image? {
        val url = image["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (url.isBlank()) return null
        return ArticleBlock.Image(
            url = erl.bilico.data.util.Formatters.normalizeImageUrl(url),
            width = image["width"]?.jsonPrimitive?.intOrNull ?: 0,
            height = image["height"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    private fun parseLinkCard(paragraph: JsonObject): ArticleBlock.LinkCard? {
        val card = paragraph["link_card"]?.asObject()?.get("card")?.asObject() ?: return null
        if (card["type"]?.jsonPrimitive?.contentOrNull == "LINK_CARD_TYPE_GOODS") return null
        val nested = listOf("ugc", "common", "opus", "live", "music", "vote")
            .firstNotNullOfOrNull { key -> card[key]?.asObject() } ?: return null
        val title = nested["title"]?.jsonPrimitive?.contentOrNull
            ?: nested["name"]?.jsonPrimitive?.contentOrNull
            ?: return null
        if (title.isBlank() || title == "undefined") return null
        val description = listOf(
            nested["desc"]?.jsonPrimitive?.contentOrNull,
            nested["desc_second"]?.jsonPrimitive?.contentOrNull,
            nested["desc1"]?.jsonPrimitive?.contentOrNull,
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        return ArticleBlock.LinkCard(
            ArticleLinkCard(
                title = title.trim(),
                description = decodeEntities(description),
                cover = erl.bilico.data.util.Formatters.normalizeImageUrl(
                    nested["cover"]?.jsonPrimitive?.contentOrNull,
                ),
                url = normalizeLink(nested["jump_url"]?.jsonPrimitive?.contentOrNull).orEmpty(),
            ),
        )
    }

    // ---------- 行内节点 ----------

    /** `{"nodes":[...]}` 到节点数组的统一入口。 */
    private fun nodesHolder(holder: JsonElement): JsonElement? =
        holder.asObject()?.get("nodes")

    private fun parseInline(element: JsonElement?): List<ArticleSpan>? {
        val nodes = element?.asObject()?.get("nodes")?.asArray() ?: return null
        val spans = mutableListOf<ArticleSpan>()
        nodes.forEach { node ->
            val obj = node.asObject() ?: return@forEach
            val rich = obj["rich"]?.asObject()
            val word = obj["word"]?.asObject()
            val emote = rich?.get("emoji")?.asObject()?.let(::parseEmote)
            val text = word?.get("words")?.jsonPrimitive?.contentOrNull
                ?: rich?.get("text")?.jsonPrimitive?.contentOrNull
                ?: rich?.get("orig_text")?.jsonPrimitive?.contentOrNull
                ?: emote?.let { "" }
                ?: obj["formula"]?.asObject()?.get("latex_content")?.jsonPrimitive?.contentOrNull
                ?: return@forEach
            if (text.isEmpty() && emote == null) return@forEach

            val fontSize = word?.get("font_size")?.jsonPrimitive?.intOrNull?.takeIf { it in 8..64 }
            val color = word?.get("color")?.jsonPrimitive?.contentOrNull?.let(::parseColor)
            val style = word?.get("style")?.asObject()
            fun flag(key: String): Boolean {
                val value = style?.get(key) ?: return false
                val primitive = value as? JsonPrimitive ?: return false
                return primitive.booleanOrNull == true ||
                    primitive.contentOrNull == "true" ||
                    primitive.intOrNull == 1
            }
            spans += ArticleSpan(
                text = text,
                fontSize = fontSize,
                colorArgb = color,
                bold = flag("bold"),
                italic = flag("italic"),
                strikethrough = flag("strikethrough") || flag("strike"),
                link = normalizeLink(rich?.get("jump_url")?.jsonPrimitive?.contentOrNull),
                emote = emote,
            )
        }
        return spans.takeIf { it.isNotEmpty() }
    }

    private fun parseEmote(emoji: JsonObject): EmoteImage? {
        val icon = emoji["icon_url"]?.jsonPrimitive?.contentOrNull
            ?: emoji["url"]?.jsonPrimitive?.contentOrNull
            ?: emoji["webp_url"]?.jsonPrimitive?.contentOrNull
            ?: return null
        if (icon.isBlank()) return null
        val size = emoji["size"]?.jsonPrimitive?.intOrNull ?: 1
        return EmoteImage(
            url = erl.bilico.data.util.Formatters.normalizeImageUrl(icon),
            large = size >= 2,
        )
    }

    private fun List<ArticleSpan>.plainText(): String = joinToString("") { it.text }

    // ---------- Ops（Quill）----------

    private fun parseOpsFromContent(content: String): List<JsonObject> {
        val raw = content.trim()
        if (raw.isEmpty() || (!raw.startsWith("{") && !raw.startsWith("["))) return emptyList()
        return runCatching {
            when (val root = erl.bilico.data.network.BiliClient.json.parseToJsonElement(raw)) {
                is JsonArray -> root.mapNotNull { it.asObject() }
                is JsonObject -> root["ops"]?.asArray()?.mapNotNull { it.asObject() }.orEmpty()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseOps(ops: List<JsonObject>): List<ArticleBlock> {
        if (ops.isEmpty()) return emptyList()
        val result = mutableListOf<ArticleBlock>()
        val segments = mutableListOf<Pair<String, ArticleSpan>>()
        var lineAttributes: JsonObject? = null

        fun flush() {
            if (segments.isEmpty()) {
                lineAttributes = null
                return
            }
            val text = segments.joinToString("") { it.second.text }
            val attributes = lineAttributes
            val header = attributes?.get("header")?.jsonPrimitive?.intOrNull
            val isQuote = attributes?.get("blockquote")?.jsonPrimitive?.contentOrNull == "true"
            val listStyle = attributes?.get("list")?.jsonPrimitive?.contentOrNull
            val spans = segments.map { it.second }
            segments.clear()
            lineAttributes = null
            if (text.isBlank()) return
            result += when {
                header != null -> ArticleBlock.Heading(spans)
                isQuote -> ArticleBlock.Quote(spans)
                !listStyle.isNullOrBlank() -> ArticleBlock.ListBlock(
                    ordered = listStyle.equals("ordered", ignoreCase = true),
                    items = listOf(spans),
                )

                else -> ArticleBlock.Paragraph(spans)
            }
        }

        ops.forEach { op ->
            val attributes = op["attributes"]?.asObject()
            when (val insert = op["insert"]) {
                is JsonPrimitive -> {
                    val parts = insert.contentOrNull.orEmpty().split('\n')
                    parts.forEachIndexed { index, part ->
                        if (part.isNotEmpty()) {
                            segments += part to ArticleSpan(
                                text = part,
                                colorArgb = attributes?.get("color")?.jsonPrimitive?.contentOrNull?.let(::parseColor),
                                bold = attributes?.get("bold")?.truthy() == true,
                                italic = attributes?.get("italic")?.truthy() == true,
                                strikethrough = attributes?.get("strike")?.truthy() == true,
                                link = normalizeLink(attributes?.get("link")?.jsonPrimitive?.contentOrNull),
                            )
                        }
                        if (index < parts.lastIndex) {
                            lineAttributes = attributes
                            flush()
                        }
                    }
                }

                is JsonObject -> {
                    flush()
                    parseOpsImage(insert)?.let(result::add)
                }

                else -> Unit
            }
        }
        flush()
        return result
    }

    private fun parseOpsImage(insert: JsonObject): ArticleBlock.Image? {
        (insert["image"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return ArticleBlock.Image(erl.bilico.data.util.Formatters.normalizeImageUrl(it))
        }
        val keys = listOf(
            "native-image", "image-card", "cut-off", "article-card",
            "live-card", "goods-card", "video-card", "mall-card", "vote-card",
        )
        return keys.firstNotNullOfOrNull { key ->
            insert[key]?.asObject()?.let(::parseImage)
        }
    }

    // ---------- HTML ----------

    private fun parseHtml(html: String?): List<ArticleBlock> {
        val content = html?.trim().orEmpty()
        if (content.isEmpty() || content.startsWith("{")) return emptyList()
        val blockRegex = Regex("""(?is)<(h[1-6]|p|pre|blockquote|li|figure)\b[^>]*>(.*?)</\1>|<img\b[^>]*>""")
        val result = mutableListOf<ArticleBlock>()
        blockRegex.findAll(content).forEach { match ->
            val tag = match.groupValues.getOrNull(1).orEmpty().lowercase()
            val inner = if (tag.isEmpty()) match.value else match.groupValues[2]
            when {
                tag.startsWith("h") -> {
                    val spans = parseHtmlSpans(inner)
                    if (spans.plainText().isNotBlank()) result += ArticleBlock.Heading(spans)
                }

                tag == "p" || tag == "figure" -> appendHtmlBlocks(result, inner, quote = false)
                tag == "blockquote" -> appendHtmlBlocks(result, inner, quote = true)
                tag == "pre" -> {
                    val text = cleanupHtml(inner)
                    if (text.isNotBlank()) result += ArticleBlock.Code("", text)
                }

                tag == "li" -> {
                    val spans = parseHtmlSpans(inner)
                    if (spans.plainText().isNotBlank()) {
                        result += ArticleBlock.ListBlock(ordered = false, items = listOf(spans))
                    }
                }

                match.value.startsWith("<img", ignoreCase = true) -> parseHtmlImage(match.value)?.let(result::add)
            }
        }
        return result
    }

    private fun appendHtmlBlocks(target: MutableList<ArticleBlock>, content: String, quote: Boolean) {
        val imgRegex = Regex("""(?is)<img\b[^>]*>""")
        var lastIndex = 0
        imgRegex.findAll(content).forEach { match ->
            appendHtmlText(target, content.substring(lastIndex, match.range.first), quote)
            parseHtmlImage(match.value)?.let(target::add)
            lastIndex = match.range.last + 1
        }
        appendHtmlText(target, content.substring(lastIndex), quote)
    }

    private fun appendHtmlText(target: MutableList<ArticleBlock>, raw: String, quote: Boolean) {
        if (raw.isBlank()) return
        val spans = parseHtmlSpans(raw)
        if (spans.plainText().isBlank()) return
        target += if (quote) ArticleBlock.Quote(spans) else ArticleBlock.Paragraph(spans)
    }

    private fun parseHtmlImage(tagBlock: String): ArticleBlock.Image? {
        val tag = Regex("""(?is)<img\b[^>]*>""").find(tagBlock)?.value ?: tagBlock
        val url = htmlAttr(tag, "data-src") ?: htmlAttr(tag, "src") ?: return null
        return ArticleBlock.Image(
            url = erl.bilico.data.util.Formatters.normalizeImageUrl(url),
            width = htmlAttr(tag, "width")?.toIntOrNull() ?: 0,
            height = htmlAttr(tag, "height")?.toIntOrNull() ?: 0,
        )
    }

    private fun htmlAttr(tag: String, name: String): String? =
        Regex("""(?is)\b$name\s*=\s*["']([^"']+)["']""")
            .find(tag)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private val htmlToken = Regex("""(?is)<(/?)([a-zA-Z][a-zA-Z0-9]*)\b[^>]*>|[^<]+""")

    private data class HtmlStyle(
        val fontSize: Int? = null,
        val color: Long? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val link: String? = null,
    )

    private fun parseHtmlSpans(raw: String): List<ArticleSpan> {
        val spans = mutableListOf<ArticleSpan>()
        val stack = ArrayDeque<HtmlStyle>().apply { addLast(HtmlStyle()) }
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isEmpty()) return
            val text = buffer.toString()
            buffer.clear()
            val style = stack.last()
            spans += ArticleSpan(
                text = text,
                fontSize = style.fontSize,
                colorArgb = style.color,
                bold = style.bold,
                italic = style.italic,
                strikethrough = style.strike,
                link = style.link,
            )
        }

        htmlToken.findAll(raw).forEach { match ->
            val token = match.value
            if (!token.startsWith("<")) {
                buffer.append(decodeEntities(token))
                return@forEach
            }
            val closing = match.groupValues[1] == "/"
            when (match.groupValues[2].lowercase()) {
                "b", "strong" -> {
                    flush()
                    if (closing) unwind(stack) { it.bold } else stack.addLast(stack.last().copy(bold = true))
                }

                "i", "em" -> {
                    flush()
                    if (closing) unwind(stack) { it.italic } else stack.addLast(stack.last().copy(italic = true))
                }

                "del", "s", "strike" -> {
                    flush()
                    if (closing) unwind(stack) { it.strike } else stack.addLast(stack.last().copy(strike = true))
                }

                "span" -> {
                    flush()
                    if (closing) {
                        unwind(stack) { it.fontSize != null || it.color != null }
                    } else {
                        val attr = htmlAttr(token, "style").orEmpty()
                        val size = Regex("""font-size\s*:\s*(\d+(?:\.\d+)?)\s*px""", RegexOption.IGNORE_CASE)
                            .find(attr)?.groupValues?.getOrNull(1)
                            ?.toFloatOrNull()?.toInt()?.takeIf { it in 8..64 }
                        val color = Regex("""(?:^|;)\s*color\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
                            .find(attr)?.groupValues?.getOrNull(1)?.trim()?.let(::parseColor)
                        if (size != null || color != null) {
                            stack.addLast(stack.last().copy(fontSize = size ?: stack.last().fontSize, color = color ?: stack.last().color))
                        }
                    }
                }

                "a" -> {
                    flush()
                    if (closing) {
                        unwind(stack) { it.link != null }
                    } else {
                        normalizeLink(htmlAttr(token, "href"))?.let { link ->
                            stack.addLast(stack.last().copy(link = link))
                        }
                    }
                }

                "br" -> buffer.append('\n')
                else -> Unit
            }
        }
        flush()
        return spans
    }

    private fun unwind(stack: ArrayDeque<HtmlStyle>, matches: (HtmlStyle) -> Boolean) {
        for (index in stack.size - 1 downTo 1) {
            if (matches(stack[index])) {
                while (stack.size > index) stack.removeLast()
                return
            }
        }
    }

    private fun cleanupHtml(raw: String): String = decodeEntities(
        raw.replace(Regex("""(?is)<br\s*/?>"""), "\n").replace(Regex("""(?is)<[^>]+>"""), ""),
    ).trim()

    // ---------- 通用 ----------

    /** "#RRGGBB" / "#AARRGGBB" / rgb(r,g,b) -> ARGB Long。 */
    internal fun parseColor(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val hex = when {
            value.startsWith("#") -> value.substring(1)
            value.startsWith("rgb(", ignoreCase = true) -> value.substringAfter("(")
                .substringBefore(")")
                .split(",", ";")
                .mapNotNull { it.trim().toIntOrNull() }
                .takeIf { it.size >= 3 }
                ?.joinToString("") { it.coerceIn(0, 255).toString(16).padStart(2, '0') }
                ?: return null

            else -> return null
        }
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { 0xFF000000L or it }
            8 -> hex.toLongOrNull(16)
            else -> null
        }
    }

    internal fun normalizeLink(raw: String?): String? {
        val value = raw?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "https://www.bilibili.com$value"
            value.startsWith("http://", ignoreCase = true) -> value.replaceFirst("http://", "https://")
            value.startsWith("https://", ignoreCase = true) -> value
            else -> null
        }
    }

    private fun decodeEntities(raw: String): String =
        erl.bilico.data.util.Formatters.decodeHtmlEntities(raw)

    private fun JsonElement.asObject(): JsonObject? = runCatching { jsonObject }.getOrNull()

    private fun JsonElement.asArray(): JsonArray? = runCatching { jsonArray }.getOrNull()

    private fun JsonElement?.truthy(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        return primitive.booleanOrNull == true || primitive.contentOrNull == "true" || primitive.intOrNull == 1
    }
}
