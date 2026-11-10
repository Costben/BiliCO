package erl.bilico.data.repository

import erl.bilico.data.model.MessageCursor
import erl.bilico.data.model.MessageEntry
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
            MessageEntry(
                key = "reply-${item.id}",
                avatar = Formatters.normalizeImageUrl(user.avatar),
                name = user.nickname,
                title = item.item?.title.orEmpty(),
                content = item.item?.targetReplyContent.orEmpty()
                    .ifBlank { item.item?.sourceContent.orEmpty() },
                extra = item.counts.takeIf { it > 1 }?.let { "等 $it 条回复" }.orEmpty(),
                timeText = Formatters.formatTimeAgo(item.replyTime),
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
            MessageEntry(
                key = "at-${item.id}",
                avatar = Formatters.normalizeImageUrl(user.avatar),
                name = user.nickname,
                title = item.item?.title.orEmpty(),
                content = item.item?.sourceContent.orEmpty(),
                extra = "",
                timeText = Formatters.formatTimeAgo(item.atTime),
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

            MessageEntry(
                key = "like-${item.id}",
                avatar = Formatters.normalizeImageUrl(first?.avatar.orEmpty()),
                name = name,
                title = title,
                content = content,
                extra = extra,
                timeText = Formatters.formatTimeAgo(item.likeTime),
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
    }
}
