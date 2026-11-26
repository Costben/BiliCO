package erl.bilico.player

import erl.bilico.platform.dlFileUriToPath
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVAssetResourceLoader
import platform.AVFoundation.AVAssetResourceLoaderDelegateProtocol
import platform.AVFoundation.AVAssetResourceLoadingRequest
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.preferredForwardBufferDuration
import platform.AVFoundation.resourceLoader
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_t
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseeko
import platform.posix.memcpy
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

internal data class DashSegment(
    val offset: Long,
    val size: Long,
    val durationSec: Double,
)

internal data class Fmp4SidxIndex(
    val initLength: Long,
    val segments: List<DashSegment>,
)

internal sealed interface SidxProbeResult {
    data class Ready(val index: Fmp4SidxIndex) : SidxProbeResult
    data object NotFragmented : SidxProbeResult
    data class Failed(val message: String) : SidxProbeResult
}

internal sealed interface DashHlsProbeOutcome {
    data class Ready(val videoIndex: Fmp4SidxIndex, val audioIndex: Fmp4SidxIndex?) : DashHlsProbeOutcome
    data object FallbackDirect : DashHlsProbeOutcome
    data class Failed(val message: String) : DashHlsProbeOutcome
}

internal data class VirtualHlsPlayback(
    val loader: DashHlsResourceLoader?,
    val item: AVPlayerItem,
    val localServer: LocalMediaHttpServer? = null,
)

internal data class LocalDirectPlayback(
    val videoItem: AVPlayerItem,
    val audioItem: AVPlayerItem?,
    val localServer: LocalMediaHttpServer,
)

private data class Fmp4SidxLocation(
    val initEnd: Long,
    val sidxStart: Long,
    val sidxSize: Long,
)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun String.toUtf8NSData(): NSData =
    NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding) ?: NSData()

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len <= 0) return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

private fun readUInt16Be(buf: ByteArray, offset: Int): Int =
    ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)

private fun readUInt32Be(buf: ByteArray, offset: Int): Long =
    ((buf[offset].toLong() and 0xFFL) shl 24) or
        ((buf[offset + 1].toLong() and 0xFFL) shl 16) or
        ((buf[offset + 2].toLong() and 0xFFL) shl 8) or
        (buf[offset + 3].toLong() and 0xFFL)

private fun readInt64Be(buf: ByteArray, offset: Int): Long =
    (readUInt32Be(buf, offset) shl 32) or (readUInt32Be(buf, offset + 4) and 0xFFFFFFFFL)

private fun boxTypeAt(buf: ByteArray, offset: Int): String = buildString(4) {
    append((buf[offset].toInt() and 0xFF).toChar())
    append((buf[offset + 1].toInt() and 0xFF).toChar())
    append((buf[offset + 2].toInt() and 0xFF).toChar())
    append((buf[offset + 3].toInt() and 0xFF).toChar())
}

private fun scanFmp4SidxLocation(buf: ByteArray): Fmp4SidxLocation? {
    var pos = 0L
    var initEnd = 0L
    var sidxStart = -1L
    var sidxSize = 0L
    while (pos + 8L <= buf.size.toLong()) {
        val p = pos.toInt()
        var boxSize = readUInt32Be(buf, p)
        val boxType = boxTypeAt(buf, p + 4)
        if (boxSize == 1L && pos + 16L <= buf.size.toLong()) {
            boxSize = readInt64Be(buf, p + 8)
        }
        if (boxSize < 8L) break
        when (boxType) {
            "ftyp", "moov" -> initEnd = max(initEnd, pos + boxSize)
            "sidx" -> {
                sidxStart = pos
                sidxSize = boxSize
                break
            }
            "moof", "mdat" -> break
        }
        pos += boxSize
    }
    if (initEnd <= 0L || sidxStart < 0L || sidxSize <= 0L || sidxSize > 2L * 1024L * 1024L) {
        return null
    }
    return Fmp4SidxLocation(initEnd = initEnd, sidxStart = sidxStart, sidxSize = sidxSize)
}

internal fun parseSidxBox(
    buf: ByteArray,
    sidxOffsetInBuf: Int,
    sidxSize: Int,
    initLength: Long,
    fileOffsetAfterSidx: Long,
): Fmp4SidxIndex? {
    if (sidxOffsetInBuf < 0 || sidxSize < 32 || sidxOffsetInBuf + sidxSize > buf.size) return null
    val version = buf[sidxOffsetInBuf + 8].toInt() and 0xFF
    val timescale = readUInt32Be(buf, sidxOffsetInBuf + 16)
    if (timescale <= 0L) return null
    val firstOffset: Long
    var cursor: Int
    if (version == 0) {
        firstOffset = readUInt32Be(buf, sidxOffsetInBuf + 24)
        cursor = sidxOffsetInBuf + 28
    } else if (version == 1) {
        if (sidxSize < 40) return null
        firstOffset = readInt64Be(buf, sidxOffsetInBuf + 28)
        cursor = sidxOffsetInBuf + 36
    } else {
        return null
    }
    if (cursor + 4 > sidxOffsetInBuf + sidxSize) return null
    val refCount = readUInt16Be(buf, cursor + 2)
    cursor += 4
    if (refCount <= 0) return null
    val end = sidxOffsetInBuf + sidxSize
    val segments = ArrayList<DashSegment>(refCount)
    var byteCursor = fileOffsetAfterSidx + firstOffset
    for (i in 0 until refCount) {
        if (cursor + 12 > end) break
        val w0 = readUInt32Be(buf, cursor)
        val subDur = readUInt32Be(buf, cursor + 4)
        val refSize = w0 and 0x7FFFFFFFL
        val durSec = subDur.toDouble() / timescale.toDouble()
        if (refSize > 0L && durSec > 0.0) {
            segments.add(DashSegment(offset = byteCursor, size = refSize, durationSec = durSec))
        }
        byteCursor += refSize
        cursor += 12
    }
    return if (segments.isNotEmpty()) Fmp4SidxIndex(initLength = initLength, segments = segments) else null
}

@OptIn(ExperimentalForeignApi::class)
private fun fetchHttpRange(
    urlString: String,
    headers: Map<String, String>,
    rangeStart: Long,
    rangeEnd: Long,
    onDone: (ByteArray?, Int?, String?) -> Unit,
) {
    val nsUrl = NSURL.URLWithString(urlString) ?: run {
        onDone(null, null, "无效的媒体地址")
        return
    }
    val req = NSMutableURLRequest(
        uRL = nsUrl,
        cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
        timeoutInterval = 4.5,
    )
    headers.forEach { (k, v) -> req.setValue(v, forHTTPHeaderField = k) }
    req.setValue("bytes=$rangeStart-$rangeEnd", forHTTPHeaderField = "Range")
    NSURLSession.sharedSession.dataTaskWithRequest(req) { data, response, error ->
        if (error != null) {
            onDone(null, null, error.localizedDescription)
            return@dataTaskWithRequest
        }
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status != null && status >= 400) {
            onDone(null, status, "媒体节点响应 HTTP $status")
            return@dataTaskWithRequest
        }
        onDone(data?.toByteArray() ?: ByteArray(0), status, null)
    }.resume()
}

internal fun resolvePlayerNsUrl(url: String): NSURL? =
    if (url.startsWith("file://", ignoreCase = true)) {
        NSURL.fileURLWithPath(dlFileUriToPath(url))
    } else {
        NSURL.URLWithString(url)
    }

@OptIn(ExperimentalForeignApi::class)
internal fun buildDirectPlayerItem(
    url: String,
    isAudio: Boolean,
    headers: Map<String, String>,
): AVPlayerItem? {
    val nsUrl = resolvePlayerNsUrl(url) ?: return null
    val isLocal = url.startsWith("file://", ignoreCase = true)
    val lowerPath = url.substringBefore('?').lowercase()
    val options: Map<Any?, *> = buildMap {
        if (!isLocal && headers.isNotEmpty()) put("AVURLAssetHTTPHeaderFieldsKey", headers)
        // 非标准扩展名 MP4 流显式覆盖 MIME 类型，避免 AVFoundation 报 -11828。
        // 注意：Apple SDK 常量 AVURLAssetOverrideMIMETypeKey 的实际运行时键名是 "AVURLAssetOutOfBandMIMETypeKey"。
        if (!isLocal && (isAudio || (!lowerPath.endsWith(".m3u8") && !lowerPath.endsWith(".flv")))) {
            put("AVURLAssetOutOfBandMIMETypeKey", if (isAudio) "audio/mp4" else "video/mp4")
        }
    }
    val asset = AVURLAsset.URLAssetWithURL(nsUrl, options = options)
    return AVPlayerItem.playerItemWithAsset(asset).apply {
        preferredForwardBufferDuration = 5.0
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readLocalFileRange(path: String, offset: Long, length: Int): ByteArray? {
    if (offset < 0L || length <= 0) return ByteArray(0)
    val fp = fopen(path, "rb") ?: return null
    try {
        if (fseeko(fp, offset, SEEK_SET) != 0) return null
        val buf = ByteArray(length)
        val read = buf.usePinned { pinned ->
            fread(pinned.addressOf(0), 1uL, length.toULong(), fp).toInt()
        }
        if (read <= 0) return ByteArray(0)
        return if (read == length) buf else buf.copyOf(read)
    } finally {
        fclose(fp)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun probeLocalFmp4Sidx(path: String): SidxProbeResult {
    val buf = readLocalFileRange(path, 0L, 32_768)
        ?: return SidxProbeResult.Failed("本地缓存文件读取失败")
    if (buf.size < 16) return SidxProbeResult.NotFragmented
    val loc = scanFmp4SidxLocation(buf) ?: return SidxProbeResult.NotFragmented
    val fileOffsetAfterSidx = loc.sidxStart + loc.sidxSize
    if (fileOffsetAfterSidx <= buf.size.toLong()) {
        val parsed = parseSidxBox(
            buf = buf,
            sidxOffsetInBuf = loc.sidxStart.toInt(),
            sidxSize = loc.sidxSize.toInt(),
            initLength = loc.initEnd,
            fileOffsetAfterSidx = fileOffsetAfterSidx,
        )
        return if (parsed != null) SidxProbeResult.Ready(parsed) else SidxProbeResult.NotFragmented
    }
    val sidxBuf = readLocalFileRange(path, loc.sidxStart, loc.sidxSize.toInt())
        ?: return SidxProbeResult.Failed("本地媒体索引读取失败")
    val parsed = parseSidxBox(
        buf = sidxBuf,
        sidxOffsetInBuf = 0,
        sidxSize = sidxBuf.size,
        initLength = loc.initEnd,
        fileOffsetAfterSidx = fileOffsetAfterSidx,
    )
    return if (parsed != null) SidxProbeResult.Ready(parsed) else SidxProbeResult.NotFragmented
}

@OptIn(ExperimentalForeignApi::class)
internal fun probeFmp4SidxAsync(
    urlString: String,
    headers: Map<String, String>,
    localQueue: dispatch_queue_t,
    onDone: (SidxProbeResult) -> Unit,
) {
    if (urlString.startsWith("file://", ignoreCase = true)) {
        val localPath = dlFileUriToPath(urlString)
        dispatch_async(localQueue) {
            onDone(probeLocalFmp4Sidx(localPath))
        }
        return
    }
    fetchHttpRange(urlString, headers, 0L, 32_767L) { buf, _, err ->
        if (err != null || buf == null) {
            onDone(SidxProbeResult.Failed(err ?: "媒体流探测失败"))
            return@fetchHttpRange
        }
        val loc = scanFmp4SidxLocation(buf) ?: run {
            onDone(SidxProbeResult.NotFragmented)
            return@fetchHttpRange
        }
        val fileOffsetAfterSidx = loc.sidxStart + loc.sidxSize
        if (fileOffsetAfterSidx <= buf.size.toLong()) {
            val parsed = parseSidxBox(
                buf = buf,
                sidxOffsetInBuf = loc.sidxStart.toInt(),
                sidxSize = loc.sidxSize.toInt(),
                initLength = loc.initEnd,
                fileOffsetAfterSidx = fileOffsetAfterSidx,
            )
            onDone(if (parsed != null) SidxProbeResult.Ready(parsed) else SidxProbeResult.NotFragmented)
            return@fetchHttpRange
        }
        // 超长视频的 sidx 超过 32KB 时按精确区间补拉一次。
        fetchHttpRange(urlString, headers, loc.sidxStart, fileOffsetAfterSidx - 1L) { sidxBuf, _, sidxErr ->
            if (sidxErr != null || sidxBuf == null) {
                onDone(SidxProbeResult.Failed(sidxErr ?: "媒体索引读取失败"))
                return@fetchHttpRange
            }
            val parsed = parseSidxBox(
                buf = sidxBuf,
                sidxOffsetInBuf = 0,
                sidxSize = sidxBuf.size,
                initLength = loc.initEnd,
                fileOffsetAfterSidx = fileOffsetAfterSidx,
            )
            onDone(if (parsed != null) SidxProbeResult.Ready(parsed) else SidxProbeResult.NotFragmented)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun probeDashHlsAsync(
    videoUrl: String,
    audioUrl: String?,
    headers: Map<String, String>,
    localQueue: dispatch_queue_t,
    onDone: (DashHlsProbeOutcome) -> Unit,
) {
    val hasAudio = !audioUrl.isNullOrBlank()
    var videoResult: SidxProbeResult? = null
    var audioResult: SidxProbeResult? = if (hasAudio) null else SidxProbeResult.NotFragmented

    fun finishIfReady() {
        val vRes = videoResult ?: return
        val aRes = audioResult ?: return
        val outcome = when {
            vRes is SidxProbeResult.Ready && (!hasAudio || aRes is SidxProbeResult.Ready) ->
                DashHlsProbeOutcome.Ready(vRes.index, (aRes as? SidxProbeResult.Ready)?.index)
            vRes is SidxProbeResult.Failed -> DashHlsProbeOutcome.Failed(vRes.message)
            hasAudio && aRes is SidxProbeResult.Failed -> DashHlsProbeOutcome.Failed(aRes.message)
            else -> DashHlsProbeOutcome.FallbackDirect
        }
        onDone(outcome)
    }

    probeFmp4SidxAsync(videoUrl, headers, localQueue) { res ->
        dispatch_async(dispatch_get_main_queue()) {
            videoResult = res
            finishIfReady()
        }
    }
    if (!audioUrl.isNullOrBlank()) {
        probeFmp4SidxAsync(audioUrl, headers, localQueue) { res ->
            dispatch_async(dispatch_get_main_queue()) {
                audioResult = res
                finishIfReady()
            }
        }
    }
}

private fun formatSeconds6(sec: Double): String {
    val scaled = round(sec * 1_000_000.0).toLong().coerceAtLeast(1L)
    val whole = scaled / 1_000_000L
    val frac = (scaled % 1_000_000L).toString().padStart(6, '0')
    return "$whole.$frac"
}

internal fun buildHlsMediaPlaylist(mediaUrl: String, index: Fmp4SidxIndex): String {
    val maxDur = ceil(index.segments.maxOf { it.durationSec }).toLong().coerceAtLeast(1L)
    return buildString(index.segments.size * (mediaUrl.length + 48) + 256) {
        append("#EXTM3U\n")
        append("#EXT-X-VERSION:7\n")
        append("#EXT-X-TARGETDURATION:").append(maxDur).append('\n')
        append("#EXT-X-MEDIA-SEQUENCE:0\n")
        append("#EXT-X-PLAYLIST-TYPE:VOD\n")
        append("#EXT-X-MAP:URI=\"").append(mediaUrl).append("\",BYTERANGE=\"").append(index.initLength).append("@0\"\n")
        for (seg in index.segments) {
            append("#EXTINF:").append(formatSeconds6(seg.durationSec)).append(",\n")
            append("#EXT-X-BYTERANGE:").append(seg.size).append('@').append(seg.offset).append('\n')
            append(mediaUrl).append('\n')
        }
        append("#EXT-X-ENDLIST\n")
    }
}

internal fun buildHlsMasterPlaylist(videoPlaylistUrl: String, audioPlaylistUrl: String): String = buildString {
    append("#EXTM3U\n")
    append("#EXT-X-VERSION:7\n")
    append("#EXT-X-INDEPENDENT-SEGMENTS\n")
    append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"Audio\",DEFAULT=YES,AUTOSELECT=YES,URI=\"")
    append(audioPlaylistUrl).append("\"\n")
    append("#EXT-X-STREAM-INF:BANDWIDTH=4000000,AUDIO=\"aud\"\n")
    append(videoPlaylistUrl).append('\n')
}

@OptIn(ExperimentalForeignApi::class)
internal fun buildLocalDirectPlayback(
    videoUrl: String,
    audioUrl: String?,
): LocalDirectPlayback? {
    val localVideoPath = if (videoUrl.startsWith("file://", ignoreCase = true)) dlFileUriToPath(videoUrl) else null
    val localAudioPath = if (!audioUrl.isNullOrBlank() && audioUrl.startsWith("file://", ignoreCase = true)) {
        dlFileUriToPath(audioUrl)
    } else {
        null
    }
    if (localVideoPath == null && localAudioPath == null) return null
    val diskFiles = buildMap {
        if (localVideoPath != null) put("/video.mp4", "video/mp4" to localVideoPath)
        if (localAudioPath != null) put("/audio.m4a", "audio/mp4" to localAudioPath)
    }
    val server = LocalMediaHttpServer.start(diskFiles = diskFiles) ?: return null
    val vNsUrl = if (localVideoPath != null) NSURL.URLWithString(server.urlFor("/video.mp4")) else resolvePlayerNsUrl(videoUrl)
    if (vNsUrl == null) {
        server.stop()
        return null
    }
    val vItem = AVPlayerItem.playerItemWithAsset(AVURLAsset.URLAssetWithURL(vNsUrl, options = emptyMap<Any?, Any>())).apply {
        preferredForwardBufferDuration = 5.0
    }
    val aItem = if (localAudioPath != null) {
        NSURL.URLWithString(server.urlFor("/audio.m4a"))?.let { aUrl ->
            AVPlayerItem.playerItemWithAsset(AVURLAsset.URLAssetWithURL(aUrl, options = emptyMap<Any?, Any>())).apply {
                preferredForwardBufferDuration = 5.0
            }
        }
    } else {
        null
    }
    return LocalDirectPlayback(videoItem = vItem, audioItem = aItem, localServer = server)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun buildVirtualHlsPlayback(
    generation: Int,
    videoUrl: String,
    videoIndex: Fmp4SidxIndex,
    audioUrl: String?,
    audioIndex: Fmp4SidxIndex?,
    headers: Map<String, String>,
    loaderQueue: dispatch_queue_t,
): VirtualHlsPlayback? {
    val activeAudioUrl = audioUrl?.takeIf { it.isNotBlank() }
    val activeAudioIndex = if (activeAudioUrl != null) audioIndex else null
    val hasAudio = activeAudioUrl != null && activeAudioIndex != null
    val localVideoPath = if (videoUrl.startsWith("file://", ignoreCase = true)) dlFileUriToPath(videoUrl) else null
    val localAudioPath = if (hasAudio && activeAudioUrl.startsWith("file://", ignoreCase = true)) {
        dlFileUriToPath(activeAudioUrl)
    } else {
        null
    }

    // 本地离线缓存统一经 127.0.0.1 回环 HTTP Range 服务提供 HLS v7 fMP4，彻底避开 AVAssetResourceLoader 自定义协议报错。
    if (localVideoPath != null || localAudioPath != null) {
        val diskFiles = buildMap {
            if (localVideoPath != null) put("/video.m4s", "video/mp4" to localVideoPath)
            if (localAudioPath != null) put("/audio.m4s", "audio/mp4" to localAudioPath)
        }
        val videoMediaUrl = if (localVideoPath != null) "video.m4s" else videoUrl
        val audioMediaUrl = if (hasAudio) {
            if (localAudioPath != null) "audio.m4s" else activeAudioUrl
        } else {
            null
        }
        val videoPlaylist = buildHlsMediaPlaylist(videoMediaUrl, videoIndex)
        val audioPlaylist = if (hasAudio && audioMediaUrl != null) {
            buildHlsMediaPlaylist(audioMediaUrl, activeAudioIndex)
        } else {
            null
        }
        val masterPlaylist = if (hasAudio) buildHlsMasterPlaylist("video.m3u8", "audio.m3u8") else null
        val memFiles = buildMap {
            put("/video.m3u8", "application/vnd.apple.mpegurl" to videoPlaylist.encodeToByteArray())
            if (audioPlaylist != null) put("/audio.m3u8", "application/vnd.apple.mpegurl" to audioPlaylist.encodeToByteArray())
            if (masterPlaylist != null) put("/master.m3u8", "application/vnd.apple.mpegurl" to masterPlaylist.encodeToByteArray())
        }
        val server = LocalMediaHttpServer.start(memoryFiles = memFiles, diskFiles = diskFiles) ?: return null
        val entryPath = if (hasAudio) "/master.m3u8" else "/video.m3u8"
        val entryUrl = NSURL.URLWithString(server.urlFor(entryPath)) ?: run {
            server.stop()
            return null
        }
        val asset = AVURLAsset.URLAssetWithURL(entryUrl, options = emptyMap<Any?, Any>())
        val item = AVPlayerItem.playerItemWithAsset(asset).apply {
            preferredForwardBufferDuration = 10.0
        }
        return VirtualHlsPlayback(loader = null, item = item, localServer = server)
    }

    val videoPlaylist = buildHlsMediaPlaylist(videoUrl, videoIndex)
    val audioPlaylist = if (hasAudio) buildHlsMediaPlaylist(activeAudioUrl, activeAudioIndex) else null
    val masterPlaylist = if (hasAudio) {
        buildHlsMasterPlaylist("bilicohls://gen-$generation/video.m3u8", "bilicohls://gen-$generation/audio.m3u8")
    } else {
        null
    }

    val loader = DashHlsResourceLoader(
        masterData = masterPlaylist?.toUtf8NSData(),
        videoData = videoPlaylist.toUtf8NSData(),
        audioData = audioPlaylist?.toUtf8NSData(),
    )
    val entryUrlString = if (hasAudio) {
        "bilicohls://gen-$generation/master.m3u8"
    } else {
        "bilicohls://gen-$generation/video.m3u8"
    }
    val entryUrl = NSURL.URLWithString(entryUrlString) ?: return null
    val options: Map<Any?, *> = if (headers.isNotEmpty()) {
        mapOf("AVURLAssetHTTPHeaderFieldsKey" to headers)
    } else {
        emptyMap<Any?, Any>()
    }
    val asset = AVURLAsset.URLAssetWithURL(entryUrl, options = options)
    asset.resourceLoader.setDelegate(loader, queue = loaderQueue)
    val item = AVPlayerItem.playerItemWithAsset(asset).apply {
        preferredForwardBufferDuration = 10.0
    }
    return VirtualHlsPlayback(loader = loader, item = item)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class DashHlsResourceLoader(
    private val masterData: NSData?,
    private val videoData: NSData,
    private val audioData: NSData?,
) : NSObject(), AVAssetResourceLoaderDelegateProtocol {

    override fun resourceLoader(
        resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest,
    ): Boolean {
        val path = shouldWaitForLoadingOfRequestedResource.request.URL?.path ?: return false
        val data = when (path) {
            "/master.m3u8" -> masterData
            "/video.m3u8" -> videoData
            "/audio.m3u8" -> audioData
            else -> null
        } ?: return false

        val totalLen = data.length.toLong()
        shouldWaitForLoadingOfRequestedResource.contentInformationRequest?.apply {
            contentType = "public.m3u-playlist"
            contentLength = totalLen
            byteRangeAccessSupported = true
        }
        shouldWaitForLoadingOfRequestedResource.dataRequest?.let { dataReq ->
            val offset = dataReq.requestedOffset.coerceIn(0L, totalLen)
            val maxAvail = (totalLen - offset).coerceAtLeast(0L)
            val length = if (dataReq.requestsAllDataToEndOfResource) {
                maxAvail
            } else {
                minOf(dataReq.requestedLength.coerceAtLeast(0L), maxAvail)
            }
            if (length > 0L) {
                val slice = if (offset == 0L && length == totalLen) {
                    data
                } else {
                    data.subdataWithRange(NSMakeRange(offset.toULong(), length.toULong()))
                }
                dataReq.respondWithData(slice)
            }
        }
        shouldWaitForLoadingOfRequestedResource.finishLoading()
        return true
    }
}
