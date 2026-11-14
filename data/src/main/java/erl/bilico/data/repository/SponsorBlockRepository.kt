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
import okhttp3.OkHttpClient
import okhttp3.Request
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

/**
 * BiliSponsorBlock（空降助手）客户端：无凭据请求 `bsbsb.top/api/skipSegments`。
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
    }
}
