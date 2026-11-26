package erl.bilico.platform

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.Collections

/**
 * 一次性将旧版 AndroidX DataStore (`files/datastore/<name>.preferences_pb`)
 * 迁移至 [SharedPreferences]，保留升级用户的登录态、多账号与偏好设置。
 */
internal object DataStoreMigration {
    const val DEFAULT_STORE_NAME = "bilico"
    const val SENTINEL_KEY = "__migrated_from_datastore_pb__"
    private const val TAG = "DataStoreMigration"

    private val lock = Any()
    private val attemptedNames: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun migrateIfNeeded(
        context: Context,
        prefs: SharedPreferences,
        name: String = DEFAULT_STORE_NAME,
    ) {
        if (name in attemptedNames) return
        synchronized(lock) {
            if (name in attemptedNames) return
            if (prefs.getBoolean(SENTINEL_KEY, false)) {
                attemptedNames.add(name)
                return
            }

            val pbFile = File(File(context.filesDir, "datastore"), "$name.preferences_pb")
            if (!pbFile.exists() || !pbFile.isFile) {
                prefs.edit().putBoolean(SENTINEL_KEY, true).commit()
                attemptedNames.add(name)
                return
            }

            try {
                val raw = pbFile.readBytes()
                val entries = PreferencesProtoDecoder.decode(raw)
                val editor = prefs.edit()
                var copiedCount = 0
                for ((key, value) in entries) {
                    if (key == SENTINEL_KEY || prefs.contains(key)) continue
                    if (applyValue(editor, key, value)) {
                        copiedCount++
                    }
                }
                editor.putBoolean(SENTINEL_KEY, true)
                val committed = editor.commit()
                if (committed) {
                    attemptedNames.add(name)
                    logDebug(TAG, "Migrated $copiedCount/${entries.size} keys from $name.preferences_pb")
                } else {
                    logWarn(TAG, "SharedPreferences.commit() returned false while migrating $name")
                }
            } catch (t: Throwable) {
                attemptedNames.add(name)
                logWarn(TAG, "Failed to migrate $name.preferences_pb", t)
            }
        }
    }

    private fun applyValue(
        editor: SharedPreferences.Editor,
        key: String,
        value: DataStorePrefValue,
    ): Boolean = when (value) {
        is DataStorePrefValue.BooleanValue -> {
            editor.putBoolean(key, value.value)
            true
        }
        is DataStorePrefValue.FloatValue -> {
            editor.putFloat(key, value.value)
            true
        }
        is DataStorePrefValue.IntValue -> {
            editor.putInt(key, value.value)
            true
        }
        is DataStorePrefValue.LongValue -> {
            editor.putLong(key, value.value)
            true
        }
        is DataStorePrefValue.StringValue -> {
            editor.putString(key, value.value)
            true
        }
        is DataStorePrefValue.StringSetValue -> {
            editor.putStringSet(key, LinkedHashSet(value.value))
            true
        }
        is DataStorePrefValue.DoubleValue -> {
            logWarn(TAG, "Skipping unsupported double preference key during migration: $key")
            false
        }
        is DataStorePrefValue.BytesValue -> {
            logWarn(TAG, "Skipping unsupported bytes preference key during migration: $key")
            false
        }
    }
}

internal sealed interface DataStorePrefValue {
    data class BooleanValue(val value: Boolean) : DataStorePrefValue
    data class FloatValue(val value: Float) : DataStorePrefValue
    data class IntValue(val value: Int) : DataStorePrefValue
    data class LongValue(val value: Long) : DataStorePrefValue
    data class StringValue(val value: String) : DataStorePrefValue
    data class StringSetValue(val value: Set<String>) : DataStorePrefValue
    data class DoubleValue(val value: Double) : DataStorePrefValue
    data class BytesValue(val value: ByteArray) : DataStorePrefValue {
        override fun equals(other: Any?): Boolean =
            this === other || (other is BytesValue && value.contentEquals(other.value))

        override fun hashCode(): Int = value.contentHashCode()
    }
}

/**
 * 零依赖纯 Kotlin Protobuf wire 解码器，用于解析 AndroidX DataStore `PreferencesProto`:
 *
 * ```protobuf
 * message PreferenceMap { map<string, Value> preferences = 1; }
 * message Value {
 *   oneof value {
 *     bool boolean = 1;
 *     float float = 2;
 *     int32 integer = 3;
 *     int64 long = 4;
 *     string string = 5;
 *     StringSet string_set = 6;
 *     double double = 7;
 *     bytes bytes = 8;
 *   }
 * }
 * message StringSet { repeated string strings = 1; }
 * ```
 */
internal object PreferencesProtoDecoder {
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_START_GROUP = 3
    private const val WIRE_END_GROUP = 4
    private const val WIRE_FIXED32 = 5
    private const val MAX_GROUP_DEPTH = 32

    fun decode(bytes: ByteArray): Map<String, DataStorePrefValue> {
        if (bytes.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, DataStorePrefValue>()
        val reader = WireReader(bytes, 0, bytes.size)
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            val fieldNumber = tag.fieldNumber
            val wireType = tag.wireType
            if (fieldNumber == 1 && wireType == WIRE_LENGTH_DELIMITED) {
                val entrySlice = reader.readLengthDelimitedSlice()
                decodeMapEntry(entrySlice)?.let { (k, v) -> result[k] = v }
            } else {
                reader.skipField(fieldNumber, wireType, 0)
            }
        }
        return result
    }

    private fun decodeMapEntry(reader: WireReader): Pair<String, DataStorePrefValue>? {
        var key = ""
        var value: DataStorePrefValue? = null
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            when {
                tag.fieldNumber == 1 && tag.wireType == WIRE_LENGTH_DELIMITED -> {
                    key = reader.readString()
                }
                tag.fieldNumber == 2 && tag.wireType == WIRE_LENGTH_DELIMITED -> {
                    val valueSlice = reader.readLengthDelimitedSlice()
                    value = decodeValue(valueSlice)
                }
                else -> reader.skipField(tag.fieldNumber, tag.wireType, 0)
            }
        }
        return value?.let { key to it }
    }

    private fun decodeValue(reader: WireReader): DataStorePrefValue? {
        var result: DataStorePrefValue? = null
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            when {
                tag.fieldNumber == 1 && tag.wireType == WIRE_VARINT -> {
                    result = DataStorePrefValue.BooleanValue(reader.readVarint64() != 0L)
                }
                tag.fieldNumber == 2 && tag.wireType == WIRE_FIXED32 -> {
                    result = DataStorePrefValue.FloatValue(Float.fromBits(reader.readFixed32()))
                }
                tag.fieldNumber == 3 && tag.wireType == WIRE_VARINT -> {
                    result = DataStorePrefValue.IntValue(reader.readVarint64().toInt())
                }
                tag.fieldNumber == 4 && tag.wireType == WIRE_VARINT -> {
                    result = DataStorePrefValue.LongValue(reader.readVarint64())
                }
                tag.fieldNumber == 5 && tag.wireType == WIRE_LENGTH_DELIMITED -> {
                    result = DataStorePrefValue.StringValue(reader.readString())
                }
                tag.fieldNumber == 6 && tag.wireType == WIRE_LENGTH_DELIMITED -> {
                    val setSlice = reader.readLengthDelimitedSlice()
                    result = DataStorePrefValue.StringSetValue(decodeStringSet(setSlice))
                }
                tag.fieldNumber == 7 && tag.wireType == WIRE_FIXED64 -> {
                    result = DataStorePrefValue.DoubleValue(Double.fromBits(reader.readFixed64()))
                }
                tag.fieldNumber == 8 && tag.wireType == WIRE_LENGTH_DELIMITED -> {
                    result = DataStorePrefValue.BytesValue(reader.readBytes())
                }
                else -> reader.skipField(tag.fieldNumber, tag.wireType, 0)
            }
        }
        return result
    }

    private fun decodeStringSet(reader: WireReader): Set<String> {
        val out = LinkedHashSet<String>()
        while (!reader.isAtEnd()) {
            val tag = reader.readTag()
            if (tag.fieldNumber == 1 && tag.wireType == WIRE_LENGTH_DELIMITED) {
                out.add(reader.readString())
            } else {
                reader.skipField(tag.fieldNumber, tag.wireType, 0)
            }
        }
        return out
    }

    private data class WireTag(val fieldNumber: Int, val wireType: Int)

    private class WireReader(
        private val data: ByteArray,
        private var pos: Int,
        private val limit: Int,
    ) {
        fun isAtEnd(): Boolean = pos >= limit

        fun readTag(): WireTag {
            val raw = readVarint64()
            if (raw <= 0L || raw > 0xFFFF_FFFFL) {
                throw IllegalArgumentException("Invalid protobuf tag: $raw")
            }
            val wireType = (raw and 0x7L).toInt()
            val fieldNumber = (raw ushr 3).toInt()
            if (fieldNumber <= 0) {
                throw IllegalArgumentException("Invalid protobuf field number: $fieldNumber")
            }
            return WireTag(fieldNumber, wireType)
        }

        fun readVarint64(): Long {
            var result = 0L
            var shift = 0
            while (shift < 64) {
                if (pos >= limit) {
                    throw IllegalArgumentException("Unexpected EOF while reading protobuf varint")
                }
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) {
                    return result
                }
                shift += 7
            }
            throw IllegalArgumentException("Malformed protobuf varint (> 10 bytes)")
        }

        fun readFixed32(): Int {
            if (limit - pos < 4) {
                throw IllegalArgumentException("Unexpected EOF while reading protobuf fixed32")
            }
            val b0 = data[pos++].toInt() and 0xFF
            val b1 = data[pos++].toInt() and 0xFF
            val b2 = data[pos++].toInt() and 0xFF
            val b3 = data[pos++].toInt() and 0xFF
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        fun readFixed64(): Long {
            if (limit - pos < 8) {
                throw IllegalArgumentException("Unexpected EOF while reading protobuf fixed64")
            }
            val b0 = data[pos++].toLong() and 0xFFL
            val b1 = data[pos++].toLong() and 0xFFL
            val b2 = data[pos++].toLong() and 0xFFL
            val b3 = data[pos++].toLong() and 0xFFL
            val b4 = data[pos++].toLong() and 0xFFL
            val b5 = data[pos++].toLong() and 0xFFL
            val b6 = data[pos++].toLong() and 0xFFL
            val b7 = data[pos++].toLong() and 0xFFL
            return b0 or
                (b1 shl 8) or
                (b2 shl 16) or
                (b3 shl 24) or
                (b4 shl 32) or
                (b5 shl 40) or
                (b6 shl 48) or
                (b7 shl 56)
        }

        fun readLengthDelimitedSlice(): WireReader {
            val len = readLength()
            val start = pos
            pos += len
            return WireReader(data, start, pos)
        }

        fun readString(): String {
            val len = readLength()
            val start = pos
            pos += len
            return data.decodeToString(startIndex = start, endIndex = pos)
        }

        fun readBytes(): ByteArray {
            val len = readLength()
            val start = pos
            pos += len
            return data.copyOfRange(start, pos)
        }

        fun skipField(fieldNumber: Int, wireType: Int, depth: Int) {
            when (wireType) {
                WIRE_VARINT -> readVarint64()
                WIRE_FIXED64 -> skipBytes(8)
                WIRE_LENGTH_DELIMITED -> skipBytes(readLength())
                WIRE_START_GROUP -> skipGroup(fieldNumber, depth + 1)
                WIRE_FIXED32 -> skipBytes(4)
                else -> throw IllegalArgumentException("Unsupported protobuf wire type: $wireType")
            }
        }

        private fun skipGroup(expectedFieldNumber: Int, depth: Int) {
            if (depth > MAX_GROUP_DEPTH) {
                throw IllegalArgumentException("Protobuf group nesting limit exceeded")
            }
            while (!isAtEnd()) {
                val tag = readTag()
                if (tag.wireType == WIRE_END_GROUP) {
                    if (tag.fieldNumber != expectedFieldNumber) {
                        throw IllegalArgumentException(
                            "Mismatched end-group field ${tag.fieldNumber}, expected $expectedFieldNumber",
                        )
                    }
                    return
                }
                skipField(tag.fieldNumber, tag.wireType, depth)
            }
            throw IllegalArgumentException("Unexpected EOF inside protobuf group $expectedFieldNumber")
        }

        private fun readLength(): Int {
            val raw = readVarint64()
            if (raw < 0L || raw > (limit - pos).toLong()) {
                throw IllegalArgumentException(
                    "Invalid protobuf length-delimited size: $raw (remaining=${limit - pos})",
                )
            }
            return raw.toInt()
        }

        private fun skipBytes(count: Int) {
            if (count < 0 || count > limit - pos) {
                throw IllegalArgumentException("Unexpected EOF while skipping $count protobuf bytes")
            }
            pos += count
        }
    }
}
