package erl.bilico.data.repository

import erl.bilico.data.model.EmoteImage
import erl.bilico.data.model.ImContentParser
import erl.bilico.data.model.ImMessagePage
import erl.bilico.data.model.ImMessageUi
import erl.bilico.data.model.ImSessionUi
import erl.bilico.data.model.UploadedPicture
import erl.bilico.data.network.BiliApi
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ImRepository(
    private val api: BiliApi,
    private val space: SpaceRepository,
    private val session: SessionRepository,
) {
    // 内存缓存 talker 资料，避免重复请求空间资料
    private val profileCache = ConcurrentHashMap<Long, Pair<String, String>>()

    suspend fun sessions(endTs: Long? = null): Result<Pair<List<ImSessionUi>, Long?>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!session.isLoggedIn()) error("请先登录查看私信")
                val params = mutableMapOf(
                    "session_type" to "1",
                    "group_fold" to "1",
                    "unfold" to "0",
                    "sort_rule" to "2",
                    "size" to "20",
                )
                if (endTs != null && endTs > 0L) {
                    params["end_ts"] = endTs.toString()
                }
                val resp = api.imSessions(params)
                if (resp.code != 0) {
                    error(resp.message.ifBlank { resp.msg.ifBlank { "拉取私信列表失败(${resp.code})" } })
                }
                val data = resp.data ?: error("私信会话数据为空")
                val list = data.sessionList

                // 系统会话（官方通知、各类小助手）的 talker_id 不是真实用户，空间资料接口查不到，
                // 名称与头像只存在于 account_info 里，因此只为普通会话补齐资料。
                coroutineScope {
                    list.filter { it.accountInfo == null && it.systemMsgType == 0 }
                        .map { item ->
                            async {
                                ensureTalkerProfile(item.talkerId)
                            }
                        }.awaitAll()
                }

                val uiList = list.map { item ->
                    val cached = profileCache[item.talkerId]
                    val lastMsg = item.lastMsg
                    val lastMsgContent = if (lastMsg != null) {
                        ImContentParser.parse(lastMsg.content, lastMsg.msgType, preview = true)
                    } else {
                        ""
                    }
                    val ts = lastMsg?.timestamp ?: 0L
                    val name = item.accountInfo?.name.orEmpty()
                        .ifBlank { cached?.first.orEmpty() }
                        .ifBlank { SYSTEM_SESSION_NAMES[item.systemMsgType] ?: "用户 ${item.talkerId}" }
                    ImSessionUi(
                        talkerId = item.talkerId,
                        name = name,
                        face = item.accountInfo?.picUrl.orEmpty()
                            .ifBlank { cached?.second.orEmpty() },
                        lastMessage = lastMsgContent,
                        timeText = Formatters.formatTimeAgo(ts),
                        unreadCount = item.unreadCount,
                        timestamp = ts,
                    )
                }

                val nextEndTs = if (data.hasMore == 1 && uiList.isNotEmpty()) {
                    uiList.last().timestamp
                } else {
                    null
                }
                uiList to nextEndTs
            }
        }

    suspend fun messages(talkerId: Long, endSeqno: Long = 0L): Result<ImMessagePage> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!session.isLoggedIn()) error("请先登录查看私信")
                val params = mutableMapOf(
                    "talker_id" to talkerId.toString(),
                    "session_type" to "1",
                    "size" to "30",
                    "begin_seqno" to "0",
                )
                if (endSeqno > 0L) {
                    params["end_seqno"] = endSeqno.toString()
                }
                val resp = api.imFetchMsgs(params)
                if (resp.code != 0) {
                    error(resp.message.ifBlank { resp.msg.ifBlank { "拉取聊天记录失败(${resp.code})" } })
                }
                val data = resp.data ?: error("聊天消息为空")
                val myUid = session.mid()
                val sessionEmotes = data.eInfos.mapNotNull { info ->
                    val key = info.text.trim()
                    val url = Formatters.normalizeImageUrl(info.uri)
                    if (key.isBlank() || url.isBlank()) null
                    else key to EmoteImage(url = url, large = info.size >= 2)
                }.toMap()

                val list = data.messages.map { msg ->
                    val parsedImage = ImContentParser.parseImage(msg.content, msg.msgType)
                    val parsedText = if (parsedImage != null) {
                        if (msg.msgType == 6) "[表情]" else "[图片]"
                    } else {
                        ImContentParser.parse(msg.content, msg.msgType)
                    }
                    val msgEmotes = if (parsedImage == null && sessionEmotes.isNotEmpty() && '[' in parsedText) {
                        sessionEmotes.filterKeys { it in parsedText }
                    } else {
                        emptyMap()
                    }
                    ImMessageUi(
                        msgKey = msg.msgKey,
                        senderUid = msg.senderUid,
                        isMe = msg.senderUid == myUid,
                        content = parsedText,
                        timeText = Formatters.formatTimeAgo(msg.timestamp),
                        timestamp = msg.timestamp,
                        imageUrl = parsedImage?.url.orEmpty(),
                        imageWidth = parsedImage?.width ?: 0,
                        imageHeight = parsedImage?.height ?: 0,
                        emotes = msgEmotes,
                    )
                }.sortedBy { it.timestamp } // 按时间正序排列以便对话流展示

                ImMessagePage(
                    messages = list,
                    hasMore = data.hasMore == 1,
                    maxSeqno = data.maxSeqno,
                )
            }
        }

    /** 打开会话后把已读位置推到最新一条；未读数由服务端在下次刷新时归零。 */
    suspend fun markSessionRead(talkerId: Long, ackSeqno: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (talkerId <= 0L || ackSeqno <= 0L || !session.isLoggedIn()) return@runCatching
                val csrf = session.csrf()
                if (csrf.isBlank()) return@runCatching
                val resp = api.imUpdateAck(
                    mapOf(
                        "talker_id" to talkerId.toString(),
                        "session_type" to "1",
                        "ack_seqno" to ackSeqno.toString(),
                        "csrf" to csrf,
                        "csrf_token" to csrf,
                    )
                )
                if (resp.code != 0) {
                    error(resp.message.ifBlank { "标记已读失败(${resp.code})" })
                }
            }
        }

    suspend fun sendMessage(talkerId: Long, text: String): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(text.isNotBlank()) { "消息内容不能为空" }
                val contentJson = JSONObject().put("content", text).toString()
                sendRawMessage(talkerId = talkerId, msgType = 1, contentJson = contentJson)
            }
        }

    suspend fun sendImageMessage(
        talkerId: Long,
        picture: UploadedPicture,
        imageType: String = "jpeg",
    ): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(picture.url.isNotBlank()) { "图片地址无效" }
                val contentJson = JSONObject()
                    .put("url", picture.url)
                    .put("height", picture.height.coerceAtLeast(1))
                    .put("width", picture.width.coerceAtLeast(1))
                    .put("imageType", imageType.ifBlank { "jpeg" })
                    .put("original", 1)
                    .put("size", picture.sizeKb)
                    .toString()
                sendRawMessage(talkerId = talkerId, msgType = 2, contentJson = contentJson)
            }
        }

    private suspend fun sendRawMessage(
        talkerId: Long,
        msgType: Int,
        contentJson: String,
    ): Long {
        if (!session.isLoggedIn()) error("请先登录发送私信")
        val myUid = session.mid()
        require(myUid > 0L) { "未获取到当前登录 UID" }
        val csrf = session.csrf()
        require(csrf.isNotBlank()) { "CSRF Token 无效，请重新登录" }

        val nowSec = System.currentTimeMillis() / 1000
        val devId = UUID.randomUUID().toString().replace("-", "").uppercase()

        val form = mapOf(
            "msg[sender_uid]" to myUid.toString(),
            "msg[receiver_id]" to talkerId.toString(),
            "msg[receiver_type]" to "1",
            "msg[msg_type]" to msgType.toString(),
            "msg[msg_status]" to "0",
            "msg[content]" to contentJson,
            "msg[new_face_version]" to "1",
            "msg[dev_id]" to devId,
            "msg[timestamp]" to nowSec.toString(),
            "csrf" to csrf,
            "csrf_token" to csrf,
        )
        val resp = api.imSendMsg(form)
        if (resp.code != 0) {
            error(resp.message.ifBlank { resp.msg.ifBlank { "发送失败(${resp.code})" } })
        }
        return resp.data?.msgKey ?: 0L
    }

    private suspend fun ensureTalkerProfile(talkerId: Long) {
        if (profileCache.containsKey(talkerId)) return
        space.profile(talkerId).onSuccess { prof ->
            profileCache[talkerId] = prof.name to prof.face
        }
    }
}

/** `system_msg_type` 到名称的兜底映射，仅在接口没下发 account_info 时使用。 */
private val SYSTEM_SESSION_NAMES = mapOf(
    1 to "主播小助手",
    5 to "系统通知",
    7 to "UP主小助手",
    8 to "客服消息",
    9 to "支付小助手",
)
