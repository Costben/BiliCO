package erl.bilico.data.repository

import erl.bilico.data.model.ArchiveMajor
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.DynamicItem
import erl.bilico.data.model.DynamicItemUi
import erl.bilico.data.model.DynamicKind
import erl.bilico.data.model.DynamicModules
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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
            DynamicFeedPage(
                items = data.items.mapNotNull { it.toUi(0) },
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
        val text = content?.desc?.text?.trim().orEmpty()

        val nested = orig?.takeIf { depth < 2 }?.toUi(depth + 1)
        val images = (major?.draw?.items ?: major?.opus?.pics).orEmpty()
            .asSequence()
            .filter { it.src.isNotBlank() }
            .take(9)
            .map { DynamicImage(Formatters.normalizeImageUrl(it.src), it.width, it.height) }
            .toList()
        val video = major?.archive?.toCard()

        val kind = when {
            nested != null -> DynamicKind.FORWARD
            video != null -> DynamicKind.VIDEO
            images.isNotEmpty() -> DynamicKind.IMAGES
            text.isNotEmpty() -> DynamicKind.TEXT
            else -> DynamicKind.UNSUPPORTED
        }
        if (kind == DynamicKind.UNSUPPORTED && author == null) return null

        val fallbackId = "${author?.mid}-${author?.pubTs}-${text.hashCode()}-${images.firstOrNull()?.url.orEmpty()}"
        return DynamicItemUi(
            id = "$depth:${idStr.ifBlank { fallbackId }}",
            kind = kind,
            authorName = author?.name.orEmpty(),
            authorFace = Formatters.normalizeImageUrl(author?.face),
            timeSec = author?.pubTs ?: 0L,
            pubTimeText = author?.pubTime.orEmpty(),
            content = text,
            images = images,
            video = video,
            forward = nested,
            forwardCount = stat?.forward?.count ?: 0,
            commentCount = stat?.comment?.count ?: 0,
            likeCount = stat?.like?.count ?: 0,
        )
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
        const val FEED_FEATURES =
            "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,decorationCard," +
                "onlyfansAssetsV2,onlyfansQaCard,forwardListHidden,ugcDelete"
    }
}
