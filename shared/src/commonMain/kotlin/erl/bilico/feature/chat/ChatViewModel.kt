package erl.bilico.feature.chat

import erl.bilico.platform.currentTimeMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import erl.bilico.data.model.CommentEmote
import erl.bilico.data.model.CommentEmotePackage
import erl.bilico.data.model.DEFAULT_EMOTE_MANAGE_URL
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.model.ImMessageUi
import erl.bilico.data.repository.CommentRepository
import erl.bilico.data.repository.ImRepository
import erl.bilico.data.repository.MessageRepository
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ImMessageUi> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val emotePackages: List<CommentEmotePackage> = emptyList(),
    val emoteManageUrl: String = DEFAULT_EMOTE_MANAGE_URL,
    val emotesFailed: Boolean = false,
    val notice: String? = null,
)

internal data class ChatImagePayload(
    val previewUri: String,
    val readBytes: suspend () -> Triple<ByteArray, String, String>?,
)

class ChatViewModel(
    private val imRepository: ImRepository,
    private val commentRepository: CommentRepository,
    private val session: SessionRepository,
    private val messageRepository: MessageRepository,
    private val talkerId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var ackedSeqno = 0L

    init {
        loadMessages()
        loadEmotes(force = false)
    }

    /** 重新进入已加载过的会话时静默拉一次，避免整页闪成加载态。 */
    fun refreshOnEnter() {
        if (_state.value.isLoading) return
        loadMessages(silent = _state.value.messages.isNotEmpty())
    }

    fun loadMessages(silent: Boolean = false) {
        _state.update { if (silent) it.copy(error = null) else it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            imRepository.messages(talkerId)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            messages = page.messages,
                            isLoading = false,
                            hasMore = page.hasMore,
                            error = null,
                        )
                    }
                    markRead(page.maxSeqno)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "获取私信记录失败",
                        )
                    }
                }
        }
    }

    /** 进会话即把已读位置推到最新；成功后立刻刷新全局未读数，红点才会当场消失。 */
    private fun markRead(seqno: Long) {
        if (seqno <= 0L || seqno <= ackedSeqno) return
        viewModelScope.launch {
            imRepository.markSessionRead(talkerId, seqno).onSuccess {
                ackedSeqno = seqno
                messageRepository.refreshUnread(force = true)
            }
        }
    }

    fun loadEmotes(force: Boolean = false) {
        val current = _state.value
        if (!force && (current.emotePackages.isNotEmpty() || current.emotesFailed)) return
        viewModelScope.launch {
            val panel = commentRepository.emotePanel().getOrElse {
                _state.update { it.copy(emotesFailed = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    emotePackages = panel.packages,
                    emoteManageUrl = panel.manageUrl,
                    emotesFailed = panel.packages.isEmpty(),
                )
            }
        }
    }

    fun showNotice(message: String) {
        _state.update { it.copy(notice = message) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun send(
        text: String,
        emoteLookup: Map<String, CommentEmote> = emptyMap(),
        onFinished: () -> Unit,
    ) {
        val content = text.trim()
        if (content.isBlank() || _state.value.isSending) return

        val msgEmotes = if ('[' in content && emoteLookup.isNotEmpty()) {
            emoteLookup.mapNotNull { (key, emote) ->
                if (key in content && emote.url.isNotBlank()) {
                    key to EmoteImage(url = emote.url, large = emote.large)
                } else {
                    null
                }
            }.toMap()
        } else {
            emptyMap()
        }

        val nowSec = currentTimeMillis() / 1000
        val tempKey = -currentTimeMillis()
        val optimisticMsg = ImMessageUi(
            msgKey = tempKey,
            senderUid = session.mid(),
            isMe = true,
            content = content,
            timeText = "刚刚",
            timestamp = nowSec,
            emotes = msgEmotes,
        )
        _state.update {
            it.copy(
                messages = it.messages + optimisticMsg,
                isSending = true,
            )
        }
        onFinished()

        viewModelScope.launch {
            imRepository.sendMessage(talkerId, content)
                .onSuccess { msgKey ->
                    _state.update { cur ->
                        cur.copy(
                            isSending = false,
                            messages = cur.messages.map { msg ->
                                if (msg.msgKey == tempKey) msg.copy(msgKey = msgKey) else msg
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { cur ->
                        cur.copy(
                            isSending = false,
                            messages = cur.messages.filterNot { it.msgKey == tempKey },
                            notice = error.message ?: "发送失败",
                        )
                    }
                }
        }
    }

    internal fun sendImages(items: List<ChatImagePayload>) {
        if (items.isEmpty() || _state.value.isSending) return
        val nowSec = currentTimeMillis() / 1000
        val baseKey = currentTimeMillis()
        val optimisticEntries = items.mapIndexed { index, item ->
            val tempKey = -(baseKey + index + 1)
            tempKey to ImMessageUi(
                msgKey = tempKey,
                senderUid = session.mid(),
                isMe = true,
                content = "[图片]",
                timeText = "发送中",
                timestamp = nowSec,
                imageUrl = item.previewUri,
            )
        }
        _state.update { cur ->
            cur.copy(
                messages = cur.messages + optimisticEntries.map { it.second },
                isSending = true,
            )
        }

        viewModelScope.launch {
            for ((index, item) in items.withIndex()) {
                val tempKey = optimisticEntries[index].first
                val payload = item.readBytes()
                if (payload == null) {
                    _state.update { cur ->
                        cur.copy(
                            messages = cur.messages.filterNot { it.msgKey == tempKey },
                            notice = "读取第 ${index + 1} 张图片失败",
                        )
                    }
                    continue
                }
                val (bytes, fileName, mime) = payload
                val uploaded = commentRepository.uploadPicture(bytes, fileName, mime).getOrElse { error ->
                    _state.update { cur ->
                        cur.copy(
                            messages = cur.messages.filterNot { it.msgKey == tempKey },
                            notice = error.message ?: "图片上传失败",
                        )
                    }
                    continue
                }
                val imageType = mime.substringAfterLast('/', "jpeg").lowercase()
                imRepository.sendImageMessage(talkerId, uploaded, imageType)
                    .onSuccess { msgKey ->
                        _state.update { cur ->
                            cur.copy(
                                messages = cur.messages.map { msg ->
                                    if (msg.msgKey == tempKey) {
                                        msg.copy(
                                            msgKey = msgKey,
                                            imageUrl = uploaded.url,
                                            imageWidth = uploaded.width,
                                            imageHeight = uploaded.height,
                                            timeText = "刚刚",
                                        )
                                    } else {
                                        msg
                                    }
                                },
                            )
                        }
                    }
                    .onFailure { error ->
                        _state.update { cur ->
                            cur.copy(
                                messages = cur.messages.filterNot { it.msgKey == tempKey },
                                notice = error.message ?: "发送图片失败",
                            )
                        }
                    }
            }
            _state.update { it.copy(isSending = false) }
        }
    }
}
