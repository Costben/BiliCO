package erl.bilico.data.repository

import erl.bilico.data.model.Comment
import erl.bilico.data.model.CommentPage
import erl.bilico.data.model.CommentSailing
import erl.bilico.data.model.DynamicImage
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.model.ReplyData
import erl.bilico.data.model.ReplyItem
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

/**
 * 评论读写链路：HTTP 版主楼 + 楼中楼 + 发表评论/回复 + 点赞评论。
 * 主楼用 WBI 的 `reply/wbi/main`（热度序），失败时退回旧版 `x/v2/reply`。
 */
class CommentRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
    private val session: SessionRepository,
) {
    suspend fun mainComments(
        oid: Long,
        cursor: String?,
        type: Int = 1,
    ): Result<CommentPage> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val keys = wbi.getKeys()
            val params = TreeMap<String, String>().apply {
                put("oid", oid.toString())
                put("type", type.toString())
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
                legacyFirstPage(oid, type)
            }
        }
    }

    suspend fun replies(
        oid: Long,
        rootId: Long,
        page: Int,
        pageSize: Int = 20,
        type: Int = 1,
    ): Result<List<Comment>> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val response = api.subComments(
                mapOf(
                    "oid" to oid.toString(),
                    "type" to type.toString(),
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

    suspend fun addComment(
        oid: Long,
        message: String,
        root: Long = 0L,
        parent: Long = 0L,
        type: Int = 1,
    ): Result<Comment> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            val trimmed = message.trim()
            require(trimmed.isNotEmpty()) { "评论内容不能为空" }

            val fields = buildMap {
                put("oid", oid.toString())
                put("type", type.toString())
                put("message", trimmed)
                put("plat", "1")
                if (root > 0L) put("root", root.toString())
                if (parent > 0L) put("parent", parent.toString())
                put("csrf", csrf)
            }
            val response = api.addComment(fields)
            if (response.code != 0) {
                val msg = when (response.code) {
                    -101 -> "请先登录"
                    -102 -> "账号被封禁"
                    -509 -> "请求过于频繁"
                    12002 -> "评论区已关闭"
                    12015 -> "需要评论验证码"
                    12016 -> "评论内容包含敏感信息"
                    12025 -> "评论字数过多"
                    12035 -> "您已被 UP 主拉黑"
                    12051 -> "重复评论，请勿刷屏"
                    else -> response.message.ifBlank { "发送失败(${response.code})" }
                }
                error(msg)
            }
            val returned = response.data?.reply
            if (returned != null && returned.rpid > 0L) {
                returned.toComment()
            } else {
                val account = session.account.value
                val rpid = response.data?.rpid?.takeIf { it > 0L } ?: System.currentTimeMillis()
                Comment(
                    id = rpid,
                    rootId = if (root > 0L) root else rpid,
                    parentId = parent,
                    userName = account?.name?.ifBlank { "我" } ?: "我",
                    avatar = account?.face.orEmpty(),
                    level = 0,
                    timeSec = System.currentTimeMillis() / 1000L,
                    content = trimmed,
                    like = 0,
                    liked = false,
                    replyCount = 0,
                    mid = account?.mid ?: session.mid(),
                )
            }
        }
    }

    suspend fun likeComment(
        oid: Long,
        rpid: Long,
        liked: Boolean,
        type: Int = 1,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            val response = api.likeComment(
                mapOf(
                    "oid" to oid.toString(),
                    "type" to type.toString(),
                    "rpid" to rpid.toString(),
                    "action" to if (liked) "1" else "0",
                    "csrf" to csrf,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "点赞失败(${response.code})" })
            }
        }
    }

    suspend fun hateComment(
        oid: Long,
        rpid: Long,
        hated: Boolean,
        type: Int = 1,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            buvid.ensure()
            val csrf = session.csrf()
            require(csrf.isNotBlank()) { "登录后可用" }
            val response = api.hateComment(
                mapOf(
                    "oid" to oid.toString(),
                    "type" to type.toString(),
                    "rpid" to rpid.toString(),
                    "action" to if (hated) "1" else "0",
                    "csrf" to csrf,
                ),
            )
            if (response.code != 0) {
                error(response.message.ifBlank { "操作失败(${response.code})" })
            }
        }
    }

    private suspend fun legacyFirstPage(oid: Long, type: Int): CommentPage {
        val response = api.legacyComments(
            mapOf(
                "oid" to oid.toString(),
                "type" to type.toString(),
                "pn" to "1",
                "ps" to "20",
                "sort" to "1",
            ),
        )
        if (response.code != 0) {
            error(response.message.ifBlank { "评论加载失败(${response.code})" })
        }
        val data = response.data ?: return CommentPage(emptyList(), null, true, 0)
        val upMid = data.upper?.mid ?: 0L
        val items = buildList {
            addAll(data.hots.orEmpty())
            addAll(data.replies.orEmpty())
        }.distinctBy { it.rpid }
        return CommentPage(
            comments = items.map { it.toComment(upMid = upMid) },
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
        val upMid = upper?.mid ?: 0L
        val topIds = buildSet {
            top?.upper?.rpid?.takeIf { it > 0L }?.let(::add)
            top?.admin?.rpid?.takeIf { it > 0L }?.let(::add)
            upper?.top?.rpid?.takeIf { it > 0L }?.let(::add)
            topReplies.orEmpty().forEach { if (it.rpid > 0L) add(it.rpid) }
        }
        val items = buildList {
            top?.upper?.let(::add)
            top?.admin?.let(::add)
            upper?.top?.let(::add)
            addAll(topReplies.orEmpty())
            addAll(hots.orEmpty())
            addAll(replies.orEmpty())
        }.distinctBy { it.rpid }

        return CommentPage(
            comments = items.map { it.toComment(isTop = it.rpid in topIds, upMid = upMid) },
            nextOffset = cursor.paginationReply?.nextOffset?.takeIf { it.isNotBlank() },
            isEnd = cursor.isEnd,
            total = maxOf(cursor.allCount, items.size),
        )
    }

    private fun ReplyItem.toComment(
        isTop: Boolean = false,
        upMid: Long = 0L,
    ): Comment {
        val card = member.userSailing?.cardbg ?: member.userSailing?.cardbgWithFocus
        val cardImg = Formatters.normalizeImageUrl(card?.image)
        val fan = card?.fan
        val sailing = if (cardImg.isNotBlank()) {
            val isFan = (fan?.isFan ?: 0) == 1 && !fan?.numDesc.isNullOrBlank()
            CommentSailing(
                imageUrl = cardImg,
                numPrefix = if (isFan) fan?.numPrefix.orEmpty().ifBlank { "NO." } else "",
                numDesc = if (isFan) fan?.numDesc.orEmpty() else "",
                fanColorHex = if (isFan) fan?.color.orEmpty() else "",
            )
        } else {
            null
        }
        val vipColor = if ((member.vip?.vipStatus ?: 0) == 1) {
            member.vip?.nicknameColor.orEmpty()
        } else {
            ""
        }
        val rawPics = content.pictures.orEmpty().ifEmpty { pictures.orEmpty() }
        val commentPictures = rawPics
            .filter { it.displayUrl.isNotBlank() }
            .map { pic ->
                DynamicImage(
                    url = Formatters.normalizeImageUrl(pic.displayUrl),
                    width = pic.imgWidth,
                    height = pic.imgHeight,
                )
            }

        return Comment(
            id = rpid,
            rootId = if (root > 0) root else rpid,
            parentId = parent,
            userName = member.uname.ifBlank { "未知用户" },
            avatar = member.avatar,
            level = member.levelInfo.currentLevel,
            timeSec = ctime,
            content = content.message,
            like = like,
            liked = action == 1,
            disliked = action == 2,
            replyCount = maxOf(count, rcount),
            mid = mid,
            location = replyControl?.location.orEmpty().trim(),
            vipColorHex = vipColor,
            sailing = sailing,
            subReplyEntryText = replyControl?.subReplyEntryText.orEmpty().trim(),
            isTop = isTop,
            isUp = upMid > 0L && mid == upMid,
            emotes = content.emote.orEmpty()
                .mapValues { (_, emote) ->
                    EmoteImage(
                        url = Formatters.normalizeImageUrl(emote.url),
                        large = (emote.meta?.size ?: 1) >= 2,
                    )
                }
                .filterValues { it.url.isNotBlank() },
            pictures = commentPictures,
            replies = replies.orEmpty().map { it.toComment(upMid = upMid) },
        )
    }
}
