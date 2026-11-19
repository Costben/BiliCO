package erl.bilico.feature.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

internal class DlnaSoapClient(
    private val http: OkHttpClient,
) {
    suspend fun setAvTransportUri(
        device: DlnaDevice,
        uri: String,
        title: String,
    ): Result<Unit> = soapCall(
        controlUrl = device.avTransportControlUrl,
        action = "SetAVTransportURI",
        body = buildString {
            val escapedUri = escapeXml(uri)
            val escapedTitle = escapeXml(title.ifBlank { "BiliCO Video" })
            val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>$escapedTitle</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:video/mp4:*">$escapedUri</res></item></DIDL-Lite>"""
            append("<u:SetAVTransportURI xmlns:u=\"$AV_TRANSPORT_SERVICE\">")
            append("<InstanceID>0</InstanceID>")
            append("<CurrentURI>$escapedUri</CurrentURI>")
            append("<CurrentURIMetaData>${escapeXml(didl)}</CurrentURIMetaData>")
            append("</u:SetAVTransportURI>")
        },
    ).map { }

    suspend fun play(device: DlnaDevice): Result<Unit> = soapCall(
        controlUrl = device.avTransportControlUrl,
        action = "Play",
        body = "<u:Play xmlns:u=\"$AV_TRANSPORT_SERVICE\"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play>",
    ).map { }

    suspend fun pause(device: DlnaDevice): Result<Unit> = soapCall(
        controlUrl = device.avTransportControlUrl,
        action = "Pause",
        body = "<u:Pause xmlns:u=\"$AV_TRANSPORT_SERVICE\"><InstanceID>0</InstanceID></u:Pause>",
    ).map { }

    suspend fun stop(device: DlnaDevice): Result<Unit> = soapCall(
        controlUrl = device.avTransportControlUrl,
        action = "Stop",
        body = "<u:Stop xmlns:u=\"$AV_TRANSPORT_SERVICE\"><InstanceID>0</InstanceID></u:Stop>",
    ).map { }

    suspend fun seek(device: DlnaDevice, positionMs: Long): Result<Unit> = soapCall(
        controlUrl = device.avTransportControlUrl,
        action = "Seek",
        body = buildString {
            append("<u:Seek xmlns:u=\"$AV_TRANSPORT_SERVICE\">")
            append("<InstanceID>0</InstanceID>")
            append("<Unit>REL_TIME</Unit>")
            append("<Target>${formatTimeHms(positionMs)}</Target>")
            append("</u:Seek>")
        },
    ).map { }

    suspend fun getPositionInfo(device: DlnaDevice): Result<DlnaPosition> = soapCall(
        controlUrl = device.avTransportControlUrl,
        action = "GetPositionInfo",
        body = "<u:GetPositionInfo xmlns:u=\"$AV_TRANSPORT_SERVICE\"><InstanceID>0</InstanceID></u:GetPositionInfo>",
    ).map { xml ->
        val relTime = extractTagValue(xml, "RelTime")?.let(::parseTimeHms) ?: 0L
        val duration = extractTagValue(xml, "TrackDuration")?.let(::parseTimeHms) ?: 0L
        DlnaPosition(relTimeMs = relTime, durationMs = duration)
    }

    private suspend fun soapCall(
        controlUrl: String,
        action: String,
        body: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val envelope = buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
                append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
                append("<s:Body>$body</s:Body>")
                append("</s:Envelope>")
            }
            val request = Request.Builder()
                .url(controlUrl)
                .header("SOAPACTION", "\"$AV_TRANSPORT_SERVICE#$action\"")
                .post(envelope.toRequestBody(XML_MEDIA_TYPE))
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    val fault = extractTagValue(text, "errorDescription")
                        ?: extractTagValue(text, "faultstring")
                    error(fault ?: "DLNA $action 失败(${resp.code})")
                }
                text
            }
        }
    }

    private fun escapeXml(input: String): String = buildString(input.length) {
        input.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private fun extractTagValue(xml: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = xml.indexOf(open, ignoreCase = true)
        if (start < 0) return null
        val contentStart = start + open.length
        val end = xml.indexOf(close, contentStart, ignoreCase = true)
        if (end < 0) return null
        return xml.substring(contentStart, end).trim()
    }

    private fun formatTimeHms(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L)
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun parseTimeHms(raw: String): Long? {
        val parts = raw.trim().split(':')
        if (parts.size != 3) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].substringBefore('.').toLongOrNull() ?: return null
        return ((h * 3600L) + (m * 60L) + s) * 1000L
    }

    private companion object {
        const val AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
        val XML_MEDIA_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()
    }
}
