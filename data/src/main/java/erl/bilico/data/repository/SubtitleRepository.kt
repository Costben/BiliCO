package erl.bilico.data.repository

import erl.bilico.data.model.PlayerAuxiliaryInfo
import erl.bilico.data.model.SubtitleCue
import erl.bilico.data.model.SubtitleRawItem
import erl.bilico.data.model.SubtitleTrack
import erl.bilico.data.network.BiliApi
import erl.bilico.data.network.BiliClient
import erl.bilico.data.network.BuvidManager
import erl.bilico.data.network.WbiKeyManager
import erl.bilico.data.network.WbiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

class SubtitleRepository(
    private val api: BiliApi,
    private val wbi: WbiKeyManager,
    private val buvid: BuvidManager,
    private val okHttp: OkHttpClient,
) {
    suspend fun fetchPlayerInfo(
        bvid: String = "",
        aid: Long = 0L,
        cid: Long,
    ): Result<PlayerAuxiliaryInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(cid > 0L && (bvid.isNotBlank() || aid > 0L)) { "缺少视频标识" }
            buvid.ensure()
            val baseParams = buildMap {
                if (bvid.isNotBlank()) put("bvid", bvid)
                if (aid > 0L) put("aid", aid.toString())
                put("cid", cid.toString())
            }
            val keys = runCatching { wbi.getKeys() }.getOrNull()
            val wbiResp = if (keys != null) {
                val signed = WbiUtils.sign(baseParams, keys.first, keys.second)
                runCatching { api.playerInfo(signed) }.getOrNull()
            } else {
                null
            }
            val data = if (wbiResp != null && wbiResp.code == 0 && wbiResp.data != null) {
                wbiResp.data
            } else {
                val legacy = api.playerInfoLegacy(baseParams)
                if (legacy.code != 0 || legacy.data == null) {
                    error(legacy.message.ifBlank { "播放器信息获取失败(${legacy.code})" })
                }
                legacy.data
            }
            val tracks = data.subtitle?.subtitles.orEmpty()
                .mapNotNull(::toSubtitleTrack)
                .distinctBy { it.key }
                .sortedWith(
                    compareBy<SubtitleTrack> { it.isAi }
                        .thenByDescending { it.lan.removePrefix("ai-").startsWith("zh", ignoreCase = true) },
                )
            PlayerAuxiliaryInfo(
                lastPlayTimeMs = data.lastPlayTime.coerceAtLeast(0L),
                lastPlayCid = data.lastPlayCid.coerceAtLeast(0L),
                subtitles = tracks,
            )
        }
    }

    suspend fun fetchCues(url: String): Result<List<SubtitleCue>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeSubtitleUrl(url)
            require(normalized.isNotBlank()) { "字幕地址为空" }
            val request = Request.Builder().url(normalized).get().build()
            val raw = okHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("字幕加载失败(${resp.code})")
                resp.body.string()
            }
            parseSubtitleBody(raw)
        }
    }

    private fun toSubtitleTrack(item: SubtitleRawItem): SubtitleTrack? {
        val url = normalizeSubtitleUrl(item.subtitleUrl.ifBlank { item.subtitleUrlV2 })
        if (url.isBlank()) return null
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        if (!host.endsWith(".hdslb.com") && host != "hdslb.com" &&
            !host.endsWith(".bilibili.com") && host != "bilibili.com"
        ) {
            return null
        }
        val baseLabel = item.lanDoc.trim().ifBlank { item.lan.trim() }.ifBlank { "字幕" }
        val isAi = item.aiStatus > 0 || item.aiType > 0 ||
            item.lan.startsWith("ai-", ignoreCase = true) ||
            baseLabel.contains("自动") || baseLabel.contains("AI", ignoreCase = true)
        val label = if (isAi && !baseLabel.contains("AI", ignoreCase = true) && !baseLabel.contains("自动")) {
            "$baseLabel · AI"
        } else {
            baseLabel
        }
        val key = "${item.id}|${item.idStr}|${item.lan}|$url"
        return SubtitleTrack(
            key = key,
            lan = item.lan,
            label = label,
            url = url,
            isAi = isAi,
        )
    }

    private fun normalizeSubtitleUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
            else -> trimmed
        }
    }

    private fun parseSubtitleBody(raw: String): List<SubtitleCue> {
        if (raw.isBlank()) return emptyList()
        val root = BiliClient.json.parseToJsonElement(raw).jsonObject
        val body = root["body"] as? JsonArray ?: return emptyList()
        return body.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val from = (obj["from"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val to = (obj["to"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val content = (obj["content"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (content.isBlank() || !from.isFinite() || !to.isFinite() || to <= from) {
                return@mapNotNull null
            }
            val startMs = (from * 1000.0).toLong().coerceAtLeast(0L)
            val endMs = (to * 1000.0).toLong().coerceAtLeast(startMs)
            SubtitleCue(startMs = startMs, endMs = endMs, content = content)
        }.sortedBy { it.startMs }
    }
}
