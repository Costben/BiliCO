package erl.bilico.data

import erl.bilico.platform.KeyValueStore
import erl.bilico.platform.PlatformContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class PrefKey<T>(val name: String)
class IntPrefKey(name: String) : PrefKey<Int>(name)
class LongPrefKey(name: String) : PrefKey<Long>(name)
class FloatPrefKey(name: String) : PrefKey<Float>(name)
class BooleanPrefKey(name: String) : PrefKey<Boolean>(name)
class StringPrefKey(name: String) : PrefKey<String>(name)

fun intPreferencesKey(name: String): PrefKey<Int> = IntPrefKey(name)
fun longPreferencesKey(name: String): PrefKey<Long> = LongPrefKey(name)
fun floatPreferencesKey(name: String): PrefKey<Float> = FloatPrefKey(name)
fun booleanPreferencesKey(name: String): PrefKey<Boolean> = BooleanPrefKey(name)
fun stringPreferencesKey(name: String): PrefKey<String> = StringPrefKey(name)

/**
 * Lightweight KMP DataStore<Preferences> backed by [KeyValueStore] ("bilico"),
 * preserving all existing NSUserDefaults keys across upgrades.
 */
class PreferencesSnapshot internal constructor(
    private val store: KeyValueStore,
    private val version: Long,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: PrefKey<T>): T? {
        if (!store.contains(key.name)) return null
        return when (key) {
            is IntPrefKey -> store.getInt(key.name, 0) as T
            is LongPrefKey -> store.getLong(key.name, 0L) as T
            is FloatPrefKey -> store.getFloat(key.name, 0f) as T
            is BooleanPrefKey -> store.getBoolean(key.name, false) as T
            is StringPrefKey -> store.getString(key.name) as T
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PreferencesSnapshot && other.version == version

    override fun hashCode(): Int = version.hashCode()
}

class MutablePreferences internal constructor(
    private val store: KeyValueStore,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: PrefKey<T>): T? {
        if (!store.contains(key.name)) return null
        return when (key) {
            is IntPrefKey -> store.getInt(key.name, 0) as T
            is LongPrefKey -> store.getLong(key.name, 0L) as T
            is FloatPrefKey -> store.getFloat(key.name, 0f) as T
            is BooleanPrefKey -> store.getBoolean(key.name, false) as T
            is StringPrefKey -> store.getString(key.name) as T
        }
    }

    operator fun <T> set(key: PrefKey<T>, value: T) {
        when (key) {
            is IntPrefKey -> store.putInt(key.name, value as Int)
            is LongPrefKey -> store.putLong(key.name, value as Long)
            is FloatPrefKey -> store.putFloat(key.name, value as Float)
            is BooleanPrefKey -> store.putBoolean(key.name, value as Boolean)
            is StringPrefKey -> store.putString(key.name, value as String)
        }
    }

    fun <T> remove(key: PrefKey<T>) {
        store.remove(key.name)
    }
}

class PlatformDataStore internal constructor(
    private val store: KeyValueStore,
) {
    private val mutex = Mutex()
    private var version = 0L
    private val _data = MutableStateFlow(PreferencesSnapshot(store, version))
    val data: Flow<PreferencesSnapshot> = _data.asStateFlow()

    suspend fun edit(transform: suspend (MutablePreferences) -> Unit): PreferencesSnapshot =
        mutex.withLock {
            transform(MutablePreferences(store))
            version++
            val snap = PreferencesSnapshot(store, version)
            _data.value = snap
            snap
        }
}

private var sharedBiliDataStore: PlatformDataStore? = null

val PlatformContext.biliDataStore: PlatformDataStore
    get() = sharedBiliDataStore ?: PlatformDataStore(KeyValueStore(this, "bilico")).also {
        sharedBiliDataStore = it
    }
