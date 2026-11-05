package erl.bilico.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** B 站同一字段有时给数字有时给字符串，统一读出文本。 */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        if (decoder !is JsonDecoder) return decoder.decodeString()
        val element = decoder.decodeJsonElement()
        return (element as? JsonPrimitive)?.contentOrNull ?: element.toString()
    }
}

object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
    override fun deserialize(decoder: Decoder): Long {
        if (decoder !is JsonDecoder) return decoder.decodeLong()
        val element = decoder.decodeJsonElement()
        return (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
    }
}

object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val element = decoder.decodeJsonElement()
        return (element as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
    }
}

/** 评论正文里的 HTML 实体在解析阶段统一反转义。 */
object UnescapedStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("UnescapedString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String = unescapeHtml(decoder.decodeString())
}

internal object HtmlEntityUtils {
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    )
    private val entityRegex = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);?")

    fun unescape(input: String): String {
        if (input.isEmpty() || '&' !in input) return input
        return entityRegex.replace(input) { match ->
            val token = match.groupValues[1]
            decode(token) ?: match.value
        }
    }

    private fun decode(token: String): String? {
        named[token]?.let { return it }
        return when {
            token.startsWith("#x") || token.startsWith("#X") ->
                token.removePrefix("#x").removePrefix("#X").toIntOrNull(16)
                    ?.takeIf { it in 1..0x10FFFF }
                    ?.let(::codePoint)

            token.startsWith("#") ->
                token.removePrefix("#").toIntOrNull()
                    ?.takeIf { it in 1..0x10FFFF }
                    ?.let(::codePoint)

            else -> null
        }
    }

    private fun codePoint(codePoint: Int): String =
        if (codePoint <= Char.MAX_VALUE.code) Char(codePoint).toString()
        else String(Character.toChars(codePoint))
}

internal fun unescapeHtml(text: String): String = HtmlEntityUtils.unescape(text)
