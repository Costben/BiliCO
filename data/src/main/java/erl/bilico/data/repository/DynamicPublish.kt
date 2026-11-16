package erl.bilico.data.repository

import erl.bilico.data.model.DynamicImageData
import erl.bilico.data.model.UploadedPicture
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun publishDynamicRequest(
    api: BiliApi,
    buvid: BuvidManager,
    session: SessionRepository?,
    text: String,
    pictures: List<UploadedPicture>,
    atMids: Map<String, Long>,
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() || pictures.isNotEmpty()) { "请输入内容或选择图片" }
        buvid.ensure()
        val csrf = session?.csrf().orEmpty()
        require(csrf.isNotBlank()) { "登录后可用" }

        val payload = buildDynamicCreatePayload(trimmed, pictures, atMids)
        val response = api.createDynamic(csrf = csrf, body = payload)
        if (response.code != 0) {
            error(response.message.ifBlank { "发布动态失败(${response.code})" })
        }
        response.data?.dynIdStr.orEmpty()
    }
}

private fun buildDynamicCreatePayload(
    text: String,
    pictures: List<UploadedPicture>,
    atMids: Map<String, Long>,
): JsonObject {
    val segments = buildContentSegments(text, atMids)
    return buildJsonObject {
        put(
            "dyn_req",
            buildJsonObject {
                put(
                    "content",
                    buildJsonObject {
                        put(
                            "contents",
                            buildJsonArray {
                                segments.forEach { seg ->
                                    add(
                                        buildJsonObject {
                                            put("raw_text", seg.rawText)
                                            put("type", seg.type)
                                            put("biz_id", seg.bizId)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
                put("scene", if (pictures.isEmpty()) 1 else 2)
                if (pictures.isNotEmpty()) {
                    put(
                        "pics",
                        buildJsonArray {
                            pictures.forEach { pic ->
                                add(
                                    buildJsonObject {
                                        put("img_src", pic.url)
                                        put("img_width", pic.width.coerceAtLeast(1))
                                        put("img_height", pic.height.coerceAtLeast(1))
                                        put("img_size", JsonPrimitive(pic.sizeKb.coerceAtLeast(0.1)))
                                    },
                                )
                            }
                        },
                    )
                }
            },
        )
    }
}

private data class DynSegment(val rawText: String, val type: Int, val bizId: String)

private fun buildContentSegments(text: String, atMids: Map<String, Long>): List<DynSegment> {
    if (text.isEmpty()) return listOf(DynSegment("", 1, ""))
    if (atMids.isEmpty()) return listOf(DynSegment(text, 1, ""))
    val tokens = atMids.entries
        .filter { it.key.isNotBlank() && it.value > 0L }
        .sortedByDescending { it.key.length }
        .map { "@${it.key} " to it.value.toString() }
    if (tokens.isEmpty()) return listOf(DynSegment(text, 1, ""))

    val result = mutableListOf<DynSegment>()
    var cursor = 0
    while (cursor < text.length) {
        var bestPos = -1
        var bestToken: Pair<String, String>? = null
        for (token in tokens) {
            val idx = text.indexOf(token.first, startIndex = cursor)
            if (idx >= 0 && (bestPos < 0 || idx < bestPos)) {
                bestPos = idx
                bestToken = token
            }
        }
        if (bestPos < 0 || bestToken == null) {
            result += DynSegment(text.substring(cursor), 1, "")
            break
        }
        if (bestPos > cursor) {
            result += DynSegment(text.substring(cursor, bestPos), 1, "")
        }
        result += DynSegment(bestToken.first, 2, bestToken.second)
        cursor = bestPos + bestToken.first.length
    }
    return if (result.isEmpty()) listOf(DynSegment(text, 1, "")) else result
}

internal data class OpusBlocks(
    val text: String = "",
    val images: List<DynamicImageData> = emptyList(),
)

internal fun JsonElement?.decodeOpusParagraphs(): OpusBlocks {
    val modules = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(this)
        else -> return OpusBlocks()
    }
    val content = modules.firstNotNullOfOrNull { it["module_content"] as? JsonObject }
        ?: return OpusBlocks()
    val paragraphs = (content["paragraphs"] as? JsonArray).orEmpty()
    val text = StringBuilder()
    val images = mutableListOf<DynamicImageData>()
    paragraphs.forEach paragraph@{ element ->
        val paragraph = element as? JsonObject ?: return@paragraph
        val pic = paragraph["pic"] as? JsonObject
        if (pic != null) {
            (pic["pics"] as? JsonArray).orEmpty().forEach picItem@{ item ->
                val obj = item as? JsonObject ?: return@picItem
                val url = obj.jsonText("url")
                if (url.isBlank()) return@picItem
                images += DynamicImageData(
                    src = url,
                    url = url,
                    width = obj.jsonInt("width"),
                    height = obj.jsonInt("height"),
                )
            }
            return@paragraph
        }
        val paragraphText = (paragraph["text"] as? JsonObject)
            ?.let { it["nodes"] as? JsonArray }
            .orEmpty()
            .mapNotNull { node ->
                val obj = node as? JsonObject ?: return@mapNotNull null
                (obj["word"] as? JsonObject)?.jsonText("words")
                    ?: (obj["rich"] as? JsonObject)?.jsonText("text")
                    ?: (obj["formula"] as? JsonObject)?.jsonText("latex")
            }
            .joinToString("")
            .trim()
        if (paragraphText.isNotEmpty()) {
            if (text.isNotEmpty()) text.append('\n')
            text.append(paragraphText)
        }
    }
    return OpusBlocks(text.toString(), images)
}

private fun JsonObject.jsonText(key: String): String {
    val value = this[key] as? JsonPrimitive ?: return ""
    if (value is JsonNull) return ""
    return value.content.takeIf { it != "null" }.orEmpty()
}

private fun JsonObject.jsonInt(key: String): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
