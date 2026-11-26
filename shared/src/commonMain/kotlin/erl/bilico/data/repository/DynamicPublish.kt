package erl.bilico.data.repository

import erl.bilico.data.model.DynamicImageData
import erl.bilico.data.model.DynamicRichTextNode
import erl.bilico.data.model.DynamicTextNode
import erl.bilico.data.model.DynamicTextNodeKind
import erl.bilico.data.model.UploadedPicture
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.session.SessionRepository
import erl.bilico.platform.ioDispatcher
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
): Result<String> = withContext(ioDispatcher) {
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

private val RICH_BVID_REGEX = Regex("BV[0-9A-Za-z]{10}")

internal data class OpusBlocks(
    val text: String = "",
    val richNodes: List<DynamicTextNode> = emptyList(),
    val images: List<DynamicImageData> = emptyList(),
)

internal fun JsonElement?.decodeOpusParagraphs(dynamicId: String = ""): OpusBlocks {
    val modules = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(this)
        else -> return OpusBlocks()
    }
    val content = modules.firstNotNullOfOrNull { it["module_content"] as? JsonObject }
        ?: return OpusBlocks()
    val paragraphs = (content["paragraphs"] as? JsonArray).orEmpty()
    val text = StringBuilder()
    val allNodes = mutableListOf<DynamicTextNode>()
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
        val paraNodes = mutableListOf<DynamicTextNode>()
        val paraText = StringBuilder()
        (paragraph["text"] as? JsonObject)
            ?.let { it["nodes"] as? JsonArray }
            .orEmpty()
            .forEach { node ->
                val obj = node as? JsonObject ?: return@forEach
                val word = (obj["word"] as? JsonObject)?.jsonText("words")
                if (!word.isNullOrEmpty()) {
                    paraText.append(word)
                    paraNodes += DynamicTextNode(DynamicTextNodeKind.TEXT, word)
                    return@forEach
                }
                val rich = obj["rich"] as? JsonObject
                if (rich != null) {
                    val mapped = mapRichNode(
                        type = rich.jsonText("type"),
                        text = rich.jsonText("text"),
                        origText = rich.jsonText("orig_text"),
                        rid = rich.jsonText("rid"),
                        jumpUrl = rich.jsonText("jump_url"),
                        emojiText = (rich["emoji"] as? JsonObject)?.jsonText("text").orEmpty(),
                        dynamicId = dynamicId,
                    )
                    if (mapped != null) {
                        paraText.append(mapped.text)
                        paraNodes += mapped
                    }
                    return@forEach
                }
                val latex = (obj["formula"] as? JsonObject)?.jsonText("latex")
                if (!latex.isNullOrEmpty()) {
                    paraText.append(latex)
                    paraNodes += DynamicTextNode(DynamicTextNodeKind.TEXT, latex)
                }
            }
        val paragraphText = paraText.toString().trim()
        if (paragraphText.isNotEmpty()) {
            if (text.isNotEmpty()) {
                text.append('\n')
                allNodes += DynamicTextNode(DynamicTextNodeKind.TEXT, "\n")
            }
            text.append(paragraphText)
            allNodes += paraNodes
        }
    }
    return OpusBlocks(text.toString(), allNodes, images)
}

internal fun List<DynamicRichTextNode>.toDynamicTextNodes(dynamicId: String = ""): List<DynamicTextNode> =
    mapNotNull { raw ->
        val node = raw.rich ?: raw
        mapRichNode(
            type = node.type.ifBlank { raw.type },
            text = node.text.ifBlank { raw.text },
            origText = node.origText.ifBlank { raw.origText },
            rid = node.rid.ifBlank { raw.rid },
            jumpUrl = node.jumpUrl.ifBlank { raw.jumpUrl },
            emojiText = node.emoji?.text.orEmpty(),
            dynamicId = dynamicId,
        )
    }

internal fun normalizeDynamicJumpUrl(raw: String): String {
    val value = raw.trim()
    return when {
        value.isEmpty() -> ""
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
        else -> ""
    }
}

private fun mapRichNode(
    type: String,
    text: String,
    origText: String,
    rid: String,
    jumpUrl: String,
    emojiText: String,
    dynamicId: String,
): DynamicTextNode? {
    val display = text.ifBlank { origText }.ifBlank { emojiText }
    return when {
        type.contains("LOTTERY", ignoreCase = true) -> {
            val url = normalizeDynamicJumpUrl(jumpUrl).ifBlank {
                val cleanRid = rid.trim()
                val cleanDyn = dynamicId.trim()
                when {
                    cleanDyn.isNotEmpty() && cleanRid.isNotEmpty() ->
                        "https://www.bilibili.com/h5/lottery/result?business_id=$cleanDyn&business_type=1&lottery_id=$cleanRid"
                    cleanDyn.isNotEmpty() ->
                        "https://www.bilibili.com/h5/lottery/result?business_id=$cleanDyn&business_type=1"
                    cleanRid.isNotEmpty() ->
                        "https://www.bilibili.com/h5/lottery/result?lottery_id=$cleanRid&business_type=1"
                    else -> ""
                }
            }
            DynamicTextNode(
                kind = DynamicTextNodeKind.LOTTERY,
                text = display.ifBlank { "互动抽奖" },
                target = url,
            )
        }

        type.contains("WEB", ignoreCase = true) -> {
            val url = normalizeDynamicJumpUrl(jumpUrl.ifBlank { origText })
            if (url.isEmpty() && display.isEmpty()) return null
            DynamicTextNode(
                kind = DynamicTextNodeKind.WEB,
                text = "网页链接",
                target = url,
            )
        }

        type.contains("EMOJI", ignoreCase = true) -> {
            if (display.isEmpty()) null else DynamicTextNode(DynamicTextNodeKind.EMOJI, display)
        }

        type.contains("AT", ignoreCase = true) -> {
            if (display.isEmpty()) null else DynamicTextNode(DynamicTextNodeKind.AT, display, rid.trim())
        }

        type.contains("TOPIC", ignoreCase = true) -> {
            if (display.isEmpty()) return null
            val query = display.trim().trim('#')
            DynamicTextNode(
                kind = DynamicTextNodeKind.TOPIC,
                text = display,
                target = query.ifBlank { normalizeDynamicJumpUrl(jumpUrl) },
            )
        }

        type.endsWith("_BV", ignoreCase = true) ||
            type.endsWith("_AV", ignoreCase = true) ||
            type.contains("VIDEO", ignoreCase = true) -> {
            val bvid = RICH_BVID_REGEX.find(rid)?.value
                ?: RICH_BVID_REGEX.find(jumpUrl)?.value
                ?: RICH_BVID_REGEX.find(origText)?.value
            when {
                bvid != null -> DynamicTextNode(DynamicTextNodeKind.VIDEO, display.ifBlank { bvid }, bvid)
                normalizeDynamicJumpUrl(jumpUrl).isNotEmpty() && display.isNotEmpty() ->
                    DynamicTextNode(DynamicTextNodeKind.LINK, display, normalizeDynamicJumpUrl(jumpUrl))
                display.isNotEmpty() -> DynamicTextNode(DynamicTextNodeKind.TEXT, display)
                else -> null
            }
        }

        else -> {
            val url = normalizeDynamicJumpUrl(jumpUrl)
            when {
                url.isNotEmpty() && display.isNotEmpty() ->
                    DynamicTextNode(DynamicTextNodeKind.LINK, display, url)
                display.isNotEmpty() -> DynamicTextNode(DynamicTextNodeKind.TEXT, display)
                else -> null
            }
        }
    }
}

private fun JsonObject.jsonText(key: String): String {
    val value = this[key] as? JsonPrimitive ?: return ""
    if (value is JsonNull) return ""
    return value.content.takeIf { it != "null" }.orEmpty()
}

private fun JsonObject.jsonInt(key: String): Int =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
