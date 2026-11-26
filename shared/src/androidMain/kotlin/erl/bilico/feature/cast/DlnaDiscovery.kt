package erl.bilico.feature.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL

internal class DlnaDiscovery(
    private val context: Context,
    private val http: OkHttpClient,
) {
    suspend fun discover(timeoutMs: Long = 2200L): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("BiliCO-SSDP")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val locations = LinkedHashSet<String>()
        try {
            val targets = listOf(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "urn:schemas-upnp-org:device:MediaRenderer:1",
            )
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = 350
                socket.bind(InetSocketAddress(0))

                val multicastGroup = InetAddress.getByName(SSDP_ADDRESS)
                targets.forEach { st ->
                    val payload = buildString {
                        append("M-SEARCH * HTTP/1.1\r\n")
                        append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                        append("MAN: \"ssdp:discover\"\r\n")
                        append("MX: 2\r\n")
                        append("ST: $st\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(payload, payload.size, multicastGroup, SSDP_PORT)
                    repeat(2) {
                        runCatching { socket.send(packet) }
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    coroutineContext.ensureActive()
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        extractLocation(text)?.let(locations::add)
                    } catch (_: SocketTimeoutException) {
                        // 等待直到扫描窗口结束。
                    }
                }
            }
        } finally {
            runCatching {
                if (lock?.isHeld == true) lock.release()
            }
        }

        locations
            .mapNotNull { location ->
                coroutineContext.ensureActive()
                runCatching { fetchDeviceDescription(location) }.getOrNull()
            }
            .distinctBy { it.avTransportControlUrl }
    }

    private fun extractLocation(response: String): String? {
        response.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).equals("LOCATION", ignoreCase = true)) {
                val value = line.substring(idx + 1).trim()
                if (value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true)
                ) {
                    return value
                }
            }
        }
        return null
    }

    private fun fetchDeviceDescription(location: String): DlnaDevice? {
        val xml = http.newCall(Request.Builder().url(location).build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body.string()
        }
        var friendlyName = ""
        var urlBase = ""
        var udn = ""
        var inService = false
        var serviceType = ""
        var controlUrl = ""
        var matchedControlUrl = ""

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(xml))
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "service" -> {
                            inService = true
                            serviceType = ""
                            controlUrl = ""
                        }

                        "friendlyname" -> if (friendlyName.isBlank()) friendlyName = safeNextText(parser)
                        "urlbase" -> if (urlBase.isBlank()) urlBase = safeNextText(parser)
                        "udn" -> if (udn.isBlank()) udn = safeNextText(parser)
                        "servicetype" -> if (inService) serviceType = safeNextText(parser)
                        "controlurl" -> if (inService) controlUrl = safeNextText(parser)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("service", ignoreCase = true)) {
                        if (serviceType.contains("AVTransport", ignoreCase = true) && controlUrl.isNotBlank()) {
                            matchedControlUrl = controlUrl
                        }
                        inService = false
                    }
                }
            }
            event = parser.next()
        }

        if (matchedControlUrl.isBlank()) return null
        val normalizedControl = if (matchedControlUrl.startsWith("_urn:schemas-upnp-org:service:")) {
            "./$matchedControlUrl"
        } else {
            matchedControlUrl
        }
        val base = urlBase.ifBlank { location }
        val resolvedControlUrl = runCatching {
            URI(base).resolve(normalizedControl).toString()
        }.getOrElse {
            @Suppress("DEPRECATION")
            URL(URL(base), normalizedControl).toString()
        }
        val hostLabel = runCatching { URI(location).host }.getOrNull().orEmpty()
        return DlnaDevice(
            usn = udn.ifBlank { resolvedControlUrl },
            friendlyName = friendlyName.ifBlank { hostLabel.ifBlank { "未知投屏设备" } },
            location = location,
            avTransportControlUrl = resolvedControlUrl,
        )
    }

    private fun safeNextText(parser: XmlPullParser): String =
        runCatching { parser.nextText().trim() }.getOrDefault("")

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900

        fun findLanIpv4Address(preferredPeerHost: String? = null): String? {
            val peerBytes = preferredPeerHost?.let {
                runCatching { InetAddress.getByName(it) as? Inet4Address }.getOrNull()?.address
            }
            val candidates = mutableListOf<Inet4Address>()
            val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
            interfaces.forEach { nif ->
                if (!nif.isUp || nif.isLoopback || nif.isPointToPoint) return@forEach
                nif.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        candidates += addr
                    }
                }
            }
            if (peerBytes != null) {
                candidates.firstOrNull { addr ->
                    val b = addr.address
                    b[0] == peerBytes[0] && b[1] == peerBytes[1] && b[2] == peerBytes[2]
                }?.let { return it.hostAddress }
                candidates.firstOrNull { addr ->
                    val b = addr.address
                    b[0] == peerBytes[0] && b[1] == peerBytes[1]
                }?.let { return it.hostAddress }
            }
            return candidates.firstOrNull()?.hostAddress
        }
    }
}
