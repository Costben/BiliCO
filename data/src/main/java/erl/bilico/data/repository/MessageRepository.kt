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
        val bucket = response.data?.latest ?: response.data?.total
        val items = bucket?.items.orEmpty().mapNotNull { item ->
            val users = item.users.orEmpty()
            val first = users.firstOrNull() ?: return@mapNotNull null
            val name = when {
                users.size > 1 -> "${first.nickname} 等 ${users.size} 人"
                else -> first.nickname
            }
            MessageEntry(
                key = "like-${item.id}",
                avatar = Formatters.normalizeImageUrl(first.avatar),
                name = name,
                title = item.item?.title.orEmpty(),
                content = item.item?.sourceContent.orEmpty()
                    .ifBlank { item.item?.rootReplyContent.orEmpty() },
                extra = item.counts.takeIf { it > 1 }?.let { "${it} 人点赞" }.orEmpty(),
                timeText = Formatters.formatTimeAgo(item.likeTime),
            )
        }
        return MessagePage(items, bucket?.cursor, bucket?.cursor?.isEnd == true)
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
