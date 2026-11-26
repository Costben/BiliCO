package erl.bilico.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import erl.bilico.debug.diag
import erl.bilico.platform.PlatformContext
import erl.bilico.platform.currentTimeMillis
import erl.bilico.platform.dlFileLength
import erl.bilico.platform.dlFileUriToPath
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.currentRoute
import platform.AVFAudio.outputVolume
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAssetResourceLoader
import platform.AVFoundation.AVAssetResourceLoaderDelegateProtocol
import platform.AVFoundation.AVAssetResourceLoadingRequest
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerItemStatusUnknown
import platform.AVFoundation.AVPlayerItemTrack
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.CMTimeRangeValue
import platform.AVFoundation.addOutput
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.automaticallyWaitsToMinimizeStalling
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.enabled
import platform.AVFoundation.loadedTimeRanges
import platform.AVFoundation.mediaType
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.preferredForwardBufferDuration
import platform.AVFoundation.presentationSize
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.resourceLoader
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.tracks
import platform.AVFoundation.volume
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNull
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSString
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSValue
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
import platform.Foundation.subdataWithRange
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseeko
import platform.posix.memcpy
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.round

@Stable
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class BiliPlayer actual constructor(context: PlatformContext) {
    internal val avPlayer: AVPlayer = AVPlayer().apply {
        automaticallyWaitsToMinimizeStalling = true
        muted = false
        volume = 1f
    }
    private var audioPlayer: AVPlayer? = null
    private var videoOutput: AVPlayerItemVideoOutput? = null
    private var hlsLoader: DashHlsResourceLoader? = null
    private val hlsLoaderQueue = dispatch_queue_create("erl.bilico.dash.hls", null)
    private var probingDashHls = false
    private var lastAudioSeekAtMs = 0L
    private var timeObserver: Any? = null
    private var endObserver: Any? = null
    private var failedObserver: Any? = null
    private var statusTimer: NSTimer? = null
    private var currentHeaders: Map<String, String> = emptyMap()
    private val listeners = ArrayList<BiliPlayerListener>()

    private var desiredPlayWhenReady = false
    private var desiredSpeed = 1f
    private var desiredVolume = 1f
    private var pendingSeekMs = 0L
    // AVPlayerItem 的对象身份不可靠：同一个 item 每次取回来可能是不同的包装对象，
    // 直接比 item 会让「这个 item 已经报过 ready」永远为假，pollState 每 0.2s 就重新
    // 配置一次 AVAudioSession（真机上实测 ~8 次/秒），系统音量 HUD 会被反复顶掉/重弹。
    // 用换源代数去重：setMedia / 清空源各推一代，一代只报一次。
    private var mediaGeneration = 0
    private var readyReportedGeneration = -1
    private var audioReadyReportedGeneration = -1
    private var failedReportedGeneration = -1
    private var failedReportedForAudioGeneration = -1
    private var isLiveMedia = false
    private var mediaSetAtMs = 0L
    private var readyAtMs = 0L
    private var lastReportedState = PlayerStates.STATE_IDLE
    private var lastReportedPlaying = false
    private var lastReportedPlayIntent = false
    private var lastReportedWidth = 0
    private var lastReportedHeight = 0
    private var lastOutputVolume = -1f

    init {
        ensureAudioSession()
        dispatch_async(dispatch_get_main_queue()) {
            val interval = CMTimeMake(value = 1, timescale = 5)
            timeObserver = avPlayer.addPeriodicTimeObserverForInterval(interval, queue = dispatch_get_main_queue()) { _ ->
                syncAudioIfNeeded()
                pollState()
            }
            statusTimer = NSTimer.scheduledTimerWithTimeInterval(
                interval = 0.2,
                repeats = true,
                block = { _ -> pollState() },
            )
        }
    }

    actual val currentPosition: Long
        get() {
            val secs = CMTimeGetSeconds(avPlayer.currentTime())
            return if (secs.isFinite() && secs >= 0.0) (secs * 1000.0).toLong() else 0L
        }

    actual val duration: Long
        get() {
            val item = avPlayer.currentItem ?: return 0L
            val secs = CMTimeGetSeconds(item.duration)
            return if (secs.isFinite() && secs > 0.0) (secs * 1000.0).toLong() else 0L
        }

    actual val bufferedPosition: Long
        get() {
            val item = avPlayer.currentItem ?: return currentPosition
            val pos = currentPosition
            var maxEnd = pos
            item.loadedTimeRanges.forEach { raw ->
                val value = raw as? NSValue ?: return@forEach
                val endSec = value.CMTimeRangeValue.useContents {
                    CMTimeGetSeconds(start.readValue()) + CMTimeGetSeconds(duration.readValue())
                }
                if (endSec.isFinite() && endSec > 0.0) {
                    maxEnd = max(maxEnd, (endSec * 1000.0).toLong())
                }
            }
            return maxEnd
        }

    actual val isPlaying: Boolean
        get() = avPlayer.timeControlStatus == AVPlayerTimeControlStatusPlaying || avPlayer.rate > 0f

    actual val playWhenReady: Boolean
        get() = desiredPlayWhenReady

    actual val playbackState: Int
        get() {
            if (probingDashHls) return PlayerStates.STATE_BUFFERING
            val item = avPlayer.currentItem ?: return PlayerStates.STATE_IDLE
            if (item.status == AVPlayerItemStatusFailed) return PlayerStates.STATE_IDLE
            if (lastReportedState == PlayerStates.STATE_ENDED) return PlayerStates.STATE_ENDED
            if (item.status != AVPlayerItemStatusReadyToPlay) return PlayerStates.STATE_BUFFERING
            if (avPlayer.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate && desiredPlayWhenReady) {
                return PlayerStates.STATE_BUFFERING
            }
            return PlayerStates.STATE_READY
        }

    actual val speed: Float
        get() = desiredSpeed

    actual var volume: Float
        get() = desiredVolume
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            desiredVolume = clamped
            avPlayer.muted = clamped <= 0f
            avPlayer.volume = clamped
            audioPlayer?.muted = clamped <= 0f
            audioPlayer?.volume = clamped
        }

    actual val videoSize: VideoDimensions
        get() {
            val item = avPlayer.currentItem ?: return VideoDimensions()
            return item.presentationSize.useContents {
                VideoDimensions(width.toInt(), height.toInt())
            }
        }

    actual fun setMedia(
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>,
        startPositionMs: Long,
        playWhenReady: Boolean,
        isLive: Boolean,
    ) {
        currentHeaders = headers
        desiredPlayWhenReady = playWhenReady
        pendingSeekMs = if (startPositionMs > 0L && !isLive) startPositionMs else 0L
        mediaGeneration++
        isLiveMedia = isLive
        mediaSetAtMs = currentTimeMillis()
        readyAtMs = 0L
        audioReadyReportedGeneration = -1
        lastReportedState = PlayerStates.STATE_BUFFERING
        if (hasPlayIntent != lastReportedPlayIntent) {
            lastReportedPlayIntent = hasPlayIntent
            listeners.toList().forEach { it.onPlayIntentChanged(lastReportedPlayIntent) }
        }

        val generation = mediaGeneration
        dispatch_async(dispatch_get_main_queue()) {
            if (generation != mediaGeneration) return@dispatch_async
            ensureAudioSession()
            detachObservers()
            videoOutput = null
            hlsLoader = null
            avPlayer.pause()
            avPlayer.replaceCurrentItemWithPlayerItem(null)
            audioPlayer?.pause()
            audioPlayer?.replaceCurrentItemWithPlayerItem(null)

            val nsUrl = resolvePlayerNsUrl(videoUrl) ?: run {
                notifyError("无效的视频地址")
                return@dispatch_async
            }
            val isLocalVideo = videoUrl.startsWith("file://", ignoreCase = true)
            val lowerVideoPath = videoUrl.substringBefore('?').lowercase()
            val shouldProbeDashHls = !isLive &&
                !lowerVideoPath.endsWith(".m3u8") &&
                !lowerVideoPath.endsWith(".flv") &&
                (!isLocalVideo || !audioUrl.isNullOrBlank() || lowerVideoPath.endsWith(".m4s"))

            println("BiliPlayer.setMedia gen=$generation host=${nsUrl.host ?: (if (isLocalVideo) "local" else "?")} hasAudio=${!audioUrl.isNullOrBlank()} probeHls=$shouldProbeDashHls")

            if (!shouldProbeDashHls) {
                probingDashHls = false
                attachDirectMedia(
                    generation = generation,
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    headers = headers,
                    playWhenReady = playWhenReady,
                )
                return@dispatch_async
            }

            probingDashHls = true
            // B 站 DASH .m4s 为 Fragmented MP4（ftyp+moov+sidx+moof/mdat）。
            // 直接交给 AVPlayer 渐进式 MP4 解析器时，CoreMedia 会把整段几百 MB 文件扫描下载完才 ReadyToPlay。
            // 先并行探测头部 32KB 解析 sidx，把音视频封装成内存虚拟 HLS v7 fMP4 播放列表，
            // 让单实例 AVPlayer 仅按需请求首段 ByteRange，实现 <1s 秒开、硬件级音画同步和秒级拖拽。
            val probeStartMs = currentTimeMillis()
            var videoResult: SidxProbeResult? = null
            var audioResult: SidxProbeResult? = if (audioUrl.isNullOrBlank()) SidxProbeResult.NotFragmented else null

            fun finishProbeIfReady() {
                if (generation != mediaGeneration) return
                val vRes = videoResult ?: return
                val aRes = audioResult ?: return
                probingDashHls = false
                val probeMs = currentTimeMillis() - probeStartMs
                val hasAudio = !audioUrl.isNullOrBlank()
                if (vRes is SidxProbeResult.Ready && (!hasAudio || aRes is SidxProbeResult.Ready)) {
                    val vIdx = vRes.index
                    val aIdx = (aRes as? SidxProbeResult.Ready)?.index
                    println(
                        "BiliPlayer.hlsReady gen=$generation probeMs=${probeMs}ms vSegs=${vIdx.segments.size} aSegs=${aIdx?.segments?.size ?: 0}",
                    )
                    attachVirtualHlsMedia(
                        generation = generation,
                        videoUrl = videoUrl,
                        videoIndex = vIdx,
                        audioUrl = audioUrl,
                        audioIndex = aIdx,
                        headers = headers,
                        playWhenReady = playWhenReady,
                    )
                } else if (vRes is SidxProbeResult.Failed) {
                    if (failedReportedGeneration != generation) {
                        failedReportedGeneration = generation
                        notifyError(vRes.message)
                    }
                } else if (hasAudio && aRes is SidxProbeResult.Failed) {
                    if (failedReportedGeneration != generation) {
                        failedReportedGeneration = generation
                        notifyError(aRes.message)
                    }
                } else {
                    println("BiliPlayer.hlsFallbackDirect gen=$generation probeMs=${probeMs}ms")
                    attachDirectMedia(
                        generation = generation,
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        headers = headers,
                        playWhenReady = playWhenReady,
                    )
                }
            }

            probeFmp4SidxAsync(videoUrl, headers, hlsLoaderQueue) { res ->
                dispatch_async(dispatch_get_main_queue()) {
                    videoResult = res
                    finishProbeIfReady()
                }
            }
            if (!audioUrl.isNullOrBlank()) {
                probeFmp4SidxAsync(audioUrl, headers, hlsLoaderQueue) { res ->
                    dispatch_async(dispatch_get_main_queue()) {
                        audioResult = res
                        finishProbeIfReady()
                    }
                }
            }
        }
    }

    private fun detachObservers() {
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        failedObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        failedObserver = null
    }

    private fun attachItemObservers(item: AVPlayerItem, generation: Int) {
        detachObservers()
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (generation != mediaGeneration) return@addObserverForName
            desiredPlayWhenReady = false
            lastReportedState = PlayerStates.STATE_ENDED
            listeners.toList().forEach { it.onPlaybackStateChanged(PlayerStates.STATE_ENDED) }
        }
        failedObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (generation != mediaGeneration) return@addObserverForName
            if (failedReportedGeneration != generation) {
                failedReportedGeneration = generation
                notifyError(item.error?.localizedDescription ?: "视频播放失败")
            }
        }
    }

    private fun attachVideoOutput(item: AVPlayerItem) {
        val attrs: Map<Any?, *> = mapOf(
            kCVPixelBufferPixelFormatTypeKey to NSNumber(unsignedInt = kCVPixelFormatType_32BGRA),
        )
        val output = AVPlayerItemVideoOutput(pixelBufferAttributes = attrs)
        item.addOutput(output)
        videoOutput = output
    }

    private fun attachVirtualHlsMedia(
        generation: Int,
        videoUrl: String,
        videoIndex: Fmp4SidxIndex,
        audioUrl: String?,
        audioIndex: Fmp4SidxIndex?,
        headers: Map<String, String>,
        playWhenReady: Boolean,
    ) {
        if (generation != mediaGeneration) return
        val hasAudio = !audioUrl.isNullOrBlank() && audioIndex != null
        val localVideoPath = if (videoUrl.startsWith("file://", ignoreCase = true)) {
            dlFileUriToPath(videoUrl)
        } else {
            null
        }
        val localAudioPath = if (hasAudio && audioUrl!!.startsWith("file://", ignoreCase = true)) {
            dlFileUriToPath(audioUrl)
        } else {
            null
        }
        val playlistVideoUrl = if (localVideoPath != null) {
            "bilicohls://gen-$generation/video.m4s"
        } else {
            videoUrl
        }
        val playlistAudioUrl = if (hasAudio) {
            if (localAudioPath != null) "bilicohls://gen-$generation/audio.m4s" else audioUrl!!
        } else {
            null
        }
        val videoPlaylist = buildHlsMediaPlaylist(playlistVideoUrl, videoIndex)
        val audioPlaylist = if (hasAudio && playlistAudioUrl != null) {
            buildHlsMediaPlaylist(playlistAudioUrl, audioIndex!!)
        } else {
            null
        }
        val masterPlaylist = if (hasAudio) buildHlsMasterPlaylist(generation) else null

        val loader = DashHlsResourceLoader(
            masterData = masterPlaylist?.toUtf8NSData(),
            videoData = videoPlaylist.toUtf8NSData(),
            audioData = audioPlaylist?.toUtf8NSData(),
            localVideoPath = localVideoPath,
            localAudioPath = localAudioPath,
        )
        hlsLoader = loader

        val entryUrlString = if (hasAudio) {
            "bilicohls://gen-$generation/master.m3u8"
        } else {
            "bilicohls://gen-$generation/video.m3u8"
        }
        val entryUrl = NSURL.URLWithString(entryUrlString) ?: run {
            notifyError("无法创建播放列表地址")
            return
        }
        val options: Map<Any?, *> = if (localVideoPath == null && headers.isNotEmpty()) {
            mapOf("AVURLAssetHTTPHeaderFieldsKey" to headers)
        } else {
            emptyMap<Any?, Any>()
        }
        val asset = AVURLAsset.URLAssetWithURL(entryUrl, options = options)
        asset.resourceLoader.setDelegate(loader, queue = hlsLoaderQueue)
        val item = AVPlayerItem.playerItemWithAsset(asset).apply {
            preferredForwardBufferDuration = 10.0
        }
        attachVideoOutput(item)

        audioPlayer?.pause()
        audioPlayer?.replaceCurrentItemWithPlayerItem(null)

        avPlayer.muted = desiredVolume <= 0f
        avPlayer.volume = desiredVolume
        avPlayer.replaceCurrentItemWithPlayerItem(item)
        attachItemObservers(item, generation)

        if (playWhenReady) {
            avPlayer.play()
            avPlayer.rate = desiredSpeed
        } else {
            avPlayer.pause()
        }
        pollState()
    }

    private fun attachDirectMedia(
        generation: Int,
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>,
        playWhenReady: Boolean,
    ) {
        if (generation != mediaGeneration) return
        val nsUrl = resolvePlayerNsUrl(videoUrl) ?: run {
            notifyError("无效的视频地址")
            return
        }
        val isLocalVideo = videoUrl.startsWith("file://", ignoreCase = true)
        val lowerVideoPath = videoUrl.substringBefore('?').lowercase()
        val videoOptions: Map<Any?, *> = buildMap {
            if (!isLocalVideo && headers.isNotEmpty()) put("AVURLAssetHTTPHeaderFieldsKey", headers)
            // 非标准扩展名 MP4 流显式覆盖 MIME 类型，避免 AVFoundation 报 -11828。
            // 注意：Apple SDK 常量 AVURLAssetOverrideMIMETypeKey 的实际运行时键名是 "AVURLAssetOutOfBandMIMETypeKey"。
            if (!isLocalVideo && !lowerVideoPath.endsWith(".m3u8") && !lowerVideoPath.endsWith(".flv")) {
                put("AVURLAssetOutOfBandMIMETypeKey", "video/mp4")
            }
        }
        val asset = AVURLAsset.URLAssetWithURL(nsUrl, options = videoOptions)
        val item = AVPlayerItem.playerItemWithAsset(asset).apply {
            preferredForwardBufferDuration = 5.0
        }
        attachVideoOutput(item)
        avPlayer.muted = desiredVolume <= 0f
        avPlayer.volume = desiredVolume
        avPlayer.replaceCurrentItemWithPlayerItem(item)

        if (!audioUrl.isNullOrBlank()) {
            val aUrl = resolvePlayerNsUrl(audioUrl)
            if (aUrl != null) {
                val isLocalAudio = audioUrl.startsWith("file://", ignoreCase = true)
                val audioOptions: Map<Any?, *> = buildMap {
                    if (!isLocalAudio && headers.isNotEmpty()) put("AVURLAssetHTTPHeaderFieldsKey", headers)
                    if (!isLocalAudio) put("AVURLAssetOutOfBandMIMETypeKey", "audio/mp4")
                }
                val aAsset = AVURLAsset.URLAssetWithURL(aUrl, options = audioOptions)
                val aItem = AVPlayerItem.playerItemWithAsset(aAsset).apply {
                    preferredForwardBufferDuration = 5.0
                }
                val ap = audioPlayer ?: AVPlayer().also {
                    it.automaticallyWaitsToMinimizeStalling = true
                    audioPlayer = it
                }
                ap.muted = desiredVolume <= 0f
                ap.volume = desiredVolume
                ap.replaceCurrentItemWithPlayerItem(aItem)
            }
        } else {
            audioPlayer?.pause()
            audioPlayer?.replaceCurrentItemWithPlayerItem(null)
        }

        attachItemObservers(item, generation)

        if (playWhenReady) {
            avPlayer.play()
            avPlayer.rate = desiredSpeed
            audioPlayer?.play()
            audioPlayer?.rate = desiredSpeed
        } else {
            avPlayer.pause()
            audioPlayer?.pause()
        }
        pollState()
    }

    actual fun clearMediaItems() {
        desiredPlayWhenReady = false
        pendingSeekMs = 0L
        probingDashHls = false
        mediaGeneration++
        lastReportedState = PlayerStates.STATE_IDLE
        dispatch_async(dispatch_get_main_queue()) {
            detachObservers()
            videoOutput = null
            hlsLoader = null
            avPlayer.pause()
            avPlayer.replaceCurrentItemWithPlayerItem(null)
            audioPlayer?.pause()
            audioPlayer?.replaceCurrentItemWithPlayerItem(null)
            pollState()
        }
    }

    actual fun stop() {
        clearMediaItems()
    }

    actual fun play() {
        desiredPlayWhenReady = true
        if (lastReportedState == PlayerStates.STATE_ENDED) {
            lastReportedState = PlayerStates.STATE_READY
        }
        if (hasPlayIntent != lastReportedPlayIntent) {
            lastReportedPlayIntent = hasPlayIntent
            listeners.toList().forEach { it.onPlayIntentChanged(lastReportedPlayIntent) }
        }
        dispatch_async(dispatch_get_main_queue()) {
            ensureAudioSession()
            avPlayer.muted = desiredVolume <= 0f
            avPlayer.volume = desiredVolume
            avPlayer.play()
            avPlayer.rate = desiredSpeed
            audioPlayer?.muted = desiredVolume <= 0f
            audioPlayer?.volume = desiredVolume
            audioPlayer?.play()
            audioPlayer?.rate = desiredSpeed
            pollState()
        }
    }

    actual fun pause() {
        desiredPlayWhenReady = false
        if (hasPlayIntent != lastReportedPlayIntent) {
            lastReportedPlayIntent = hasPlayIntent
            listeners.toList().forEach { it.onPlayIntentChanged(lastReportedPlayIntent) }
        }
        dispatch_async(dispatch_get_main_queue()) {
            avPlayer.pause()
            audioPlayer?.pause()
            pollState()
        }
    }

    actual fun seekTo(positionMs: Long) {
        val target = CMTimeMakeWithSeconds(positionMs.coerceAtLeast(0L) / 1000.0, preferredTimescale = 1000)
        dispatch_async(dispatch_get_main_queue()) {
            avPlayer.seekToTime(target)
            audioPlayer?.seekToTime(target)
        }
    }

    actual fun setPlaybackSpeed(speed: Float) {
        desiredSpeed = speed.coerceIn(0.25f, 4f)
        dispatch_async(dispatch_get_main_queue()) {
            if (desiredPlayWhenReady) {
                avPlayer.rate = desiredSpeed
                audioPlayer?.rate = desiredSpeed
            }
        }
    }

    actual fun addListener(listener: BiliPlayerListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    actual fun removeListener(listener: BiliPlayerListener) {
        listeners.remove(listener)
    }

    actual fun captureCurrentFrame(): ImageBitmap? {
        val output = videoOutput ?: return null
        val item = avPlayer.currentItem ?: return null
        if (item.status != AVPlayerItemStatusReadyToPlay) return null
        val time = avPlayer.currentTime()
        val pixelBuffer = output.copyPixelBufferForItemTime(time, itemTimeForDisplay = null) ?: return null
        try {
            if (CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly) != 0) return null
            try {
                val srcW = CVPixelBufferGetWidth(pixelBuffer).toInt()
                val srcH = CVPixelBufferGetHeight(pixelBuffer).toInt()
                val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer).toInt()
                val baseAddr = CVPixelBufferGetBaseAddress(pixelBuffer) ?: return null
                if (srcW <= 0 || srcH <= 0 || bytesPerRow < srcW * 4) return null

                val maxDim = 240
                val scale = maxOf(srcW, srcH).toFloat() / maxDim.toFloat()
                val dstW = if (scale > 1f) (srcW / scale).toInt().coerceAtLeast(1) else srcW
                val dstH = if (scale > 1f) (srcH / scale).toInt().coerceAtLeast(1) else srcH
                val outBytes = ByteArray(dstW * dstH * 4)
                val srcPtr = baseAddr.reinterpret<ByteVar>()

                var dstIdx = 0
                for (y in 0 until dstH) {
                    val sy = (y.toLong() * srcH / dstH).toInt().coerceIn(0, srcH - 1)
                    val rowBase = sy * bytesPerRow
                    for (x in 0 until dstW) {
                        val sx = (x.toLong() * srcW / dstW).toInt().coerceIn(0, srcW - 1)
                        val off = rowBase + sx * 4
                        outBytes[dstIdx] = srcPtr[off]
                        outBytes[dstIdx + 1] = srcPtr[off + 1]
                        outBytes[dstIdx + 2] = srcPtr[off + 2]
                        outBytes[dstIdx + 3] = 0xFF.toByte()
                        dstIdx += 4
                    }
                }
                val info = ImageInfo(
                    width = dstW,
                    height = dstH,
                    colorType = ColorType.BGRA_8888,
                    alphaType = ColorAlphaType.OPAQUE,
                )
                return Image.makeRaster(info, outBytes, dstW * 4).toComposeImageBitmap()
            } finally {
                CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
            }
        } finally {
            CVPixelBufferRelease(pixelBuffer)
        }
    }

    actual fun release() {
        dispatch_async(dispatch_get_main_queue()) {
            statusTimer?.invalidate()
            statusTimer = null
            timeObserver?.let { avPlayer.removeTimeObserver(it) }
            timeObserver = null
            detachObservers()
            videoOutput = null
            hlsLoader = null
            avPlayer.pause()
            avPlayer.replaceCurrentItemWithPlayerItem(null)
            audioPlayer?.pause()
            audioPlayer?.replaceCurrentItemWithPlayerItem(null)
            audioPlayer = null
            listeners.clear()
        }
    }

    private fun syncAudioIfNeeded() {
        val ap = audioPlayer ?: return
        val aItem = ap.currentItem ?: return
        if (aItem.status != AVPlayerItemStatusReadyToPlay) return
        ap.muted = desiredVolume <= 0f
        ap.volume = desiredVolume
        val vSecs = CMTimeGetSeconds(avPlayer.currentTime())
        val aSecs = CMTimeGetSeconds(ap.currentTime())
        val nowMs = currentTimeMillis()
        if (vSecs.isFinite() && aSecs.isFinite() && abs(vSecs - aSecs) > 0.45 && nowMs - lastAudioSeekAtMs > 1_500L) {
            lastAudioSeekAtMs = nowMs
            ap.seekToTime(CMTimeMakeWithSeconds(vSecs, preferredTimescale = 1000))
        }
        if (isPlaying && ap.rate == 0f) {
            ap.play()
            ap.rate = desiredSpeed
        } else if (!isPlaying && ap.rate > 0f) {
            ap.pause()
        }
    }

    private fun pollState() {
        val nowMs = currentTimeMillis()
        val item = avPlayer.currentItem
        if (item?.status == AVPlayerItemStatusFailed && failedReportedGeneration != mediaGeneration) {
            failedReportedGeneration = mediaGeneration
            val err = item.error?.localizedDescription ?: "视频加载失败"
            notifyError(err)
        }
        val audioItem = audioPlayer?.currentItem
        if (audioItem?.status == AVPlayerItemStatusFailed &&
            failedReportedForAudioGeneration != mediaGeneration
        ) {
            failedReportedForAudioGeneration = mediaGeneration
            val err = audioItem.error?.localizedDescription ?: "音频流加载失败"
            println("BiliPlayer.audioItemFailed err=$err")
            notifyError(err)
        }
        if (audioItem?.status == AVPlayerItemStatusReadyToPlay &&
            audioReadyReportedGeneration != mediaGeneration
        ) {
            audioReadyReportedGeneration = mediaGeneration
            val ap = audioPlayer
            if (ap != null) {
                ap.muted = desiredVolume <= 0f
                ap.volume = desiredVolume
                val vSecs = CMTimeGetSeconds(avPlayer.currentTime())
                if (vSecs.isFinite() && vSecs > 0.1) {
                    ap.seekToTime(CMTimeMakeWithSeconds(vSecs, preferredTimescale = 1000))
                }
                if (desiredPlayWhenReady && lastReportedState != PlayerStates.STATE_ENDED) {
                    ap.play()
                    ap.rate = desiredSpeed
                }
            }
        }
        // 起播超时看门狗：部分 B 站镜像节点会长时间停在 Unknown 不报错，8.5s 未就绪自动切备用线路/编码。
        if (!isLiveMedia && item != null && mediaSetAtMs > 0L &&
            failedReportedGeneration != mediaGeneration &&
            nowMs - mediaSetAtMs > 8_500L &&
            (item.status == AVPlayerItemStatusUnknown || audioItem?.status == AVPlayerItemStatusUnknown)
        ) {
            failedReportedGeneration = mediaGeneration
            notifyError("媒体流加载超时")
        }
        if (item?.status == AVPlayerItemStatusReadyToPlay) {
            if (readyAtMs == 0L) readyAtMs = nowMs
            if (readyReportedGeneration != mediaGeneration) {
                readyReportedGeneration = mediaGeneration
                ensureAudioSession()
                val session = AVAudioSession.sharedInstance()
                val trackDesc = item.tracks.joinToString(",") { raw ->
                    val tr = raw as? AVPlayerItemTrack
                    if (tr != null) {
                        if (!tr.enabled) tr.enabled = true
                        "${tr.assetTrack?.mediaType ?: "?"}:${tr.enabled}"
                    } else {
                        "?"
                    }
                }
                val outputs = session.currentRoute.outputs.joinToString(",") { raw ->
                    val p = raw as? AVAudioSessionPortDescription
                    p?.portType ?: "?"
                }
                println(
                    "BiliPlayer.ready tracks=[$trackDesc] vol=${avPlayer.volume} muted=${avPlayer.muted} outVol=${session.outputVolume} cat=${session.category ?: "?"} mode=${session.mode ?: "?"} out=[$outputs]",
                )
            }
            if (pendingSeekMs > 0L) {
                val target = CMTimeMakeWithSeconds(pendingSeekMs / 1000.0, preferredTimescale = 1000)
                pendingSeekMs = 0L
                avPlayer.seekToTime(target)
                audioPlayer?.seekToTime(target)
            }
            if (desiredPlayWhenReady && avPlayer.rate == 0f && lastReportedState != PlayerStates.STATE_ENDED) {
                avPlayer.play()
                avPlayer.rate = desiredSpeed
                if (audioItem?.status == AVPlayerItemStatusReadyToPlay) {
                    audioPlayer?.play()
                    audioPlayer?.rate = desiredSpeed
                }
            }
            // 黑屏看门狗：ReadyToPlay 后超过 3s 仍无有效视频分辨率（如 AV1 不可解码），自动换线/换编码。
            if (!isLiveMedia && readyAtMs > 0L && nowMs - readyAtMs > 3_000L &&
                videoSize.width <= 0 && failedReportedGeneration != mediaGeneration
            ) {
                failedReportedGeneration = mediaGeneration
                notifyError("视频画面解码异常")
            }
        }
        val state = playbackState
        if (state != lastReportedState) {
            lastReportedState = state
            listeners.toList().forEach { it.onPlaybackStateChanged(state) }
        }
        val playing = isPlaying
        if (playing != lastReportedPlaying) {
            lastReportedPlaying = playing
            listeners.toList().forEach { it.onIsPlayingChanged(playing) }
        }
        val playIntentNow = hasPlayIntent
        if (playIntentNow != lastReportedPlayIntent) {
            lastReportedPlayIntent = playIntentNow
            listeners.toList().forEach { it.onPlayIntentChanged(playIntentNow) }
        }
        val dims = videoSize
        if (dims.width > 0 && dims.height > 0 && (dims.width != lastReportedWidth || dims.height != lastReportedHeight)) {
            lastReportedWidth = dims.width
            lastReportedHeight = dims.height
            diag("ios") { "videoSize=${dims.width}x${dims.height}" }
            listeners.toList().forEach { it.onVideoSizeChanged(dims.width, dims.height) }
        }
        // 系统音量键：输出音量变了就记一条，给「呼出音量 HUD 时闪屏」当时间锚点。
        val outVol = AVAudioSession.sharedInstance().outputVolume
        if (outVol != lastOutputVolume) {
            lastOutputVolume = outVol
            diag("ios") { "outputVolume=$outVol cat=${AVAudioSession.sharedInstance().category ?: "?"}" }
        }
    }

    private fun ensureAudioSession() {
        runCatching {
            memScoped {
                val session = AVAudioSession.sharedInstance()
                val catErr = alloc<ObjCObjectVar<NSError?>>()
                var okCat = session.setCategory(
                    AVAudioSessionCategoryPlayback,
                    mode = AVAudioSessionModeMoviePlayback,
                    options = 0u,
                    error = catErr.ptr,
                )
                if (!okCat) {
                    catErr.value = null
                    okCat = session.setCategory(
                        AVAudioSessionCategoryPlayback,
                        mode = AVAudioSessionModeDefault,
                        options = 0u,
                        error = catErr.ptr,
                    )
                }
                val actErr = alloc<ObjCObjectVar<NSError?>>()
                val okAct = session.setActive(true, error = actErr.ptr)
                println(
                    "BiliPlayer.audioSession okCat=$okCat catErr=${catErr.value?.localizedDescription ?: "none"} okAct=$okAct actErr=${actErr.value?.localizedDescription ?: "none"} cat=${session.category ?: "?"} mode=${session.mode ?: "?"}",
                )
            }
        }.onFailure {
            println("BiliPlayer.audioSession err=${it.message ?: "unknown"}")
        }
    }

    private fun notifyError(msg: String) {
        println("BiliPlayer.error msg=$msg")
        listeners.toList().forEach { it.onPlayerError(msg) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PlayerContainerView(
    private val playerLayer: AVPlayerLayer,
) : UIView(frame = CGRectZero.readValue()) {
    private var lastWidth = -1.0
    private var lastHeight = -1.0

    init {
        backgroundColor = UIColor.blackColor
        clipsToBounds = true
        // 手势 100% 由上层 Compose PlayerGestureLayer 处理，禁用原生 UIKit 命中测试，
        // 避免手指滑到视频区域时触发 UIKitView 触摸仲裁延迟与 CoreAnimation 同步闪烁。
        userInteractionEnabled = false
        playerLayer.contentsScale = UIScreen.mainScreen.scale
        val noAction = NSNull.`null`()
        playerLayer.actions = mapOf(
            "bounds" to noAction,
            "position" to noAction,
            "frame" to noAction,
            "opacity" to noAction,
            "hidden" to noAction,
        )
        layer.addSublayer(playerLayer)
    }

    /**
     * 只有尺寸真的变了才动 [playerLayer]。
     *
     * 之前每次布局都无条件写一遍 frame，而且 `BiliPlayerSurface` 的 update 每帧都在
     * `setNeedsLayout()`，于是系统音量 HUD、转场这类 UIKit 动画窗口里，播放图层会一遍遍
     * 重新起隐式动画，画面看着一闪一闪。尺寸没变就直接返回，别的什么也不做。
     */
    override fun layoutSubviews() {
        super.layoutSubviews()
        val w = CGRectGetWidth(bounds)
        val h = CGRectGetHeight(bounds)
        if (w == lastWidth && h == lastHeight) return
        diag("ios") { "containerLayout ${lastWidth}x${lastHeight} -> ${w}x$h anim=${UIView.inheritedAnimationDuration}" }
        lastWidth = w
        lastHeight = h
        val animDuration = UIView.inheritedAnimationDuration
        CATransaction.begin()
        if (animDuration > 0.0) {
            CATransaction.setAnimationDuration(animDuration)
        } else {
            CATransaction.setDisableActions(true)
        }
        playerLayer.frame = bounds
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun BiliPlayerSurface(
    player: BiliPlayer,
    resizeMode: VideoResizeMode,
    modifier: Modifier,
) {
    val playerLayer = remember(player) {
        AVPlayerLayer.playerLayerWithPlayer(player.avPlayer)
    }
    val alphaProvider = LocalPlayerSurfaceAlpha.current
    UIKitView(
        factory = {
            diag("ios") { "surfaceFactory" }
            PlayerContainerView(playerLayer).apply {
                playerLayer.videoGravity = when (resizeMode) {
                    VideoResizeMode.FIT -> AVLayerVideoGravityResizeAspect
                    VideoResizeMode.ZOOM -> AVLayerVideoGravityResizeAspectFill
                    VideoResizeMode.FILL -> AVLayerVideoGravityResize
                }
                val nativeAlpha = alphaProvider().coerceIn(0f, 1f)
                alpha = nativeAlpha.toDouble()
                hidden = nativeAlpha <= 0.01f
            }
        },
        update = { view ->
            val gravity = when (resizeMode) {
                VideoResizeMode.FIT -> AVLayerVideoGravityResizeAspect
                VideoResizeMode.ZOOM -> AVLayerVideoGravityResizeAspectFill
                VideoResizeMode.FILL -> AVLayerVideoGravityResize
            }
            if (playerLayer.videoGravity != gravity) playerLayer.videoGravity = gravity
            val nativeAlpha = alphaProvider().coerceIn(0f, 1f).toDouble()
            if (view.alpha != nativeAlpha) view.alpha = nativeAlpha
            val hide = nativeAlpha <= 0.01
            if (view.hidden != hide) view.hidden = hide
        },
        properties = UIKitInteropProperties(
            interactionMode = null,
            isNativeAccessibilityEnabled = false,
        ),
        modifier = modifier,
    )
}

private data class DashSegment(
    val offset: Long,
    val size: Long,
    val durationSec: Double,
)

private data class Fmp4SidxIndex(
    val initLength: Long,
    val segments: List<DashSegment>,
)

private sealed interface SidxProbeResult {
    data class Ready(val index: Fmp4SidxIndex) : SidxProbeResult
    data object NotFragmented : SidxProbeResult
    data class Failed(val message: String) : SidxProbeResult
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun String.toUtf8NSData(): NSData =
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

private fun parseSidxBox(
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

private fun resolvePlayerNsUrl(url: String): NSURL? =
    if (url.startsWith("file://", ignoreCase = true)) {
        NSURL.fileURLWithPath(dlFileUriToPath(url))
    } else {
        NSURL.URLWithString(url)
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
private fun probeLocalFmp4Sidx(path: String): SidxProbeResult {
    val buf = readLocalFileRange(path, 0L, 32_768)
        ?: return SidxProbeResult.Failed("本地缓存文件读取失败")
    if (buf.size < 16) return SidxProbeResult.NotFragmented
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
        return SidxProbeResult.NotFragmented
    }
    val fileOffsetAfterSidx = sidxStart + sidxSize
    if (fileOffsetAfterSidx <= buf.size.toLong()) {
        val parsed = parseSidxBox(
            buf = buf,
            sidxOffsetInBuf = sidxStart.toInt(),
            sidxSize = sidxSize.toInt(),
            initLength = initEnd,
            fileOffsetAfterSidx = fileOffsetAfterSidx,
        )
        return if (parsed != null) SidxProbeResult.Ready(parsed) else SidxProbeResult.NotFragmented
    }
    val sidxBuf = readLocalFileRange(path, sidxStart, sidxSize.toInt())
        ?: return SidxProbeResult.Failed("本地媒体索引读取失败")
    val parsed = parseSidxBox(
        buf = sidxBuf,
        sidxOffsetInBuf = 0,
        sidxSize = sidxBuf.size,
        initLength = initEnd,
        fileOffsetAfterSidx = fileOffsetAfterSidx,
    )
    return if (parsed != null) SidxProbeResult.Ready(parsed) else SidxProbeResult.NotFragmented
}

@OptIn(ExperimentalForeignApi::class)
private fun probeFmp4SidxAsync(
    urlString: String,
    headers: Map<String, String>,
    localQueue: platform.darwin.dispatch_queue_t,
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
            onDone(SidxProbeResult.NotFragmented)
            return@fetchHttpRange
        }
        val fileOffsetAfterSidx = sidxStart + sidxSize
        if (fileOffsetAfterSidx <= buf.size.toLong()) {
            val parsed = parseSidxBox(
                buf = buf,
                sidxOffsetInBuf = sidxStart.toInt(),
                sidxSize = sidxSize.toInt(),
                initLength = initEnd,
                fileOffsetAfterSidx = fileOffsetAfterSidx,
            )
            onDone(if (parsed != null) SidxProbeResult.Ready(parsed) else SidxProbeResult.NotFragmented)
            return@fetchHttpRange
        }
        // 超长视频的 sidx 超过 32KB 时按精确区间补拉一次。
        fetchHttpRange(urlString, headers, sidxStart, fileOffsetAfterSidx - 1L) { sidxBuf, _, sidxErr ->
            if (sidxErr != null || sidxBuf == null) {
                onDone(SidxProbeResult.Failed(sidxErr ?: "媒体索引读取失败"))
                return@fetchHttpRange
            }
            val parsed = parseSidxBox(
                buf = sidxBuf,
                sidxOffsetInBuf = 0,
                sidxSize = sidxBuf.size,
                initLength = initEnd,
                fileOffsetAfterSidx = fileOffsetAfterSidx,
            )
            onDone(if (parsed != null) SidxProbeResult.Ready(parsed) else SidxProbeResult.NotFragmented)
        }
    }
}

private fun formatSeconds6(sec: Double): String {
    val scaled = round(sec * 1_000_000.0).toLong().coerceAtLeast(1L)
    val whole = scaled / 1_000_000L
    val frac = (scaled % 1_000_000L).toString().padStart(6, '0')
    return "$whole.$frac"
}

private fun buildHlsMediaPlaylist(mediaUrl: String, index: Fmp4SidxIndex): String {
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

private fun buildHlsMasterPlaylist(generation: Int): String = buildString {
    append("#EXTM3U\n")
    append("#EXT-X-VERSION:7\n")
    append("#EXT-X-INDEPENDENT-SEGMENTS\n")
    append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"Audio\",DEFAULT=YES,AUTOSELECT=YES,URI=\"bilicohls://gen-")
    append(generation)
    append("/audio.m3u8\"\n")
    append("#EXT-X-STREAM-INF:BANDWIDTH=4000000,AUDIO=\"aud\"\n")
    append("bilicohls://gen-").append(generation).append("/video.m3u8\n")
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class DashHlsResourceLoader(
    private val masterData: NSData?,
    private val videoData: NSData,
    private val audioData: NSData?,
    private val localVideoPath: String? = null,
    private val localAudioPath: String? = null,
) : NSObject(), AVAssetResourceLoaderDelegateProtocol {

    override fun resourceLoader(
        resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest,
    ): Boolean {
        val path = shouldWaitForLoadingOfRequestedResource.request.URL?.path ?: return false
        if (path == "/video.m4s" || path == "/audio.m4s") {
            val filePath = if (path == "/video.m4s") localVideoPath else localAudioPath
            if (filePath.isNullOrBlank()) return false
            val mime = if (path == "/video.m4s") "video/mp4" else "audio/mp4"
            return serveLocalMediaFile(filePath, mime, shouldWaitForLoadingOfRequestedResource)
        }
        val data = when (path) {
            "/master.m3u8" -> masterData
            "/video.m3u8" -> videoData
            "/audio.m3u8" -> audioData
            else -> null
        } ?: return false

        val totalLen = data.length.toLong()
        shouldWaitForLoadingOfRequestedResource.contentInformationRequest?.apply {
            contentType = "application/vnd.apple.mpegurl"
            contentLength = totalLen
            byteRangeAccessSupported = true
        }
        shouldWaitForLoadingOfRequestedResource.dataRequest?.let { dataReq ->
            val offset = dataReq.requestedOffset.coerceIn(0L, totalLen)
            val maxAvail = (totalLen - offset).coerceAtLeast(0L)
            val length = if (dataReq.requestsAllDataToEndOfResource) {
                maxAvail
            } else {
                minOf(dataReq.requestedLength.toLong().coerceAtLeast(0L), maxAvail)
            }
            if (length > 0L) {
                val slice = if (offset == 0L && length == totalLen) {
                    data
                } else {
                    data.subdataWithRange(
                        platform.Foundation.NSMakeRange(offset.toULong(), length.toULong()),
                    )
                }
                dataReq.respondWithData(slice)
            }
        }
        shouldWaitForLoadingOfRequestedResource.finishLoading()
        return true
    }

    private fun serveLocalMediaFile(
        filePath: String,
        mimeType: String,
        loadingRequest: AVAssetResourceLoadingRequest,
    ): Boolean {
        val totalLen = dlFileLength(filePath)
        if (totalLen <= 0L) return false
        loadingRequest.contentInformationRequest?.apply {
            contentType = mimeType
            contentLength = totalLen
            byteRangeAccessSupported = true
        }
        loadingRequest.dataRequest?.let { dataReq ->
            val offset = dataReq.requestedOffset.coerceIn(0L, totalLen)
            val maxAvail = (totalLen - offset).coerceAtLeast(0L)
            var remaining = if (dataReq.requestsAllDataToEndOfResource) {
                maxAvail
            } else {
                minOf(dataReq.requestedLength.toLong().coerceAtLeast(0L), maxAvail)
            }
            if (remaining > 0L) {
                val fp = fopen(filePath, "rb") ?: return false
                try {
                    if (fseeko(fp, offset, SEEK_SET) != 0) return false
                    val chunkBuf = ByteArray(128 * 1024)
                    while (remaining > 0L) {
                        val toRead = minOf(remaining, chunkBuf.size.toLong()).toInt()
                        val read = chunkBuf.usePinned { pinned ->
                            fread(pinned.addressOf(0), 1uL, toRead.toULong(), fp).toInt()
                        }
                        if (read <= 0) break
                        val nsChunk = chunkBuf.usePinned { pinned ->
                            NSData.create(bytes = pinned.addressOf(0), length = read.toULong())
                        }
                        dataReq.respondWithData(nsChunk)
                        remaining -= read.toLong()
                    }
                } finally {
                    fclose(fp)
                }
            }
        }
        loadingRequest.finishLoading()
        return true
    }
}
