package erl.bilico.data.repository

import erl.bilico.data.model.MessageCursor
import erl.bilico.data.model.MessageEntry
import erl.bilico.data.model.MessageJumpTarget
import erl.bilico.data.model.MessageKind
import erl.bilico.data.model.MessagePage
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 消息中心：回复我的、@我的、收到的赞、系统通知。
 * 三类互动消息用 `id` + 时间戳双游标，系统通知用单一 cursor。
 */
class MessageRepository(
    private val api: BiliApi,
    private val buvid: BuvidManager,
) {
    suspend fun page(kind: MessageKind, cursor: MessageCursor?): Result<MessagePage> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                when (kind) {
                    MessageKind.REPLY -> reply(cursor)
                    MessageKind.AT -> at(cursor)
                    MessageKind.LIKE -> like(cursor)
                    MessageKind.SYSTEM -> system(cursor)
                    MessageKind.SESSION -> MessagePage(emptyList(), null, true)
                }
            }
        }

    private suspend fun reply(cursor: MessageCursor?): MessagePage {
        val response = api.messageReply(
            baseParams(
                "id" to cursor?.id?.takeIf { it > 0L }?.toString(),
                "reply_time" to cursor?.time?.takeIf { it > 0L }?.toString(),
            )
        )
        if (response.code != 0) error(response.message.ifBlank { "消息加载失败(${response.code})" })
        val data = response.data
        val items = data?.items.orEmpty().mapNotNull { item ->
            val user = item.user ?: return@mapNotNull null
            val contentItem = item.item
            MessageEntry(
                key = "reply-${item.id}",
                avatar = Formatters.normalizeImageUrl(user.avatar),
                name = user.nickname,
                title = contentItem?.title.orEmpty(),
                content = contentItem?.targetReplyContent.orEmpty()
                    .ifBlank { contentItem?.sourceContent.orEmpty() },
                extra = item.counts.takeIf { it > 1 }?.let { "等 $it 条回复" }.orEmpty(),
                timeText = Formatters.formatTimeAgo(item.replyTime),
                senderMid = user.mid,
                target = contentItem?.let {
                    resolveMessageTarget(
                        uri = it.uri,
                        nativeUri = it.nativeUri,
                        type = it.type,
                        business = it.business,
                        businessId = it.businessId,
                        subjectId = it.subjectId,
                        title = it.title,
                        rootId = it.rootId,
                        targetId = it.targetId,
                        sourceId = it.sourceId,
                        itemId = it.itemId,
                        onlyWhenCommentSignal = false,
                    )
                },
            )
        }
        return MessagePage(items, data?.cursor, data?.cursor?.isEnd == true)
    }

    private suspend fun at(cursor: MessageCursor?): MessagePage {
        val response = api.messageAt(
            baseParams(
                "id" to cursor?.id?.takeIf { it > 0L }?.toString(),
                "at_time" to cursor?.time?.takeIf { it > 0L }?.toString(),
            )
        )
        if (response.code != 0) error(response.message.ifBlank { "消息加载失败(${response.code})" })
        val data = response.data
        val items = data?.items.orEmpty().mapNotNull { item ->
            val user = item.user ?: return@mapNotNull null
            val contentItem = item.item
            MessageEntry(
                key = "at-${item.id}",
                avatar = Formatters.normalizeImageUrl(user.avatar),
                name = user.nickname,
                title = contentItem?.title.orEmpty(),
                content = contentItem?.sourceContent.orEmpty(),
                extra = "",
                timeText = Formatters.formatTimeAgo(item.atTime),
                senderMid = user.mid,
                target = contentItem?.let {
                    resolveMessageTarget(
                        uri = it.uri,
                        nativeUri = it.nativeUri,
                        type = it.type,
                        business = it.business,
                        businessId = it.businessId,
                        subjectId = it.subjectId,
                        title = it.title,
                        rootId = it.rootId,
                        targetId = it.targetId,
                        sourceId = it.sourceId,
                        itemId = it.itemId,
                        onlyWhenCommentSignal = false,
                    )
                },
            )
        }
        return MessagePage(items, data?.cursor, data?.cursor?.isEnd == true)
    }

    private suspend fun like(cursor: MessageCursor?): MessagePage {
        val response = api.messageLike(
            baseParams(
                "id" to cursor?.id?.takeIf { it > 0L }?.toString(),
                "like_time" to cursor?.time?.takeIf { it > 0L }?.toString(),
            )
        )
        if (response.code != 0) error(response.message.ifBlank { "消息加载失败(${response.code})" })
        val totalBucket = response.data?.total
        val latestBucket = response.data?.latest

        // 将最新未读和全量历史项合并并按 id 去重；避免因 latestBucket.items 为空而遗漏 totalBucket
        val rawItems = buildList {
            latestBucket?.items?.let { addAll(it) }
            totalBucket?.items?.let { addAll(it) }
        }.distinctBy { it.id }

        val effectiveItems = when {
            rawItems.isNotEmpty() -> rawItems
            totalBucket?.items != null -> totalBucket.items
            else -> latestBucket?.items.orEmpty()
        }
        val effectiveCursor = totalBucket?.cursor ?: latestBucket?.cursor

        val items = effectiveItems.mapNotNull { item ->
            val allUsers = buildList {
                item.user?.let { add(it) }
                item.users?.let { addAll(it) }
            }.distinctBy { it.mid }
            val first = allUsers.firstOrNull()
            val name = when {
                allUsers.size > 1 -> "${first?.nickname ?: "用户"} 等 ${allUsers.size} 人"
                first != null -> first.nickname
                item.counts > 0 -> "${item.counts} 人"
                else -> "有人"
            }
            val business = item.item?.business.orEmpty()
            val actionText = when (business) {
                "archive" -> "赞了你的视频"
                "article" -> "赞了你的专栏"
                "dynamic" -> "赞了你的动态"
                "reply" -> "赞了你的评论"
                else -> if (business.isNotBlank()) "赞了你的内容" else "赞了你"
            }
            val sourceTitle = item.item?.title.orEmpty().ifBlank {
                item.item?.detailTitle.orEmpty()
            }.ifBlank {
                item.item?.detailName.orEmpty()
            }
            val title = when {
                sourceTitle.isNotBlank() -> "$actionText · $sourceTitle"
                else -> actionText
            }

            val content = item.item?.sourceContent.orEmpty()
                .ifBlank { item.item?.targetReplyContent.orEmpty() }
                .ifBlank { item.item?.rootReplyContent.orEmpty() }
                .ifBlank { item.item?.desc.orEmpty() }

            val extra = item.counts.takeIf { it > 1 }?.let { "${it} 人点赞" }.orEmpty()
            val contentItem = item.item

            MessageEntry(
                key = "like-${item.id}",
                avatar = Formatters.normalizeImageUrl(first?.avatar.orEmpty()),
                name = name,
                title = title,
                content = content,
                extra = extra,
                timeText = Formatters.formatTimeAgo(item.likeTime),
                senderMid = first?.mid ?: 0L,
                target = contentItem?.let {
                    resolveMessageTarget(
                        uri = it.uri,
                        nativeUri = it.nativeUri,
                        type = it.type,
                        business = it.business,
                        businessId = it.businessId,
                        subjectId = it.subjectId,
                        title = sourceTitle.ifBlank { it.title },
                        rootId = it.rootId,
                        targetId = it.targetId,
                        sourceId = it.sourceId,
                        itemId = it.itemId,
                        onlyWhenCommentSignal = true,
                    )
                },
            )
        }
        return MessagePage(items, effectiveCursor, effectiveCursor?.isEnd == true || effectiveItems.isEmpty())
    }

    private suspend fun system(cursor: MessageCursor?): MessagePage {
        val params = mutableMapOf(
            "page_size" to PAGE_SIZE.toString(),
            "mobi_app" to "web",
            "build" to "0",
        )
        cursor?.time?.takeIf { it > 0L }?.let { params["cursor"] = it.toString() }
        val response = api.systemNotices(params)
        if (response.code != 0) error(response.message.ifBlank { "消息加载失败(${response.code})" })
        val list = response.data.orEmpty()
        val items = list.map { notice ->
            MessageEntry(
                key = "system-${notice.id}-${notice.cursor}",
                avatar = "",
                name = "系统通知",
                title = notice.title,
                content = notice.content,
                extra = "",
                timeText = notice.timeAt,
            )
        }
        val next = list.lastOrNull()?.cursor?.takeIf { it > 0L }?.let { MessageCursor(time = it) }
        return MessagePage(items, next, list.size < PAGE_SIZE)
    }

    suspend fun resolveBvid(aid: Long): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.videoInfoByAid(aid)
            if (response.code != 0) error(response.message.ifBlank { "获取视频信息失败(${response.code})" })
            response.data?.bvid?.takeIf { it.isNotBlank() } ?: error("未找到对应视频")
        }
    }

    private fun resolveMessageTarget(
        uri: String,
        nativeUri: String,
        type: String,
        business: String,
        businessId: Long,
        subjectId: Long,
        title: String,
        rootId: Long,
        targetId: Long,
        sourceId: Long,
        itemId: Long,
        onlyWhenCommentSignal: Boolean,
    ): MessageJumpTarget? {
        val (commentRootId, commentTargetId) = extractCommentIds(
            uri = uri,
            nativeUri = nativeUri,
            type = type,
            business = business,
            subjectId = subjectId,
            rootId = rootId,
            targetId = targetId,
            sourceId = sourceId,
            itemId = itemId,
            onlyWhenCommentSignal = onlyWhenCommentSignal,
        )
        parseJumpUrl(uri, title, commentRootId, commentTargetId)?.let { return it }
        parseJumpUrl(nativeUri, title, commentRootId, commentTargetId)?.let { return it }
        if (subjectId <= 0L) return null
        val biz = business.lowercase()
        return when {
            businessId == 1L || biz == "archive" || biz == "video" || biz == "视频" ->
                MessageJumpTarget.VideoAid(subjectId, commentRootId, commentTargetId)
            businessId == 12L || biz == "article" || biz == "专栏" ->
                MessageJumpTarget.Article(subjectId, title, commentRootId, commentTargetId)
            businessId == 17L || biz == "dynamic" || biz == "opus" || biz == "动态" ->
                MessageJumpTarget.Opus(subjectId.toString(), title, commentRootId, commentTargetId)
            else -> null
        }
    }

    private fun extractCommentIds(
        uri: String,
        nativeUri: String,
        type: String,
        business: String,
        subjectId: Long,
        rootId: Long,
        targetId: Long,
        sourceId: Long,
        itemId: Long,
        onlyWhenCommentSignal: Boolean,
    ): Pair<Long, Long> {
        val urls = listOf(uri, nativeUri)
        val detailRoot = urls.firstNotNullOfOrNull {
            COMMENT_DETAIL_REGEX.find(it)?.groupValues?.getOrNull(3)?.toLongOrNull()?.takeIf { id -> id > 0L }
        } ?: 0L
        val uriRoot = urls.firstNotNullOfOrNull {
            COMMENT_ROOT_REGEX.find(it)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { id -> id > 0L }
        } ?: detailRoot
        val uriTarget = urls.firstNotNullOfOrNull {
            COMMENT_TARGET_REGEX.find(it)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { id -> id > 0L }
                ?: HASH_REPLY_REGEX.find(it)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { id -> id > 0L }
        } ?: 0L
        val hasUriCommentHint = uriRoot > 0L || uriTarget > 0L || detailRoot > 0L
        val isReplyType = business.equals("reply", ignoreCase = true) || type.equals("reply", ignoreCase = true)
        if (onlyWhenCommentSignal && !isReplyType && rootId <= 0L && targetId <= 0L && !hasUriCommentHint) {
            return 0L to 0L
        }
        val safeSource = sourceId.takeIf { it > 0L && (isReplyType || it != subjectId) } ?: 0L
        val safeItem = itemId.takeIf { it > 0L && (isReplyType || it != subjectId) } ?: 0L
        return when {
            rootId > 0L -> {
                val resolvedTarget = safeSource.takeIf { it > 0L }
                    ?: targetId.takeIf { it > 0L }
                    ?: safeItem.takeIf { it > 0L }
                    ?: uriTarget.takeIf { it > 0L }
                    ?: rootId
                rootId to resolvedTarget
            }
            targetId > 0L && safeSource > 0L -> targetId to safeSource
            safeSource > 0L -> safeSource to (uriTarget.takeIf { it > 0L } ?: safeSource)
            targetId > 0L -> targetId to (
                safeItem.takeIf { it > 0L && it != targetId }
                    ?: uriTarget.takeIf { it > 0L }
                    ?: targetId
                )
            safeItem > 0L -> safeItem to (uriTarget.takeIf { it > 0L } ?: safeItem)
            uriRoot > 0L -> uriRoot to (uriTarget.takeIf { it > 0L } ?: uriRoot)
            uriTarget > 0L -> uriTarget to uriTarget
            else -> 0L to 0L
        }
    }

    private fun parseJumpUrl(
        rawUrl: String,
        title: String,
        commentRootId: Long = 0L,
        commentTargetId: Long = 0L,
    ): MessageJumpTarget? {
        if (rawUrl.isBlank()) return null
        BVID_REGEX.find(rawUrl)?.let {
            return MessageJumpTarget.VideoBvid(it.groupValues[1], commentRootId, commentTargetId)
        }
        OPUS_REGEX.find(rawUrl)?.let {
            return MessageJumpTarget.Opus(it.groupValues[1], title, commentRootId, commentTargetId)
        }
        AVID_REGEX.find(rawUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }?.let {
            return MessageJumpTarget.VideoAid(it, commentRootId, commentTargetId)
        }
        ARTICLE_REGEX.find(rawUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }?.let {
            return MessageJumpTarget.Article(it, title, commentRootId, commentTargetId)
        }
        PGC_EP_REGEX.find(rawUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }?.let {
            return MessageJumpTarget.PgcEpisode(it)
        }
        PGC_SS_REGEX.find(rawUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }?.let {
            return MessageJumpTarget.PgcSeason(it)
        }
        LIVE_REGEX.find(rawUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }?.let {
            return MessageJumpTarget.LiveRoom(it)
        }
        COMMENT_DETAIL_REGEX.find(rawUrl)?.let { match ->
            val pageType = match.groupValues[1].toLongOrNull()
            val oid = match.groupValues[2].toLongOrNull()?.takeIf { it > 0L }
            val detailRoot = match.groupValues[3].toLongOrNull()?.takeIf { it > 0L } ?: commentRootId
            val detailTarget = commentTargetId.takeIf { it > 0L } ?: detailRoot
            if (oid != null) {
                when (pageType) {
                    1L -> return MessageJumpTarget.VideoAid(oid, detailRoot, detailTarget)
                    12L -> return MessageJumpTarget.Article(oid, title, detailRoot, detailTarget)
                    17L -> return MessageJumpTarget.Opus(oid.toString(), title, detailRoot, detailTarget)
                }
            }
        }
        val oid = OID_PARAM_REGEX.find(rawUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
        val pageType = PAGE_TYPE_REGEX.find(rawUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (oid != null) {
            when (pageType) {
                1L -> return MessageJumpTarget.VideoAid(oid, commentRootId, commentTargetId)
                12L -> return MessageJumpTarget.Article(oid, title, commentRootId, commentTargetId)
                17L -> return MessageJumpTarget.Opus(oid.toString(), title, commentRootId, commentTargetId)
            }
        }
        return null
    }

    private fun baseParams(vararg pairs: Pair<String, String?>): Map<String, String> = buildMap {
        put("platform", "web")
        put("mobi_app", "web")
        put("build", "0")
        put("web_location", "333.40164")
        pairs.forEach { (key, value) ->
            if (!value.isNullOrBlank()) put(key, value)
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        val BVID_REGEX = Regex("""(?<![A-Za-z0-9])(BV[0-9A-Za-z]{10})(?![A-Za-z0-9])""")
        val OPUS_REGEX = Regex("""(?:t\.bilibili\.com/|/opus/|/dynamic/|bilibili://(?:following|opus|dynamic)/(?:detail/)?)(\d+)""")
        val AVID_REGEX = Regex("""(?:/video/av|bilibili://video/)(\d+)""", RegexOption.IGNORE_CASE)
        val ARTICLE_REGEX = Regex("""(?:/read/cv|/read/mobile\?(?:[^#]*&)?id=|bilibili://article/)(\d+)""", RegexOption.IGNORE_CASE)
        val PGC_EP_REGEX = Regex("""(?:/bangumi/play/ep|bilibili://bangumi/episode/)(\d+)""", RegexOption.IGNORE_CASE)
        val PGC_SS_REGEX = Regex("""(?:/bangumi/play/ss|bilibili://bangumi/season/)(\d+)""", RegexOption.IGNORE_CASE)
        val LIVE_REGEX = Regex("""(?:live\.bilibili\.com/|bilibili://live/)(\d+)""", RegexOption.IGNORE_CASE)
        val OID_PARAM_REGEX = Regex("""[?&]oid=(\d+)""")
        val PAGE_TYPE_REGEX = Regex("""[?&](?:pageType|type|business_id)=(\d+)""")
        val HASH_REPLY_REGEX = Regex("""#reply(\d+)""", RegexOption.IGNORE_CASE)
        val COMMENT_ROOT_REGEX = Regex("""[?&](?:comment_root_id|root_id|root)=(\d+)""", RegexOption.IGNORE_CASE)
        val COMMENT_TARGET_REGEX = Regex("""[?&](?:comment_secondary_id|comment_target_id|reply_id|anchor|target_id)=(\d+)""", RegexOption.IGNORE_CASE)
        val COMMENT_DETAIL_REGEX = Regex("""bilibili://comment/detail/(\d+)/(\d+)/(\d+)""", RegexOption.IGNORE_CASE)
    }
}
