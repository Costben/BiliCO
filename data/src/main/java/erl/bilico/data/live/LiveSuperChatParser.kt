package erl.bilico.data.live

import erl.bilico.data.model.LiveSuperChat
import erl.bilico.data.util.Formatters
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** 将 REST `getMessageList` 与 WebSocket `SUPER_CHAT_MESSAGE` 的 JSON 对象统一解成 [LiveSuperChat]。 */
internal fun JsonObject.toLiveSuperChat(): LiveSuperChat? {
    val msg = string("message").orEmpty().trim()
    val price = int("price") ?: 0
    if (msg.isBlank() && price <= 0) return null

    val userInfo = obj("user_info")
    val uinfo = obj("uinfo")
    val medal = obj("medal_info") ?: uinfo?.obj("medal")

    val nowSec = System.currentTimeMillis() / 1000L
    val start = long("start_time") ?: long("ts") ?: nowSec
    val duration = long("time") ?: 60L
    val end = long("end_time") ?: (start + duration)
    val id = long("id") ?: ((start * 31L) + (long("uid") ?: 0L) + msg.hashCode())

    return LiveSuperChat(
        id = id,
        uid = long("uid") ?: 0L,
        userName = userInfo?.string("uname").orEmpty().ifBlank { "匿名" },
        userFace = Formatters.normalizeImageUrl(userInfo?.string("face")),
        faceFrame = Formatters.normalizeImageUrl(userInfo?.string("face_frame")),
        nameColor = userInfo?.string("name_color").orEmpty(),
        medalName = medal?.string("medal_name") ?: medal?.string("name").orEmpty(),
        medalLevel = medal?.int("medal_level") ?: medal?.int("level") ?: 0,
        price = price,
        message = msg,
        startTimeSec = start,
        endTimeSec = end,
        backgroundColor = string("background_color")?.takeIf { it.isNotBlank() } ?: "#EDF5FF",
        backgroundBottomColor = string("background_bottom_color")?.takeIf { it.isNotBlank() } ?: "#2A60B2",
        backgroundPriceColor = string("background_price_color")?.takeIf { it.isNotBlank() } ?: "#7497CD",
        messageFontColor = string("message_font_color")?.takeIf { it.isNotBlank() } ?: "#FFFFFF",
        backgroundImage = Formatters.normalizeImageUrl(string("background_image")),
    )
}

internal fun JsonObject.parseDeletedSuperChatIds(): List<Long> {
    val arr = this["ids"] as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        runCatching { el as? kotlinx.serialization.json.JsonPrimitive }.getOrNull()
            ?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
    }
}

private fun JsonObject.obj(key: String): JsonObject? =
    this[key]?.let { runCatching { it.jsonObject }.getOrNull() }

private fun JsonObject.string(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let {
        it.longOrNull ?: it.contentOrNull?.toLongOrNull()
    }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let {
        it.intOrNull ?: it.contentOrNull?.toIntOrNull()
    }

/** 合并本场直播累计 SC 列表：按 id 去重并保持最新在前，不过期淘汰。 */
fun mergeSuperChatHistory(
    existing: List<LiveSuperChat>,
    incoming: List<LiveSuperChat>,
): List<LiveSuperChat> {
    if (incoming.isEmpty()) return existing
    return (incoming + existing)
        .distinctBy { it.id }
        .sortedByDescending { it.startTimeSec }
}
