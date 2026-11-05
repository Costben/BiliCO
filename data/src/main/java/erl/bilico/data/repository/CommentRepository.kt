package erl.bilico.data.repository

import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentPage
import erl.bilico.data.model.ReplyData
import erl.bilico.data.model.ReplyItem
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

/**
 * 评论只读链路：HTTP 版主楼 + 楼中楼。
 * 主楼用 WBI 的 `reply/wbi/main`（热度序），失败时退回旧版 `x/v2/reply`。
 */
class CommentRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
) {
    suspend fun mainComments(oid: Long, cursor: String?): Result<CommentPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val keys = wbi.getKeys()
                val params = TreeMap<String, String>().apply {
                    put("oid", oid.toString())
                    put("type", "1")
                    put("mode", "3")
                    put("ps", "20")
                    put("plat", "1")
                    put("web_location", "1315875")
                    put(
                        "pagination_str",
                        if (cursor.isNullOrBlank()) {
                            """{"offset":""}"""
                        } else {
                            BiliClient.json.encodeToString(mapOf("offset" to cursor))
                        },
                    )
                }
                val signed = WbiUtils.sign(params, keys.first, keys.second)
                val response = runCatching { api.mainComments(signed) }.getOrNull()

                if (response != null && response.code == 0 && response.data?.hasContent() == true) {
                    response.data.toPage()
                } else {
                    legacyFirstPage(oid)
                }
            }
        }

    suspend fun replies(oid: Long, rootId: Long, page: Int, pageSize: Int = 20): Result<List<Comment>> =
        withContext(Dispatchers.IO) {
            runCatching {
                buvid.ensure()
                val response = api.subComments(
                    mapOf(
                        "oid" to oid.toString(),
                        "type" to "1",
                        "root" to rootId.toString(),
                        "pn" to page.coerceAtLeast(1).toString(),
                        "ps" to pageSize.toString(),
                    ),
                )
                if (response.code != 0) {
                    error(response.message.ifBlank { "回复加载失败(${response.code})" })
                }
                response.data?.replies.orEmpty().map { it.toComment() }
            }
        }

    private suspend fun legacyFirstPage(oid: Long): CommentPage {
        val response = api.legacyComments(
            mapOf(
                "oid" to oid.toString(),
                "type" to "1",
                "pn" to "1",
                "ps" to "20",
                "sort" to "1",
            ),
        )
        if (response.code != 0) {
            error(response.message.ifBlank { "评论加载失败(${response.code})" })
        }
        val data = response.data ?: return CommentPage(emptyList(), null, true, 0)
        val items = buildList {
            addAll(data.hots.orEmpty())
            addAll(data.replies.orEmpty())
        }.distinctBy { it.rpid }
        return CommentPage(
            comments = items.map { it.toComment() },
            nextOffset = null,
            isEnd = true,
            total = maxOf(data.page.acount, data.page.count, items.size),
        )
    }

    private fun ReplyData?.hasContent(): Boolean {
        if (this == null) return false
        return replies.orEmpty().isNotEmpty() ||
            hots.orEmpty().isNotEmpty() ||
            topReplies.orEmpty().isNotEmpty() ||
            top != null ||
            upper?.top != null
    }

    private fun ReplyData.toPage(): CommentPage {
        val items = buildList {
            top?.upper?.let(::add)
            top?.admin?.let(::add)
            upper?.top?.let(::add)
            addAll(topReplies.orEmpty())
            addAll(hots.orEmpty())
            addAll(replies.orEmpty())
        }.distinctBy { it.rpid }

        return CommentPage(
            comments = items.map { it.toComment() },
            nextOffset = cursor.paginationReply?.nextOffset?.takeIf { it.isNotBlank() },
            isEnd = cursor.isEnd,
            total = maxOf(cursor.allCount, items.size),
        )
    }

    private fun ReplyItem.toComment(): Comment = Comment(
        id = rpid,
        rootId = if (root > 0) root else rpid,
        userName = member.uname.ifBlank { "未知用户" },
        avatar = member.avatar,
        level = member.levelInfo.currentLevel,
        timeSec = ctime,
        content = content.message,
        like = like,
        replyCount = maxOf(count, rcount),
        mid = mid,
        replies = replies.orEmpty().map { it.toComment() },
    )
}
