package erl.bilico.data.network

import erl.bilico.data.session.CookieStore
import erl.bilico.platform.encodeBase64
import erl.bilico.platform.randomUuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * 设备标识：buvid3 跟随 Cookie 保持稳定，首次生成后随 Cookie 一起落盘。
 * 生成后尝试向设备指纹接口上报一次，失败不影响正常请求。
 */
class BuvidManager(
    private val cookies: CookieStore,
    private val api: () -> BiliApi,
) {
    private val mutex = Mutex()
    private var activated = false

    suspend fun ensure() = mutex.withLock {
        if (activated) return@withLock
        if (cookies.get("buvid3").isNullOrBlank()) {
            cookies.put("buvid3", randomUuid().uppercase() + "infoc")
        }
        if (cookies.get("buvid4").isNullOrBlank()) {
            cookies.put("buvid4", buildBuvid4() + "infoc")
        }
        activated = true
        try {
            api().activateBuvid(mapOf("payload" to buildActivationPayload()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 指纹上报是尽力而为，失败不阻塞内容请求。
        }
    }

    private fun buildBuvid4(): String {
        val raw = randomUuid().uppercase().replace("-", "")
        return "${raw.take(8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20, 32)}"
    }

    private fun buildActivationPayload(): String {
        val random = Random
        val bytes = ByteArray(32) { random.nextInt(256).toByte() } +
            byteArrayOf(0, 0, 0, 0, 73, 69, 78, 68) +
            ByteArray(4) { random.nextInt(256).toByte() }
        val pngTail = encodeBase64(bytes).takeLast(50)
        return buildJsonObject {
            put("3064", 1)
            put("39c8", "333.1387.fp.risk")
            put("3c43", buildJsonObject {
                put("adca", "Linux")
                put("bfe9", pngTail)
            })
        }.toString()
    }
}
