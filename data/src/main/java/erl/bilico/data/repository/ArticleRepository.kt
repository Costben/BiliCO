package erl.bilico.data.repository

import erl.bilico.data.model.ArticleBlock
import erl.bilico.data.model.ArticleDetail
import erl.bilico.data.model.ArticleSpan
import erl.bilico.data.model.ArticleViewData
import erl.bilico.data.model.DynamicItem
import erl.bilico.data.model.DynamicModules
import erl.bilico.data.model.DynamicRichTextNode
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.model.flattenDynamicModules
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 专栏（cv）与图文动态（opus）共用一个仓库：两者最终都归一成 [ArticleDetail]，
 * 阅读页只认这一个模型。
 */
class ArticleRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {

    suspend fun article(cvId: Long): Result<ArticleDetail> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            if (cvId <= 0L) error("专栏编号无效")
            val response = api.articleView(wbiParams("id" to cvId.toString()))
            if (response.code != 0) error(response.message.ifBlank { "专栏加载失败(${response.code})" })
            val data = response.data ?: error("专栏内容为空")

            var blocks = ArticleContentParser.parse(
                paragraphs = data.opus?.paragraphs().orEmpty(),
                ops = data.ops,
                htmlOrJson = data.content,
            )
            var opusTitle = ""
            // 老专栏正文常常只给一段摘要，真正的正文挂在关联的 opus 上。
            if (ArticleContentParser.score(blocks) < SPARSE_SCORE && data.dynamicId.isNotBlank()) {
                val opus = fetchOpus(data.dynamicId)
                if (opus != null) {
                    opusTitle = opus.first
                    if (ArticleContentParser.score(opus.second) > ArticleContentParser.score(blocks)) {
                        blocks = opus.second
                    }
                }
            }
            data.toDetail(cvId, blocks, opusTitle)
        }
    }

    /**
     * 动态详情。`commentType` 决定评论接口的 oid 口径：动态 17、图文 11、专栏 12，
     * 三者可用的 oid 字段不一样，所以在这里一次性定死。
     */
    suspend fun dynamic(dynamicId: String): Result<ArticleDetail> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val id = dynamicId.trim()
            if (id.isEmpty()) error("动态编号无效")

            val detailItem = fetchDetail(id)
            val opus = fetchOpus(id)

            val modules = detailItem?.modules.decodeModules()
            val author = modules?.author
            val content = modules?.content
            val major = content?.major
            val opusMajor = major?.opus
            val articleMajor = major?.article

            var title = opusMajor?.title.orEmpty().ifBlank { opus?.first.orEmpty() }
            var blocks = opus?.second.orEmpty()
            if (blocks.isEmpty()) blocks = detailItem?.let(::blocksFromDetailItem).orEmpty()

            // 专栏卡片自带 cv 号：正文和标题都还能从专栏接口补一次。
            val articleId = articleMajor?.id?.takeIf { it > 0L }
            if (title.isBlank()) title = articleMajor?.title.orEmpty().trim()
            if (blocks.isEmpty() && articleId != null) {
                article(articleId).getOrNull()?.let { article ->
                    blocks = article.blocks
                    if (title.isBlank()) title = article.title
                }
            }

            val basic = detailItem?.basic
            val commentType = basic?.commentType?.takeIf { it > 0 }
                ?: if (articleId != null) 12 else 17
            val commentOid = firstNumericId(basic?.commentIdStr, basic?.ridStr, id) ?: articleId ?: 0L
            val images = opusMajor?.pics.orEmpty() + major?.draw?.items.orEmpty()
            val hasBlockImage = blocks.any { it is ArticleBlock.Image }
            val banner = if (hasBlockImage) {
                ""
            } else {
                images.firstOrNull { it.displayUrl.isNotBlank() }?.displayUrl.orEmpty()
            }

            ArticleDetail(
                articleId = articleId ?: 0L,
                dynamicId = id,
                title = title.ifBlank { articleMajor?.title.orEmpty() },
                summary = opusMajor?.summary?.text.orEmpty().ifBlank { content?.desc?.text.orEmpty() }.trim(),
                banner = Formatters.normalizeImageUrl(banner),
                authorMid = author?.mid ?: 0L,
                authorName = author?.name.orEmpty(),
                authorFace = Formatters.normalizeImageUrl(author?.face),
                publishTimeSec = author?.pubTs ?: 0L,
                blocks = blocks,
                commentOid = commentOid,
                commentType = commentType,
            )
        }
    }

    private suspend fun fetchDetail(id: String): DynamicItem? = runCatching {
        val response = api.dynamicDetail(
            mapOf(
                "id" to id,
                "features" to DYNAMIC_DETAIL_FEATURES,
                "timezone_offset" to "-480",
                "gaia_source" to "Athena",
                "web_location" to "333.1330",
            ),
        )
        if (response.code != 0) null else response.data?.item
    }.getOrNull()

    /** opus/detail 把作者、标题、正文、统计拆成三个 module 对象，这里只取标题与正文。 */
    private suspend fun fetchOpus(id: String): Pair<String, List<ArticleBlock>>? = runCatching {
        val response = api.opusDetail(
            wbiParams(
                "id" to id,
                "timezone_offset" to "-480",
                "features" to "htmlNewStyle",
            ),
        )
        if (response.code != 0) return@runCatching null
        val flattened = response.data?.item?.modules.flattenDynamicModules() ?: return@runCatching null
        val title = flattened["module_title"]
            ?.objectOrNull()
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()
        val paragraphs = flattened["module_content"]
            ?.objectOrNull()
            ?.get("paragraphs")
            ?.arrayOrNull()
            ?.mapNotNull { it.objectOrNull() }
            .orEmpty()
        title to ArticleContentParser.parseOpusParagraphs(paragraphs)
    }.getOrNull()

    /** 没有 opus 正文时退一步：用详情里的 desc、表情节点、图片与转发原帖拼一份完整可读的内容。 */
    private fun blocksFromDetailItem(item: DynamicItem): List<ArticleBlock> {
        val modules = item.modules.decodeModules() ?: return emptyList()
        val content = modules.content
        val blocks = mutableListOf<ArticleBlock>()
        if (content != null) {
            val rawNodes = content.desc?.richTextNodes.orEmpty() +
                content.major?.opus?.summary?.richTextNodes.orEmpty()
            val text = content.desc?.text.orEmpty()
                .ifBlank { content.major?.opus?.summary?.text.orEmpty() }
                .trim()
            blocks += buildParagraphBlocks(text, rawNodes, quote = false)

            val images = content.major?.opus?.pics.orEmpty() + content.major?.draw?.items.orEmpty()
            images.forEach { pic ->
                if (pic.displayUrl.isNotBlank()) {
                    blocks += ArticleBlock.Image(
                        url = Formatters.normalizeImageUrl(pic.displayUrl),
                        width = pic.width,
                        height = pic.height,
                    )
                }
            }
        }
        item.orig?.let { origItem ->
            val origModules = origItem.modules.decodeModules()
            val origAuthor = origModules?.author?.name.orEmpty()
            val origContent = origModules?.content
            val origNodes = origContent?.desc?.richTextNodes.orEmpty() +
                origContent?.major?.opus?.summary?.richTextNodes.orEmpty()
            val origBody = origContent?.desc?.text.orEmpty()
                .ifBlank { origContent?.major?.opus?.summary?.text.orEmpty() }
                .ifBlank { origContent?.major?.archive?.title.orEmpty() }
                .trim()
            val fullOrigText = if (origAuthor.isNotBlank()) "@$origAuthor：$origBody" else origBody
            val quoteParas = buildParagraphBlocks(fullOrigText, origNodes, quote = true)
            if (quoteParas.isNotEmpty()) {
                val mergedSpans = buildList {
                    quoteParas.filterIsInstance<ArticleBlock.Quote>().forEachIndexed { idx, q ->
                        if (idx > 0) add(ArticleSpan("\n"))
                        addAll(q.spans)
                    }
                }
                blocks += ArticleBlock.Quote(mergedSpans)
            }
            val origImages = origContent?.major?.opus?.pics.orEmpty() +
                origContent?.major?.draw?.items.orEmpty()
            origImages.forEach { pic ->
                if (pic.displayUrl.isNotBlank()) {
                    blocks += ArticleBlock.Image(
                        url = Formatters.normalizeImageUrl(pic.displayUrl),
                        width = pic.width,
                        height = pic.height,
                    )
                }
            }
        }
        return blocks
    }

    private fun buildParagraphBlocks(
        text: String,
        nodes: List<DynamicRichTextNode>,
        quote: Boolean,
    ): List<ArticleBlock> {
        if (text.isBlank()) return emptyList()
        val emotes = buildMap {
            nodes.forEach { node ->
                val emoji = node.emoji ?: node.rich?.emoji ?: return@forEach
                val url = Formatters.normalizeImageUrl(emoji.displayUrl)
                if (url.isBlank()) return@forEach
                val img = EmoteImage(url = url, large = emoji.size >= 2)
                listOf(node.text, node.origText, emoji.text, node.rich?.text.orEmpty(), node.rich?.origText.orEmpty())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { put(it, img) }
            }
        }
        val tokenRegex = Regex("\\[[^\\[\\]\\n]{1,64}\\]")
        return text.split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val spans = if (emotes.isEmpty()) {
                    listOf(ArticleSpan(line))
                } else {
                    buildList {
                        var cursor = 0
                        tokenRegex.findAll(line).forEach { match ->
                            if (match.range.first > cursor) {
                                add(ArticleSpan(line.substring(cursor, match.range.first)))
                            }
                            val token = match.value
                            val emote = emotes[token]
                            if (emote != null) {
                                add(ArticleSpan(text = token, emote = emote))
                            } else {
                                add(ArticleSpan(token))
                            }
                            cursor = match.range.last + 1
                        }
                        if (cursor < line.length) {
                            add(ArticleSpan(line.substring(cursor)))
                        }
                    }
                }
                if (quote) ArticleBlock.Quote(spans) else ArticleBlock.Paragraph(spans)
            }
    }

    private fun ArticleViewData.toDetail(
        fallbackId: Long,
        resolvedBlocks: List<ArticleBlock>,
        opusTitle: String,
    ): ArticleDetail {
        val resolvedTitle = title.trim()
            .ifBlank { opus?.title.orEmpty() }
            .ifBlank { opusTitle }
            .ifBlank { summary.trim() }
            .ifBlank { "专栏详情" }
        val banner = listOf(
            bannerUrl,
            originImageUrls.firstOrNull().orEmpty(),
            imageUrls.firstOrNull().orEmpty(),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        return ArticleDetail(
            articleId = id.takeIf { it > 0L } ?: fallbackId,
            dynamicId = dynamicId,
            title = resolvedTitle,
            summary = summary.trim(),
            banner = Formatters.normalizeImageUrl(banner),
            authorMid = author?.mid ?: 0L,
            authorName = author?.name.orEmpty(),
            authorFace = Formatters.normalizeImageUrl(author?.face),
            publishTimeSec = publishTime,
            blocks = resolvedBlocks,
            stats = stats,
            commentOid = id.takeIf { it > 0L } ?: fallbackId,
            commentType = 12,
        )
    }

    private fun firstNumericId(vararg candidates: String?): Long? {
        candidates.forEach { candidate ->
            val value = candidate?.trim().orEmpty()
            if (value.isEmpty() || !value.all(Char::isDigit)) return@forEach
            value.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        }
        return null
    }

    private suspend fun wbiParams(vararg pairs: Pair<String, String>): Map<String, String> {
        val keys = wbi.getKeys()
        return WbiUtils.sign(linkedMapOf(*pairs), keys.first, keys.second)
    }

    private fun JsonElement?.decodeModules(): DynamicModules? {
        val element = this ?: return null
        return runCatching {
            when (element) {
                is JsonObject -> BiliClient.json.decodeFromJsonElement(DynamicModules.serializer(), element)
                is JsonArray -> element
                    .mapNotNull { node ->
                        runCatching { BiliClient.json.decodeFromJsonElement(DynamicModules.serializer(), node) }
                            .getOrNull()
                    }
                    .fold(DynamicModules()) { acc, next ->
                        DynamicModules(
                            author = acc.author ?: next.author,
                            content = acc.content ?: next.content,
                            stat = acc.stat ?: next.stat,
                        )
                    }

                else -> null
            }
        }.getOrNull()
    }

    private fun JsonElement.objectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

    private fun JsonElement.arrayOrNull(): JsonArray? = runCatching { jsonArray }.getOrNull()

    private companion object {
        /** 正文评分低于这个值就认为「只有摘要」，值得再去拉一次 opus。 */
        const val SPARSE_SCORE = 800

        const val DYNAMIC_DETAIL_FEATURES =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,endFooterHidden," +
                "decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,commentsNewVersion," +
                "forwardListHidden,htmlNewStyle"
    }
}
