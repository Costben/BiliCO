package erl.bilico.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import erl.bilico.data.biliDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

/** 多账号名册，落在同一个 DataStore 里的一份 JSON 列表。 */
class AccountStore(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): List<StoredSession> {
        val raw = context.biliDataStore.data.first()[KEY_ACCOUNTS] ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredSession>>(raw)
        }.getOrDefault(emptyList())
    }

    fun write(sessions: List<StoredSession>) {
        val encoded = json.encodeToString(sessions)
        scope.launch(Dispatchers.IO) {
            context.biliDataStore.edit { it[KEY_ACCOUNTS] = encoded }
        }
    }

    private companion object {
        val KEY_ACCOUNTS = stringPreferencesKey("accounts")
    }
}
