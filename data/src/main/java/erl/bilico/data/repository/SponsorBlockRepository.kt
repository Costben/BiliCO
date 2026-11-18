package erl.bilico.data.repository

import erl.bilico.data.model.SponsorActionType
import erl.bilico.data.model.SponsorCategory
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.network.BiliClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

@Serializable
private data class RawSponsorSegment(
    @SerialName("UUID") val uuid: String = "",
    val cid: String = "",
    val category: String = "",
    val actionType: String = "",
    val segment: List<Double> = emptyList(),
    val videoDuration: Double = 0.0,
    val votes: Int = 0,
    val description: String = "",
)

@Serializable
private data class SubmitSegmentEntry(
    val segment: List<Double>,
    val category: String,
    val actionType: String,
)

@Serializable
private data class SubmitSegmentPayload(
    val videoID: String,
    val cid: String,
    val userID: String,
    val userAgent: String,
    val videoDuration: Double,
    val segments: List<SubmitSegmentEntry>,
)

/**
 * BiliSponsorBlock（空降助手）客户端：对接 `bsbsb.top/api/skipSegments` 查询与提交片段。
 */
class SponsorBlockRepository(
    baseOkHttp: OkHttpClient,
) {
    private val httpClient: OkHttpClient = baseOkHttp.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun skipSegments(
        bvid: String,
        cid: Long,
        categories: Collection<SponsorCategory> = SponsorCategory.entries,
        bypassCache: Boolean = false,
    ): Result<List<SponsorSegment>> = withContext(Dispatchers.IO) {
        runCatching {
            if (bvid.isBlank() || cid <= 0L || categories.isEmpty()) return@runCatching emptyList()
            val categoriesJson = categories.joinToString(prefix = "[", postfix = "]") { "\"${it.id}\"" }
            val url = ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("videoID", bvid)
                .addQueryParameter("cid", cid.toString())
                .addQueryParameter("categories", categoriesJson)
                .addQueryParameter("actionTypes", """["skip","mute","poi"]""")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Origin", "bilibili-sponsor-block")
                .header("x-ext-version", "0.5.0")
                .apply { if (bypassCache) header("x-skip-cache", "1") }
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) return@use emptyList()
                if (!response.isSuccessful) {
                    throw IllegalStateException("SponsorBlock HTTP ${response.code}")
                }
                val body = response.body?.string()?.trim().orEmpty()
                if (body.isEmpty() || body == "[]") return@use emptyList()
                val rawList = BiliClient.json.decodeFromString(
                    ListSerializer(RawSponsorSegment.serializer()),
                    body,
                )
                rawList.mapNotNull { raw -> raw.toModel(cid) }
                    .sortedBy { it.startMs }
            }
        }
    }

    suspend fun submitSegment(
        bvid: String,
        cid: Long,
        videoDurationMs: Long,
        startMs: Long,
        endMs: Long,
        category: SponsorCategory,
        actionType: SponsorActionType,
        userId: String,
        userAgent: String,
    ): Result<List<SponsorSegment>> = withContext(Dispatchers.IO) {
        runCatching {
            require(bvid.isNotBlank() && cid > 0L) { "缺少视频标识，无法提交" }
            require(userId.length >= 30) { "本地提交标识无效" }
            val resolvedEndMs = if (actionType == SponsorActionType.Poi) startMs else endMs
            if (actionType != SponsorActionType.Poi) {
                require(resolvedEndMs - startMs >= MIN_SEGMENT_MS) { "片段长度至少 0.5 秒" }
            }
            val startSec = (startMs.coerceAtLeast(0L) / 1000.0).round3()
            val endSec = (resolvedEndMs.coerceAtLeast(startMs) / 1000.0).round3()
            val durationSec = (videoDurationMs.coerceAtLeast(0L) / 1000.0).round3()
            val payload = SubmitSegmentPayload(
                videoID = bvid,
                cid = cid.toString(),
                userID = userId,
                userAgent = userAgent,
                videoDuration = durationSec,
                segments = listOf(
                    SubmitSegmentEntry(
                        segment = listOf(startSec, endSec),
                        category = category.id,
                        actionType = actionType.id,
                    ),
                ),
            )
            val bodyJson = BiliClient.json.encodeToString(SubmitSegmentPayload.serializer(), payload)
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Origin", "bilibili-sponsor-block")
                .header("x-ext-version", "0.5.0")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string()?.trim().orEmpty()
                if (!response.isSuccessful) {
                    val hint = when (response.code) {
                        400 -> "参数无效，请检查起止时间与片段类型"
                        403 -> "当前提交标识暂不可用"
                        409 -> "该时段已存在相似片段，请勿重复提交"
                        429 -> "提交过于频繁，请稍后再试"
                        else -> "提交失败（HTTP ${response.code}）"
                    }
                    val detail = rawBody.takeIf { it.isNotEmpty() && !it.startsWith("<") }
                    throw IllegalStateException(if (detail != null) "$hint：$detail" else hint)
                }
                val parsed = runCatching {
                    if (rawBody.startsWith("[")) {
                        BiliClient.json.decodeFromString(
                            ListSerializer(RawSponsorSegment.serializer()),
                            rawBody,
                        ).mapNotNull { it.toModel(cid) }
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())
                parsed.ifEmpty {
                    listOf(
                        SponsorSegment(
                            uuid = "local-${System.currentTimeMillis()}",
                            cid = cid,
                            category = category,
                            actionType = actionType,
                            startMs = startMs,
                            endMs = resolvedEndMs,
                            videoDurationMs = videoDurationMs,
                            votes = 0,
                            description = "",
                        ),
                    )
                }
            }
        }
    }

    private fun Double.round3(): Double = kotlin.math.round(this * 1000.0) / 1000.0

    private fun RawSponsorSegment.toModel(fallbackCid: Long): SponsorSegment? {
        val cat = SponsorCategory.fromId(category) ?: return null
        val action = SponsorActionType.fromId(actionType)
        if (action == SponsorActionType.Unknown || action == SponsorActionType.Full) return null
        if (segment.size < 2) return null
        val startMs = (segment[0] * 1000.0).roundToLong().coerceAtLeast(0L)
        val endMs = (segment[1] * 1000.0).roundToLong().coerceAtLeast(startMs)
        if (action != SponsorActionType.Poi && endMs - startMs < MIN_SEGMENT_MS) return null
        return SponsorSegment(
            uuid = uuid,
            cid = cid.toLongOrNull() ?: fallbackCid,
            category = cat,
            actionType = action,
            startMs = startMs,
            endMs = endMs,
            videoDurationMs = (videoDuration * 1000.0).roundToLong().coerceAtLeast(0L),
            votes = votes,
            description = description,
        )
    }

    private companion object {
        const val ENDPOINT = "https://bsbsb.top/api/skipSegments"
        const val MIN_SEGMENT_MS = 500L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
