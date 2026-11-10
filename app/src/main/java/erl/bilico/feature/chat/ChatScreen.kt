package erl.bilico.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import erl.bilico.data.model.ImMessageUi
import erl.bilico.data.repository.ImRepository
import erl.bilico.data.session.SessionRepository
import erl.bilico.data.util.Formatters
import erl.bilico.di.AppContainer
import erl.bilico.ui.NavState
import erl.bilico.ui.Route
import erl.bilico.ui.components.Avatar
import erl.bilico.ui.components.EmptyState
import erl.bilico.ui.components.ErrorState
import erl.bilico.ui.components.LoadingState
import erl.bilico.ui.components.SecondaryPage
import erl.bilico.ui.pageContainerColor
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonSize
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.theme.COUITheme
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
)

class ChatViewModel(
    private val imRepository: ImRepository,
    private val session: SessionRepository,
    private val talkerId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        loadMessages()
    }

    fun loadMessages() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            imRepository.messages(talkerId)
                .onSuccess { (msgs, hasMore) ->
                    _state.update {
                        it.copy(
                            messages = msgs,
                            isLoading = false,
                            hasMore = hasMore,
                            error = null,
                        )
                    }
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

    fun send(text: String, onFinished: () -> Unit) {
        val content = text.trim()
        if (content.isBlank() || _state.value.isSending) return

        // 乐观追加一条本端消息
        val nowSec = System.currentTimeMillis() / 1000
        val tempKey = System.currentTimeMillis()
        val optimisticMsg = ImMessageUi(
            msgKey = tempKey,
            senderUid = session.mid(),
            isMe = true,
            content = content,
            timeText = "刚刚",
            timestamp = nowSec,
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
                .onFailure {
                    _state.update { cur ->
                        cur.copy(isSending = false)
                    }
                }
        }
    }
}

@Composable
fun ChatScreen(
    container: AppContainer,
    nav: NavState,
    talkerId: Long,
    talkerName: String,
    talkerFace: String,
) {
    val viewModel: ChatViewModel = viewModel(key = "chat-$talkerId") {
        ChatViewModel(container.im, container.session, talkerId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val pageColor = pageContainerColor()
    var inputText by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    // 当收到新消息或者发送新消息时，自动滚动到最底部
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageColor)
            .imePadding(),
    ) {
        SecondaryPage(
            title = talkerName.ifBlank { "UID: $talkerId" },
            onBack = nav::pop,
            listState = listState,
            actions = {
                IconButton(onClick = { nav.push(Route.Space(talkerId)) }) {
                    Icon(
                        imageVector = COUIIcons.Community,
                        contentDescription = "个人空间",
                    )
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            if (state.isLoading) {
                item(key = "loading") { LoadingState() }
            } else if (state.error != null && state.messages.isEmpty()) {
                item(key = "error") {
                    ErrorState(message = state.error.orEmpty(), onRetry = viewModel::loadMessages)
                }
            } else if (state.messages.isEmpty()) {
                item(key = "empty") {
                    EmptyState(text = "暂无聊天记录，发条私信打个招呼吧")
                }
            } else {
                items(
                    items = state.messages,
                    key = { it.msgKey.toString() + "_" + it.timestamp },
                ) { msg ->
                    ChatBubbleRow(
                        message = msg,
                        talkerFace = talkerFace,
                    )
                }
            }
        }

        // 底部输入栏
        ChatBottomBar(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                viewModel.send(inputText) {
                    inputText = ""
                }
            },
            isSending = state.isSending,
        )
    }
}

@Composable
private fun ChatBubbleRow(
    message: ImMessageUi,
    talkerFace: String,
) {
    val isMe = message.isMe
    val scheme = COUITheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (!isMe) {
                Avatar(url = talkerFace, size = 36.dp)
                Spacer(Modifier.width(8.dp))
            }

            // 气泡主体
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp,
                        )
                    )
                    .background(if (isMe) scheme.primary else scheme.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.content,
                    fontSize = 15.sp,
                    color = if (isMe) Color.White else scheme.onSurface,
                    lineHeight = 20.sp,
                )
            }

            if (isMe) {
                Spacer(Modifier.width(8.dp))
            }
        }

        if (message.timeText.isNotBlank()) {
            Text(
                text = message.timeText,
                fontSize = 10.sp,
                color = scheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 4.dp, start = if (!isMe) 44.dp else 0.dp, end = if (isMe) 8.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun ChatBottomBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InputField(
                query = text,
                onQueryChange = onTextChange,
                onSearch = { onSend() },
                expanded = false,
                onExpandedChange = {},
                label = "发送私信...",
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = onSend,
                enabled = text.isNotBlank() && !isSending,
                size = ButtonSize.Small,
            ) {
                Text(
                    text = "发送",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
