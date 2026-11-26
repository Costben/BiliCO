package erl.bilico.data.session

import erl.bilico.platform.KeyValueStore
import erl.bilico.platform.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 已登录过的一个账号：画像加上它那份 Cookie，切回来时整份塞回 [CookieStore]。 */
@Serializable
data class StoredSession(
    val mid: Long,
    val name: String,
    val face: String,
    val cookies: Map<String, String> = emptyMap(),
    val savedAt: Long = 0L,
)

/** 多账号名册，落在 KeyValueStore 里的一份 JSON 列表。 */
class AccountStore(
    context: PlatformContext,
    private val scope: CoroutineScope,
) {
    private val store = KeyValueStore(context, "bilico")
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): List<StoredSession> {
        val raw = store.getString(KEY_ACCOUNTS) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredSession>>(raw)
        }.getOrDefault(emptyList())
    }

    fun write(sessions: List<StoredSession>) {
        val encoded = json.encodeToString(sessions)
        scope.launch(erl.bilico.platform.ioDispatcher) {
            store.putString(KEY_ACCOUNTS, encoded)
        }
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
    }
}
