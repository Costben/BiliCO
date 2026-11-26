package erl.bilico.data.repository

import erl.bilico.data.model.SponsorActionType
import erl.bilico.data.model.SponsorCategory
import erl.bilico.data.model.SponsorSegment
import erl.bilico.data.network.BiliClient
import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
    private val http: HttpClient,
) {
    suspend fun skipSegments(
        bvid: String,
        cid: Long,
        categories: Collection<SponsorCategory> = SponsorCategory.entries,
        bypassCache: Boolean = false,
    ): Result<List<SponsorSegment>> = withContext(ioDispatcher) {
        runCatching {
            if (bvid.isBlank() || cid <= 0L || categories.isEmpty()) return@runCatching emptyList()
            val categoriesJson = categories.joinToString(prefix = "[", postfix = "]") { "\"${it.id}\"" }
            val response = http.get(ENDPOINT) {
                parameter("videoID", bvid)
                parameter("cid", cid.toString())
                parameter("categories", categoriesJson)
                parameter("actionTypes", """["skip","mute","poi","full"]""")
                header("Origin", "bilibili-sponsor-block")
                header("x-ext-version", "0.5.0")
                if (bypassCache) header("x-skip-cache", "1")
            }
            if (response.status.value == 404) return@runCatching emptyList()
            if (!response.status.isSuccess()) {
                throw IllegalStateException("SponsorBlock HTTP ${response.status.value}")
            }
            val body = response.bodyAsText().trim()
            if (body.isEmpty() || body == "[]") return@runCatching emptyList()
            val rawList = BiliClient.json.decodeFromString(
                ListSerializer(RawSponsorSegment.serializer()),
                body,
            )
            rawList.mapNotNull { raw -> raw.toModel(cid) }.sortedBy { it.startMs }
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
    ): Result<List<SponsorSegment>> = withContext(ioDispatcher) {
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
            val response = http.post(ENDPOINT) {
                header("Origin", "bilibili-sponsor-block")
                header("x-ext-version", "0.5.0")
                contentType(ContentType.Application.Json)
                setBody(bodyJson)
            }
            val rawBody = response.bodyAsText().trim()
            if (!response.status.isSuccess()) {
                val hint = when (response.status.value) {
                    400 -> "参数无效，请检查起止时间与片段类型"
                    403 -> "当前提交标识暂不可用"
                    409 -> "该时段已存在相似片段，请勿重复提交"
                    429 -> "提交过于频繁，请稍后再试"
                    else -> "提交失败（HTTP ${response.status.value}）"
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
                        uuid = "local-${currentTimeMillis()}",
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

    private fun Double.round3(): Double = kotlin.math.round(this * 1000.0) / 1000.0

    private fun RawSponsorSegment.toModel(fallbackCid: Long): SponsorSegment? {
        val cat = SponsorCategory.fromId(category) ?: return null
        val action = SponsorActionType.fromId(actionType)
        if (action == SponsorActionType.Unknown) return null
        if (segment.size < 2) return null
        val startMs = (segment[0] * 1000.0).roundToLong().coerceAtLeast(0L)
        val endMs = (segment[1] * 1000.0).roundToLong().coerceAtLeast(startMs)
        if (action != SponsorActionType.Poi &&
            action != SponsorActionType.Full &&
            endMs - startMs < MIN_SEGMENT_MS
        ) {
            return null
        }
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
    }
}
