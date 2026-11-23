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
import erl.bilico.data.model.DynamicLiveCard
import erl.bilico.data.model.DynamicLockedUi
import erl.bilico.data.model.DynamicMajor
import erl.bilico.data.model.DynamicModules
import erl.bilico.data.model.DynamicRichTextNode
import erl.bilico.data.model.DynamicTextNode
import erl.bilico.data.model.DynamicVideoCard
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.model.LiveMajor
import erl.bilico.data.model.LiveRcmdContent
import erl.bilico.data.model.UploadedPicture
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    val richNodes: List<DynamicTextNode> = emptyList(),
    val images: List<DynamicImage>,
)

class DynamicRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
    private val session: SessionRepository? = null,
) {
    val votes = DynamicVoteRepository(api, buvid, session)
    private val detailSeedCache = ConcurrentHashMap<String, DynamicItemUi>()
    private val _deletedIds = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val deletedIds: SharedFlow<String> = _deletedIds.asSharedFlow()

    suspend fun publishDynamic(
        text: String,
        pictures: List<UploadedPicture> = emptyList(),
        atMids: Map<String, Long> = emptyMap(),
    ): Result<String> = publishDynamicRequest(api, buvid, session, text, pictures, atMids)

    suspend fun deleteDynamic(dynamicId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val id = dynamicId.trim()
            require(id.isNotEmpty()) { "动态编号无效" }
            buvid.ensure()
            val csrf = session?.csrf().orEmpty()
            require(csrf.isNotBlank()) { "登录后可用" }
            val body = buildJsonObject { put("dyn_id_str", id) }
            val response = api.removeDynamic(csrf = csrf, body = body)
            if (response.code != 0) {
                val msg = when (response.code) {
                    -101 -> "请先登录"
                    4101144 -> "只能删除自己的动态"
                    else -> response.message.ifBlank { "删除动态失败(${response.code})" }
                }
                error(msg)
            }
            detailSeedCache.remove(id)
            _deletedIds.tryEmit(id)
            Unit
        }
    }

    /**
     * 给动态点赞或取消。`up` 为 1 是点赞，2 是取消。
     * 成功后把缓存里的点赞态一起改掉，详情页再打开时不会退回旧状态。
     */
    suspend fun thumbDynamic(dynamicId: String, liked: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val id = dynamicId.trim()
            require(id.isNotEmpty()) { "动态编号无效" }
            buvid.ensure()
            val csrf = session?.csrf().orEmpty()
            require(csrf.isNotBlank()) { "登录后可用" }
            val body = buildJsonObject {
                put("dyn_id_str", id)
                put("up", if (liked) 1 else 2)
            }
            val response = api.thumbDynamic(csrf = csrf, body = body)
            if (response.code != 0) {
                val msg = when (response.code) {
                    -101 -> "请先登录"
                    else -> response.message.ifBlank { "点赞失败(${response.code})" }
                }
                error(msg)
            }
            detailSeedCache[id]?.let { cached ->
                detailSeedCache[id] = cached.copy(
                    liked = liked,
                    likeCount = (cached.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
                )
            }
            Unit
        }
    }

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
            richNodes = blocks.richNodes.ifEmpty { item.richNodes },
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
                val blocks = response.data?.item?.modules.decodeOpusParagraphs(dynamicId = id)
                require(blocks.text.isNotBlank() || blocks.images.isNotEmpty()) { "正文为空" }
                DynamicOpusContent(
                    text = blocks.text,
                    richNodes = blocks.richNodes,
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

    /** 某个 UP 主（空间）的动态列表：使用 spaceDynamicFeed 接口。 */
    suspend fun spaceFeed(
        mid: Long,
        offset: String = "",
    ): Result<DynamicFeedPage> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val params = mutableMapOf(
                "host_mid" to mid.toString(),
                "features" to FEED_FEATURES,
                "timezone_offset" to "-480",
                "platform" to "web",
                "web_location" to "333.999",
            )
            if (offset.isNotBlank()) {
                params["offset"] = offset
            }
            val response = api.spaceDynamicFeed(params)
            if (response.code != 0) {
                error(response.message.ifBlank { "空间动态加载失败(${response.code})" })
            }
            val data = response.data ?: error("空间动态数据为空")
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
        val selfId = idStr.ifBlank { basic?.ridStr.orEmpty() }
        val blocks = this.modules.decodeOpusParagraphs(dynamicId = selfId)
        // 图文动态的正文在 major.opus.summary.text，desc 通常为空；
        // itemOpusStyle 下整条卡片只有 summary 这一个载体。
        val opusSummary = major?.opus?.summary?.text.orEmpty().trim()
        val articleTitle = major?.article?.title.orEmpty().trim()
        val opusTitle = major?.opus?.title.orEmpty().trim()
        val resolvedTitle = opusTitle.ifBlank { articleTitle }
        val articleDesc = major?.article?.desc.orEmpty().trim()
        val descNodes = content?.desc?.richTextNodes.orEmpty()
        val opusNodes = major?.opus?.summary?.richTextNodes.orEmpty()
        val rawNodes = descNodes + opusNodes
        val chosenRichNodes = descNodes.ifEmpty { opusNodes }.toDynamicTextNodes(dynamicId = selfId)
            .ifEmpty { blocks.richNodes }
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
        val live = major?.toLiveCard(authorMid = author?.mid ?: 0L)
        val vote = extractDynamicVote(content, rawNodes)

        val articleId = major?.article?.id ?: 0L
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
            live != null -> DynamicKind.LIVE
            video != null -> DynamicKind.VIDEO
            images.isNotEmpty() -> DynamicKind.IMAGES
            text.isNotEmpty() || resolvedTitle.isNotEmpty() || article != null || vote != null -> DynamicKind.TEXT
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
            authorMid = author?.mid ?: 0L, authorName = author?.name.orEmpty(),
            authorFace = Formatters.normalizeImageUrl(author?.face),
            authorOfficialType = author?.officialVerify?.resolvedType() ?: -1, authorOfficialDesc = author?.officialVerify?.resolvedDesc().orEmpty(),
            authorPendant = Formatters.normalizeImageUrl(author?.pendant?.image),
            authorNameColor = nameColor,
            nameplateUrl = Formatters.normalizeImageUrl(decorate?.cardUrl),
            nameplateNumber = nameplateNumber,
            nameplateColor = decorate?.fan?.color.orEmpty(),
            hotReplies = hotReplies,
            timeSec = author?.pubTs ?: 0L,
            pubTimeText = author?.pubTime.orEmpty(),
            pubAction = author?.pubAction.orEmpty(),
            title = resolvedTitle,
            content = text,
            richNodes = chosenRichNodes,
            emotes = emotes,
            images = images,
            video = video,
            live = live,
            article = article,
            vote = vote,
            forward = nested,
            forwardCount = stat?.forward?.count ?: 0,
            commentCount = stat?.comment?.count ?: 0,
            likeCount = stat?.like?.count ?: 0,
            liked = stat?.like?.status == true,
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

    /**
     * 开播动态：`major.live` 和 `major.live_rcmd.content` 里是同一份直播间信息，
     * 后者是 JSON 文本，要先解一层。
     */
    private fun DynamicMajor.toLiveCard(authorMid: Long): DynamicLiveCard? {
        val info = live ?: liveRcmd?.content
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { LIVE_CONTENT_JSON.decodeFromString<LiveRcmdContent>(raw).playInfo }.getOrNull()
            }
            ?: return null
        if (info.roomId <= 0L) return null
        return DynamicLiveCard(
            roomId = info.roomId,
            uid = info.uid.takeIf { it > 0L } ?: authorMid,
            title = info.title,
            cover = Formatters.normalizeImageUrl(info.cover),
            areaName = info.areaName.ifBlank { info.parentAreaName },
            living = info.liveStatus == 1,
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

    private companion object {
        const val MAJOR_TYPE_OPUS = "MAJOR_TYPE_OPUS"

        /** `live_rcmd.content` 是一段 JSON 文本，单独解一次。 */
        val LIVE_CONTENT_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /** 长文型 opus 在 feed/详情正文里给图片占位用的写法。 */
        const val OPUS_IMAGE_PLACEHOLDER = "[图片]"

        const val FEED_FEATURES = "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,decorationCard,onlyfansAssetsV2,onlyfansQaCard,forwardListHidden,ugcDelete"
        const val DETAIL_FEATURES = "itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,endFooterHidden,decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,commentsNewVersion,forwardListHidden,htmlNewStyle"
    }
}
