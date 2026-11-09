package erl.bilico.data.repository

import erl.bilico.data.model.ArchiveMajor
import erl.bilico.data.model.DynamicArticleCard
import erl.bilico.data.model.DynamicBlocked
import erl.bilico.data.model.DynamicDesc
import erl.bilico.data.model.DynamicHotReply
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicImageData
import erl.bilico.data.model.DynamicItem
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicKind
import erl.bilico.data.model.DynamicLockedUi
import erl.bilico.data.model.DynamicModules
import erl.bilico.data.model.DynamicRichTextNode
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

enum class DynamicFeedType(val apiValue: String) {
    ALL("all"),
    VIDEO("video"),
}

data class DynamicFeedPage(
    val items: List<DynamicItemUi>,
    val offset: String,
    val hasMore: Boolean,
    val baseline: String,
)

/** 长文型 opus 的正文与内嵌图片：feed 卡片给不了，要 opus/detail 才有。 */
data class DynamicOpusContent(
    val text: String,
    val images: List<DynamicImage>,
)

class DynamicRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
) {
    private val detailSeedCache = ConcurrentHashMap<String, DynamicItemUi>()

    fun rememberSeed(item: DynamicItemUi) {
        if (item.dynamicId.isNotBlank()) {
            detailSeedCache[item.dynamicId] = item
        }
        item.forward?.let { fwd ->
            if (fwd.dynamicId.isNotBlank()) {
                detailSeedCache[fwd.dynamicId] = fwd
            }
        }
    }

    fun peekSeed(dynamicId: String): DynamicItemUi? = detailSeedCache[dynamicId.trim()]

    suspend fun detail(dynamicId: String): Result<DynamicItemUi> = withContext(Dispatchers.IO) {
        val id = dynamicId.trim()
        runCatching {
            require(id.isNotEmpty()) { "动态编号无效" }
            buvid.ensure()
            val response = api.dynamicDetail(
                mapOf(
                    "id" to id,
                    "features" to DETAIL_FEATURES,
                    "timezone_offset" to "-480",
                    "gaia_source" to "Athena",
                    "web_location" to "333.1330",
                ),
            )
            val parsed = response.data?.item?.toUi(0)
            if (response.code == 0 && parsed != null) {
                withOpusContent(parsed).withLockedInfo().also(::rememberSeed)
            } else {
                detailSeedCache[id]?.let { seed ->
                    withOpusContent(seed)
                } ?: error(response.message.ifBlank { "动态加载失败(${response.code})" })
            }
        }.recoverCatching { err ->
            detailSeedCache[id]?.let { seed -> withOpusContent(seed) } ?: throw err
        }
    }

    /**
     * 长文型 opus（专栏搬过来的图文）在 feed 和详情里都只有一张封面加一串「[图片]」占位，
     * 真正的正文和图片要按动态编号再取一次。不是这类卡片就原样返回，一次请求都不发。
     */
    suspend fun withOpusContent(item: DynamicItemUi): DynamicItemUi {
        if (item.dynamicId.isBlank() || !item.content.contains(OPUS_IMAGE_PLACEHOLDER)) return item
        val blocks = opusContent(item.dynamicId).getOrNull() ?: return item
        return item.copy(
            content = blocks.text.ifBlank { item.content },
            images = blocks.images.ifEmpty { item.images },
            kind = if (blocks.images.isNotEmpty()) DynamicKind.IMAGES else item.kind,
        )
    }

    /**
     * 长文型 opus 的完整正文与图片。feed 卡片里这类动态只有一张封面和一堆「[图片]」占位，
     * 真正的图文段落要再按 opus 编号取一次。
     */
    suspend fun opusContent(dynamicId: String): Result<DynamicOpusContent> =
        withContext(Dispatchers.IO) {
            val id = dynamicId.trim()
            runCatching {
                require(id.isNotEmpty()) { "动态编号无效" }
                buvid.ensure()
                val response = api.opusDetail(
                    mapOf(
                        "id" to id,
                        "timezone_offset" to "-480",
                        "features" to DETAIL_FEATURES,
                    ),
                )
                if (response.code != 0) {
                    error(response.message.ifBlank { "正文加载失败(${response.code})" })
                }
                val blocks = response.data?.item?.modules.decodeOpusParagraphs()
                require(blocks.text.isNotBlank() || blocks.images.isNotEmpty()) { "正文为空" }
                DynamicOpusContent(
                    text = blocks.text,
                    images = blocks.images
                        .filter { it.displayUrl.isNotBlank() }
                        .take(9)
                        .map {
                            DynamicImage(
                                Formatters.normalizeImageUrl(it.displayUrl),
                                it.width,
                                it.height,
                            )
                        },
                )
            }
        }

    /**
     * 详情接口走的是旧版 `/v1/detail`，里面没有 `module_blocked`；
     * 充电专属的卡片要再取一次 opus/detail 才能拿到锁定面板。
     */
    private suspend fun DynamicItemUi.withLockedInfo(): DynamicItemUi {
        if (!chargeLocked || locked != null) return this
        val info = lockedInfo(dynamicId).getOrNull() ?: return this
        return copy(locked = info)
    }

    /**
     * 充电专属的动态在 feed 里只有空壳（`is_only_fans` 为真、正文为空），
     * 锁定面板的标题、说明、按钮和跳转都只在 opus/detail 的 `module_blocked` 里。
     */
    suspend fun lockedInfo(dynamicId: String): Result<DynamicLockedUi> = withContext(Dispatchers.IO) {
        val id = dynamicId.trim()
        runCatching {
            require(id.isNotEmpty()) { "动态编号无效" }
            buvid.ensure()
            val response = api.opusDetail(
                mapOf(
                    "id" to id,
                    "timezone_offset" to "-480",
                    "features" to DETAIL_FEATURES,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "锁定信息加载失败(${response.code})" })
            }
            response.data?.item?.modules.decodeModules()?.blocked?.toLockedUi()
                ?: error("没有锁定信息")
        }
    }

    suspend fun feed(
        type: DynamicFeedType,
        offset: String,
        baseline: String,
    ): Result<DynamicFeedPage> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val params = mapOf(
                "type" to type.apiValue,
                "offset" to offset,
                "update_baseline" to baseline,
                "features" to FEED_FEATURES,
                "timezone_offset" to "-480",
                "platform" to "web",
                "web_location" to "333.1365",
            )
            val response = api.dynamicFeed(params)
            if (response.code != 0) {
                error(response.message.ifBlank { "动态加载失败(${response.code})" })
            }
            val data = response.data ?: error("动态数据为空")
            val items = data.items.mapNotNull { it.toUi(0) }
            items.forEach(::rememberSeed)
            DynamicFeedPage(
                items = items,
                offset = data.offset,
                hasMore = data.hasMore,
                baseline = data.updateBaseline,
            )
        }
    }

    private fun DynamicItem.toUi(depth: Int): DynamicItemUi? {
        val modules = modules.decodeModules()
        val author = modules?.author
        val content = modules?.content
        val stat = modules?.stat
        val major = content?.major
        // 长文型 opus（专栏搬过来的图文）正文和图片只存在于 `module_content.paragraphs`：
        // 文字段是正文，图片段是内嵌大图。feed 卡片给的 desc 里只有一串「[图片]」占位，
        // opus/detail 才带真正的段落，所以这里两种载体都收。
        val blocks = this.modules.decodeOpusParagraphs()
        // 图文动态的正文在 major.opus.summary.text，desc 通常为空；
        // itemOpusStyle 下整条卡片只有 summary 这一个载体。
        val opusSummary = major?.opus?.summary?.text.orEmpty().trim()
        val articleTitle = major?.article?.title.orEmpty().trim()
        val opusTitle = major?.opus?.title.orEmpty().trim()
        val resolvedTitle = opusTitle.ifBlank { articleTitle }
        val articleDesc = major?.article?.desc.orEmpty().trim()
        val rawNodes = content?.desc?.richTextNodes.orEmpty() +
            major?.opus?.summary?.richTextNodes.orEmpty()
        val nodesFallbackText = if (rawNodes.isNotEmpty()) {
            rawNodes.joinToString("") { it.text.ifBlank { it.origText } }.trim()
        } else {
            ""
        }
        val text = content?.desc?.text?.trim().orEmpty()
            .ifBlank { opusSummary }
            .ifBlank { articleDesc }
            .ifBlank { nodesFallbackText }
            .ifBlank { blocks.text }
            .ifBlank { resolvedTitle }

        val emotes = rawNodes.toEmoteMap()

        val nested = orig?.takeIf { depth < 2 }?.toUi(depth + 1)
        // opus 的图字段叫 url，draw 的叫 src，专栏封面在 article.covers，长文在段落里，几处都收。
        val rawImages = (major?.draw?.items ?: major?.opus?.pics).orEmpty()
            .ifEmpty { major?.article?.covers.orEmpty().map { DynamicImageData(src = it) } }
            .ifEmpty { blocks.images }
        val images = rawImages
            .asSequence()
            .filter { it.displayUrl.isNotBlank() }
            .take(9)
            .map { DynamicImage(Formatters.normalizeImageUrl(it.displayUrl), it.width, it.height) }
            .toList()
        val video = major?.archive?.toCard()

        val articleId = major?.article?.id ?: 0L
        val selfId = idStr.ifBlank { basic?.ridStr.orEmpty() }
        val article = if (articleId > 0L || selfId.isNotBlank()) {
            DynamicArticleCard(
                articleId = articleId,
                title = resolvedTitle,
                dynamicId = selfId,
            )
        } else {
            null
        }

        val kind = when {
            nested != null -> DynamicKind.FORWARD
            video != null -> DynamicKind.VIDEO
            images.isNotEmpty() -> DynamicKind.IMAGES
            text.isNotEmpty() || resolvedTitle.isNotEmpty() || article != null -> DynamicKind.TEXT
            else -> DynamicKind.UNSUPPORTED
        }
        if (kind == DynamicKind.UNSUPPORTED && author == null) return null

        val fallbackId = "${author?.mid}-${author?.pubTs}-${text.hashCode()}-${images.firstOrNull()?.url.orEmpty()}"
        val resolvedCommentType = basic?.commentType?.takeIf { it > 0 }
            ?: if (articleId > 0L) 12 else 17
        val resolvedCommentOid = firstNumericId(basic?.commentIdStr, basic?.ridStr, selfId)
            ?: articleId.takeIf { it > 0L }
            ?: 0L

        val hotReplies = modules?.interaction?.items.orEmpty()
            .asSequence()
            .filter { it.type == 1 }
            .mapNotNull { it.desc?.toHotReply() }
            .take(2)
            .toList()

        val vip = author?.vip
        val nameColor = when {
            vip == null || vip.status <= 0 -> ""
            vip.nicknameColor.isNotBlank() -> vip.nicknameColor
            vip.type == 2 -> "#FB7299"
            else -> ""
        }
        val decorate = author?.decorateCard
        val nameplateNumber = decorate?.fan?.numberText.orEmpty()

        return DynamicItemUi(
            // 前缀区分转发层级，避免同一条动态在不同层级撞 key；dynamicId 留给详情页用。
            id = "$depth:${selfId.ifBlank { fallbackId }}",
            dynamicId = selfId,
            kind = kind,
            authorMid = author?.mid ?: 0L,
            authorName = author?.name.orEmpty(),
            authorFace = Formatters.normalizeImageUrl(author?.face),
            authorPendant = Formatters.normalizeImageUrl(author?.pendant?.image),
            authorNameColor = nameColor,
            nameplateUrl = Formatters.normalizeImageUrl(decorate?.cardUrl),
            nameplateNumber = nameplateNumber,
            nameplateColor = decorate?.fan?.color.orEmpty(),
            hotReplies = hotReplies,
            timeSec = author?.pubTs ?: 0L,
            pubTimeText = author?.pubTime.orEmpty(),
            title = resolvedTitle,
            content = text,
            emotes = emotes,
            images = images,
            video = video,
            article = article,
            forward = nested,
            forwardCount = stat?.forward?.count ?: 0,
            commentCount = stat?.comment?.count ?: 0,
            likeCount = stat?.like?.count ?: 0,
            commentOid = resolvedCommentOid,
            commentType = resolvedCommentType,
            authorBadge = author?.iconBadge?.text.orEmpty(),
            authorBadgeIcon = Formatters.normalizeImageUrl(
                author?.iconBadge?.renderImg.orEmpty().ifBlank { author?.iconBadge?.icon.orEmpty() },
            ),
            chargeLocked = basic?.isOnlyFans == true || modules?.blocked != null,
            locked = modules?.blocked?.toLockedUi(),
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

    /**
     * 锁定卡片的文案都在 `hint_message` 里，用换行把标题和说明分开：
     * 第一行是「剥壳员专属动态」这类标题，后面的才是说明；`title` 字段本身通常是空的。
     */
    private fun DynamicBlocked.toLockedUi(): DynamicLockedUi? {
        val lines = hintMessage.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val explicitTitle = title.trim()
        val resolvedTitle = explicitTitle.ifBlank { lines.firstOrNull().orEmpty() }
        val hint = (if (explicitTitle.isEmpty()) lines.drop(1) else lines).joinToString("\n")
        val action = button
        val buttonText = action?.text.orEmpty().trim()
        if (resolvedTitle.isEmpty() && hint.isEmpty() && buttonText.isEmpty()) return null
        return DynamicLockedUi(
            title = resolvedTitle,
            hint = hint,
            buttonText = buttonText,
            buttonIcon = Formatters.normalizeImageUrl(action?.icon.orEmpty()),
            jumpUrl = action?.jumpUrl.orEmpty(),
        )
    }

    private fun ArchiveMajor.toCard(): DynamicVideoCard? {
        if (bvid.isBlank()) return null
        return DynamicVideoCard(
            bvid = bvid,
            title = title,
            cover = Formatters.normalizeImageUrl(cover),
            durationText = durationText,
            aid = aid,
        )
    }

    /** 富文本节点里的表情：评论正文和精选评论共用一套收集逻辑。 */
    private fun List<DynamicRichTextNode>.toEmoteMap(): Map<String, EmoteImage> {
        val result = LinkedHashMap<String, EmoteImage>()
        forEach { node ->
            val emoji = node.emoji ?: node.rich?.emoji ?: return@forEach
            val url = Formatters.normalizeImageUrl(emoji.displayUrl)
            if (url.isBlank()) return@forEach
            val emoteImg = EmoteImage(url = url, large = emoji.size >= 2)
            listOf(node.text, node.origText, emoji.text, node.rich?.text.orEmpty(), node.rich?.origText.orEmpty())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { key -> result[key] = emoteImg }
        }
        return result
    }

    /**
     * 精选评论的 `desc.text` 有时只留了表情，昵称和正文都在 `rich_text_nodes` 里，
     * 所以按节点拼一遍；节点为空才退回 `text`。
     */
    private fun DynamicDesc.toHotReply(): DynamicHotReply? {
        val nodes = richTextNodes
        val body = if (nodes.isNotEmpty()) {
            nodes.joinToString("") { it.text.ifBlank { it.origText } }.trim()
        } else {
            text.trim()
        }
        if (body.isBlank()) return null
        return DynamicHotReply(text = body, emotes = nodes.toEmoteMap())
    }

    /**
     * feed 的 modules 是对象，opus 详情可能给数组。逐条解析并合并，
     * 单条结构异常只丢这条卡片，不影响整页。
     */
    private fun JsonElement?.decodeModules(): DynamicModules? {
        val element = this ?: return null
        return runCatching {
            when (element) {
                is JsonObject -> BiliClient.json.decodeFromJsonElement(DynamicModules.serializer(), element)
                is JsonArray -> element
                    .mapNotNull { node ->
                        (node as? JsonObject)?.let {
                            runCatching {
                                BiliClient.json.decodeFromJsonElement(DynamicModules.serializer(), it)
                            }.getOrNull()
                        }
                    }
                    .fold(DynamicModules()) { acc, next ->
                        DynamicModules(
                            author = acc.author ?: next.author,
                            content = acc.content ?: next.content,
                            stat = acc.stat ?: next.stat,
                            interaction = acc.interaction ?: next.interaction,
                            blocked = acc.blocked ?: next.blocked,
                        )
                    }

                else -> null
            }
        }.getOrNull()
    }

    /**
     * 长文型 opus 的正文与图片全在 `module_content.paragraphs` 里：
     * 图片段（`para_type == 2`）是内嵌大图，文字段（`para_type == 1`）的 `text.nodes` 里
     * 每个节点要么是 `word.words`，要么是 `rich.text`。feed 卡片里同一份正文只给「[图片]」占位，
     * 所以这条路径主要给 opus/detail 用。
     */
    private fun JsonElement?.decodeOpusParagraphs(): OpusBlocks {
        val modules = when (this) {
            is JsonArray -> mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(this)
            else -> return OpusBlocks()
        }
        val content = modules.firstNotNullOfOrNull { it["module_content"] as? JsonObject }
            ?: return OpusBlocks()
        val paragraphs = (content["paragraphs"] as? JsonArray).orEmpty()
        val text = StringBuilder()
        val images = mutableListOf<DynamicImageData>()
        paragraphs.forEach paragraph@{ element ->
            val paragraph = element as? JsonObject ?: return@paragraph
            val pic = paragraph["pic"] as? JsonObject
            if (pic != null) {
                (pic["pics"] as? JsonArray).orEmpty().forEach picItem@{ item ->
                    val obj = item as? JsonObject ?: return@picItem
                    val url = obj.text("url")
                    if (url.isBlank()) return@picItem
                    images += DynamicImageData(
                        src = url,
                        url = url,
                        width = obj.int("width"),
                        height = obj.int("height"),
                    )
                }
                return@paragraph
            }
            val paragraphText = (paragraph["text"] as? JsonObject)
                ?.let { it["nodes"] as? JsonArray }
                .orEmpty()
                .mapNotNull { node ->
                    val obj = node as? JsonObject ?: return@mapNotNull null
                    (obj["word"] as? JsonObject)?.text("words")
                        ?: (obj["rich"] as? JsonObject)?.text("text")
                        ?: (obj["formula"] as? JsonObject)?.text("latex")
                }
                .joinToString("")
                .trim()
            if (paragraphText.isNotEmpty()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(paragraphText)
            }
        }
        return OpusBlocks(text.toString(), images)
    }

    /** 段落解析结果：正文文本 + 内嵌图片。 */
    private data class OpusBlocks(
        val text: String = "",
        val images: List<DynamicImageData> = emptyList(),
    )

    private fun JsonObject.text(key: String): String {
        val value = this[key] as? JsonPrimitive ?: return ""
        if (value is JsonNull) return ""
        return value.content.takeIf { it != "null" }.orEmpty()
    }

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

    private companion object {
        const val MAJOR_TYPE_OPUS = "MAJOR_TYPE_OPUS"

        /** 长文型 opus 在 feed/详情正文里给图片占位用的写法。 */
        const val OPUS_IMAGE_PLACEHOLDER = "[图片]"

        const val FEED_FEATURES =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,decorationCard," +
                "onlyfansAssetsV2,onlyfansQaCard,forwardListHidden,ugcDelete"

        const val DETAIL_FEATURES =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,endFooterHidden," +
                "decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,commentsNewVersion," +
                "forwardListHidden,htmlNewStyle"
    }
}
