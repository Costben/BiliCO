package erl.bilico.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class DanmakuType {
    SCROLL,
    TOP,
    BOTTOM,
}

/** 一条弹幕。timeMs 是相对视频开头的绝对毫秒，来自 seg.so 的 progress 字段。 */
data class DanmakuItem(
    val id: Long,
    val timeMs: Int,
    val type: DanmakuType,
    val content: String,
    val color: Int,
    val fontSize: Int,
    val midHash: String = "",
    val senderName: String = "",
    val senderMedal: String = "",
    val emotes: Map<String, EmoteImage> = emptyMap(),
    val isSelf: Boolean = false,
)

@Serializable
data class SendDanmakuResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
    val data: SendDanmakuData? = null,
)

@Serializable
data class SendDanmakuData(
    val dmid: Long = 0L,
    @SerialName("dmid_str") val dmidStr: String = "",
)
