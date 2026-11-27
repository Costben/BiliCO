package erl.bilico.feature.live

import erl.bilico.data.live.LiveDanmakuState
import erl.bilico.data.model.LiveDanmakuMessage
import erl.bilico.data.model.LivePlayback
import erl.bilico.data.model.LiveRoomDetail
import erl.bilico.data.model.LiveStreamSelection
import erl.bilico.data.model.LiveSuperChat
import erl.bilico.platform.currentTimeMillis

sealed interface LiveChatEntry {
    val seq: Long

    data class Danmaku(
        override val seq: Long,
        val message: LiveDanmakuMessage,
    ) : LiveChatEntry

    data class SuperChat(
        override val seq: Long,
        val sc: LiveSuperChat,
    ) : LiveChatEntry
}

data class LiveRoomUiState(
    val detail: LiveRoomDetail? = null,
    val playback: LivePlayback? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isPlaybackLoading: Boolean = false,
    val playbackError: String? = null,
    val requestedQuality: Int = 10000,
    val activeQuality: Int = 0,
    val lineSelection: LiveStreamSelection = LiveStreamSelection(),
    val onlyAudio: Boolean = false,
    /** 每次成功取流/手动切线路加一：让播放器重新 prepare。 */
    val playbackGeneration: Int = 0,
    /** 聊天区混合条目（普通弹幕 + SC），每条带单调递增稳定 seq。 */
    val chatEntries: List<LiveChatEntry> = emptyList(),
    /** 弹幕机专用的实时弹幕队列与累计计数。 */
    val danmaku: List<LiveDanmakuMessage> = emptyList(),
    val danmakuSeq: Long = 0L,
    /** 当前仍未过期的醒目留言列表，按最新在前排列。 */
    val activeSuperChats: List<LiveSuperChat> = emptyList(),
    /** 本场直播累计收到的所有 SC（不随时间过期或弹幕上限丢失），用于统计与回放。 */
    val superChatHistory: List<LiveSuperChat> = emptyList(),
    val fullscreenSuperChat: LiveSuperChat? = null,
    val fullscreenSuperChatEndSec: Long = 0L,
    val fullscreenSuperChatIsReplay: Boolean = false,
    val nowSec: Long = currentTimeMillis() / 1000L,
    val danmakuState: LiveDanmakuState = LiveDanmakuState.Idle,
    val danmakuError: String? = null,
)

internal fun LiveRoomUiState.statusText(): String {
    val detail = detail ?: return ""
    if (!detail.isLive) return "未开播"
    return if (playback == null) "加载中" else "直播中"
}

internal fun LiveRoomUiState.qualityLabel(): String? {
    val playback = playback ?: return null
    val label = playback.qualities
        .firstOrNull { it.quality == activeQuality }
        ?.label
        ?.takeIf { it.isNotBlank() }
    return label ?: activeQuality.takeIf { it > 0 }?.let { "${it}P" }
}
