package erl.bilico.data.repository

import erl.bilico.data.model.ArchiveMajor
import erl.bilico.data.model.DynamicArticleCard
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicImageData
import erl.bilico.data.model.DynamicItem
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicKind
import erl.bilico.data.model.DynamicModules
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
import kotlinx.serialization.json.JsonObject
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
                rememberSeed(parsed)
                parsed
            } else {
                detailSeedCache[id]
                    ?: error(response.message.ifBlank { "动态加载失败(${response.code})" })
            }
        }.recoverCatching { err ->
            detailSeedCache[id] ?: throw err
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
            .ifBlank { resolvedTitle }

        val emotes = buildMap {
            rawNodes.forEach { node ->
                val emoji = node.emoji ?: node.rich?.emoji ?: return@forEach
                val url = Formatters.normalizeImageUrl(emoji.displayUrl)
                if (url.isBlank()) return@forEach
                val emoteImg = EmoteImage(url = url, large = emoji.size >= 2)
                listOf(node.text, node.origText, emoji.text, node.rich?.text.orEmpty(), node.rich?.origText.orEmpty())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { key -> put(key, emoteImg) }
            }
        }

        val nested = orig?.takeIf { depth < 2 }?.toUi(depth + 1)
        // opus 的图字段叫 url，draw 的叫 src，专栏封面在 article.covers，三处都收。
        val rawImages = (major?.draw?.items ?: major?.opus?.pics).orEmpty().ifEmpty {
            major?.article?.covers.orEmpty().map { DynamicImageData(src = it) }
        }
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

        return DynamicItemUi(
            // 前缀区分转发层级，避免同一条动态在不同层级撞 key；dynamicId 留给详情页用。
            id = "$depth:${selfId.ifBlank { fallbackId }}",
            dynamicId = selfId,
            kind = kind,
            authorMid = author?.mid ?: 0L,
            authorName = author?.name.orEmpty(),
            authorFace = Formatters.normalizeImageUrl(author?.face),
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

    private fun ArchiveMajor.toCard(): DynamicVideoCard? {
        if (bvid.isBlank()) return null
        return DynamicVideoCard(
            bvid = bvid,
            title = title,
            cover = Formatters.normalizeImageUrl(cover),
            durationText = durationText,
        )
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
                        )
                    }

                else -> null
            }
        }.getOrNull()
    }

    private companion object {
        const val MAJOR_TYPE_OPUS = "MAJOR_TYPE_OPUS"

        const val FEED_FEATURES =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,decorationCard," +
                "onlyfansAssetsV2,onlyfansQaCard,forwardListHidden,ugcDelete"

        const val DETAIL_FEATURES =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,endFooterHidden," +
                "decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,commentsNewVersion," +
                "forwardListHidden,htmlNewStyle"
    }
}
