package erl.bilico.feature.live

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import erl.bilico.data.live.LiveDanmakuState
import erl.bilico.data.model.EmoteImage
import erl.bilico.data.model.LiveDanmakuMessage
import erl.bilico.data.model.LiveSuperChat
import erl.bilico.data.util.Formatters
import erl.bilico.feature.video.ACTION_END_SLACK
import erl.bilico.feature.video.DETAIL_CAPSULE_HEIGHT
import erl.bilico.feature.video.actionEndSlack
import erl.bilico.feature.video.copyDanmakuToClipboard
import erl.bilico.ui.components.CompactSendButton
import erl.bilico.ui.components.DanmakuToggle
import erl.bilico.ui.components.RichCommentText
import erl.bilico.ui.components.inputCapsuleGlass
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.preciseClickAnchor
import io.github.suqi8.coui.kmp.basic.rememberPreciseClickState
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.launch

/**
 * 直播间竖屏聊天面板：
 * 用 `reverseLayout = true` 将最新一条固定在底部，新弹幕到来时直接从底部平滑上移顶出，不会全页闪现；
 * 任一条弹幕或 SC 均支持点击/长按弹出操作菜单（复制、屏蔽该用户、屏蔽此内容、查看主页）。
 */
@Composable
internal fun LiveDanmakuPanel(
    entries: List<LiveChatEntry>,
    activeSuperChats: List<LiveSuperChat>,
    nowSec: Long,
    state: LiveDanmakuState,
    error: String?,
    danmakuEnabled: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSendDanmaku: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onPlusOne: (text: String, emotes: Map<String, EmoteImage>) -> Unit,
    onAdvancedPlusOne: (medalLabel: String, senderName: String, text: String, emotes: Map<String, EmoteImage>) -> Unit,
    onCopyDanmaku: (String) -> Unit,
    onBlockUser: (mid: Long, name: String) -> Unit,
    onBlockContent: (String) -> Unit,
    onOpenSpace: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDanmakuSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var composing by remember { mutableStateOf(false) }
    var autoFollow by remember { mutableStateOf(true) }
    var pressingList by remember { mutableStateOf(false) }
    var menuOpenSeq by remember { mutableStateOf<Long?>(null) }
    var visibleEntries by remember { mutableStateOf(entries) }
    LaunchedEffect(entries, pressingList, menuOpenSeq) {
        if ((!pressingList && menuOpenSeq == null) || visibleEntries.isEmpty()) {
            visibleEntries = entries
        }
    }
    val userScrollObserver = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // reverseLayout = true 下，手指向下拖（available.y > 0）是在向上回翻历史弹幕。
                if (source == NestedScrollSource.UserInput && available.y > 4f) {
                    autoFollow = false
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 24)
        }.collect { (scrolling, atBottom) ->
            if (!scrolling && atBottom) {
                autoFollow = true
            }
        }
    }
    val latestSeq = visibleEntries.lastOrNull()?.seq
    LaunchedEffect(latestSeq, autoFollow, pressingList, menuOpenSeq) {
        if (autoFollow && !pressingList && menuOpenSeq == null && visibleEntries.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier) {
        LiveDanmakuHeaderBar(
            state = state,
            error = error,
            danmakuEnabled = danmakuEnabled,
            onToggleDanmaku = onToggleDanmaku,
            onOpenDanmakuSettings = onOpenDanmakuSettings,
            composing = composing,
            onComposingChange = { composing = it },
            draft = draft,
            onDraftChange = onDraftChange,
            onSend = {
                onSendDanmaku()
                composing = false
            },
        )

        if (activeSuperChats.isNotEmpty()) {
            SuperChatStrip(
                items = activeSuperChats,
                nowSec = nowSec,
                onSelect = { sc ->
                    if (sc.message.isNotBlank()) {
                        copyDanmakuToClipboard(context, sc.message)
                        onCopyDanmaku("已复制 SC：${sc.message.take(20)}")
                    }
                },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (visibleEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when (state) {
                            LiveDanmakuState.Connected -> "还没有人说话"
                            LiveDanmakuState.Connecting, LiveDanmakuState.Idle -> "正在连接弹幕…"
                            LiveDanmakuState.Failed -> error ?: "弹幕连接失败"
                        },
                        fontSize = 12.sp,
                        color = COUITheme.colorScheme.onSurfaceSecondary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // 手指按下期间冻结列表刷新，防止高频弹幕把目标行顶走或打断长按。
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                pressingList = true
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.none { it.pressed }) break
                                }
                                pressingList = false
                            }
                        }
                        .nestedScroll(userScrollObserver)
                        .scrollEndHaptic(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                ) {
                    items(
                        count = visibleEntries.size,
                        key = { index -> visibleEntries[visibleEntries.lastIndex - index].seq },
                        contentType = { index ->
                            when (visibleEntries[visibleEntries.lastIndex - index]) {
                                is LiveChatEntry.Danmaku -> "live-danmaku"
                                is LiveChatEntry.SuperChat -> "live-sc"
                            }
                        },
                    ) { index ->
                        val entry = visibleEntries[visibleEntries.lastIndex - index]
                        val pressState = rememberPreciseClickState()
                        val menuExpanded = menuOpenSeq == entry.seq
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .preciseClickAnchor(pressState),
                        ) {
                            when (entry) {
                                is LiveChatEntry.Danmaku -> DanmakuRow(
                                    message = entry.message,
                                    onClick = { menuOpenSeq = entry.seq },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuOpenSeq = entry.seq
                                    },
                                )
                                is LiveChatEntry.SuperChat -> LiveSuperChatCard(
                                    sc = entry.sc,
                                    nowSec = nowSec,
                                    onClick = { menuOpenSeq = entry.seq },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuOpenSeq = entry.seq
                                    },
                                )
                            }
                            ChatEntryPopupMenu(
                                expanded = menuExpanded,
                                entry = entry,
                                pressState = pressState,
                                context = context,
                                onDismiss = { menuOpenSeq = null },
                                onPlusOne = onPlusOne,
                                onAdvancedPlusOne = onAdvancedPlusOne,
                                onCopyDanmaku = onCopyDanmaku,
                                onBlockUser = onBlockUser,
                                onBlockContent = onBlockContent,
                                onOpenSpace = onOpenSpace,
                            )
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !autoFollow && visibleEntries.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp),
            ) {
                Text(
                    text = "回到底部",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(COUITheme.colorScheme.primary)
                        .clickable {
                            autoFollow = true
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveDanmakuHeaderBar(
    state: LiveDanmakuState,
    error: String?,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    onOpenDanmakuSettings: (() -> Unit)?,
    composing: Boolean,
    onComposingChange: (Boolean) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scheme = COUITheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val actionSlack by animateDpAsState(if (composing) ACTION_END_SLACK else 0.dp, tween(300))
    val hintText = when (state) {
        LiveDanmakuState.Connected -> "发弹幕"
        LiveDanmakuState.Connecting, LiveDanmakuState.Idle -> "弹幕连接中…"
        LiveDanmakuState.Failed -> error ?: "连接失败 · 发弹幕"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SearchBar(
            inputField = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    InputField(
                        query = draft,
                        onQueryChange = onDraftChange,
                        onSearch = {
                            keyboard?.hide()
                            onSend()
                        },
                        expanded = composing,
                        onExpandedChange = onComposingChange,
                        label = "",
                        textStyle = COUITheme.textStyles.body2,
                        colors = SearchBarDefaults.searchBarColors(backgroundColor = Color.Transparent),
                        stroke = null,
                        glowColor = null,
                        backdrop = null,
                        leadingIcon = { Spacer(Modifier.width(12.dp)) },
                        trailingIcon = {
                            DanmakuToggle(
                                enabled = danmakuEnabled,
                                onToggle = onToggleDanmaku,
                                onOpenSettings = onOpenDanmakuSettings,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(DETAIL_CAPSULE_HEIGHT)
                            .inputCapsuleGlass(backdrop = null, isDark = isDark, fallbackColor = scheme.surfaceContainer),
                    )
                    if (draft.isEmpty()) {
                        Text(
                            text = hintText,
                            style = COUITheme.textStyles.body2,
                            color = scheme.onSurfaceSecondary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp, end = if (onOpenDanmakuSettings != null) 66.dp else 32.dp),
                        )
                    }
                }
            },
            onExpandedChange = onComposingChange,
            modifier = Modifier.fillMaxWidth().actionEndSlack(actionSlack),
            insideMargin = DpSize(0.dp, 2.dp),
            expanded = composing,
            outsideEndAction = {
                CompactSendButton(
                    text = "发送",
                    onClick = {
                        keyboard?.hide()
                        onSend()
                    },
                    enabled = draft.isNotBlank(),
                    height = DETAIL_CAPSULE_HEIGHT,
                    containerColor = scheme.surfaceContainer,
                    isDark = isDark,
                    backdrop = null,
                )
            },
        ) { }
    }
}

internal fun formatAdvancedPlusOneText(
    medalLabel: String,
    senderName: String,
    content: String,
): String {
    val prefix = buildString {
        if (medalLabel.isNotBlank()) append("[$medalLabel]")
        if (senderName.isNotBlank()) {
            if (isNotEmpty()) append(' ')
            append("@$senderName")
        }
        if (isNotEmpty()) append(": ")
    }
    return prefix + content
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DanmakuRow(
    message: LiveDanmakuMessage,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (message.medalName.isNotBlank()) {
                Text(
                    text = "${message.medalName} ${message.medalLevel}",
                    modifier = Modifier
                        .clip(SquircleShape(4.dp))
                        .background(COUITheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    fontSize = 10.sp,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = message.name,
                fontSize = 12.sp,
                color = COUITheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
        val standaloneSticker = message.emotes[message.text]?.takeIf {
            message.emotes.size == 1 && it.large && !message.text.startsWith("[")
        }
        if (standaloneSticker != null) {
            AsyncImage(
                model = Formatters.sizedImageUrl(standaloneSticker.url, 96, 96),
                contentDescription = message.text,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(top = 2.dp).size(34.dp),
            )
        } else if (message.emotes.isNotEmpty()) {
            RichCommentText(
                content = message.text,
                emotes = message.emotes,
                compactEmotes = true,
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                color = COUITheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 1.dp),
            )
        } else {
            Text(
                text = message.text,
                modifier = Modifier.padding(top = 1.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = COUITheme.colorScheme.onSurface,
            )
        }
    }
}
